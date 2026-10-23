package com.eliormachlev.currencix.view.main

import android.content.Context
import android.content.Intent
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import java.math.BigDecimal

/**
 * Intents that open the app on a given pair (and amount) or on the cart —
 * from the text-selection popup, launcher shortcuts and widgets — and the
 * parsing of them on arrival ([parse]).
 *
 * MainActivity is exported, so any app can send these: every extra is
 * validated and anything unreadable is dropped rather than guessed.
 */
object ConverterLaunch {
    const val ACTION_CONVERT = "com.eliormachlev.currencix.action.CONVERT"
    const val ACTION_OPEN_CART = "com.eliormachlev.currencix.action.OPEN_CART"

    private const val EXTRA_FROM = "from"
    private const val EXTRA_TO = "to"
    private const val EXTRA_AMOUNT = "amount"

    // Longer than any amount the keypad takes; a guard against junk input.
    private const val MAX_AMOUNT_LENGTH = 24

    /** What an incoming intent asks for. */
    sealed interface Request {
        /** The converter on [pair], with [amount] typed in when given. */
        data class Convert(
            val pair: CurrencyPair,
            val amount: BigDecimal?,
        ) : Request

        data object OpenCart : Request
    }

    /** Opens the converter on [from] → [to], with [amount] typed in when given. */
    fun convert(
        context: Context,
        from: Currency,
        to: Currency,
        amount: BigDecimal? = null,
    ): Intent =
        base(context, ACTION_CONVERT)
            .putExtra(EXTRA_FROM, from.iso4217Alpha())
            .putExtra(EXTRA_TO, to.iso4217Alpha())
            .apply { amount?.let { putExtra(EXTRA_AMOUNT, it.toPlainString()) } }

    fun openCart(context: Context): Intent = base(context, ACTION_OPEN_CART)

    /** Just the app, as it was. */
    fun openConverter(context: Context): Intent = base(context, Intent.ACTION_MAIN)

    // Brings an open app forward (onNewIntent) instead of stacking a second one.
    private fun base(
        context: Context,
        action: String,
    ): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** The request [intent] carries; null for a plain launch or anything unreadable. */
    fun parse(intent: Intent?): Request? =
        when (intent?.action) {
            ACTION_OPEN_CART -> Request.OpenCart
            ACTION_CONVERT -> parseConvert(intent)
            else -> null
        }

    private fun parseConvert(intent: Intent): Request.Convert? {
        val from = intent.getStringExtra(EXTRA_FROM)?.let(Currency::fromString) ?: return null
        val to = intent.getStringExtra(EXTRA_TO)?.let(Currency::fromString) ?: return null
        if (from == to) return null
        val amount =
            intent
                .getStringExtra(EXTRA_AMOUNT)
                ?.takeIf { it.length <= MAX_AMOUNT_LENGTH }
                ?.toBigDecimalOrNull()
                ?.takeIf { it.signum() >= 0 }
        return Request.Convert(CurrencyPair(from, to), amount)
    }
}
