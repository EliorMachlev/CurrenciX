package com.eliormachlev.currencix.model

import java.math.BigDecimal

/**
 * A single fee entry that can be stacked with others when converting
 * currencies. All entries store the [percent] as a positive number;
 * whether the fee is added or subtracted is controlled by [isMarkup].
 *
 * Every fee carries a user-facing [name] (may be empty for legacy entries)
 * and an [isActive] flag that lets the user temporarily skip it without
 * deleting the entry.
 */
sealed class Fee {
    abstract val id: String
    abstract val name: String
    abstract val percent: BigDecimal
    abstract val isMarkup: Boolean
    abstract val isActive: Boolean
    abstract val feeSide: FeeSide
    abstract val type: FeeType

    /** Applies to every conversion, no matter which currencies are involved. */
    data class GlobalExchange(
        override val id: String,
        override val name: String,
        override val percent: BigDecimal,
        override val isMarkup: Boolean,
        override val isActive: Boolean = true,
        override val feeSide: FeeSide = FeeSide.ORIGINAL,
    ) : Fee() {
        override val type: FeeType get() = FeeType.GLOBAL_EXCHANGE
    }

    /** Bank / card fee that also applies to every conversion. */
    data class GlobalBank(
        override val id: String,
        override val name: String,
        override val percent: BigDecimal,
        override val isMarkup: Boolean,
        override val isActive: Boolean = true,
        override val feeSide: FeeSide = FeeSide.ORIGINAL,
    ) : Fee() {
        override val type: FeeType get() = FeeType.GLOBAL_BANK
    }

    /**
     * A fee tied to a specific currency pair. When [bothWays] is true the
     * fee also matches the reverse direction ([to] -> [from]).
     */
    data class SpecificPair(
        override val id: String,
        override val name: String,
        override val percent: BigDecimal,
        override val isMarkup: Boolean,
        val from: String,
        val to: String,
        val bothWays: Boolean,
        override val isActive: Boolean = true,
        override val feeSide: FeeSide = FeeSide.ORIGINAL,
    ) : Fee() {
        override val type: FeeType get() = FeeType.SPECIFIC_PAIR
    }
}

/**
 * Discriminator tag for the [Fee] sealed hierarchy, kept next to the model so
 * on-disk backups and the [Database] JSON encoding read from a single source
 * of truth. `wire` values are persisted verbatim and **must not change**.
 */
enum class FeeType(
    val wire: String,
) {
    GLOBAL_EXCHANGE("global_exchange"),
    GLOBAL_BANK("global_bank"),
    SPECIFIC_PAIR("specific_pair"),
    ;

    companion object {
        fun fromWire(wire: String?): FeeType? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * Which side of the conversion a fee applies to.
 * - [ORIGINAL]: the fee inflates the input-side "true cost"; the displayed
 *   converted amount is left at the mid-market value.
 * - [CONVERTED]: the fee is baked into the displayed converted amount; the
 *   pre-fee "original value" is surfaced separately when needed.
 */
enum class FeeSide {
    ORIGINAL,
    CONVERTED,
}
