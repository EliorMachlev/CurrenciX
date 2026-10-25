package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eliormachlev.currencix.ocr.OcrLanguages
import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.ocr.Tessdata
import com.eliormachlev.currencix.ocr.TessdataDownloadWorker
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.AppDispatchers
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
 * The receipt languages screen: lists the languages on the phone (and those
 * downloading) with when each goes, and adds, keeps or deletes one
 * ([OcrLanguages]). File work runs on the IO dispatcher.
 */
class ReceiptLanguagesViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val dispatchers = AppDispatchers.production

    // Bumped after a change the flows below don't see (a file deleted).
    private val refresh = MutableStateFlow(0)

    val state: StateFlow<ReceiptLanguagesState> =
        combine(
            TessdataDownloadWorker.downloading(app),
            Database(app).receiptLanguages.keepUntil.flow,
            refresh,
        ) { downloading, keepUntil, _ -> stateOf(downloading, keepUntil) }
            .flowOn(dispatchers.io)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ReceiptLanguagesState())

    init {
        // A language whose time came while the app was closed goes before it's listed.
        onIo { OcrLanguages.deleteExpired(getApplication()) }
    }

    /** Downloads [language], which the user just agreed to, to keep until [until] (null: for good). */
    fun add(
        language: TessLanguage,
        until: Instant?,
    ) = onIo { OcrLanguages.download(getApplication(), language, until) }

    fun keep(
        language: TessLanguage,
        until: Instant?,
    ) = onIo { OcrLanguages.keep(getApplication(), language, until) }

    fun delete(language: TessLanguage) = onIo { OcrLanguages.delete(getApplication(), language) }

    private fun onIo(block: () -> Unit) {
        viewModelScope.launch(dispatchers.io) {
            block()
            refresh.value++
        }
    }

    private fun stateOf(
        downloading: Set<TessLanguage>,
        keepUntil: Map<String, Long>,
    ): ReceiptLanguagesState {
        val app = getApplication<Application>()
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
