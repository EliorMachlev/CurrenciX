package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.ocr.OcrLanguages
import com.eliormachlev.currencix.ocr.Tessdata
import com.eliormachlev.currencix.ocr.TessdataDownloadWorker
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPromptSheet
import com.eliormachlev.currencix.view.compose.dialogs.PromptMessage
import com.eliormachlev.currencix.view.compose.rememberDatabase
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.viewmodel.cart.LanguageCheck
import com.eliormachlev.currencix.viewmodel.cart.RatesCheck
import com.eliormachlev.currencix.viewmodel.cart.TripChecklist
import com.eliormachlev.currencix.viewmodel.cart.tripChecklist
import com.eliormachlev.currencix.worker.RateRefreshScheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Before you go: what [trip] needs ready, each line ticked or with the one
 * thing that fixes it. Rates refresh in the background (once there's a
 * network); the receipt language downloads once the user taps its button,
 * which names its size (kept until the trip's last day ends); fees open the
 * fee manager ([onOpenFees]).
 */
@Composable
fun TripChecklistSheet(
    trip: Trip,
    onOpenFees: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val db = rememberDatabase()
    val rates by remember(db) { db.rates.getExchangeRates() }.observeAsState()
    val fees by db.fees.fees.collectAsStateWithLifecycle()
    val downloading by remember(context) { TessdataDownloadWorker.downloading(context) }.collectAsStateWithLifecycle(emptySet())
    val today = remember { LocalDate.now() }
    val checklist =
        tripChecklist(trip, rates, fees, today) { language ->
            LanguageCheck(language, installed = Tessdata.isInstalled(context, language), downloading = language in downloading)
        } ?: return
    LedgerPromptSheet(
        title = stringResource(R.string.trip_checklist),
        confirmLabel = stringResource(R.string.done),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        cancelLabel = stringResource(R.string.trip_checklist_later),
    ) {
        PromptMessage(
            stringResource(
                if (checklist.ready) R.string.trip_checklist_ready else R.string.trip_checklist_intro,
                trip.name.ifBlank {
                    stringResource(R.string.trip_unnamed)
                },
            ),
        )
        Spacer(Modifier.height(Spacing.Small))
        RatesItem(checklist, trip, onRefresh = { RateRefreshScheduler.refreshNow(context) })
        checklist.language?.let { check ->
            LanguageItem(check, trip, onDownload = { OcrLanguages.download(context, check.language, trip.endsAt()) })
        }
        FeesItem(checklist, trip, onOpenFees)
    }
}

@Composable
private fun RatesItem(
    checklist: TripChecklist,
    trip: Trip,
    onRefresh: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val destination = trip.destination?.iso4217Alpha().orEmpty()
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    when (val rates = checklist.rates) {
        is RatesCheck.Fresh -> {
            ChecklistItem(done = true, text = stringResource(R.string.trip_checklist_rates_fresh, destination, format.format(rates.date)))
        }

        is RatesCheck.Old -> {
            ChecklistItem(
                done = false,
                text = stringResource(R.string.trip_checklist_rates_old, destination, format.format(rates.date)),
                action = stringResource(R.string.trip_checklist_refresh),
                onAction = onRefresh,
            )
        }

        RatesCheck.Missing -> {
            ChecklistItem(
                done = false,
                text = stringResource(R.string.trip_checklist_rates_missing, destination),
                action = stringResource(R.string.trip_checklist_refresh),
                onAction = onRefresh,
            )
        }
    }
}

@Composable
private fun LanguageItem(
    check: LanguageCheck,
    trip: Trip,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val name = check.language.displayName(LocalConfiguration.current.locales[0])
    when {
        check.installed -> {
            ChecklistItem(done = true, text = stringResource(R.string.trip_checklist_language_ready, name))
        }

        check.downloading -> {
            ChecklistItem(done = false, text = stringResource(R.string.trip_checklist_language_downloading, name))
        }

        else -> {
            val size = check.language.downloadSize(context)
            ChecklistItem(
                done = false,
                text = stringResource(R.string.trip_checklist_language_missing, name),
                // The button names what it downloads and its size: tapping it is the yes.
                action =
                    trip.end?.let {
                        stringResource(
                            R.string.trip_checklist_language_download_until,
                            size,
                            DateTimeFormatter
                                .ofLocalizedDate(
                                    FormatStyle.MEDIUM,
                                ).withLocale(LocalConfiguration.current.locales[0])
                                .format(it),
                        )
                    } ?: stringResource(R.string.trip_checklist_language_download, size),
                onAction = onDownload,
            )
        }
    }
}

@Composable
private fun FeesItem(
    checklist: TripChecklist,
    trip: Trip,
    onOpenFees: () -> Unit,
) {
    val destination = trip.destination?.iso4217Alpha().orEmpty()
    if (checklist.fees.isEmpty()) {
        ChecklistItem(
            done = false,
            text = stringResource(R.string.trip_checklist_fees_missing, destination),
            action = stringResource(R.string.trip_checklist_fees_add),
            onAction = onOpenFees,
        )
    } else {
        ChecklistItem(
            done = true,
            text =
                stringResource(
                    R.string.trip_checklist_fees_set,
                    destination,
                    checklist.fees.joinToString {
                        it.name.ifBlank {
                            it.percent.toPlainString() +
                                "%"
                        }
                    },
                ),
            action = stringResource(R.string.trip_checklist_fees_review),
            onAction = onOpenFees,
        )
    }
}

// One line: a tick or a dot, what it is, and the button that fixes it (if any).
@Composable
private fun ChecklistItem(
    done: Boolean,
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val state = stringResource(if (done) R.string.trip_checklist_done else R.string.trip_checklist_todo)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Tiny),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(
            painter = painterResource(if (done) R.drawable.ic_check else R.drawable.ic_info),
            contentDescription = state,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = Spacing.Hairline),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier.clearAndSetSemantics {
                        contentDescription =
                            "$state: $text"
                    },
            )
            action?.let { label ->
                TextButton(onClick = rememberHapticOnClick(onAction)) { Text(label) }
            }
        }
    }
}
