package com.eliormachlev.currencix.util

import com.eliormachlev.currencix.view.compose.MAX_NAME_LENGTH
import java.math.BigDecimal

/** An item read off a receipt: its name and price as printed. */
data class ReceiptLine(
    val name: String,
    val price: BigDecimal,
)

/**
 * Finds the items in a receipt's OCR text: a line ending in a price
 * ("Milk 1L   6.90", "Bread 3,25 A") is an item named by the words before
 * it; so is one starting with a price followed by words (a right-to-left
 * receipt read as left-to-right puts the price first). A line with a price but no words ("2 x 3.50   7.00") takes the name
 * from the line above when that one had words and no price. Totals,
 * taxes, payment and change lines are left out (by their usual words in the
 * app's languages), and so are discounts (a minus with the price).
 */
object ReceiptParser {
    // A price with cents: "6.90", "1,234.50", "12,50", "1 234,50".
    private val PRICE = Regex("""\d{1,3}(?:[.,' ]\d{3})*[.,]\d{2}(?!\d)|\d+[.,]\d{2}(?!\d)""")

    // After the price: a tax letter, a currency sign or code ("A", "₪", "EUR"), nothing more.
    private const val MAX_SUFFIX_LENGTH = 4
    private const val MIN_NAME_LETTERS = 2
    private const val MINUS = '-'

    // Lines that aren't items: totals, tax, payment, change, discounts.
    private val NOT_ITEMS =
        listOf(
            "total",
            "subtotal",
            "sub total",
            "tax",
            "vat",
            "gst",
            "change",
            "cash",
            "card",
            "visa",
            "mastercard",
            "amex",
            "debit",
            "credit",
            "balance",
            "tip",
            "gratuity",
            "payment",
            "paid",
            "due",
            "amount",
            "rounding",
            "discount",
            "savings",
            "tender",
            "סה\"כ",
            "סה״כ",
            "סהכ",
            "סך הכל",
            "מע\"מ",
            "מע״מ",
            "עודף",
            "מזומן",
            "אשראי",
            "לתשלום",
            "הנחה",
            "summe",
            "gesamt",
            "mwst",
            "ust",
            "rückgeld",
            "zahlung",
            "karte",
            "tva",
            "montant",
            "espèces",
            "rendu",
            "carte",
            "iva",
            "importe",
            "efectivo",
            "cambio",
            "tarjeta",
            "pagamento",
            "troco",
            "contanti",
            "resto",
            "carta",
            "totale",
            "итого",
            "сумма",
            "ндс",
            "сдача",
            "наличные",
            "карта",
            "разом",
            "решта",
            "пдв",
            "totaal",
            "btw",
            "wisselgeld",
            "contant",
            "suma",
            "razem",
            "ptu",
            "reszta",
            "gotówka",
            "karta",
            "toplam",
            "kdv",
            "para üstü",
        )
    private val NOT_ITEM = Regex("(?<!\\p{L})(" + NOT_ITEMS.joinToString("|") { Regex.escape(it) } + ")(?!\\p{L})", RegexOption.IGNORE_CASE)

    // What's trimmed off a name's ends: spacing, dots, colons, currency signs.
    private val NAME_TRIM = Regex("""^[\s.:·*#$€£¥₪-]+|[\s.:·*#$€£¥₪-]+$""")

    private val MULTIPLE_SPACES = Regex("\\s{2,}")

    // Invisible direction marks OCR leaves around mixed-script words.
    private val BIDI_MARKS = Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069]")

    fun parse(text: String): List<ReceiptLine> {
        val items = mutableListOf<ReceiptLine>()
        var nameAbove: String? = null
        text.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
            // "7.00 Milk": the words after the price name it, even with a short tail.
            val price = trailingPrice(line)?.takeIf { hasWords(cleanName(it.name)) } ?: leadingPrice(line) ?: trailingPrice(line)
            if (price == null) {
                nameAbove = cleanName(line).takeIf(::hasWords)
                return@forEach
            }
            val name = cleanName(price.name)
            // A price first comes with its words; a price last may take the line above's.
            val chosen = if (hasWords(name) || price.leading) name else nameAbove
            nameAbove = null
            if (chosen == null || NOT_ITEM.containsMatchIn(line) || price.isDiscount) return@forEach
            PriceParser.parseNumber(price.value)?.let { items += ReceiptLine(chosen.take(MAX_NAME_LENGTH), it) }
        }
        return items
    }

    private class FoundPrice(
        val value: String,
        // The rest of the line: the words before a price last, after a price first.
        val name: String,
        val isDiscount: Boolean,
        val leading: Boolean = false,
    )

    // The line's last price, if only a short suffix (tax letter, currency) follows it.
    private fun trailingPrice(line: String): FoundPrice? {
        val match = PRICE.findAll(line).lastOrNull() ?: return null
        val after = line.substring(match.range.last + 1).trim()
        if (after.length > MAX_SUFFIX_LENGTH || after.any(Char::isDigit)) return null
        val before = line.substring(0, match.range.first).trimEnd()
        val discount = before.endsWith(MINUS) || after.startsWith(MINUS)
        return FoundPrice(match.value, before, discount)
    }

    // A line's first price, when something with letters follows it ("6.90 Milk").
    private fun leadingPrice(line: String): FoundPrice? {
        val match = PRICE.find(line)?.takeIf { line.substring(0, it.range.first).isBlank() } ?: return null
        val after = line.substring(match.range.last + 1).trimStart()
        if (after.none(Char::isLetter)) return null
        return FoundPrice(match.value, after, isDiscount = after.startsWith(MINUS), leading = true)
    }

    private fun cleanName(text: String): String = text.replace(BIDI_MARKS, "").replace(NAME_TRIM, "").replace(MULTIPLE_SPACES, " ")

    private fun hasWords(text: String): Boolean = text.count(Char::isLetter) >= MIN_NAME_LETTERS
}
