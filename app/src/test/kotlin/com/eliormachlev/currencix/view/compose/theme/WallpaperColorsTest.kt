package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

// The wallpaper palette follows its colour: a navy night sky gives a blue
// app, not the phone theme's olive; light and dark modes stay light and dark.
class WallpaperColorsTest {
    // Hue in degrees, 0–360.
    private fun hue(color: Color): Float =
        java.awt.Color.RGBtoHSB(channel(color.red), channel(color.green), channel(color.blue), null)[0] * FULL_CIRCLE

    private fun channel(value: Float): Int = (value * MAX_CHANNEL).toInt()

    @Test fun `a navy wallpaper gives a blue palette`() {
        val scheme = wallpaperColorScheme(NAVY, dark = false)
        assertTrue("primary hue ${hue(scheme.primary)}", hue(scheme.primary) in BLUE_HUES)
    }

    @Test fun `light and dark modes keep their backgrounds`() {
        assertTrue(wallpaperColorScheme(NAVY, dark = false).background.luminance() > HALF)
        assertTrue(wallpaperColorScheme(NAVY, dark = true).background.luminance() < HALF)
    }

    @Test fun `text reads on its background`() {
        listOf(false, true).forEach { dark ->
            val scheme = wallpaperColorScheme(NAVY, dark)
            val gap = abs(scheme.onBackground.luminance() - scheme.background.luminance())
            assertTrue("dark=$dark gap=$gap", gap > HALF)
        }
    }

    private companion object {
        const val NAVY = 0xFF1F2A55.toInt()
        val BLUE_HUES = 200f..280f
        const val HALF = 0.5f
        const val FULL_CIRCLE = 360f
        const val MAX_CHANNEL = 255
    }
}
