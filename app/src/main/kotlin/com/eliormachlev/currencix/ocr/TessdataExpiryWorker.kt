package com.eliormachlev.currencix.ocr

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val UNIQUE_NAME = "tessdata-expiry"

/**
 * Deletes the receipt languages kept until a time that has come
 * ([OcrLanguages.deleteExpired]), then waits for the next one. One run is
 * scheduled at a time, for the soonest such time ([schedule]); a run late by
 * a few minutes (WorkManager's batching) is fine for a trip's end.
 */
internal class TessdataExpiryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        OcrLanguages.deleteExpired(applicationContext)
        return Result.success()
    }

    companion object {
        /** Schedules the next run for the soonest time a language goes, or none when none does. */
        fun schedule(
            context: Context,
            now: Instant = Instant.now(),
        ) {
            val workManager = WorkManager.getInstance(context)
            val next = OcrLanguages.keepUntil(context).values.minOrNull()
            if (next == null) {
                workManager.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<TessdataExpiryWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
            workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
