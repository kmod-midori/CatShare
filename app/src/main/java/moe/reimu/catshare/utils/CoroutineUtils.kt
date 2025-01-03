package moe.reimu.catshare.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.time.withTimeout
import java.time.Duration
import java.util.concurrent.TimeoutException


suspend fun <T> withTimeoutReason(
    duration: Duration,
    reason: String,
    block: suspend CoroutineScope.() -> T
): T {
    try {
        return withTimeout(duration, block)
    } catch (e: TimeoutCancellationException) {
        throw TimeoutException("Timed out after ${duration.toMillis()} ms: $reason")
    }
}

suspend fun <T> Deferred<T>.awaitWithTimeout(timeout: Duration, reason: String): T {
    return withTimeoutReason(timeout, reason) {
        await()
    }
}