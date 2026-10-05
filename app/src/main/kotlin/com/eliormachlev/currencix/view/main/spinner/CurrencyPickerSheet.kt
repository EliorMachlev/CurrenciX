package com.eliormachlev.currencix.view.main.spinner

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.RecentPairs
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.math.BigDecimal

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
    selectedCurrency: Currency? = null,
) {
    val application = LocalContext.current.applicationContext as Application
    val mainViewModel: MainViewModel =
        viewModel(
            key = PICKER_MAIN_VM_KEY,
            factory = MainViewModel.factory(application, onlyCache = true),
        )
    val prefViewModel: PreferenceViewModel = viewModel(key = PICKER_PREF_VM_KEY)

    LedgerBottomSheet(
        title = stringResource(id = R.string.picker_currency_title),
        onDismiss = onDismiss,
        scrollableBody = false,
        // Start at the M3 half-height anchor; upward drag / list-scroll-past-top
        // will expand it to full. Matches the pre-Compose SearchableSpinnerDialog
        // proportions and the user's mental model for a searchable picker.
        skipPartiallyExpanded = false,
    ) {
        val rates by mainViewModel.getExchangeRates().observeAsState()
        val stars by mainViewModel.getStarredCurrencies().collectAsStateWithLifecycle()
        val filterStarred by mainViewModel.isFilterStarredEnabled().collectAsStateWithLifecycle()
        val previewEnabled by prefViewModel.isPreviewConversionEnabled.collectAsStateWithLifecycle()
        val decimalPlaces by mainViewModel.getDecimalPlaces().collectAsStateWithLifecycle()
        val database = rememberDatabase()
        val recents = rememberRecentCurrencies(database, excluded = setOfNotNull(disabledCurrency, selectedCurrency))

        val conversion =
            currentRate?.takeIf { previewEnabled }?.let { CurrencyPickerConversion(it, currentSum, decimalPlaces) }

        val ready = rates != null
        // fillMaxHeight so the picker occupies the full expanded-anchor slot;
        // at the partially-expanded anchor the M3 sheet clips this Column and
        // the top portion is what shows. Nested-scroll then hands upward
        // fling velocity from the LazyColumn to the sheet, which transitions
        // to Expanded before the list starts consuming the drag.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
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
                recents = recents,
                onRemoveRecent = database::removeRecentCurrency,
            )
        }
    }
}

@Composable
private fun rememberDatabase(): Database {
    val context = LocalContext.current
    return remember(context) { Database(context) }
}

// The recent pairs' currencies, most recent first, as picker shortcuts —
// minus [excluded]: the currency already on this side and the one it can't
// be (the other side).
@Composable
private fun rememberRecentCurrencies(
    database: Database,
    excluded: Set<Currency>,
): ImmutableList<Currency> {
    val recentPairs by database.getRecentPairsFlow().collectAsStateWithLifecycle(emptyList())
    return remember(recentPairs, excluded) {
        RecentPairs
            .currencies(recentPairs)
            .filterNot { it in excluded }
            .take(RecentPairs.MAX)
            .toImmutableList()
    }
}
