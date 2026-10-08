package com.eliormachlev.currencix.view.compose.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.compose.theme.Brass
import kotlinx.coroutines.launch

// Ledger sheet chrome. The M3 ModalBottomSheet gives us the drag handle and
// scrim for free; we override just the container
// color + tonal elevation so the sheet sits on the same paper background as
// the rest of the ledger surfaces (rather than the raised tonal container the
// default palette would paint).
private val SHEET_HORIZONTAL_PADDING = 0.dp
private val SHEET_TITLE_HORIZONTAL_PADDING = 20.dp
private val SHEET_TITLE_TOP_PADDING = 4.dp
private val SHEET_TITLE_BOTTOM_PADDING = 12.dp
private val SHEET_BOTTOM_PADDING = 8.dp
private val SHEET_TITLE_LETTER_SPACING = 0.14.em

/**
 * Ledger-styled modal bottom sheet — [ModalBottomSheet] with a paper-toned
 * container, a brass small-caps title header, and a body slot that inherits
 * [ColumnScope] so callers can drop [com.eliormachlev.currencix.view.compose.LedgerRow]
 * children in directly.
 *
 * Set [scrollableBody] = false when the caller's content already owns its own
 * scroll (e.g. a [androidx.compose.foundation.lazy.LazyColumn]) — nesting two
 * vertical scrollables would crash. Defaults to true so the common
 * pick-a-value shape (short row list) still scrolls when the sheet is short
 * of the screen edge.
 *
 * Set [skipPartiallyExpanded] = false to let the sheet settle at the M3
 * half-height anchor first and expand to full on upward drag/scroll. The
 * default (true) is right for short pickers that only need one anchor.
 *
 * Replaces the stock AlertDialog-with-radio-rows shape for list-shaped choice
 * surfaces (data-provider picker, and any future picker that wants sheet
 * ergonomics — swipe-to-dismiss, drag handle, edge-to-edge with the nav bar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    scrollableBody: Boolean = true,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val scope = rememberCoroutineScope()
    AppTheme {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            // Back is handled below rather than by the sheet, whose own
            // handler previews the gesture by shrinking the sheet under the
            // finger.
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false, shouldDismissOnClickOutside = true),
        ) {
            BackHandler { scope.launch { sheetState.stepBack(onDismiss) } }
            val bodyModifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (scrollableBody) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(
                        horizontal = SHEET_HORIZONTAL_PADDING,
                        vertical = SHEET_BOTTOM_PADDING,
                    )
            // Inside the sheet's window: the app's supportsRtl="false" makes
            // every window resolve left to right, so a direction set around
            // the sheet wouldn't reach in here.
            ReadingDirection {
                Column(modifier = bodyModifier) {
                    LedgerSheetTitle(title)
                    content()
                }
            }
        }
    }
}

/**
 * What back does to a sheet, as M3's own handler would: a fully expanded
 * sheet that also has a half-height anchor settles there; otherwise it
 * slides away and is then dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
private suspend fun SheetState.stepBack(onDismiss: () -> Unit) {
    if (currentValue == SheetValue.Expanded && hasPartiallyExpandedState) {
        partialExpand()
    } else {
        hide()
        onDismiss()
    }
}

/**
 * Brass small-caps title header for [LedgerBottomSheet] — mirrors the ledger
 * section header (see [com.eliormachlev.currencix.view.compose.LedgerSection])
 * so the sheet reads as an extension of the surface underneath it.
 */
@Composable
private fun LedgerSheetTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Brass,
        letterSpacing = SHEET_TITLE_LETTER_SPACING,
        fontFamily = FontFamily.Monospace,
        modifier =
            Modifier
                .padding(
                    start = SHEET_TITLE_HORIZONTAL_PADDING,
                    end = SHEET_TITLE_HORIZONTAL_PADDING,
                    top = SHEET_TITLE_TOP_PADDING,
                    bottom = SHEET_TITLE_BOTTOM_PADDING,
                ).semantics { heading() },
    )
}
