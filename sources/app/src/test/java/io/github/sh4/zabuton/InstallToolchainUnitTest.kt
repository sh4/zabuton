package io.github.sh4.zabuton

import android.util.Log
import io.github.sh4.zabuton.app.InstallProgress
import io.github.sh4.zabuton.app.installToolchain
import io.github.sh4.zabuton.app.toolchainRoot
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLog

private val TAG = InstallToolchainUnitTest::class.java.simpleName

@RunWith(RobolectricTestRunner::class)
class InstallToolchainUnitTest {
    @Before
    fun setup() {
        ShadowLog.stream = System.out
    }

    @Test
    fun testCleanInstall() {
        val context = RuntimeEnvironment.application.applicationContext
        val filesDir = context.filesDir
        Assert.assertNotNull(filesDir)
        context.assets.open("build/toolchain.zip").use {
            Assert.assertNotNull(it)
        }
        installToolchain(context, InstallProgress({ current, total ->
             Log.d(TAG, "current = " + current + ", total = " + total)
        }, 3000))
        val root = toolchainRoot(context)
        Assert.assertTrue(root.exists())

        //val context = getApplicatonContext()
        //context.filesDir
    }
}