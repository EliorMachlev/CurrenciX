package com.eliormachlev.currencix.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorExpressionTest {
    @Test
    fun `plain arithmetic with display glyphs`() {
        assertEquals("9", "1 + 2 ${OPERATOR_MULTIPLY} 4".evaluateCalculatorExpression())
        assertEquals("3", "5 ${OPERATOR_MINUS} 2".evaluateCalculatorExpression())
        assertEquals("2.5", "5 ${OPERATOR_DIVIDE} 2".evaluateCalculatorExpression())
    }

    @Test
    fun `simple percent converts to divide-by-100`() {
        assertEquals("0.5", "50%".evaluateCalculatorExpression())
    }

    @Test
    fun `smart percent adds proportional amount`() {
        // 200 + 10% -> 200 + (200 * 10 / 100) = 220
        assertEquals("220", "200${OPERATOR_PLUS}10%".evaluateCalculatorExpression())
    }

    @Test
    fun `smart percent subtracts proportional amount`() {
        // 200 - 10% -> 200 - (200 * 10 / 100) = 180
        assertEquals("180", "200${OPERATOR_MINUS}10%".evaluateCalculatorExpression())
    }

    @Test
    fun `trailing operator is padded with neutral operand`() {
        assertEquals("5", "5 ${OPERATOR_PLUS}".evaluateCalculatorExpression())
        assertEquals("5", "5 ${OPERATOR_MINUS}".evaluateCalculatorExpression())
        assertEquals("5", "5 ${OPERATOR_MULTIPLY}".evaluateCalculatorExpression())
        assertEquals("5", "5 ${OPERATOR_DIVIDE}".evaluateCalculatorExpression())
    }

    @Test
    fun `trailing decimal point is padded with zero`() {
        assertEquals("5", "5.".evaluateCalculatorExpression())
    }

    @Test
    fun `NaN result collapses to zero`() {
        assertEquals("0", "0 ${OPERATOR_DIVIDE} 0".evaluateCalculatorExpression())
    }

    @Test
    fun `operator regex matches every display glyph`() {
        listOf(OPERATOR_PLUS, OPERATOR_MINUS, OPERATOR_MULTIPLY, OPERATOR_DIVIDE)
            .forEach { assert(it.matches(OPERATOR_REGEX)) { "expected $it to match OPERATOR_REGEX" } }
    }

    @Test
    fun `parenthesised expression respects grouping`() {
        assertEquals("108", "(100 ${OPERATOR_PLUS} 20) ${OPERATOR_MULTIPLY} 0.9".evaluateCalculatorExpression())
    }

    @Test
    fun `unclosed parens are auto-closed at eval time`() {
        // half-typed `(1+2` evaluates as `(1+2)` = 3 instead of falling back
        assertEquals("3", "(1 ${OPERATOR_PLUS} 2".evaluateCalculatorExpression())
        // multiple missing closers all get padded
        assertEquals("6", "((1 ${OPERATOR_PLUS} 2) ${OPERATOR_MULTIPLY} 2".evaluateCalculatorExpression())
    }

    @Test
    fun `calc token regex matches parens as well as operators`() {
        assertEquals(true, "(".contains(CALC_TOKEN_REGEX))
        assertEquals(true, ")".contains(CALC_TOKEN_REGEX))
        assertEquals(true, OPERATOR_PLUS.contains(CALC_TOKEN_REGEX))
    }
}
