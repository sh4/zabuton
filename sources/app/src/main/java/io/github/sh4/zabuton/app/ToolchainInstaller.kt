package io.github.sh4.zabuton.app

import android.content.Context
import android.system.Os
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import io.github.sh4.zabuton.util.Progress
import io.github.sh4.zabuton.util.extractZipAsParallel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import okio.Okio
import java.io.File
import java.io.InputStreamReader

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

suspend fun toolchainInstall(
        installRoot: File,
        context: Context,
        block: suspend CoroutineScope.(channel: ReceiveChannel<Progress<Unit>>) -> Unit
) = coroutineScope {
    val installRootTemp = File(installRoot.absolutePath + ".tmp")
    if (installRootTemp.exists()) {
        installRootTemp.deleteRecursively()
    }
    installRootTemp.mkdir()
    extractZipAsParallel({ context.assets.open(INSTALL_TOOLCHAIN) }, installRootTemp, block)
    replaceToolchainRoot(installRoot, installRootTemp)
    installPostProcess(installRoot, context)
}

private fun replaceToolchainRoot(root: File, newRoot: File) {
    val toolchainInstalled = root.exists()
    // delete previous toolchain tree
    val previousToolchainRoot = File(root.absolutePath + ".old") // TODO: uniqueness
    if (toolchainInstalled) {
        root.renameTo(previousToolchainRoot)
    }
    newRoot.renameTo(root)
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