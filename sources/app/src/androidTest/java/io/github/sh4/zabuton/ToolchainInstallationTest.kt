package io.github.sh4.zabuton

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import io.github.sh4.zabuton.app.InstallProgress
import io.github.sh4.zabuton.app.ToolchainInstaller
import io.github.sh4.zabuton.app.toolchainRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

private val TAG = ToolchainInstallationTest::class.java.simpleName

@RunWith(AndroidJUnit4::class)
class ToolchainInstallationTest {
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
                val progress = InstallProgress()
                ToolchainInstaller(progress).install(context) {
                    while (!progress.finished) {
                        Log.d(TAG, "progress: [${Thread.currentThread().name}] ${progress.current} / ${progress.total} [bytes]")
                        delay(1500)
                    }
                }
            }
        }
        Log.d(TAG, "elapsed time = $elapsed [ms]")
        val root = toolchainRoot(context)
        Assert.assertTrue(root.exists())
    }
}