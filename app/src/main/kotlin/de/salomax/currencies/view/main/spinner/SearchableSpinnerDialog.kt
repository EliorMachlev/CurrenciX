package de.salomax.currencies.view.main.spinner

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import de.salomax.currencies.model.Rate
import de.salomax.currencies.util.DECIMAL_PLACES_DEFAULT
import de.salomax.currencies.view.compose.AppTheme
import de.salomax.currencies.viewmodel.main.MainViewModel
import de.salomax.currencies.viewmodel.preference.PreferenceViewModel
import java.math.BigDecimal

class SearchableSpinnerDialog(
    @Suppress("UNUSED_PARAMETER") context: Context,
) : AppCompatDialogFragment() {
    var onRateClicked: ((Rate, Int) -> Unit)? = null

    // Backed by MutableState so Compose recomposes when callers push new
    // conversion-preview inputs (rate/sum) from outside the composition.
    private val currentRateState = mutableStateOf<Rate?>(null)
    private val currentSumState = mutableStateOf(BigDecimal.ONE)

    fun setCurrentRate(currentRate: Rate) {
        currentRateState.value = currentRate
    }

    fun setCurrentSum(currentSum: BigDecimal) {
        currentSumState.value = currentSum
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val mainViewModel =
            ViewModelProvider(
                this,
                MainViewModel.Factory(requireActivity().application, true),
            )[MainViewModel::class.java]
        val prefViewModel = ViewModelProvider(this)[PreferenceViewModel::class.java]

        val composeView =
            ComposeView(requireContext()).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    AppTheme {
                        val rates by mainViewModel.getExchangeRates().observeAsState()
                        val stars by mainViewModel.getStarredCurrencies().observeAsState(initial = emptyList())
                        val filterStarred by mainViewModel.isFilterStarredEnabled().observeAsState(initial = false)
                        val previewEnabled by prefViewModel.isPreviewConversionEnabled().observeAsState(initial = false)
                        val decimalPlaces by mainViewModel.getDecimalPlaces().observeAsState()

                        val baseRate = currentRateState.value
                        val conversion =
                            if (previewEnabled && baseRate != null) {
                                CurrencyPickerConversion(
                                    baseRate = baseRate,
                                    baseSum = currentSumState.value,
                                    decimalPlaces = decimalPlaces ?: DECIMAL_PLACES_DEFAULT,
                                )
                            } else {
                                null
                            }

                        SearchableCurrencyPicker(
                            rates = rates?.rates.orEmpty(),
                            stars = stars,
                            filterStarred = filterStarred,
                            conversion = conversion,
                            onRateClicked = { rate ->
                                onRateClicked?.invoke(rate, rates?.rates?.indexOf(rate) ?: -1)
                                dismiss()
                            },
                            onStarClicked = { mainViewModel.toggleCurrencyStar(it.currency) },
                            onToggleStarredFilter = { mainViewModel.toggleStarredActive() },
                            onStarredOrderChanged = { mainViewModel.setStarredCurrencyOrder(it) },
                        )
                    }
                }
            }

        return AlertDialog
            .Builder(requireContext())
            .setNegativeButton(getString(android.R.string.cancel), null)
            .setView(composeView)
            .create()
    }

    override fun onPause() {
        super.onPause()
        // Close on config change; state restore across rotation isn't worth the complexity.
        dismiss()
    }
}
