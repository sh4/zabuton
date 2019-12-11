package io.github.sh4.zabuton.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

suspend fun extractZipAsParallel(
        input: () -> InputStream,
        extractDir: File,
        block: suspend CoroutineScope.(channel: ReceiveChannel<Progress>) -> Unit,
        defaultProgressContext: ProgressContext? = null,
        parallelLevel:Int = Runtime.getRuntime().availableProcessors() - 1
) = coroutineScope {
    // calculate total write size
    var zipEntryTotalCount = 0
    var zipEntryTotalSize = 0L
    ZipInputStream(input()).use { zipInput ->
        while (true) {
            val entry = zipInput.nextEntry ?: break
            if (entry.isDirectory) {
                File(extractDir, entry.name).mkdir()
            } else {
                zipEntryTotalSize += entry.size
            }
            zipEntryTotalCount++
        }
    }

    val progressContext = defaultProgressContext ?: ProgressContext(this, block)
    val progress = progressContext.next(ProgressType.ExtractZip, zipEntryTotalSize)

    val blockCount = parallelLevel.coerceAtLeast(1)
    val entryCountPerJob = zipEntryTotalCount / blockCount
    val additionalEntryCount = zipEntryTotalCount % blockCount
    val expandJobs = (1..blockCount).map { block ->
        launch(Dispatchers.Default) {
            ZipInputStream(input()).use { zipInput ->
                val skipEntryCount = entryCountPerJob * (block - 1)
                repeat(skipEntryCount) { zipInput.nextEntry ?: return@repeat }
                val processEntryCount = entryCountPerJob +
                        if (block == blockCount) additionalEntryCount else 0
                extractZipArchive(zipInput, extractDir, processEntryCount, progress)
            }
        }
    }.toTypedArray()
    joinAll(*expandJobs)
    progress.finish()

    if (defaultProgressContext == null) {
        progressContext.finish()
    }
}

private fun extractZipArchive(
        zipInput: ZipInputStream,
        extractDir: File,
        processEntryCount: Int,
        progress: Progress
) {
    repeat(processEntryCount) {
        val entry = zipInput.nextEntry ?: return
        if (entry.isDirectory) return@repeat
        val writeBytes = File(extractDir, entry.name).outputStream().use { zipInput.copyTo(it) }
        progress.report(writeBytes)
    }
}