package com.eliormachlev.currencix.view.cart

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.feeStackDelta
import com.eliormachlev.currencix.util.formatCartAmount
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.toCartFeePercentDisplay
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel
import java.math.BigDecimal
import java.math.MathContext

/**
 * Binds the cart footer (subtotal / total rows plus the per-side fee-extra
 * annotations) to the [CartViewModel]. Owns the 14 footer views so the host
 * activity only needs to forward LiveData emissions via [onSubtotalChanged] /
 * [onTotalChanged] / [refreshFeeAnnotations].
 */
class CartFooterBinding(
    root: View,
    private val ctx: Context,
    private val viewModel: CartViewModel,
) {
    private val subtotalLabel: TextView = root.findViewById(R.id.cart_subtotal_value)
    private val subtotalExtra: View = root.findViewById(R.id.cart_subtotal_extra)
    private val subtotalExtraLabel: TextView = root.findViewById(R.id.cart_subtotal_extra_label)
    private val subtotalExtraValue: TextView = root.findViewById(R.id.cart_subtotal_extra_value)
    private val subtotalFee: View = root.findViewById(R.id.cart_subtotal_fee)
    private val subtotalFeeLabel: TextView = root.findViewById(R.id.cart_subtotal_fee_label)
    private val subtotalFeeValue: TextView = root.findViewById(R.id.cart_subtotal_fee_value)
    private val feeLine: TextView = root.findViewById(R.id.cart_fee_line)
    private val totalLabel: TextView = root.findViewById(R.id.cart_total_value)
    private val totalExtra: View = root.findViewById(R.id.cart_total_extra)
    private val totalExtraLabel: TextView = root.findViewById(R.id.cart_total_extra_label)
    private val totalExtraValue: TextView = root.findViewById(R.id.cart_total_extra_value)
    private val totalFee: View = root.findViewById(R.id.cart_total_fee)
    private val totalFeeLabel: TextView = root.findViewById(R.id.cart_total_fee_label)
    private val totalFeeValue: TextView = root.findViewById(R.id.cart_total_fee_value)

    fun onSubtotalChanged(value: BigDecimal?) {
        subtotalLabel.text = ctx.formatCartAmount(value, viewModel.getBaseCurrency().value)
        updateFeeExtras()
    }

    fun onTotalChanged(value: BigDecimal?) {
        totalLabel.text = ctx.formatCartAmount(value, viewModel.getDestinationCurrency().value)
        updateFeeExtras()
    }

    /**
     * Re-render every fee-driven row when either the fee list or the cart's
     * currencies change — both the combined-percent line and the per-side
     * annotations depend on that state.
     */
    fun refreshFeeAnnotations() {
        updateFeeLine()
        updateFeeExtras()
    }

    private fun updateFeeLine() {
        // Only surface the combined percent when *both* sides carry a fee —
        // otherwise the per-side annotation block already spells out the same
        // number ("Fee: +2%") and this row is a duplicate.
        val stacks = viewModel.currentSideStacks()
        val bothSides = !stacks.original.isNeutralFeeStack() && !stacks.converted.isNeutralFeeStack()
        if (!bothSides) {
            feeLine.visibility = View.GONE
            return
        }
        feeLine.text = ctx.getString(R.string.cart_fee_line, stacks.combined.toCartFeePercentDisplay())
        feeLine.visibility = View.VISIBLE
    }

    /**
     * Show both anchor values for each per-side fee: the fee amount itself
     * ("Conversion fee" / "Reduction fee") and the effective total-with-fee /
     * pre-fee value ("Cost with fee" / "Value before fee"). Ordering matches
     * the on-screen layout: fee then cost-with-fee on ORIGINAL; value-before-
     * fee then reduction-fee on CONVERTED. Either or both blocks may hide.
     */
    private fun updateFeeExtras() {
        val stacks = viewModel.currentSideStacks()
        val baseCurrency = viewModel.getBaseCurrency().value
        val destCurrency = viewModel.getDestinationCurrency().value
        val subtotal = viewModel.getSubtotal().value
        val total = viewModel.getTotal().value
        renderFeeExtraRow(
            subtotalFee,
            subtotalFeeLabel,
            subtotalFeeValue,
            R.string.fee_true_cost_prefix,
            stacks.original,
            subtotal,
            baseCurrency,
            FeeRowMode.DELTA,
        )
        renderFeeExtraRow(
            subtotalExtra,
            subtotalExtraLabel,
            subtotalExtraValue,
            R.string.fee_cost_with_fee_prefix,
            stacks.original,
            subtotal,
            baseCurrency,
            FeeRowMode.TOTAL,
        )
        renderFeeExtraRow(
            totalExtra,
            totalExtraLabel,
            totalExtraValue,
            R.string.fee_value_before_fee_prefix,
            stacks.converted,
            total,
            destCurrency,
            FeeRowMode.TOTAL,
        )
        renderFeeExtraRow(
            totalFee,
            totalFeeLabel,
            totalFeeValue,
            R.string.fee_original_value_prefix,
            stacks.converted,
            total,
            destCurrency,
            FeeRowMode.DELTA,
        )
    }

    // Hidden when the [stack] is trivial. TOTAL renders `base * stack` (the
    // effective with-fee cost or pre-fee value); DELTA renders `|base *
    // (stack - 1)|` (the fee magnitude — the percent tail already carries
    // the sign so we avoid a double negative). Only DELTA rows show the
    // percent — TOTAL rows sit next to a DELTA row that already spells it
    // out, so restating it here would just be a duplicate.
    private fun renderFeeExtraRow(
        container: View,
        labelView: TextView,
        valueView: TextView,
        @StringRes prefixRes: Int,
        stack: BigDecimal,
        base: BigDecimal?,
        currency: Currency?,
        mode: FeeRowMode,
    ) {
        if (stack.isNeutralFeeStack()) {
            container.visibility = View.GONE
            return
        }
        val multiplier =
            when (mode) {
                FeeRowMode.TOTAL -> stack
                FeeRowMode.DELTA -> stack.feeStackDelta()
            }
        val raw = (base ?: BigDecimal.ZERO).multiply(multiplier, MathContext.DECIMAL128)
        val adjusted = if (mode == FeeRowMode.DELTA) raw.abs() else raw
        labelView.text = stripLabelSeparator(ctx.getString(prefixRes))
        val amountText = ctx.formatCartAmount(adjusted, currency)
        valueView.text =
            when (mode) {
                FeeRowMode.TOTAL -> amountText
                FeeRowMode.DELTA -> ctx.getString(R.string.cart_fee_extra_value, amountText, stack.toCartFeePercentDisplay())
            }
        container.visibility = View.VISIBLE
    }

    private enum class FeeRowMode { TOTAL, DELTA }

    // Existing prefix strings end with a locale-specific ": " / " : " / "：" for
    // inline use. When we're showing them as a standalone left-aligned label,
    // strip the trailing separator so it doesn't dangle before the right column.
    private fun stripLabelSeparator(text: String): String = text.trimEnd(' ', '\u00A0', ':', '：')
}
