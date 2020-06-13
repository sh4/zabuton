package io.github.sh4.zabuton.git;

import java.util.function.Consumer;
import java.util.function.Function;

public class Repository {
    private final long repositoryHandle;

    private Repository(long repositoryHandle) {
        this.repositoryHandle = repositoryHandle;
    }

    public static native Repository open(String repoPath);

    // Cloning into bare repository 'repository.git'...
    // remote: Enumerating objects: 2, done.
    // remote: Counting objects: 100% (2/2), done.
    // remote: Compressing objects: 100% (2/2), done.
    // remote: Total 169903 (delta 0), reused 1 (delta 0), pack-reused 169901
    // Receiving objects: 100% (169903/169903), 126.51 MiB | 5.29 MiB/s, done.
    // Resolving deltas: 100% (112118/112118), done.
    public static native Repository clone(String url, String cloneRepoPath, Consumer<ICloneProgress> progress);

    public native void fetch(String remoteName, Consumer<IFetchProgress> progress);
    public native void checkout(String refspec, Consumer<ICheckoutProgress> progress);
    public native void reset(ResetKind resetKind, Consumer<ICheckoutProgress> progress);

    public native String getHeadName();
    public native String[] getRemoteBranchNames();
    public native String[] getLocalBranchNames();
    public native String[] getTagNames();
    public native Remote[] getRemotes();

    public native void log(Function<ICommitObject, Boolean> callback);

    @Override
    protected void finalize() throws Throwable {
        destroy();
        super.finalize();
    }

    private native void destroy();
}
