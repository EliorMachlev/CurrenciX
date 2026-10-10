package com.eliormachlev.currencix.repository.cache

import com.eliormachlev.currencix.util.ApiHttpError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import kotlin.random.Random

private const val MAX_ATTEMPTS = 3

class RetryPolicyTest {
    @Test
    fun `retries transient failures up to max attempts then surfaces last error`() =
        runBlocking {
            var attempts = 0
            val policy =
                RetryPolicy(
                    maxAttempts = MAX_ATTEMPTS,
                    baseDelayMs = 1L,
                    jitterMs = 0L,
                    random = Random(0),
                )

            val result: Result<String> =
                policy.execute {
                    attempts++
                    Result.failure(SocketTimeoutException("boom $attempts"))
                }

            assertEquals(MAX_ATTEMPTS, attempts)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is SocketTimeoutException)
        }

    @Test
    fun `stops immediately on non-retryable failure`() =
        runBlocking {
            var attempts = 0
            val policy = RetryPolicy(maxAttempts = MAX_ATTEMPTS, baseDelayMs = 1L, jitterMs = 0L)

            // 4xx is not retryable — retry would be pointless.
            val result: Result<String> =
                policy.execute {
                    attempts++
                    Result.failure(ApiHttpError(statusCode = 404))
                }

            assertEquals(1, attempts)
            assertTrue(result.isFailure)
        }

    @Test
    fun `retries 5xx and eventually surfaces success`() =
        runBlocking {
            var attempts = 0
            val policy = RetryPolicy(maxAttempts = MAX_ATTEMPTS, baseDelayMs = 1L, jitterMs = 0L)

            val result: Result<String> =
                policy.execute {
                    attempts++
                    if (attempts < MAX_ATTEMPTS) {
                        Result.failure(ApiHttpError(statusCode = 503))
                    } else {
                        Result.success("ok")
                    }
                }

            assertEquals(MAX_ATTEMPTS, attempts)
            assertEquals("ok", result.getOrNull())
        }
}
