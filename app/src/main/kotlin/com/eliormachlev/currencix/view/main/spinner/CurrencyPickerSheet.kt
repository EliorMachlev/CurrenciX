package com.eliormachlev.currencix.view.main.spinner

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.math.BigDecimal

// Cap the sheet at ~90% of the screen so the paper background peeks above the
// drag handle — matches the other ledger sheets, which never quite reach the
// status bar even when their content wants to.
private const val SHEET_HEIGHT_FRACTION = 0.9f

// Distinct keys so this sheet's read-only MainViewModel and PreferenceViewModel
// instances don't collide with the primary VMs the hosting screen may already
// own on the same ViewModelStore. Matches the intent of the pre-Compose
// [SearchableSpinnerDialog], which scoped its VM to its own DialogFragment.
private const val PICKER_MAIN_VM_KEY = "CurrencyPickerSheet.main"
private const val PICKER_PREF_VM_KEY = "CurrencyPickerSheet.pref"

/**
 * Compose-native currency picker — a [LedgerBottomSheet] wrapping the shared
 * [SearchableCurrencyPicker] with its search bar, favorites-first list, and
 * drag-to-reorder starred rows. Replaces the DialogFragment-based
 * `SearchableSpinnerDialog` so callers no longer need a `FragmentManager`.
 *
 * [currentRate] + [currentSum] drive the optional per-row preview conversion
 * (when the user has enabled it in preferences). [disabledCurrency] greys out
 * the row for the opposite side of the pair so it can't be picked.
 */
@Composable
fun CurrencyPickerSheet(
    currentRate: Rate?,
    currentSum: BigDecimal,
    disabledCurrency: Currency?,
    onRateClicked: (Rate) -> Unit,
    onDismiss: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val mainViewModel: MainViewModel =
        viewModel(
            key = PICKER_MAIN_VM_KEY,
            factory = MainViewModel.Factory(application, onlyCache = true),
        )
    val prefViewModel: PreferenceViewModel = viewModel(key = PICKER_PREF_VM_KEY)

    LedgerBottomSheet(
        title = stringResource(id = R.string.picker_currency_title),
        onDismiss = onDismiss,
        scrollableBody = false,
    ) {
        val rates by mainViewModel.getExchangeRates().observeAsState()
        val stars by mainViewModel.getStarredCurrencies().collectAsStateWithLifecycle()
        val filterStarred by mainViewModel.isFilterStarredEnabled().collectAsStateWithLifecycle()
        val previewEnabled by prefViewModel.isPreviewConversionEnabled.collectAsStateWithLifecycle()
        val decimalPlaces by mainViewModel.getDecimalPlaces().collectAsStateWithLifecycle()

        val conversion =
            if (previewEnabled && currentRate != null) {
                CurrencyPickerConversion(
                    baseRate = currentRate,
                    baseSum = currentSum,
                    decimalPlaces = decimalPlaces,
                )
            } else {
                null
            }

        val ready = rates != null
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(SHEET_HEIGHT_FRACTION),
        ) {
            SearchableCurrencyPicker(
                rates =
                    if (ready) {
                        rates?.rates.orEmpty().toImmutableList()
                    } else {
                        persistentListOf()
                    },
                stars = stars,
                filterStarred = filterStarred,
                conversion = conversion,
                disabledCurrency = disabledCurrency,
                onRateClicked = { rate ->
                    onRateClicked(rate)
                    onDismiss()
                },
                onStarClicked = { mainViewModel.toggleCurrencyStar(it.currency) },
                onToggleStarredFilter = { mainViewModel.toggleStarredActive() },
                onStarredOrderChanged = { mainViewModel.setStarredCurrencyOrder(it) },
            )
        }
    }
}
