package moe.reimu.catshare.utils

import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.time.withTimeout
import moe.reimu.catshare.exceptions.ExceptionWithMessage
import java.time.Duration
import java.util.concurrent.TimeoutException


suspend fun <T> withTimeoutReason(
    duration: Duration,
    reason: String,
    @StringRes messageId: Int,
    block: suspend CoroutineScope.() -> T
): T {
    try {
        return withTimeout(duration, block)
    } catch (e: TimeoutCancellationException) {
        throw ExceptionWithMessage(
            "Timed out after ${duration.toMillis()} ms: $reason",
            e,
            messageId
        )
    }
}

suspend fun <T> Deferred<T>.awaitWithTimeout(
    timeout: Duration,
    reason: String,
    @StringRes messageId: Int,
): T {
    return withTimeoutReason(timeout, reason, messageId) {
        await()
    }
}