package com.eliormachlev.currencix.model

import com.eliormachlev.currencix.util.newId
import java.math.BigDecimal

/** Which way a rate has to move to reach an alert's threshold. */
enum class AlertDirection(
    /** The stored form; kept apart from the name so a rename doesn't break saved alerts. */
    val key: String,
) {
    ABOVE("above"),
    BELOW("below"),
    ;

    companion object {
        fun fromKey(key: String?): AlertDirection? = entries.firstOrNull { it.key == key }
    }
}

/**
 * "Tell me when 1 USD is 3.50 ILS or less": a [pair], a [threshold] for one
 * [CurrencyPair.from] in [CurrencyPair.to], and the [direction] the rate had
 * to move when the alert was set. It fires once, then it's gone.
 */
data class RateAlert(
    val id: String,
    val pair: CurrencyPair,
    val threshold: BigDecimal,
    val direction: AlertDirection,
) {
    /** Whether [rate] has reached the threshold. */
    fun isReachedBy(rate: BigDecimal): Boolean =
        when (direction) {
            AlertDirection.ABOVE -> rate >= threshold
            AlertDirection.BELOW -> rate <= threshold
        }

    companion object {
        /** An alert for [threshold] on [pair], in the direction away from [current] (above when it's higher). */
        fun towards(
            pair: CurrencyPair,
            threshold: BigDecimal,
            current: BigDecimal,
        ) = RateAlert(
            id = newId(),
            pair = pair,
            threshold = threshold,
            direction = if (threshold >= current) AlertDirection.ABOVE else AlertDirection.BELOW,
        )
    }
}
