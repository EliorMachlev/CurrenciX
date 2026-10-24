package com.eliormachlev.currencix.ocr

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.eliormachlev.currencix.util.HttpClientProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "TessdataDownload"
private const val KEY_LANGUAGE = "language"
private const val UNIQUE_PREFIX = "tessdata-"
private const val BACKOFF_MINUTES = 5L
private const val DOWNLOAD_TAG = "tessdata"

/**
 * Downloads one OCR language file in the background, the way the rate
 * refresh runs: WorkManager, once there's a network, retried with backoff,
 * through the app's HTTP client (without its call time limit and cache, as
 * these files run to a few megabytes). The file is kept only if its SHA-256
 * matches ([Tessdata.install]).
 */
internal class TessdataDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val language = TessLanguage.entries.firstOrNull { it.name == inputData.getString(KEY_LANGUAGE) } ?: return Result.failure()
        if (Tessdata.isInstalled(applicationContext, language)) return Result.success()
        val client = HttpClientProvider.downloadClient(applicationContext)
        return try {
            client.newCall(Request.Builder().url(language.url).build()).execute().use { response ->
                val body = response.body
                when {
                    !response.isSuccessful -> {
                        Result.retry()
                    }

                    Tessdata.install(body.byteStream(), Tessdata.file(applicationContext, language), language) -> {
                        Result.success()
                    }

                    else -> {
                        // Not the file we know: retrying would fetch the same thing.
                        Timber.tag(TAG).w("Checksum mismatch for %s", language.code)
                        Result.failure()
                    }
                }
            }
        } catch (e: IOException) {
            Timber.tag(TAG).d(e, "Download of %s failed, will retry", language.code)
            Result.retry()
        }
    }

    companion object {
        /** Downloads [language]'s file unless it's here or already on its way. */
        fun enqueue(
            context: Context,
            language: TessLanguage,
        ) {
            if (Tessdata.isInstalled(context, language)) return
            val request =
                OneTimeWorkRequestBuilder<TessdataDownloadWorker>()
                    .setInputData(workDataOf(KEY_LANGUAGE to language.name))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                    .addTag(DOWNLOAD_TAG)
                    // Its own tag too: a unique work's name isn't one of its tags.
                    .addTag(UNIQUE_PREFIX + language.code)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_PREFIX + language.code, ExistingWorkPolicy.KEEP, request)
        }

        /** Stops [language]'s download, if one is on its way. */
        fun cancel(
            context: Context,
            language: TessLanguage,
        ) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PREFIX + language.code)
        }

        /** The languages downloading now (or waiting for a network to), as they change. */
        fun downloading(context: Context): Flow<Set<TessLanguage>> =
            WorkManager
                .getInstance(context)
                .getWorkInfosByTagFlow(DOWNLOAD_TAG)
                .map { infos ->
                    infos
                        .filterNot { it.state.isFinished }
                        .mapNotNullTo(HashSet()) { info -> TessLanguage.entries.firstOrNull { UNIQUE_PREFIX + it.code in info.tags } }
                }

        /**
         * Waits for [language]'s download ([enqueue]d before) to end; true
         * when its file is then on the device. A download retrying after a
         * failure keeps it waiting.
         */
        suspend fun awaitInstalled(
            context: Context,
            language: TessLanguage,
        ): Boolean {
            if (Tessdata.isInstalled(context, language)) return true
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkFlow(UNIQUE_PREFIX + language.code)
                .first { infos -> infos.none { !it.state.isFinished } }
            return Tessdata.isInstalled(context, language)
        }
    }
}
