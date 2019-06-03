package io.github.sh4.zabuton.git;

class FetchProgress implements IFetchProgress {
    private long totalObjects;
    private long indexedObjects;
    private long receivedObjects;
    private long localObjects;
    private long totalDeltas;
    private long indexedDeltas;
    private long receivedBytes;
    private String sidebandMessage;

    @Override
    public long getTotalObjects() {
        return totalObjects;
    }

    @Override
    public long getIndexedObjects() {
        return indexedObjects;
    }

    @Override
    public long getReceivedObjects() {
        return receivedObjects;
    }

    @Override
    public long getLocalObjects() {
        return localObjects;
    }

    @Override
    public long getTotalDeltas() {
        return totalDeltas;
    }

    @Override
    public long getIndexedDeltas() {
        return indexedDeltas;
    }

    @Override
    public long getReceivedBytes() {
        return receivedBytes;
    }

    @Override
    public String getSidebandMessage() {
        return sidebandMessage;
    }
}
