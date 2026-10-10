package com.eliormachlev.currencix.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eliormachlev.currencix.repository.ExchangeRatesRepository
import timber.log.Timber
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * WorkManager worker that periodically refreshes exchange rates in the
 * background (#151). Delegates to
 * [ExchangeRatesRepository.refreshLatestRates] — which routes through the
 * in-house [com.eliormachlev.currencix.repository.cache.RateCache]
 * (retry-with-jitter, in-flight dedupe, memory+disk tiering) — so this
 * worker never talks to a provider directly nor pokes at cache internals.
 *
 * ### Result mapping
 *  - Success                           → [Result.success]
 *  - Transient network error           → [Result.retry] (exponential backoff)
 *  - Any other exception               → [Result.retry] too; a systemic
 *    upstream 5xx should get another chance on the next backoff window
 *    rather than silently disappear from the log.
 *
 * The provider layer surfaces network failures either as an exception
 * bubbling out of `refresh()` (retry policy exhausted) *or* as a
 * `Result.failure` from the cache. Both paths funnel through the same
 * retry-or-fail decision.
 */
internal class RateRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = ExchangeRatesRepository(applicationContext)
        val outcome =
            runCatching { repository.refreshLatestRates() }
                .getOrElse { throwable -> kotlin.Result.failure(throwable) }
        return outcome.fold(
            onSuccess = {
                Timber.tag(TAG).d("Auto-refresh success")
                Result.success()
            },
            onFailure = { throwable ->
                if (throwable.isTransientNetworkFailure()) {
                    Timber.tag(TAG).d(throwable, "Auto-refresh transient failure — will retry")
                } else {
                    Timber.tag(TAG).w(throwable, "Auto-refresh failed — will retry")
                }
                Result.retry()
            },
        )
    }

    // Broken out so both the "transient" log-line and any future decision to
    // bucket differently on unknown-host vs. socket-timeout live in one place.
    private fun Throwable.isTransientNetworkFailure(): Boolean = this is UnknownHostException || this is SocketTimeoutException

    private companion object {
        const val TAG = "RateRefreshWorker"
    }
}
