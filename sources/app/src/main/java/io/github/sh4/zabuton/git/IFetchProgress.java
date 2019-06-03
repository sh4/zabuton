package io.github.sh4.zabuton.git;

public interface IFetchProgress {
    long getTotalObjects();
    long getIndexedObjects();
    long getReceivedObjects();
    long getLocalObjects();
    long getTotalDeltas();
    long getIndexedDeltas();
    long getReceivedBytes();
    String getSidebandMessage();
}
