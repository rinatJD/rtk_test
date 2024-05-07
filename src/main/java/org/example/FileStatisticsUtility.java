package org.example;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequiredArgsConstructor
public class FileStatisticsUtility {
    private final Path directoryPath;
    private final boolean recursive;
    private final int maxDepth;
    private final int threadCount;
    private final Set<String> includeExtensions;
    private final Set<String> excludeExtensions;
    private final boolean gitIgnore;
    private final String outputFormat;

    private final Map<String, FileStatistics> statisticsMap = new HashMap<>();
    Set<String> ignorePatterns = new HashSet<>();

    public void calculateStatistics() throws Exception {
        ignorePatterns = readGitIgnorePatterns();
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        Files.walkFileTree(directoryPath, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!recursive && !dir.equals(directoryPath)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                int depth = directoryPath.relativize(dir).getNameCount();
                return (maxDepth == -1 || depth <= maxDepth) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (includeExtensions.isEmpty() || includeExtensions.contains(getFileExtension(file))) {
                    if (excludeExtensions.isEmpty() || !excludeExtensions.contains(getFileExtension(file))) {
                        if (gitIgnore && isIgnored(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        executorService.execute(() -> {
                            try {
                                processFile(file);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        executorService.shutdown();
        while (!executorService.isTerminated()) {

        }
        outputStatistics();
    }
    private Set<String> readGitIgnorePatterns() throws IOException {
        Set<String> patterns = new HashSet<>();
        Path gitIgnorePath = Paths.get(String.valueOf(directoryPath), ".gitignore");

        if (Files.exists(gitIgnorePath)) {
            try (BufferedReader reader = Files.newBufferedReader(gitIgnorePath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty() && !line.trim().startsWith("#")) {
                        patterns.add(line.trim());
                    }
                }
            }
        }

        return patterns;
    }
    private boolean isIgnored(Path file) {
        Path relativePath = Paths.get(directoryPath.toString()).relativize(file);
        String filePath = relativePath.toString().replace('\\', '/');

        return ignorePatterns.stream()
                .anyMatch(x -> x.contains(relativePath.toString()));
    }
    private void processFile(Path file) throws IOException {
        String extension = getFileExtension(file);
        FileStatistics fileStats = statisticsMap.getOrDefault(extension, new FileStatistics());
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        fileStats.incrementFileCount();
        fileStats.incrementByteCount(Files.size(file));
        fileStats.incrementLineCount(lines.size());
        long nonEmptyLines = lines.stream().filter(line -> !line.trim().isEmpty()).count();
        fileStats.incrementNonEmptyLineCount(nonEmptyLines);
        long commentLines = lines.stream().filter(this::isCommentLine).count();
        fileStats.incrementCommentLineCount(commentLines);
        statisticsMap.put(extension, fileStats);
    }
    private boolean isCommentLine(String line) {
        return line.trim().startsWith("//") || line.trim().startsWith("#");
    }
    private String getFileExtension(Path file) {
        String fileName = file.getFileName().toString();
        int index = fileName.lastIndexOf(".");
        return fileName.substring(index + 1);
    }
    private void outputStatistics() throws Exception {
        switch (outputFormat) {
            case "xml" -> System.out.println(mapToXML(statisticsMap));
            case "json" -> System.out.println(mapToJson(statisticsMap));
            default -> statisticsMap.forEach((k, v) -> {
                System.out.println(k + ":");
                System.out.printf("Количество файлов: %d\n", v.getFileCount());
                System.out.printf("Размер в байтах: %d\n", v.getByteCount());
                System.out.printf("Количество строк всего %d\n", v.getLineCount());
                System.out.printf("Количество не пустых строк: %d\n", v.getNonEmptyLineCount());
                System.out.printf("Количество строк с комментариями: %d\n\n", v.getCommentLineCount());
            });
        }

    }
    public static String mapToXML(Map<String, FileStatistics> map) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();

        Element rootElement = doc.createElement("statistics");
        doc.appendChild(rootElement);

        for (Map.Entry<String, FileStatistics> entry : map.entrySet()) {
            Element entryElement = doc.createElement("entry");
            rootElement.appendChild(entryElement);

            Element keyElement = doc.createElement("key");
            keyElement.appendChild(doc.createTextNode(entry.getKey()));
            entryElement.appendChild(keyElement);

            Element valueElement = doc.createElement("value");
            FileStatistics fileStats = entry.getValue();
            valueElement.setAttribute("fileCount", String.valueOf(fileStats.getFileCount()));
            valueElement.setAttribute("byteCount", String.valueOf(fileStats.getByteCount()));
            valueElement.setAttribute("lineCount", String.valueOf(fileStats.getLineCount()));
            valueElement.setAttribute("nonEmptyLineCount", String.valueOf(fileStats.getNonEmptyLineCount()));
            valueElement.setAttribute("commentLineCount", String.valueOf(fileStats.getCommentLineCount()));
            entryElement.appendChild(valueElement);
        }

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.getBuffer().toString();
    }
    public static String mapToJson(Map<String, FileStatistics> map) {
        Gson gson = new Gson();
        return gson.toJson(map);
    }

}
