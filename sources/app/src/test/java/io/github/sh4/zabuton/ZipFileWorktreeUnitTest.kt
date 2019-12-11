package io.github.sh4.zabuton

import io.github.sh4.zabuton.util.PROGRESS_NOT_SPECIFIED
import io.github.sh4.zabuton.workspace.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URL
import java.util.*

class ZipFileWorktreeUnitTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun simpleTest () {
        val workspace = Workspace(WorkspaceId(UUID.randomUUID()), WorkspaceName("test"))
        runBlocking {
            var progressCount = 0
            createZipFileWorktree(
                    workspace,
                    tempFolder.root,
                    tempFolder.newFolder("cache"),
                    URL("https://github.com/sh4/test-git/archive/master.zip")
            ) { channel ->
                for (p in channel) {
                    val label = p.type.name
                    while (!p.finished) {
                        println("[$label] ${p.current} / ${p.total} bytes")
                        delay(500)
                    }
                    println("[$label] ${p.current} / ${p.total} bytes")
                    if (p.total != PROGRESS_NOT_SPECIFIED)
                    {
                        Assert.assertEquals(p.total, p.current)
                    }
                    progressCount++
                }
                Assert.assertEquals(2, progressCount)
            }
        }
    }
}