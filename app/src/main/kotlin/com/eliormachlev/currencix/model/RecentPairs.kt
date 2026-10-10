package com.eliormachlev.currencix.model

/** A conversion direction: [from] → [to]. */
data class CurrencyPair(
    val from: Currency,
    val to: Currency,
) {
    /** Same two currencies, either direction — a swap doesn't make a new pair. */
    fun isSameCurrencies(other: CurrencyPair?): Boolean = other != null && (this == other || (from == other.to && to == other.from))
}

/**
 * The converter's recently used pairs, newest first — the quick-switch row
 * under the hero card. Stored as one short string ("EUR:USD,USD:ILS").
 */
object RecentPairs {
    /** How many pairs are kept (the row shows the ones other than the current pair). */
    const val MAX = 6

    private const val PAIR_SEPARATOR = ','
    private const val SIDE_SEPARATOR = ':'

    /**
     * [pair] moved (or added) to the front, capped at [MAX]. It replaces
     * itself in either direction, so swapping doesn't use up a second slot.
     */
    fun push(
        recents: List<CurrencyPair>,
        pair: CurrencyPair,
    ): List<CurrencyPair> = (listOf(pair) + without(recents, pair)).take(MAX)

    /**
     * Undoes a removal: [before] (the list as it was) back in its order,
     * after any pair first used since ([now] holds those at its front),
     * capped at [MAX].
     */
    fun restore(
        before: List<CurrencyPair>,
        now: List<CurrencyPair>,
    ): List<CurrencyPair> {
        val usedSince = now.filterNot { pair -> before.any(pair::isSameCurrencies) }
        return (usedSince + before).take(MAX)
    }

    /** [recents] without [pair], in either direction. */
    fun without(
        recents: List<CurrencyPair>,
        pair: CurrencyPair,
    ): List<CurrencyPair> = recents.filterNot(pair::isSameCurrencies)

    /** [recents] without the pairs that use [currency] — which is what takes it off the picker's shortcuts. */
    fun without(
        recents: List<CurrencyPair>,
        currency: Currency,
    ): List<CurrencyPair> = recents.filterNot { it.from == currency || it.to == currency }

    /** The currencies in [recents], most recent first, each once — the picker's shortcuts. */
    fun currencies(recents: List<CurrencyPair>): List<Currency> = recents.flatMap { listOf(it.from, it.to) }.distinct()

    fun encode(recents: List<CurrencyPair>): String =
        recents.joinToString(PAIR_SEPARATOR.toString()) { "${it.from.iso4217Alpha()}$SIDE_SEPARATOR${it.to.iso4217Alpha()}" }

    /** Inverse of [encode]; entries it can't read (a currency no longer shipped) are dropped. */
    fun decode(encoded: String?): List<CurrencyPair> =
        encoded
            .orEmpty()
            .split(PAIR_SEPARATOR)
            .mapNotNull { entry ->
                val sides = entry.split(SIDE_SEPARATOR)
                val from = sides.getOrNull(0)?.let(Currency::fromString)
                val to = sides.getOrNull(1)?.let(Currency::fromString)
                if (from != null && to != null && from != to) CurrencyPair(from, to) else null
            }.distinct()
            .take(MAX)
}
