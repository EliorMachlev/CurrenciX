package com.eliormachlev.currencix.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The app lays out left to right in every language; inReadingOrder makes a
// right-to-left label that names something in Latin script still read
// right to left ("היום · InforEuro": the date first, on the right).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadingOrderTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    @Config(qualifiers = "iw")
    fun `a Hebrew label is isolated as right to left`() {
        assertEquals("⁧היום · InforEuro⁩", "היום · InforEuro".inReadingOrder(context))
    }

    @Test
    @Config(qualifiers = "ar")
    fun `an Arabic label is isolated as right to left`() {
        assertEquals("⁧اليوم · InforEuro⁩", "اليوم · InforEuro".inReadingOrder(context))
    }

    @Test
    @Config(qualifiers = "en")
    fun `a left-to-right label is left alone`() {
        assertEquals("Today · InforEuro", "Today · InforEuro".inReadingOrder(context))
    }
}
