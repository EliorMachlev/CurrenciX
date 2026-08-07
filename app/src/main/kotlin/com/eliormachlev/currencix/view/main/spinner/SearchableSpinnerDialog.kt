package com.eliormachlev.currencix.view.main.spinner

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.util.DECIMAL_PLACES_DEFAULT
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
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
                        // No initial value — we must know when stars has actually
                        // emitted. If we defaulted to emptyList, the picker would
                        // render rates in raw order on the first frame, then
                        // reorder (starred → top) once stars arrived. LazyColumn's
                        // key-based scroll preservation reacts to that reorder by
                        // holding the previously visible row on screen, which
                        // pushes index 0 off the top and opens the picker mid- or
                        // bottom-scroll. Wait for both sources, render once.
                        val stars by mainViewModel.getStarredCurrencies().observeAsState()
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

                        val ready = rates != null && stars != null
                        SearchableCurrencyPicker(
                            rates = if (ready) rates?.rates.orEmpty() else emptyList(),
                            stars = stars.orEmpty(),
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

    override fun onStart() {
        super.onStart()
        // AlertDialog theme sets FLAG_ALT_FOCUSABLE_IM, which routes IME to
        // the underlying window instead of this dialog — Compose TextField
        // taps then can't raise the keyboard. Clear that flag and opt into
        // RESIZE so tapping the search field opens the IME.
        dialog?.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onPause() {
        super.onPause()
        // Close on config change; state restore across rotation isn't worth the complexity.
        dismiss()
    }
}
