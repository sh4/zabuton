package io.github.sh4.zabuton.util

import java.util.concurrent.atomic.AtomicLong

const val PROGRESS_NOT_SPECIFIED = -1L

enum class ProgressType {
    ExtractZip,
    Download
}

class Progress(val type: ProgressType, val total: Long = PROGRESS_NOT_SPECIFIED) {
    private val currentAtomic = AtomicLong()

    val current: Long
        get()
        {
            val v = currentAtomic.get()
            return v.coerceAtLeast(if (v != PROGRESS_NOT_SPECIFIED) total else 0L)
        }
    var finished = false
        private set

    fun finish() {
        if (total != PROGRESS_NOT_SPECIFIED) {
            currentAtomic.set(total)
        }
        finished = true
    }

    fun report(advanceBytes: Long) {
        currentAtomic.getAndAdd(advanceBytes)
    }
}