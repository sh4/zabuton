package io.github.sh4.zabuton;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import io.github.sh4.zabuton.git.ICloneProgress;
import io.github.sh4.zabuton.git.IFetchProgress;
import io.github.sh4.zabuton.git.LibGit2;
import io.github.sh4.zabuton.git.Repository;
import io.github.sh4.zabuton.git.ResetKind;
import io.github.sh4.zabuton.util.ContextUtil;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LibGit2Test {
    static {
        System.loadLibrary("native-lib");
    }

    private static final String CLONE_URL = "https://github.com/sh4/test-git.git";

    @Test
    public void cloneAndOpen() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Path reposPath = context.getDir("test-repos", Context.MODE_PRIVATE).toPath();
        cleanupRepos(reposPath);
        initLibGit2(context);
        final ICloneProgress[] p = {null};
        assertNotNull(Repository.clone(CLONE_URL, reposPath.toString(), cur -> p[0] = cur));
        assertNotEquals(0, p[0].getCompletedSteps());
        assertNotEquals(0, p[0].getTotalSteps());
        assertNotEquals(0, p[0].getIndexedDeltas());
        assertNotEquals(0, p[0].getIndexedObjects());
        assertEquals(0, p[0].getLocalObjects()); // Local object is 0 because it is clone to empty repository.
        assertNotEquals(0, p[0].getReceivedBytes());
        assertNotEquals(0, p[0].getReceivedObjects());
        assertNotEquals(0, p[0].getTotalDeltas());
        assertNotEquals(0, p[0].getTotalObjects());
        assertNotEquals(1, Files.walk(reposPath).count());

        assertNotNull(Repository.open(reposPath.toString()));
    }

    @Test
    public void fetchRemote() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        initLibGit2(context);
        Path reposPath = context.getDir("test-repos", Context.MODE_PRIVATE).toPath();
        Repository repos = ensureRepositoryOpened(reposPath);
        assertNotNull(repos);
        final IFetchProgress[] p = {null};
        repos.fetch("origin", cur -> p[0] = cur);
        assertNotNull(p[0]);
        //assertNotEquals(0, p[0].getReceivedBytes());
    }

    private Repository ensureRepositoryOpened(Path reposPath) {
        return Files.exists(reposPath)
                ? Repository.open(reposPath.toString())
                : Repository.clone(CLONE_URL, reposPath.toString(), cur -> {});
    }

    @Test
    public void checkoutBranch()  throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        initLibGit2(context);
        Path reposPath = context.getDir("test-repos", Context.MODE_PRIVATE).toPath();
        cleanupRepos(reposPath);
        Repository repos = Repository.clone(CLONE_URL, reposPath.toString(), cur -> {});
        repos.fetch("origin", cur -> {});

        File anotherBranchFile = new File(reposPath.toFile(), "Test/Another.txt");
        assertFalse(anotherBranchFile.exists());

        repos.checkout("origin/sh4-patch-1", p -> {});
        assertTrue(anotherBranchFile.exists());

        repos.checkout("master", p -> {});
        assertFalse(anotherBranchFile.exists());
    }

    @Test
    public void reset() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        initLibGit2(context);
        Path reposPath = context.getDir("test-repos", Context.MODE_PRIVATE).toPath();
        File testFile = new File(reposPath.toFile(), "Test/Files.txt");

        FileUtils.writeStringToFile(testFile, "Foobar2000", "utf-8");
        assertEquals("Foobar2000", FileUtils.readFileToString(testFile, "utf-8"));

        Repository repos = ensureRepositoryOpened(reposPath);
        repos.reset(ResetKind.HARD, p -> {});

        assertNotEquals("Foobar2000", FileUtils.readFileToString(testFile, "utf-8"));
    }

    @Test
    public void branchOperations() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        initLibGit2(context);
        Path reposPath = context.getDir("test-repos", Context.MODE_PRIVATE).toPath();
        cleanupRepos(reposPath);
        Repository repos = ensureRepositoryOpened(reposPath);

        String branch;
        branch = repos.getHeadName();
        assertEquals("master", branch);
        repos.checkout("origin/sh4-patch-1", p -> {});

        branch = repos.getHeadName();
        assertEquals("sh4-patch-1", repos.getHeadName());
        repos.checkout("origin/master", p -> {});

        branch = repos.getHeadName();
        assertEquals("master", repos.getHeadName());

        {
            String[] names = repos.getLocalBranchNames();
            assertArrayEquals(new String[] {"master", "sh4-patch-1"}, names);
        }

        {
            String[] names = repos.getRemoteBranchNames();
            assertArrayEquals(new String[] {"origin/master", "origin/sh4-patch-1"}, names);
        }
    }

    private void cleanupRepos(Path reposPath) throws IOException {
        if (Files.exists(reposPath)) {
            Files.walk(reposPath)
                    .sorted(Comparator.reverseOrder())
                    .filter(x -> !reposPath.equals(x))
                    .map(Path::toFile)
                    .forEach(File::delete);
            Files.delete(reposPath);
        }
    }

    private void initLibGit2(Context context) {
        File sslCertificatesFile = ContextUtil.writeToFilesDir(context, "build/cacert.pem");
        assertNotNull(sslCertificatesFile);
        LibGit2.init(sslCertificatesFile.getAbsolutePath());
    }
}
