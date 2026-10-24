package com.eliormachlev.currencix.view.main.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.AbroadOffer
import com.eliormachlev.currencix.util.ltrIsolate
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPromptSheet
import com.eliormachlev.currencix.view.compose.dialogs.PromptMessage
import java.util.Locale

/**
 * The phone is abroad ([offer]): convert the local currency to home's
 * there? [onSwitch] sets the pair and refreshes the rates while there's a
 * connection; closing it ([onDismiss]) leaves the converter as it is. Asked
 * once per visit.
 */
@Composable
fun AbroadSheet(
    offer: AbroadOffer,
    onSwitch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val country =
        Locale
            .Builder()
            .setRegion(offer.country)
            .build()
            .getDisplayCountry(locale)
            .ifBlank { offer.country }
    LedgerPromptSheet(
        title = stringResource(R.string.abroad_title, country),
        confirmLabel = stringResource(R.string.abroad_switch),
        onConfirm = onSwitch,
        onDismiss = onDismiss,
        cancelLabel = stringResource(R.string.abroad_not_now),
    ) {
        // "KRW → ILS" kept whole and left to right inside a right-to-left sentence.
        PromptMessage(stringResource(R.string.abroad_message, ltrIsolate("${offer.local.iso4217Alpha()} → ${offer.home.iso4217Alpha()}")))
    }
}
