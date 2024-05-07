package org.example;

import lombok.Getter;

@Getter
public class FileStatistics {

    private int fileCount;
    private long byteCount;
    private long lineCount;
    private long nonEmptyLineCount;
    private long commentLineCount;

    public void incrementFileCount() {
        fileCount++;
    }
    public void incrementByteCount(long size) {
        byteCount += size;
    }
    public void incrementLineCount(int size) {
        lineCount += size;
    }
    public void incrementNonEmptyLineCount(long nonEmptyLines) {
        nonEmptyLineCount += nonEmptyLines;
    }
    public void incrementCommentLineCount(long commentLines) {
        commentLineCount += commentLines;
    }

}
