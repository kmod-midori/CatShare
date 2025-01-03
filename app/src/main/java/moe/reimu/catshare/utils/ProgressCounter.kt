package moe.reimu.catshare.utils

import java.util.concurrent.TimeUnit

class ProgressCounter(private val totalSize: Long, private val callback: (Long, Long) -> Unit) {
    private var lastProgressUpdate = 0L

    fun update(processedSize: Long) {
        val now = System.nanoTime()
        val elapsed = TimeUnit.SECONDS.convert(
            now - lastProgressUpdate, TimeUnit.NANOSECONDS
        )
        if (elapsed > 1) {
            callback(totalSize, processedSize)
            lastProgressUpdate = now
        }
    }
}