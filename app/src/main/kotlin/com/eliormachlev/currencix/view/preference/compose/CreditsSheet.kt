package com.eliormachlev.currencix.view.preference.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet

/**
 * Compose-native credits sheet — a [LedgerBottomSheet] wrapping the existing
 * [CreditsList]. Replaces the DialogFragment-based `CreditsDialog` so the
 * hosting Fragment no longer needs a `FragmentManager` (and the sheet inherits
 * the paper/brass chrome the rest of the ledger surfaces use).
 *
 * [CreditsList] is a LazyColumn — the sheet passes `scrollableBody = false` so
 * the list owns its own scroll instead of nesting inside a verticalScroll.
 */
@Composable
fun CreditsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sections = remember(context) { creditsSections(context) }
    LedgerBottomSheet(
        title = stringResource(id = R.string.title_credits),
        onDismiss = onDismiss,
        scrollableBody = false,
    ) {
        CreditsList(sections)
    }
}
