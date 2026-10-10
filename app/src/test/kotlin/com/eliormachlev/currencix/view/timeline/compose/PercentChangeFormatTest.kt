package com.eliormachlev.currencix.view.timeline.compose

import android.content.Context
import com.eliormachlev.currencix.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class PercentChangeFormatTest {
    private val context: Context = mock(Context::class.java)

    @Before
    fun init() {
        `when`(context.getString(R.string.locale_language)).thenReturn("en")
        `when`(context.getString(R.string.locale_country)).thenReturn("")
    }

    @Test
    fun `a rise gets a plus and a fall a true minus sign`() {
        assertEquals("+2.10%", formatPercentChange(context, BigDecimal("2.1")))
        assertEquals("−7.55%", formatPercentChange(context, BigDecimal("-7.55")))
    }

    @Test
    fun `no change reads as plus zero`() {
        assertEquals("+0.00%", formatPercentChange(context, BigDecimal.ZERO))
    }
}
