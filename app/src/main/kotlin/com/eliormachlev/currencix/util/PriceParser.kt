package com.eliormachlev.currencix.util

import com.eliormachlev.currencix.model.Currency
import java.math.BigDecimal

/** An amount found in free text, with its currency when the text names one. */
data class ParsedPrice(
    val amount: BigDecimal,
    val currency: Currency?,
)

/**
 * Finds a price in text someone selected: "€49.99", "49,99 €", "USD 1,234.56",
 * "1.234,56 EUR", "$5", "¥1000". Only the first number counts.
 *
 * A currency is recognised by its ISO code (in capitals) or its symbol. A symbol several
 * currencies share ("$", "kr", "¥") resolves to the first of [preferred]
 * that uses it (the user's own currencies), then to [COMMON_BY_SYMBOL], then
 * to the first currency with it. Separators: with both "," and ".", the last
 * one is the decimal point; with one kind appearing once and followed by
 * exactly three digits it's a thousands separator ("1,234"), otherwise the
 * decimal point ("49,99").
 */
object PriceParser {
    // A run of digits with the separators people write inside numbers.
    private val NUMBER = Regex("""\d[\d.,  ' ]*""")

    // Capitals only: in lowercase, words like "all" (ALL) and "top" (TOP) are just words.
    private val ISO_CODE = Regex("""(?<![A-Za-z])[A-Z]{3}(?![A-Za-z])""")
    private const val THOUSANDS_GROUP = 3

    // The usual reading of a shared symbol when none of the user's own
    // currencies uses it.
    private val COMMON_BY_SYMBOL =
        mapOf(
            "$" to Currency.USD,
            "¥" to Currency.JPY,
            "£" to Currency.GBP,
            "kr" to Currency.SEK,
            "Fr." to Currency.CHF,
            "R$" to Currency.BRL,
        )

    fun parse(
        text: String,
        preferred: List<Currency> = emptyList(),
    ): ParsedPrice? {
        val match = NUMBER.find(text) ?: return null
        val amount = parseNumber(match.value) ?: return null
        val rest = text.removeRange(match.range)
        return ParsedPrice(amount, findCurrency(rest, preferred))
    }

    /**
     * Every price in [text], one per line at most (a photo of a menu or a
     * receipt reads as one price per line), without repeats, in reading order.
     * Lines without a currency count only when [requireCurrency] is false.
     */
    fun parseAll(
        text: String,
        preferred: List<Currency> = emptyList(),
        requireCurrency: Boolean = false,
    ): List<ParsedPrice> =
        text
            .lineSequence()
            .mapNotNull { parse(it, preferred) }
            .filter { !requireCurrency || it.currency != null }
            .filter { it.amount.signum() > 0 }
            .distinctBy { it.amount.stripTrailingZeros() to it.currency }
            .toList()

    // Every currency's symbol, longest first so "R$" wins over "$" and "Fr."
    // over "F". Built once: parsing a photo runs this for every line.
    private val symbolMatchers: List<SymbolMatcher> by lazy {
        Currency.entries
            .mapNotNull { c -> c.plainSymbol?.let { SymbolMatcher(it, c) } }
            .sortedByDescending { it.symbol.length }
    }

    // A symbol made of letters ("kr", "L", "Fr.") counts only as a word of its
    // own — the "L" in "Table" isn't a lek. Signs ("€", "$") count anywhere.
    private class SymbolMatcher(
        val symbol: String,
        val currency: Currency,
    ) {
        private val word = if (symbol.any { it.isLetter() }) Regex("(?<!\\p{L})" + Regex.escape(symbol) + "(?!\\p{L})") else null

        fun matches(text: String): Boolean = word?.containsMatchIn(text) ?: text.contains(symbol)
    }

    internal fun parseNumber(raw: String): BigDecimal? {
        val digits = raw.trim().trimEnd('.', ',').filterNot { it == ' ' || it == ' ' || it == ' ' || it == '\'' }
        val lastDot = digits.lastIndexOf('.')
        val lastComma = digits.lastIndexOf(',')
        val decimalMark =
            when {
                lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) '.' else ','
                lastDot >= 0 -> '.'.takeUnless { isThousands(digits, '.') }
                lastComma >= 0 -> ','.takeUnless { isThousands(digits, ',') }
                else -> null
            }
        val normalized =
            digits
                .filter { it.isDigit() || it == decimalMark }
                .replace(decimalMark ?: ' ', '.')
        return normalized.toBigDecimalOrNull()
    }

    // "1,234" / "1,234,567": groups of exactly three digits after each mark.
    private fun isThousands(
        digits: String,
        mark: Char,
    ): Boolean {
        val groups = digits.split(mark)
        return groups.size > 2 || groups.drop(1).all { it.length == THOUSANDS_GROUP }
    }

    private fun findCurrency(
        text: String,
        preferred: List<Currency>,
    ): Currency? {
        ISO_CODE.findAll(text).firstNotNullOfOrNull { Currency.fromString(it.value) }?.let { return it }
        val found = symbolMatchers.filter { it.matches(text) }
        val symbol = found.firstOrNull()?.symbol ?: return null
        val candidates = found.filter { it.symbol == symbol }.map { it.currency }
        return preferred.firstOrNull { it in candidates } ?: COMMON_BY_SYMBOL[symbol] ?: candidates.first()
    }
}
