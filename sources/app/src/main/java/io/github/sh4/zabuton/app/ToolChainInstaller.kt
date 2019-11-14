package io.github.sh4.zabuton.app

import android.content.Context
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

private const val INSTALL_PATH_PREFIX = "root"
private const val INSTALL_PATH_PREFIX_TMP = "root.tmp"
private const val INSTALL_TOOLCHAIN = "build/toolchain.zip"
private val INSTALL_EXECUTABLE_DIRS = arrayOf("avr", "bin", "libexec")

private const val MODE_OWNER_EXECUTE = 64
private const val MODE_OWNER_WRITE = 128
private const val MODE_OWNER_READ = 256
private const val MODE_OWNER_RWX = MODE_OWNER_EXECUTE + MODE_OWNER_WRITE + MODE_OWNER_READ

class InstallProgress(private val progress: (current:Long, total: Long) -> Unit,
                      private val intervalMilliseconds: Int)
{
    private var start: Long = 0

    fun restart() {
        start = System.currentTimeMillis()
    }

    fun report(current: Long, total: Long) {
        val now = System.currentTimeMillis()
        val elapsed =  now - start
        if (elapsed > intervalMilliseconds) {
            progress(current, total)
            start = now
        }
    }

    fun forceReport(current: Long, total: Long) = progress(current, total)
}

fun toolchainRoot(context: Context) = File(context.filesDir, INSTALL_PATH_PREFIX)

fun installToolchain(context: Context, progress:InstallProgress) {
    val targetPrefix = File(context.filesDir, INSTALL_PATH_PREFIX_TMP)
    if (targetPrefix.exists()) {
        targetPrefix.deleteRecursively()
    }
    targetPrefix.mkdir()

    var zipEntryTotalSize = 0L
    ZipInputStream(context.assets.open(INSTALL_TOOLCHAIN)).use { zipInput ->
        while (true) {
            val entry = zipInput.nextEntry ?: break
            if (entry.isDirectory) continue
            zipEntryTotalSize += entry.size
        }
    }

    progress.restart()

    var zipEntryWrittenSize = 0L
    ZipInputStream(context.assets.open(INSTALL_TOOLCHAIN)).use { zipInput ->
        while (true) {
            val entry = zipInput.nextEntry ?: break
            val target = File(targetPrefix, entry.name)
            val targetDir = if (entry.isDirectory) target else target.parentFile
            if (!targetDir.exists()) targetDir.mkdir()
            if (entry.isDirectory) continue
            FileOutputStream(target).use {
                target.writeBytes(zipInput.readBytes())
            }
            zipEntryWrittenSize += entry.size
            progress.report(zipEntryWrittenSize, zipEntryTotalSize)
        }
    }
    progress.forceReport(zipEntryWrittenSize, zipEntryTotalSize)

    // executables
    for (file in INSTALL_EXECUTABLE_DIRS.map { File(targetPrefix, it) }) {
        file.walk().filter { it.isFile }.forEach {
            Os.chmod(it.absolutePath, MODE_OWNER_RWX)
        }
    }

    // busybox symlink
    val busyboxFile = File(targetPrefix, "bin/busybox")
    ProcessBuilder(mutableListOf(busyboxFile.absolutePath, "--list")).start().let {
        InputStreamReader(it.inputStream).useLines { lines ->
            val busyboxPath = busyboxFile.absolutePath
            for (line in lines) {
                Os.symlink(busyboxPath, File(targetPrefix, "bin/${line}").absolutePath)
            }
        }
    }

    val installPrefix = File(context.filesDir, INSTALL_PATH_PREFIX)
    if (installPrefix.exists()) {
        installPrefix.deleteRecursively()
    }
    targetPrefix.renameTo(installPrefix)
}