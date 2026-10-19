package com.eliormachlev.currencix.view.compose

import android.app.Application
import android.text.Html
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.eliormachlev.currencix.view.preference.compose.maskedApiKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Html.fromHtml is Android's: Robolectric supplies it, the way getText's spans come.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LinkedTextTest {
    private val linkStyle = SpanStyle()

    private fun urls(text: CharSequence) =
        linkedText(text, linkStyle)
            .getLinkAnnotations(0, text.length)
            .map { it.start to (it.item as LinkAnnotation.Url).url }

    @Test
    fun `a marked-up link stays a link, over its own words`() {
        val text = Html.fromHtml("""<a href="https://fer.example/">Example</a> tracks rates.""", Html.FROM_HTML_MODE_LEGACY)
        assertEquals(listOf(0 to "https://fer.example/"), urls(text))
    }

    @Test
    fun `a web address written out becomes a link, without the sentence's punctuation`() {
        val text = "Create a free account at https://allratestoday.com, then copy your key."
        assertEquals(listOf(text.indexOf("https") to "https://allratestoday.com"), urls(text))
    }

    @Test
    fun `a marked-up link whose words are an address is linked once`() {
        val text =
            Html.fromHtml(
                """See <a href="https://openexchangerates.org/">https://openexchangerates.org/</a>""",
                Html.FROM_HTML_MODE_LEGACY,
            )
        assertEquals(1, urls(text).size)
    }

    @Test
    fun `bold stays bold`() {
        val text = Html.fromHtml("About <b>160</b> rates", Html.FROM_HTML_MODE_LEGACY)
        val styles = linkedText(text, linkStyle).spanStyles
        assertTrue(styles.any { it.item.fontWeight == FontWeight.Bold && text.substring(it.start, it.end) == "160" })
    }

    @Test
    fun `plain text without an address has no links`() {
        assertTrue(urls("Updates every business day").isEmpty())
    }

    @Test
    fun `a key row shows only the key's last characters`() {
        assertEquals("•••• 9f2c", maskedApiKey("art_live_0123456789f2c"))
    }
}
