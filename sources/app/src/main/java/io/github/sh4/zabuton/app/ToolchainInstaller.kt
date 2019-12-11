package io.github.sh4.zabuton.app

import android.content.Context
import android.system.Os
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import io.github.sh4.zabuton.util.Progress
import io.github.sh4.zabuton.util.ProgressContext
import io.github.sh4.zabuton.util.extractZipAsParallel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import okio.Okio
import java.io.File
import java.io.InputStreamReader

private const val INSTALL_PATH_PREFIX = "root"
private const val INSTALL_PATH_PREFIX_TMP = "root.tmp"
private const val INSTALL_TOOLCHAIN = "build/toolchain.zip"
private const val INSTALL_SYMLINK_MAPS = "build/symlinkMaps.json"
private val INSTALL_EXECUTABLE_DIRS = arrayOf("avr", "bin", "libexec")

private const val MODE_OWNER_EXECUTE = 64
private const val MODE_OWNER_WRITE = 128
private const val MODE_OWNER_READ = 256
private const val MODE_OWNER_RWX = MODE_OWNER_EXECUTE + MODE_OWNER_WRITE + MODE_OWNER_READ

@JsonClass(generateAdapter = true)
data class SymlinkMapEntry(val target: String, val src: String)
@JsonClass(generateAdapter = true)
data class SymlinkMaps(val files: List<SymlinkMapEntry>)


fun toolchainRoot(context: Context) = File(context.filesDir, INSTALL_PATH_PREFIX)

suspend fun toolchainInstall(
        context: Context,
        block: suspend CoroutineScope.(channel: ReceiveChannel<Progress>) -> Unit
) = coroutineScope {
    val targetPrefix = File(context.filesDir, INSTALL_PATH_PREFIX_TMP)
    if (targetPrefix.exists()) {
        targetPrefix.deleteRecursively()
    }
    targetPrefix.mkdir()
    extractZipAsParallel({ context.assets.open(INSTALL_TOOLCHAIN) }, targetPrefix, block)
    val toolchainRoot = File(context.filesDir, INSTALL_PATH_PREFIX)
    replaceToolchainRoot(context, toolchainRoot, targetPrefix)
    installPostProcess(toolchainRoot, context)
}

private fun replaceToolchainRoot(context: Context, toolchainRoot: File, targetPrefix: File) {
    val toolchainInstalled = toolchainRoot.exists()
    // delete previous toolchain tree
    val previousToolchainRoot = File(context.filesDir, INSTALL_PATH_PREFIX + ".old") // TODO: uniqueness
    if (toolchainInstalled) {
        toolchainRoot.renameTo(previousToolchainRoot)
    }
    targetPrefix.renameTo(toolchainRoot)
    if (toolchainInstalled) {
        previousToolchainRoot.deleteRecursively()
    }
}

private suspend fun installPostProcess(toolchainRoot: File, context: Context) = coroutineScope {
    // set executable bits
    for (file in INSTALL_EXECUTABLE_DIRS.map { File(toolchainRoot, it) }) {
        file.walk().filter { it.isFile }.forEach {
            Os.chmod(it.absolutePath, MODE_OWNER_RWX)
        }
    }
    // create symlinks
    val crateBusyBoxSymlinkJob = launch(Dispatchers.IO) {
        val busyboxFile = File(toolchainRoot, "bin/busybox")
        ProcessBuilder(mutableListOf(busyboxFile.absolutePath, "--list")).start().let {
            InputStreamReader(it.inputStream).useLines { lines ->
                val busyboxPath = busyboxFile.absolutePath
                for (line in lines) {
                    Os.symlink(busyboxPath, File(toolchainRoot, "bin/${line}").absolutePath)
                }
            }
        }
    }
    val createSpecificSymlinkJob = launch(Dispatchers.IO) {
        val moshi = Moshi.Builder().build()
        Okio.buffer(Okio.source(context.assets.open(INSTALL_SYMLINK_MAPS))).use {
            val symlinkMaps = moshi.adapter(SymlinkMaps::class.java).fromJson(JsonReader.of(it))
            for (file in symlinkMaps?.files.orEmpty()) {
                val source = File(toolchainRoot, file.src)
                val target = File(toolchainRoot, file.target)
                val targetDir = target.parentFile
                if (!targetDir.exists()) targetDir.mkdir()
                Os.symlink(source.absolutePath, target.absolutePath)
            }
        }
    }
    joinAll(crateBusyBoxSymlinkJob, createSpecificSymlinkJob)
}