package io.github.sh4.zabuton.git;

public class LibGit2Exception extends Exception {
    private static native String getLastError();

    private final int returnCode;

    public LibGit2Exception(int returnCode) {
        super(getLastError());
        this.returnCode = returnCode;
    }

    public int getReturnCode() {
        return returnCode;
    }
}
