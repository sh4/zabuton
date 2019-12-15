package io.github.sh4.zabuton

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sh4.zabuton.workspace.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.util.*

@RunWith(AndroidJUnit4::class)
class GitRepositoryWorktreeTest {
    private val TAG = GitRepositoryWorktreeTest::class.java.simpleName

    companion object {
        private val CLONE_URL = URL("https://github.com/sh4/test-git.git")

        init {
            System.loadLibrary("native-lib")
        }
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun cloneTest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initializeLibGit2(context)
        val worktree = runBlocking {
            val workspace = Workspace(WorkspaceId(UUID.randomUUID()), WorkspaceName("test"))
            return@runBlocking createGitRepositoryWorktree(
                    workspace,
                    tempFolder.root,
                    CLONE_URL
            ) { channel ->
                val p = channel.receive()
                while (!p.finished) {
                    Log.d(TAG, "Clone Progress ${p.current} / ${p.total}")
                    delay(500)
                }
                Log.d(TAG, "Clone Finished ${p.current} / ${p.total}")
            }
        }
        Log.d(TAG, "Cloned repsitory test")
        Assert.assertNotNull(worktree)
        Assert.assertTrue(worktree.root.exists())
        Assert.assertTrue(File(worktree.root, ".git").exists())
        runBlocking {
            Log.d(TAG, "Fetch origin")
            worktree.fetch("origin") { }

            Log.d(TAG, "Checkout remote and local refs")
            worktree.checkout("origin/sh4-patch-1") {}
            worktree.checkout("master") {}
            worktree.checkout("origin/master") {}
        }
    }
}