package com.eliormachlev.currencix.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

// Receipt text as Tesseract (tessdata_fast 4.1.0, single-column mode) read
// test receipts, and the items found in it.
class ReceiptParserTest {
    private fun items(text: String) = ReceiptParser.parse(text).map { it.name to it.price.toPlainString() }

    @Test
    fun `an English receipt gives its items, not its totals, tax, payment or discount`() {
        val text =
            """
            FRESH MARKET

            Milk 1L 6.90
            Bread whole wheat 12.50A
            2 x 3.50

            Bananas 7.00
            Discount -2.00
            SUBTOTAL 24.40
            TAX 17% 4.15
            TOTAL 28.55
            VISA 28.55

            CHANGE 0.00
            """.trimIndent()
        assertEquals(listOf("Milk 1L" to "6.90", "Bread whole wheat" to "12.50", "Bananas" to "7.00"), items(text))
    }

    @Test
    fun `a Hebrew receipt read in Hebrew`() {
        val text =
            """
            סופר השכונה

            חלב 3% 6.90
            לחם ‎TNX‏ 12.50
            בננות 7.00
            הנחה 2.00-
            סה"כ 24.40
            מע"מ 3.54

            אשראי 24.40
            """.trimIndent()
        assertEquals(listOf("חלב 3%" to "6.90", "לחם TNX" to "12.50", "בננות" to "7.00"), items(text))
    }

    @Test
    fun `a Hebrew receipt read as English still gives its prices`() {
        val text =
            """
            NDwWN WI1O

            6.90 3% 27N
            12.50 TNX ONT
            7.00 nia
            """.trimIndent()
        assertEquals(listOf("6.90", "12.50", "7.00"), items(text).map { it.second })
    }

    @Test
    fun `a quantity line takes its name from the line above, and comma decimals count`() {
        val text =
            """
            Orangensaft
            2 x 1,49   2,98
            Käse 3,25 €
            """.trimIndent()
        assertEquals(listOf("Orangensaft" to "2.98", "Käse" to "3.25"), items(text))
        assertEquals(BigDecimal("1234.50"), ReceiptParser.parse("TV stand 1,234.50").single().price)
    }
}
