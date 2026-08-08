package com.eliormachlev.currencix.view.main

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.FeeSide
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.main.compose.QuickConversionsContent
import com.eliormachlev.currencix.view.main.compose.QuickConversionsRow
import com.eliormachlev.currencix.view.main.compose.buildQuickConversionRows
import com.eliormachlev.currencix.view.main.compose.hasFees
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import java.math.RoundingMode

private const val FEE_PERCENT_DECIMAL_PLACES = 2

class QuickConversionsDialog : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        val truePrefix = getString(R.string.fee_true_cost_prefix)
        val originalPrefix = getString(R.string.fee_original_value_prefix)
        val emptyText = getString(R.string.quick_conversions_no_rates)

        val view =
            ComposeView(ctx).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    AppTheme {
                        val from by viewModel.getBaseCurrency().observeAsState()
                        val to by viewModel.getDestinationCurrency().observeAsState()
                        val rates by viewModel.getExchangeRates().observeAsState()
                        val side by viewModel.getFeeSide().observeAsState(FeeSide.ORIGINAL)
                        // Read `.value` so a fees change triggers recomposition —
                        // feeStackFor() is the actual data source but doesn't
                        // participate in snapshot reads.
                        val fees = viewModel.getFees().observeAsState().value

                        val stack =
                            if (from != null && to != null) {
                                // fees participates in the key so a change forces re-eval
                                @Suppress("UNUSED_EXPRESSION")
                                fees
                                viewModel.feeStackFor(from!!, to!!)
                            } else {
                                java.math.BigDecimal.ZERO
                            }
                        val hasFees = stack.hasFees()
                        val rows: List<QuickConversionsRow> =
                            if (from != null && to != null && rates != null) {
                                buildQuickConversionRows(
                                    ctx = ctx,
                                    from = from!!,
                                    to = to!!,
                                    rates = rates!!,
                                    feeStack = stack,
                                    feeSide = side,
                                    truePrefix = truePrefix,
                                    originalPrefix = originalPrefix,
                                )
                            } else {
                                emptyList()
                            }
                        val feeInfoText =
                            if (hasFees) {
                                val percent =
                                    stack.feePercentDelta(
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
                            feeSide = side,
                            showFeeSideButton = hasFees,
                            feeInfoText = feeInfoText,
                            rows = rows,
                            emptyText = emptyText,
                            onSwap = { (activity as? MainActivity)?.toggleEvent(null) },
                            onSwapLongPress = { openFeesSettings(ctx) },
                            onToggleFeeSide = {
                                viewModel.setFeeSide(side.toggled())
                            },
                            onFeeSideLongPress = { openFeesSettings(ctx) },
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
