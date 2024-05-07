package org.example;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Arrays.*;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    public static void main(String[] args) {

        String directoryPathString = args[0].substring(1);
        boolean recursive = false;
        int maxDepth = -1;
        int threadCount = 1;
        Set<String> includeExtensions = new HashSet<>();
        Set<String> excludeExtensions = new HashSet<>();
        boolean gitIgnore = false;
        String outputFormat = "plain";

        Path directoryPath = Paths.get(directoryPathString);

        for (String arg : args ) {
            switch (arg) {
                case "-recursive" -> recursive = true;
                case "-git-ignore" -> gitIgnore = true;
                case String s when s.startsWith("-max-depth") -> {
                    String subS = s.substring(s.indexOf("=")+1);
                    maxDepth = Integer.parseInt(subS);
                }
                case String s when s.startsWith("-include-ext") -> {
                    String subS = s.substring(s.indexOf("=")+1);
                    String[] items = subS.split(",");
                    includeExtensions.addAll(asList(items));
                }
                case String s when s.startsWith("-exclude-ext") -> {
                    String subS = s.substring(s.indexOf("=")+1);
                    String[] items = subS.split(",");
                    excludeExtensions.addAll(asList(items));
                }
                case String s when s.startsWith("-git-ignore") -> {
                    gitIgnore = true;
                }
                case String s when s.startsWith("-output") -> {
                    outputFormat = s.substring(s.indexOf("=")+1);
                }
                default -> {}
            }

        }

        FileStatisticsUtility utility = new FileStatisticsUtility(directoryPath, recursive, maxDepth, threadCount,
                includeExtensions, excludeExtensions, gitIgnore, outputFormat);

        try {
            utility.calculateStatistics();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}