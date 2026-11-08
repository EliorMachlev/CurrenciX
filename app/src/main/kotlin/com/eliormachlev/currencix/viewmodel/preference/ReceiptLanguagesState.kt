package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import androidx.compose.runtime.Immutable
import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.ocr.Tessdata
import com.eliormachlev.currencix.ocr.TessdataDownloadWorker
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.Instant

/** One language on the phone, or on its way: when it goes ([keepUntil], null for good). */
@Immutable
data class ReceiptLanguageRow(
    val language: TessLanguage,
    val keepUntil: Instant?,
    val downloading: Boolean,
)

/** Settings → Receipt languages: those on the phone, and the rest, which can be added. */
@Immutable
data class ReceiptLanguagesState(
    val rows: List<ReceiptLanguageRow> = emptyList(),
    val available: List<TessLanguage> = emptyList(),
)

/**
 * The receipt languages screen's state: the languages on the phone (and those
 * downloading) with when each goes. Worked out on [io]; follows the settings
 * and the downloads, and [refresh] for a change they don't show (a file deleted).
 */
fun receiptLanguagesState(
    app: Application,
    refresh: Flow<Int>,
    io: CoroutineDispatcher = AppDispatchers.production.io,
): Flow<ReceiptLanguagesState> =
    combine(
        TessdataDownloadWorker.downloading(app),
        Database(app).receiptLanguages.keepUntil.flow,
        refresh,
    ) { downloading, keepUntil, _ -> stateOf(app, downloading, keepUntil) }
        .flowOn(io)

private fun stateOf(
    app: Application,
    downloading: Set<TessLanguage>,
    keepUntil: Map<String, Long>,
): ReceiptLanguagesState {
    val here = TessLanguage.entries.filter { it in downloading || Tessdata.isInstalled(app, it) }
    val rows =
        here.map { language ->
            ReceiptLanguageRow(
                language = language,
                keepUntil = keepUntil[language.code]?.let(Instant::ofEpochMilli),
                downloading = language in downloading && !Tessdata.isInstalled(app, language),
            )
        }
    return ReceiptLanguagesState(rows = rows, available = TessLanguage.entries - here.toSet())
}
