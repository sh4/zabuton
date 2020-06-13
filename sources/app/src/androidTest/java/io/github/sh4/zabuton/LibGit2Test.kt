package io.github.sh4.zabuton

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sh4.zabuton.git.*
import io.github.sh4.zabuton.workspace.initializeLibGit2
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.charset.Charset

@RunWith(AndroidJUnit4::class)
class LibGit2Test {
    companion object {
        private const val CLONE_URL = "https://github.com/sh4/test-git.git"

        init {
            System.loadLibrary("native-lib")
        }
    }

    @Test
    @Throws(IOException::class)
    fun cloneAndOpen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reposPath = context.getDir("test-repos", Context.MODE_PRIVATE)
        reposPath.deleteRecursively()
        initializeLibGit2(context)
        val p = arrayOf<ICloneProgress?>(null)
        Assert.assertNotNull(Repository.clone(CLONE_URL, reposPath.absolutePath) { cur: ICloneProgress? -> p[0] = cur })
        Assert.assertNotEquals(0, p[0]!!.completedSteps)
        Assert.assertNotEquals(0, p[0]!!.totalSteps)
        Assert.assertNotEquals(0, p[0]!!.indexedDeltas)
        Assert.assertNotEquals(0, p[0]!!.indexedObjects)
        Assert.assertEquals(0, p[0]!!.localObjects) // Local object is 0 because it is clone to empty repository.
        Assert.assertNotEquals(0, p[0]!!.receivedBytes)
        Assert.assertNotEquals(0, p[0]!!.receivedObjects)
        Assert.assertNotEquals(0, p[0]!!.totalDeltas)
        Assert.assertNotEquals(0, p[0]!!.totalObjects)
        Assert.assertNotEquals(1, reposPath.walk().count())
        Assert.assertNotNull(Repository.open(reposPath.absolutePath))
    }

    @Test
    fun fetchRemote() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initializeLibGit2(context)
        val reposPath = context.getDir("test-repos", Context.MODE_PRIVATE)
        val repos = ensureRepositoryOpened(reposPath)
        Assert.assertNotNull(repos)
        val p = arrayOf<IFetchProgress?>(null)
        repos.fetch("origin") { cur: IFetchProgress? -> p[0] = cur }
        Assert.assertNotNull(p[0])
        //assertNotEquals(0, p[0].getReceivedBytes());
    }

    private fun ensureRepositoryOpened(reposPath: File): Repository {
        if (reposPath.exists()) {
            return Repository.open(reposPath.absolutePath)
        } else {
            return Repository.clone(CLONE_URL, reposPath.absolutePath) { cur: ICloneProgress? -> }
        }
    }

    @Test
    @Throws(IOException::class)
    fun checkoutBranch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initializeLibGit2(context)
        val reposPath = context.getDir("test-repos", Context.MODE_PRIVATE)
        reposPath.deleteRecursively()
        val repos = Repository.clone(CLONE_URL, reposPath.absolutePath) { cur: ICloneProgress? -> }
        repos.fetch("origin") { cur: IFetchProgress? -> }
        val anotherBranchFile = File(reposPath, "Test/Another.txt")
        Assert.assertFalse(anotherBranchFile.exists())
        repos.checkout("origin/sh4-patch-1") { p: ICheckoutProgress? -> }
        Assert.assertTrue(anotherBranchFile.exists())
        repos.checkout("master") { p: ICheckoutProgress? -> }
        Assert.assertFalse(anotherBranchFile.exists())
    }

    @Test
    @Throws(IOException::class)
    fun reset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initializeLibGit2(context)
        val reposPath = context.getDir("test-repos", Context.MODE_PRIVATE)
        val testFile = File(reposPath, "Test/Files.txt")
        testFile.writeText("Foobar2000", Charset.defaultCharset());
        Assert.assertEquals("Foobar2000", testFile.readText(Charset.defaultCharset()))
        val repos = ensureRepositoryOpened(reposPath)
        repos.reset(ResetKind.HARD) { p: ICheckoutProgress? -> }
        Assert.assertNotEquals("Foobar2000", testFile.readText(Charset.defaultCharset()))
    }

    @Test
    @Throws(IOException::class)
    fun branchOperations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initializeLibGit2(context)
        val reposPath = context.getDir("test-repos", Context.MODE_PRIVATE)
        reposPath.deleteRecursively()
        val repos = ensureRepositoryOpened(reposPath)
        var branch: String?
        branch = repos.headName
        Assert.assertEquals("master", branch)
        repos.checkout("origin/sh4-patch-1") { p: ICheckoutProgress? -> }
        branch = repos.headName
        Assert.assertEquals("sh4-patch-1", repos.headName)
        repos.checkout("origin/master") { p: ICheckoutProgress? -> }
        branch = repos.headName
        Assert.assertEquals("master", repos.headName)
        run {
            val names = repos.localBranchNames
            Assert.assertArrayEquals(arrayOf("master", "sh4-patch-1"), names)
        }
        run {
            val names = repos.remoteBranchNames
            Assert.assertArrayEquals(arrayOf("origin/master", "origin/sh4-patch-1"), names)
        }
    }

    @Test
    fun tags() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initializeLibGit2(context)
        val reposPath = context.getDir("test-repos", Context.MODE_PRIVATE)
        reposPath.deleteRecursively()
        val repos = ensureRepositoryOpened(reposPath)
        val tags = repos.tagNames
        Assert.assertTrue(tags.isNotEmpty())
    }

    @Test
    fun logs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        initializeLibGit2(context)
        val reposPath = context.getDir("test-repos", Context.MODE_PRIVATE)
        reposPath.deleteRecursively()
        val repos = ensureRepositoryOpened(reposPath)
        repos.log { commit ->
            Assert.assertNotNull(commit)
            return@log true
        }
    }
}