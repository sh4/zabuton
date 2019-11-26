package io.github.sh4.zabuton.app

import android.content.Context
import android.system.Os
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream

private const val INSTALL_PATH_PREFIX = "root"
private const val INSTALL_PATH_PREFIX_TMP = "root.tmp"
private const val INSTALL_TOOLCHAIN = "build/toolchain.zip"
private val INSTALL_EXECUTABLE_DIRS = arrayOf("avr", "bin", "libexec")

private const val MODE_OWNER_EXECUTE = 64
private const val MODE_OWNER_WRITE = 128
private const val MODE_OWNER_READ = 256
private const val MODE_OWNER_RWX = MODE_OWNER_EXECUTE + MODE_OWNER_WRITE + MODE_OWNER_READ

class InstallProgress()
{
    private val currentAtomic = AtomicLong()

    var total: Long = 0
    val current: Long
        get() = currentAtomic.get()
    var finished = false
        private set

    fun finish() {
        finished = true
    }

    fun report(advanceBytes: Long) {
        currentAtomic.getAndAdd(advanceBytes)
    }
}

fun toolchainRoot(context: Context) = File(context.filesDir, INSTALL_PATH_PREFIX)

class ToolchainInstaller(private val installProgress: InstallProgress) {
    suspend fun install(context: Context, block: suspend CoroutineScope.() -> Unit) = coroutineScope {
        val targetPrefix = File(context.filesDir, INSTALL_PATH_PREFIX_TMP)
        if (targetPrefix.exists()) {
            targetPrefix.deleteRecursively()
        }
        targetPrefix.mkdir()

        // calculate total write size
        var zipEntryTotalCount = 0
        var zipEntryTotalSize = 0L
        ZipInputStream(context.assets.open(INSTALL_TOOLCHAIN)).use { zipInput ->
            while (true) {
                val entry = zipInput.nextEntry ?: break
                if (entry.isDirectory) {
                    val target = File(targetPrefix, entry.name)
                    target.mkdir()
                } else {
                    zipEntryTotalSize += entry.size
                }
                zipEntryTotalCount++
            }
        }

        installProgress.total = zipEntryTotalSize

        val blockCount = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
        val entryCountPerJob = zipEntryTotalCount / blockCount
        val additionalEntryCount = zipEntryTotalCount % blockCount
        val expandJobs = (1..blockCount).map { block ->
            launch(Dispatchers.Default) {
                ZipInputStream(context.assets.open(INSTALL_TOOLCHAIN)).use { zipInput ->
                    val skipEntryCount = entryCountPerJob * (block - 1)
                    repeat(skipEntryCount) { zipInput.nextEntry ?: return@repeat }
                    val processEntryCount = entryCountPerJob +
                            if (block == blockCount) additionalEntryCount else 0
                    extractZipArchive(zipInput, targetPrefix, processEntryCount)
                }
            }
        }.toTypedArray()
        val progressJob = launch(block = block)
        joinAll(*expandJobs)
        installProgress.finish()

        // set executable bits
        for (file in INSTALL_EXECUTABLE_DIRS.map { File(targetPrefix, it) }) {
            file.walk().filter { it.isFile }.forEach {
                Os.chmod(it.absolutePath, MODE_OWNER_RWX)
            }
        }

        // create symlinks
        val crateBusyBoxSymlinkJob = launch (Dispatchers.IO) {
            val busyboxFile = File(targetPrefix, "bin/busybox")
            ProcessBuilder(mutableListOf(busyboxFile.absolutePath, "--list")).start().let {
                InputStreamReader(it.inputStream).useLines { lines ->
                    val busyboxPath = busyboxFile.absolutePath
                    for (line in lines) {
                        Os.symlink(busyboxPath, File(targetPrefix, "bin/${line}").absolutePath)
                    }
                }
            }
        }
        val createSpecificSymlinkJob = launch (Dispatchers.IO) {
        }

        joinAll(crateBusyBoxSymlinkJob, createSpecificSymlinkJob)

        // delete previous toolchain tree
        val toolchainRoot = File(context.filesDir, INSTALL_PATH_PREFIX)
        val toolchainInstalled = toolchainRoot.exists()
        val previousToolchainRoot = File(context.filesDir, INSTALL_PATH_PREFIX + ".old") // TODO: uniqueness
        if (toolchainInstalled) {
            toolchainRoot.renameTo(previousToolchainRoot)
        }
        targetPrefix.renameTo(toolchainRoot)
        if (toolchainInstalled) {
            previousToolchainRoot.deleteRecursively()
        }

        progressJob.join()
    }

    private suspend fun extractZipArchive(zipInput: ZipInputStream, targetPrefix: File, processEntryCount: Int) {
        repeat(processEntryCount) {
            val entry = zipInput.nextEntry ?: return
            if (entry.isDirectory) return@repeat
            val target = File(targetPrefix, entry.name)
            FileOutputStream(target).use {
                target.writeBytes(zipInput.readBytes())
            }
            installProgress.report(entry.size)
        }
    }
}