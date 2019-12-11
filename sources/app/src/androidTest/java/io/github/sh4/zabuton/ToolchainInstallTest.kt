package io.github.sh4.zabuton

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import io.github.sh4.zabuton.app.toolchainInstall
import io.github.sh4.zabuton.app.toolchainRoot
import io.github.sh4.zabuton.util.Progress
import io.github.sh4.zabuton.util.ProgressContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

private val TAG = ToolchainInstallTest::class.java.simpleName

@RunWith(AndroidJUnit4::class)
class ToolchainInstallTest {
    @Test
    fun testCleanInstall() {
        val context = getInstrumentation().targetContext
        val filesDir = context.filesDir
        Assert.assertNotNull(filesDir)
        context.assets.open("build/toolchain.zip").use {
            Assert.assertNotNull(it)
        }
        val elapsed = measureTimeMillis {
            runBlocking {
                toolchainInstall(context) { channel ->
                    val p = channel.receive()
                    while (!p.finished) {
                        Log.d(TAG, "progress: [${Thread.currentThread().name}] ${p.current} / ${p.total} [bytes]")
                        delay(1000)
                    }
                }
            }
        }
        Log.d(TAG, "elapsed time = $elapsed [ms]")
        val root = toolchainRoot(context)
        Assert.assertTrue(root.exists())
    }
}