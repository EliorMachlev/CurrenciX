package com.eliormachlev.currencix.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.math.RoundingMode

@RunWith(MockitoJUnitRunner::class)
class MathUtilsTest {
    @Test
    fun calculateDifferenceTest() {
        assertEquals(0, calculateDifference(bd("100"), bd("110"))?.compareTo(bd("10")))
        assertEquals(0, calculateDifference(bd("100"), bd("90"))?.compareTo(bd("-10")))
        assertNull(calculateDifference(null, bd("1")))
        assertNull(calculateDifference(bd("1"), null))
        assertNull(calculateDifference(null, null))
        assertNull(calculateDifference(BigDecimal.ZERO, bd("1")))
    }

    @Test
    fun getSignificantDecimalPlacesTest() {
        assertEquals(2, bd("1").getSignificantDecimalPlaces(2))
        assertEquals(2, bd("0.9").getSignificantDecimalPlaces(2))
        assertEquals(2, bd("0.991").getSignificantDecimalPlaces(2))
        assertEquals(4, bd("0.0026").getSignificantDecimalPlaces(2))
        assertEquals(2, bd("0.998").getSignificantDecimalPlaces(2))
        assertEquals(2, bd("0.9991").getSignificantDecimalPlaces(2))
        assertEquals(2, bd("0.99991").getSignificantDecimalPlaces(2))
        assertEquals(2, bd("0.999991").getSignificantDecimalPlaces(2))
        assertEquals(5, bd("0.9999991").getSignificantDecimalPlaces(5))
    }

    @Test
    fun `isNeutralFeeStack is true only for exactly one`() {
        assertTrue(bd("1").isNeutralFeeStack())
        assertTrue(bd("1.0").isNeutralFeeStack())
        assertTrue(bd("1.000000").isNeutralFeeStack())
        assertTrue(BigDecimal.ONE.isNeutralFeeStack())
    }

    @Test
    fun `isNeutralFeeStack is false for any non-unit stack`() {
        assertFalse(bd("1.0001").isNeutralFeeStack())
        assertFalse(bd("0.9999").isNeutralFeeStack())
        assertFalse(bd("1.025").isNeutralFeeStack())
        assertFalse(bd("0.975").isNeutralFeeStack())
        assertFalse(BigDecimal.ZERO.isNeutralFeeStack())
    }

    @Test
    fun `feePercentDelta converts multiplicative stack to signed percent`() {
        assertEquals(bd("2.50"), bd("1.025").feePercentDelta())
        assertEquals(bd("-2.50"), bd("0.975").feePercentDelta())
        assertEquals(bd("0.00"), bd("1").feePercentDelta())
        assertEquals(bd("100.00"), bd("2").feePercentDelta())
        assertEquals(bd("-100.00"), bd("0").feePercentDelta())
    }

    @Test
    fun `feePercentDelta composes stacked multiplicative fees`() {
        // 2.5% payment + 1% conversion applied multiplicatively → 1.03525
        val stack = bd("1.025").multiply(bd("1.01"))
        // HALF_EVEN rounds the trailing 5 down because the last kept digit (2) is even
        assertEquals(bd("3.52"), stack.feePercentDelta())
        assertEquals(bd("3.53"), stack.feePercentDelta(roundingMode = RoundingMode.HALF_UP))
    }

    @Test
    fun `feePercentDelta honours custom scale`() {
        assertEquals(bd("2.5"), bd("1.025").feePercentDelta(scale = 1))
        assertEquals(bd("2.5000"), bd("1.025").feePercentDelta(scale = 4))
    }

    @Test
    fun `feePercentDelta HALF_EVEN vs HALF_UP diverge on ties`() {
        // 1.00005 -> 0.005% exactly at the scale-2 boundary; last kept digit is 0 (even)
        assertEquals(bd("0.00"), bd("1.00005").feePercentDelta(roundingMode = RoundingMode.HALF_EVEN))
        assertEquals(bd("0.01"), bd("1.00005").feePercentDelta(roundingMode = RoundingMode.HALF_UP))
    }

    @Test
    fun `feePercentDelta preserves sub-cent precision for tiny stacks`() {
        assertEquals(bd("0.01"), bd("1.0001").feePercentDelta())
        assertEquals(bd("0.00"), bd("1.00001").feePercentDelta())
        assertEquals(bd("0.0001"), bd("1.000001").feePercentDelta(scale = 4))
    }

    private fun bd(value: String) = BigDecimal(value)
}
