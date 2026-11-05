package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.util.ReceiptLine
import com.eliormachlev.currencix.util.formatCartAmount
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.cart.ReceiptSetup
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.SupportingText
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPromptSheet
import com.eliormachlev.currencix.view.compose.dialogs.PromptMessage
import com.eliormachlev.currencix.view.compose.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** "Reading the receipt…" while the photo is read; it takes a few seconds. */
@Composable
fun ReceiptReadingSheet(onDismiss: () -> Unit) {
    LedgerBottomSheet(title = stringResource(R.string.receipt_scan), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.XXLarge, vertical = Spacing.Large)) {
            PromptMessage(stringResource(R.string.receipt_reading))
            Spacer(Modifier.height(Spacing.Medium))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

/**
 * The first scan, with no language on the device yet: the ones [offer]
 * lists are downloaded only on [onDownload], each named with its size and
 * left unticked; Download waits for at least one. [onDownload] gets those
 * ticked.
 */
@Composable
fun ReceiptSetupOfferSheet(
    offer: ReceiptSetup.Offer,
    onDownload: (chosen: Set<TessLanguage>) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = remember(offer) { mutableStateListOf<TessLanguage>() }
    val missingLanguage = chosen.isEmpty()
    LedgerPromptSheet(
        title = stringResource(R.string.receipt_scan),
        confirmLabel = stringResource(R.string.receipt_setup_download),
        onConfirm = { onDownload(chosen.toSet()) },
        onDismiss = onDismiss,
        confirmEnabled = !missingLanguage,
    ) {
        PromptMessage(stringResource(R.string.receipt_setup_message))
        Spacer(Modifier.height(Spacing.Small))
        offer.languages.forEach { language ->
            LanguageChoice(
                language = language,
                until = offer.until[language],
                checked = language in chosen,
                onCheckedChange = { on -> if (on) chosen += language else chosen -= language },
            )
        }
        if (missingLanguage) {
            Text(
                stringResource(R.string.receipt_setup_pick_language),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.Small),
            )
        }
    }
}

// One language as a tick: its name, its size, and the day it goes when it's for a trip.
@Composable
private fun LanguageChoice(
    language: TessLanguage,
    until: Instant?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(vertical = Spacing.Tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        val name = language.displayName(locale)
        val size = language.downloadSize(context)
        Text(
            until?.let { stringResource(R.string.receipt_setup_language_until, name, size, formatUntilDay(it, locale)) }
                ?: stringResource(R.string.receipt_setup_language, name, size),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Whether to also read receipts in [language] (the trip's, the cart
 * currency's or the app's own), its data a one-time download of its size:
 * [onAnswer] true to download it, false for not now (closing the sheet says
 * no too).
 */
@Composable
fun ReceiptLanguageOfferSheet(
    language: TessLanguage,
    onAnswer: (download: Boolean) -> Unit,
    until: Instant? = null,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val name = language.displayName(locale)
    LedgerPromptSheet(
        title = stringResource(R.string.receipt_scan),
        confirmLabel = stringResource(R.string.receipt_setup_download),
        onConfirm = { onAnswer(true) },
        onDismiss = { onAnswer(false) },
        cancelLabel = stringResource(R.string.receipt_language_not_now),
    ) {
        val size = language.downloadSize(context)
        PromptMessage(
            until?.let { stringResource(R.string.receipt_language_offer_trip, name, size, formatUntilDay(it, locale)) }
                ?: stringResource(R.string.receipt_language_offer, name, size),
        )
    }
}

// The day a trip's language goes ("14 Nov 2026"), in the phone's time zone.
private fun formatUntilDay(
    until: Instant,
    locale: Locale,
): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(until.atZone(ZoneId.systemDefault()))

/** The languages downloading; closing it lets them carry on unseen. */
@Composable
fun ReceiptSetupProgressSheet(onDismiss: () -> Unit) {
    LedgerBottomSheet(title = stringResource(R.string.receipt_scan), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.XXLarge, vertical = Spacing.Large)) {
            PromptMessage(stringResource(R.string.receipt_setup_progress))
            Spacer(Modifier.height(Spacing.Medium))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

/**
 * The items found on a receipt, each ticked, prices in the cart's
 * [currency]: untick what isn't wanted, then add the rest ([onAdd]). With
 * none found, says so. [languagePending]: a language's OCR data is still
 * downloading, so names were read in the ones already here. [languageDeclined]: the
 * user said no to it, so a button offers it again ([onOfferLanguage]).
 */
@Composable
fun ReceiptReviewSheet(
    lines: List<ReceiptLine>,
    currency: Currency?,
    languagePending: Boolean,
    onAdd: (List<ReceiptLine>) -> Unit,
    onDismiss: () -> Unit,
    languageDeclined: TessLanguage? = null,
    onOfferLanguage: () -> Unit = {},
) {
    val context = LocalContext.current
    val chosen = remember(lines) { lines.indices.toList().toMutableStateList() }
    LedgerPromptSheet(
        title = stringResource(R.string.receipt_scan),
        confirmLabel = pluralStringResource(R.plurals.receipt_add, chosen.size, chosen.size),
        onConfirm = { onAdd(chosen.sorted().map(lines::get)) },
        onDismiss = onDismiss,
        confirmEnabled = chosen.isNotEmpty(),
    ) {
        if (lines.isEmpty()) PromptMessage(stringResource(R.string.receipt_none_found))
        lines.forEachIndexed { index, line ->
            val ticked = index in chosen
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = ticked, role = Role.Checkbox) { on -> if (on) chosen += index else chosen -= index }
                        .padding(vertical = Spacing.Tiny),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Checkbox(checked = ticked, onCheckedChange = null)
                Text(line.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Ltr { Text(context.formatCartAmount(line.price, currency), style = MaterialTheme.typography.bodyLarge) }
            }
        }
        Spacer(Modifier.height(Spacing.Small))
        SupportingText(stringResource(if (languagePending) R.string.receipt_language_pending else R.string.receipt_check_hint))
        // Said no to the app's language: names were read as English; it can still be had.
        languageDeclined?.let { language ->
            TextButton(onClick = rememberHapticOnClick(onOfferLanguage)) {
                Text(stringResource(R.string.receipt_language_read_too, language.displayName(LocalConfiguration.current.locales[0])))
            }
        }
    }
}
