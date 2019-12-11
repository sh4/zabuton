package io.github.sh4.zabuton.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProgressContext(scope: CoroutineScope, val progressBlock: suspend CoroutineScope.(channel: ReceiveChannel<Progress>) -> Unit) {
    private val progressSequence = Channel<Progress>()

    init {
        scope.launch { progressBlock(this, progressSequence) }
    }

    suspend fun next(type: ProgressType, total: Long): Progress {
        val p = Progress(type, total)
        progressSequence.send(p)
        return p
    }

    fun finish() = progressSequence.close()
}