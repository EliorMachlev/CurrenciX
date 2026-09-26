package com.eliormachlev.currencix.repository.cache

import com.eliormachlev.currencix.util.ApiHttpError
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.random.Random

// Total attempts INCLUDING the initial one. Three means "one try + two
// retries" — enough to ride out a transient hiccup without turning a
// definitively-broken endpoint into a 30-second UI stall.
private const val DEFAULT_MAX_ATTEMPTS = 3

// Base delay before the first retry; doubles each subsequent retry
// (attempt 1 → 250ms, attempt 2 → 500ms). Tight enough that a user
// staring at a spinner sees action within a second, loose enough to
// let a slow-recovering endpoint breathe.
private const val DEFAULT_BASE_DELAY_MS = 250L

// Random jitter added to each backoff, in millis. Prevents thundering
// herds if a whole cohort of app instances retry at the exact same
// tick after a provider outage.
private const val DEFAULT_JITTER_MS = 150L

// HTTP 5xx range boundaries — server-side transient errors that are
// worth retrying, in contrast to 4xx which almost always indicates a
// client-side / auth problem that a retry can't fix.
private const val HTTP_5XX_MIN = 500
private const val HTTP_5XX_MAX = 599

/**
 * Exponential backoff with jitter, retrying only on failures that could
 * plausibly succeed on a subsequent attempt.
 *
 * ### Retryable
 *  - [SocketTimeoutException] — read/connect timeouts from OkHttp.
 *  - [ApiHttpError] with a 5xx status — server-side transient.
 *  - Any other [IOException] — DNS blips, connection resets, TLS handshake
 *    interruptions. Covered because the underlying network stack keeps
 *    inventing new failure classes and dropping them into `IOException`.
 *
 * ### Not retryable
 *  - 4xx HTTP responses (auth failure, malformed request, quota) — no
 *    amount of retrying fixes these.
 *  - CancellationException — cooperative cancellation must propagate.
 *  - Anything else (`IllegalStateException` on a malformed payload, etc.).
 */
internal class RetryPolicy(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
    private val jitterMs: Long = DEFAULT_JITTER_MS,
    private val random: Random = Random.Default,
) {
    suspend fun <T> execute(block: suspend (attempt: Int) -> Result<T>): Result<T> {
        var last: Result<T> = block(0)
        var attempt = 1
        while (attempt < maxAttempts && shouldRetry(last)) {
            delay(backoffFor(attempt))
            Timber.tag(TAG).d("Retry attempt %d/%d after %s", attempt, maxAttempts - 1, last.exceptionOrNull())
            last = block(attempt)
            attempt++
        }
        return last
    }

    private fun shouldRetry(result: Result<*>): Boolean {
        val error = result.exceptionOrNull() ?: return false
        return when (error) {
            is SocketTimeoutException -> true
            is ApiHttpError -> error.statusCode in HTTP_5XX_MIN..HTTP_5XX_MAX
            is IOException -> true
            else -> false
        }
    }

    private fun backoffFor(attempt: Int): Long {
        // Exponential: base * 2^(attempt-1). attempt is 1-based here so the
        // first retry backs off by exactly `baseDelayMs`, not zero.
        val exp = baseDelayMs shl (attempt - 1)
        val jitter = if (jitterMs > 0) random.nextLong(jitterMs) else 0L
        return exp + jitter
    }

    private companion object {
        const val TAG = "RateCache.Retry"
    }
}
