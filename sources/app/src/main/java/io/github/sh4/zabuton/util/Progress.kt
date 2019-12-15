package io.github.sh4.zabuton.util

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

const val PROGRESS_NOT_SPECIFIED = -1L

class Progress<T>(val type: ProgressType, val total: Long = PROGRESS_NOT_SPECIFIED) {
    private val currentAtomic = AtomicLong()
    private val additionalDataAtomic = AtomicReference<T>()

    var additionalData: T
        get() { return additionalDataAtomic.get() }
        set(value) = additionalDataAtomic.set(value)

    val current: Long
        get()
        {
            val v = currentAtomic.get()
            return if (total == PROGRESS_NOT_SPECIFIED) v else v.coerceAtMost(total)
        }
    var finished = false
        private set

    fun finish() {
        if (total != PROGRESS_NOT_SPECIFIED) {
            currentAtomic.set(total)
        }
        finished = true
    }

    fun reportAdvance(advance: Long) {
        currentAtomic.getAndAdd(advance)
    }

    fun report(value: Long) {
        currentAtomic.set(value)
    }
}