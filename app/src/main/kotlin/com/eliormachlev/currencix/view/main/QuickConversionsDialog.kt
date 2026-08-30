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
import com.eliormachlev.currencix.view.main.compose.FeeRowPrefixes
import com.eliormachlev.currencix.view.main.compose.QuickConversionsContent
import com.eliormachlev.currencix.view.main.compose.QuickConversionsRow
import com.eliormachlev.currencix.view.main.compose.buildQuickConversionRows
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import java.math.RoundingMode

private const val FEE_PERCENT_DECIMAL_PLACES = 2

class QuickConversionsDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        val prefixes =
            FeeRowPrefixes(
                fee = getString(R.string.fee_true_cost_prefix),
                costWithFee = getString(R.string.fee_cost_with_fee_prefix),
                valueBeforeFee = getString(R.string.fee_value_before_fee_prefix),
                reduction = getString(R.string.fee_original_value_prefix),
            )
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
                                    prefixes = prefixes,
                                )
                            } else {
                                emptyList()
                            }
                        val combined = stacks.combined
                        val feeInfoText =
                            if (!combined.isNeutralFeeStack()) {
                                val percent =
                                    combined.feePercentDelta(
                                        FEE_PERCENT_DECIMAL_PLACES,
                                        RoundingMode.HALF_UP,
                                    )
                                val sign = if (percent.signum() >= 0) "+" else ""
                                getString(
                                    R.string.quick_conversions_fees_applied,
                                    "$sign$percent%",
                                )
                            } else {
                                null
                            }
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
}
