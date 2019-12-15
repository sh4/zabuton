package io.github.sh4.zabuton.workspace

import io.github.sh4.zabuton.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.*

suspend fun createZipFileWorktree(
        workspace: Workspace,
        root: File,
        cacheRoot: File,
        url: URL,
        block: suspend CoroutineScope.(channel: ReceiveChannel<Progress<Unit>>) -> Unit
): ZipFileWorktree {
    val canonicalRoot = File(root.canonicalPath, workspace.id.toString())
    when (url.protocol?.toLowerCase(Locale.ENGLISH)) {
        null -> throw IllegalArgumentException("URL protocol cannot be empty")
        "file" -> expandZipFile(canonicalRoot, File(url.path), block)
        "http", "https" -> downloadZipFile(canonicalRoot, cacheRoot, url, block)
        else -> throw IllegalArgumentException("Unsupported URL protocol ${url.protocol}")
    }
    return ZipFileWorktree(workspace, canonicalRoot)
}

private suspend fun expandZipFile(
        canonicalRoot: File, zipFile: File,
        block: suspend CoroutineScope.(channel: ReceiveChannel<Progress<Unit>>) -> Unit,
        defaultProgressContext: ProgressContext<Unit>? = null
) {
    canonicalRoot.mkdirs()
    extractZipAsParallel({ zipFile.inputStream() }, canonicalRoot,
            block = block,
            defaultProgressContext = defaultProgressContext)
}

private suspend fun downloadZipFile(
        canonicalRoot: File,
        cacheRoot: File,
        url: URL,
        block: suspend CoroutineScope.(channel: ReceiveChannel<Progress<Unit>>) -> Unit,
        defaultProgressContext: ProgressContext<Unit>? = null
) = coroutineScope {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    val response = async(Dispatchers.IO) { client.newCall(request).execute() }

    val progressContext = defaultProgressContext ?: ProgressContext(this, block)
    val zipFile = async(Dispatchers.IO) {
        val zipFile = File.createTempFile(ZipFileWorktree::class.java.simpleName,".zip", cacheRoot)
        val responseBody = response.await().body()
        val downloadProgress = progressContext.next(
                type = ProgressType.DownloadFile,
                total = responseBody?.contentLength()?.coerceAtLeast(PROGRESS_NOT_SPECIFIED) ?: PROGRESS_NOT_SPECIFIED)
        val inputStream = responseBody?.byteStream() ?: EmptyInputStream()
        zipFile.outputStream().use { outputStream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytes = inputStream.read(buffer)
            while (bytes >= 0) {
                outputStream.write(buffer, 0, bytes)
                downloadProgress.reportAdvance(bytes.toLong())
                bytes = inputStream.read(buffer)
            }
        }
        downloadProgress.finish()
        return@async zipFile
    }.await()
    expandZipFile(canonicalRoot, zipFile, block = block, defaultProgressContext = progressContext)
    if (defaultProgressContext == null) {
        progressContext.finish()
    }
}

private class EmptyInputStream : InputStream() {
    override fun read(): Int = -1
}

class ZipFileWorktree(override val workspace: Workspace,
                      override val root: File) : Worktree {
    override fun deletePermanently() {
        root.deleteRecursively()
    }
}
