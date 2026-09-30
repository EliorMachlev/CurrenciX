package com.eliormachlev.currencix.model

import java.math.BigDecimal

/**
 * A single fee entry that can be stacked with others when converting
 * currencies. All entries store the [percent] as a positive markup — real
 * exchange fees always inflate the cost, never reduce it.
 *
 * Every fee carries a user-facing [name] (may be empty for legacy entries)
 * and an [isActive] flag that lets the user temporarily skip it without
 * deleting the entry.
 */
sealed class Fee {
    abstract val id: String
    abstract val name: String
    abstract val percent: BigDecimal
    abstract val isActive: Boolean
    abstract val type: FeeType

    /**
     * Return a copy with the fields the editor UI exposes overwritten,
     * preserving each subclass's non-editable fields (e.g. SpecificPair's
     * [SpecificPair.from]/[SpecificPair.to]/[SpecificPair.bothWays]). Dispatches
     * to the concrete `copy` so the runtime type is preserved without the
     * fragment having to `when` over the sealed hierarchy.
     */
    abstract fun withEditableFields(
        name: String,
        percent: BigDecimal,
        isActive: Boolean,
    ): Fee

    /** Applies to every conversion, no matter which currencies are involved. */
    data class GlobalExchange(
        override val id: String,
        override val name: String,
        override val percent: BigDecimal,
        override val isActive: Boolean = true,
    ) : Fee() {
        override val type: FeeType get() = FeeType.GLOBAL_EXCHANGE

        override fun withEditableFields(
            name: String,
            percent: BigDecimal,
            isActive: Boolean,
        ): GlobalExchange =
            copy(
                name = name,
                percent = percent,
                isActive = isActive,
            )
    }

    /** Bank / card fee that also applies to every conversion. */
    data class GlobalBank(
        override val id: String,
        override val name: String,
        override val percent: BigDecimal,
        override val isActive: Boolean = true,
    ) : Fee() {
        override val type: FeeType get() = FeeType.GLOBAL_BANK

        override fun withEditableFields(
            name: String,
            percent: BigDecimal,
            isActive: Boolean,
        ): GlobalBank =
            copy(
                name = name,
                percent = percent,
                isActive = isActive,
            )
    }

    /**
     * A fee tied to a specific currency pair. When [bothWays] is true the
     * fee also matches the reverse direction ([to] -> [from]).
     */
    data class SpecificPair(
        override val id: String,
        override val name: String,
        override val percent: BigDecimal,
        val from: String,
        val to: String,
        val bothWays: Boolean,
        override val isActive: Boolean = true,
    ) : Fee() {
        override val type: FeeType get() = FeeType.SPECIFIC_PAIR

        override fun withEditableFields(
            name: String,
            percent: BigDecimal,
            isActive: Boolean,
        ): SpecificPair =
            copy(
                name = name,
                percent = percent,
                isActive = isActive,
            )
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
