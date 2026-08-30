package com.eliormachlev.currencix.view.main

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.SideStacks
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.main.compose.QuickConversionsContent
import com.eliormachlev.currencix.view.main.compose.QuickConversionsRow
import com.eliormachlev.currencix.view.main.compose.buildQuickConversionRows
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import java.math.BigDecimal
import java.math.RoundingMode

private const val FEE_PERCENT_DECIMAL_PLACES = 2

class QuickConversionsDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        val costWithFeePrefix = getString(R.string.fee_cost_with_fee_prefix)
        val emptyText = getString(R.string.quick_conversions_no_rates)

        val view =
            ComposeView(ctx).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    AppTheme {
                        val from by viewModel.getBaseCurrency().observeAsState()
                        val to by viewModel.getDestinationCurrency().observeAsState()
                        val rates by viewModel.getExchangeRates().observeAsState()
                        // Fees are read as an observable so a fees change
                        // recomposes; the actual stack derivation lives in
                        // sideStacksFor() which isn't itself snapshot-observed.
                        val fees = viewModel.getFees().observeAsState().value

                        val stacks =
                            remember(from, to, fees) {
                                if (from != null && to != null) {
                                    viewModel.sideStacksFor(from, to)
                                } else {
                                    SideStacks.NEUTRAL
                                }
                            }
                        val rows: List<QuickConversionsRow> =
                            if (from != null && to != null && rates != null) {
                                buildQuickConversionRows(
                                    ctx = ctx,
                                    from = from!!,
                                    to = to!!,
                                    rates = rates!!,
                                    sideStacks = stacks,
                                    costWithFeePrefix = costWithFeePrefix,
                                )
                            } else {
                                emptyList()
                            }
                        val feeInfoText = buildFeeInfoText(stacks.original, stacks.converted)
                        QuickConversionsContent(
                            from = from,
                            to = to,
                            feeInfoText = feeInfoText,
                            rows = rows,
                            emptyText = emptyText,
                            onSwap = { (activity as? MainActivity)?.toggleEvent(null) },
                            onSwapLongPress = { openFeesSettings(ctx) },
                        )
                    }
                }
            }

        return AlertDialog
            .Builder(ctx)
            .setTitle(R.string.quick_conversions_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    private fun openFeesSettings(ctx: Context) {
        startActivity(PreferenceActivity.feesIntent(ctx))
    }

    // Split the top-of-dialog "fees applied" line by side so users see which
    // is the conversion (ORIGINAL) fee vs the reduction (CONVERTED) fee,
    // instead of a single combined percentage that hides the breakdown.
    private fun buildFeeInfoText(
        original: BigDecimal,
        converted: BigDecimal,
    ): String? {
        val parts =
            listOfNotNull(
                feeSegment(original, R.string.quick_conversions_fee_conversion),
                feeSegment(converted, R.string.quick_conversions_fee_reduction),
            )
        if (parts.isEmpty()) return null
        return getString(R.string.quick_conversions_fees_applied, parts.joinToString(", "))
    }

    private fun feeSegment(
        stack: BigDecimal,
        templateRes: Int,
    ): String? {
        if (stack.isNeutralFeeStack()) return null
        val percent = stack.feePercentDelta(FEE_PERCENT_DECIMAL_PLACES, RoundingMode.HALF_UP)
        val sign = if (percent.signum() >= 0) "+" else ""
        return getString(templateRes, "$sign$percent%")
    }
}
