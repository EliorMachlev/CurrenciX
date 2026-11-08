package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import androidx.compose.runtime.Immutable
import com.eliormachlev.currencix.ocr.OcrLanguages
import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.ocr.Tessdata
import com.eliormachlev.currencix.ocr.TessdataDownloadWorker
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.AppDispatchers
import com.eliormachlev.currencix.viewmodel.util.RetainedScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

// How long the screen's state outlives its last observer (a rotation).
private const val STOP_TIMEOUT_MILLIS = 5_000L

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
 * The receipt languages screen's state, kept with `retain`: lists the languages on the phone (and those
 * downloading) with when each goes, and adds, keeps or deletes one
 * ([OcrLanguages]). File work runs on the IO dispatcher.
 */
class ReceiptLanguagesStateHolder(
    private val app: Application,
    private val dispatchers: AppDispatchers = AppDispatchers.production,
) : RetainedScope(dispatchers) {
    // Bumped after a change the flows below don't see (a file deleted).
    private val refresh = MutableStateFlow(0)

    val state: StateFlow<ReceiptLanguagesState> =
        combine(
            TessdataDownloadWorker.downloading(app),
            Database(app).receiptLanguages.keepUntil.flow,
            refresh,
        ) { downloading, keepUntil, _ -> stateOf(downloading, keepUntil) }
            .flowOn(dispatchers.io)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReceiptLanguagesState())

    init {
        // A language whose time came while the app was closed goes before it's listed.
        onIo { OcrLanguages.deleteExpired(app) }
    }

    /** Downloads [language], which the user just agreed to, to keep until [until] (null: for good). */
    fun add(
        language: TessLanguage,
        until: Instant?,
    ) = onIo { OcrLanguages.download(app, language, until) }

    fun keep(
        language: TessLanguage,
        until: Instant?,
    ) = onIo { OcrLanguages.keep(app, language, until) }

    fun delete(language: TessLanguage) = onIo { OcrLanguages.delete(app, language) }

    private fun onIo(block: () -> Unit) {
        scope.launch(dispatchers.io) {
            block()
            refresh.value++
        }
    }

    private fun stateOf(
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
}
