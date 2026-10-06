package com.eliormachlev.currencix.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArithmeticTest {
    private fun eval(expression: String): String = evaluateArithmetic(expression).stripTrailingZeros().toPlainString()

    @Test
    fun `multiplication and division bind tighter than addition and subtraction`() {
        assertEquals("9", eval("1+2*4"))
        assertEquals("26", eval("2*3+4*5"))
        assertEquals("-0.4", eval("2-3*4/5"))
    }

    @Test
    fun `operators of the same precedence run left to right`() {
        assertEquals("0", eval("3-2-1"))
        assertEquals("1", eval("8/4/2"))
    }

    @Test
    fun `brackets group, however deep`() {
        assertEquals("108", eval("(100+20)*0.9"))
        assertEquals("70", eval("2*(3+4)*5"))
        assertEquals("1", eval("(((((1)))))"))
    }

    @Test
    fun `a sign may lead a number or a bracket`() {
        assertEquals("-2", eval("-5+3"))
        assertEquals("-15", eval("5*-3"))
        assertEquals("8", eval("5--3"))
        assertEquals("5", eval("--5"))
        assertEquals("-3", eval("-(1+2)"))
    }

    @Test
    fun `a value followed by a bracket multiplies`() {
        assertEquals("14", eval("2(3+4)"))
        assertEquals("21", eval("(1+2)(3+4)"))
        assertEquals("24", eval("4(2)(3)"))
    }

    @Test
    fun `decimals are exact`() {
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("1.5", eval(".5+1"))
        assertEquals("121932631356500531.347203169112635269", eval("123456789.123456789*987654321.987654321"))
    }

    @Test
    fun `a fraction that never ends is cut far past any amount of money`() {
        assertEquals("0." + "3".repeat(68), eval("1/3"))
        // …so a third times three stops just short of one; the display rounds
        // to a handful of decimals and shows 1.
        assertEquals("0." + "9".repeat(68), eval("1/3*3"))
    }

    @Test
    fun `dividing by zero is an arithmetic error`() {
        assertThrows(ArithmeticException::class.java) { eval("1/0") }
        assertThrows(ArithmeticException::class.java) { eval("0/0") }
    }

    @Test
    fun `anything that isn't arithmetic is refused`() {
        listOf("", "5+", "*5", "5**2", "1.2.3", ".", "(", ")", "()", "(1+2", "1+2)", "(1+2)3", "abc", "1,5", "1 + 2")
            .forEach { assertThrows("\"$it\"", ArithmeticSyntaxException::class.java) { eval(it) } }
    }

    @Test
    fun `brackets nested absurdly deep are refused rather than overflowing the stack`() {
        val depth = 100_000
        assertThrows(ArithmeticSyntaxException::class.java) { eval("(".repeat(depth) + "1" + ")".repeat(depth)) }
        assertEquals("1", eval("(".repeat(64) + "1" + ")".repeat(64)))
    }
}
