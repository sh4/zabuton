package io.github.sh4.zabuton.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ProgressType {
    ExtractZip,
    DownloadFile,
    CloneGitRepository,
    FetchGitRepository,
    CheckoutGitRepository,
    ResetGitRepository,
}

class ProgressContext<T>(
        scope: CoroutineScope,
        progressBlock: suspend CoroutineScope.(channel: ReceiveChannel<Progress<T>>) -> Unit)
{
    private val progressSequence = Channel<Progress<T>>(Channel.UNLIMITED)

    init {
        scope.launch { progressBlock(this, progressSequence) }
    }

    suspend fun next(type: ProgressType, total: Long): Progress<T> {
        val p = Progress<T>(type, total)
        progressSequence.send(p)
        return p
    }

    fun finish() = progressSequence.close()
}