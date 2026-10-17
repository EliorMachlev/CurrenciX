package com.eliormachlev.currencix.view.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

// The widget's amount button: 1 → 10 → 100 → 1,000 → 1.
class WidgetAmountsTest {
    @Test
    fun `steps through the presets and wraps`() {
        assertEquals(BigDecimal.ONE, WidgetAmounts.at(null))
        assertEquals(1, WidgetAmounts.next(null))
        assertEquals(0, WidgetAmounts.next(3))
        assertEquals(BigDecimal(1000), WidgetAmounts.at(3))
        assertEquals(BigDecimal.ONE, WidgetAmounts.at(4))
    }

    @Test
    fun `labels fit the round button`() {
        assertEquals("×1", WidgetAmounts.label(BigDecimal.ONE))
        assertEquals("×100", WidgetAmounts.label(BigDecimal(100)))
        assertEquals("×1K", WidgetAmounts.label(BigDecimal(1000)))
    }
}
