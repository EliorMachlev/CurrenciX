package com.eliormachlev.currencix.ocr

import android.content.Context
import com.eliormachlev.currencix.repository.Database
import java.time.Instant

/**
 * The receipt-reading languages on the phone, as the user manages them
 * (Settings → Receipt languages): one is added only once they agree to its
 * download, kept for good or until a time they pick (a trip's last day), and
 * deleted when they say, or when that time comes ([TessdataExpiryWorker]).
 */
object OcrLanguages {
    /** The languages whose data is on the phone. */
    fun installed(context: Context): List<TessLanguage> = TessLanguage.entries.filter { Tessdata.isInstalled(context, it) }

    /** When each language kept for a while goes. */
    fun keepUntil(context: Context): Map<TessLanguage, Instant> =
        Database(context)
            .receiptLanguages.keepUntil
            .get()
            .mapNotNull { (code, millis) ->
                TessLanguage.entries.firstOrNull { it.code == code }?.let { it to Instant.ofEpochMilli(millis) }
            }.toMap()

    /**
     * Downloads [language], which the user just agreed to, to keep until
     * [until] (null: for good). The download runs in the background.
     */
    fun download(
        context: Context,
        language: TessLanguage,
        until: Instant?,
    ) {
        val settings = Database(context).receiptLanguages
        settings.setAllowed(language.code, allowed = true)
        settings.setKeepUntil(language.code, until?.toEpochMilli())
        TessdataDownloadWorker.enqueue(context, language)
        TessdataExpiryWorker.schedule(context)
    }

    /** Keeps [language] until [until], or for good (null). */
    fun keep(
        context: Context,
        language: TessLanguage,
        until: Instant?,
    ) {
        Database(context).receiptLanguages.setKeepUntil(language.code, until?.toEpochMilli())
        TessdataExpiryWorker.schedule(context)
    }

    /**
     * Deletes [language]'s data and stops its download. It isn't fetched
     * again unasked: the app's own language counts as said no to (the review
     * can offer it again), English is asked for again with the next scan.
     */
    fun delete(
        context: Context,
        language: TessLanguage,
    ) {
        TessdataDownloadWorker.cancel(context, language)
        Tessdata.delete(context, language)
        val settings = Database(context).receiptLanguages
        settings.setKeepUntil(language.code, null)
        if (language == TessLanguage.ENGLISH) settings.forget(language.code) else settings.setAllowed(language.code, allowed = false)
        TessdataExpiryWorker.schedule(context)
    }

    /** Deletes every language whose time is up at [now]; returns those deleted. */
    fun deleteExpired(
        context: Context,
        now: Instant = Instant.now(),
    ): List<TessLanguage> {
        val expired = keepUntil(context).filterValues { !it.isAfter(now) }.keys.toList()
        expired.forEach { delete(context, it) }
        return expired
    }
}
