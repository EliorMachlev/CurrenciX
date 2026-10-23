package com.eliormachlev.currencix.view.compose

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature icons take the theme's primary, darkened or lightened only as far
// as it takes to reach 3:1 against the background.
class FeatureIconColorTest {
    @Test
    fun `a pale primary on a light background is darkened to read`() {
        val icon = readableOn(BILL_GREEN, PAPER, toward = PAPER_INK)
        assertTrue("contrast ${contrastRatio(icon, PAPER)}", contrastRatio(icon, PAPER) >= MIN_CONTRAST)
        assertTrue("still green, not the text color", icon != PAPER_INK)
    }

    @Test
    fun `a primary that already reads is left as it is`() {
        assertEquals(BILL_GREEN, readableOn(BILL_GREEN, INK, toward = INK_FOREGROUND))
    }

    @Test
    fun `the contrast ratio matches WCAG`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(PAPER, PAPER), 0.001f)
    }

    private companion object {
        const val MIN_CONTRAST = 3f
        val BILL_GREEN = Color(0xFF85BB65)
        val PAPER = Color(0xFFF3EEE5)
        val PAPER_INK = Color(0xFF1C1B18)
        val INK = Color(0xFF131311)
        val INK_FOREGROUND = Color(0xFFEFEAE0)
    }
}
