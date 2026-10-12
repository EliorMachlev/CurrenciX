package com.eliormachlev.currencix.view.compose

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.URLSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

// A web address written out in plain text ("…at https://allratestoday.com,
// then…"): up to the first space, minus punctuation that ends the sentence.
private val BARE_URL = Regex("""https?://[^\s<>"]+[^\s<>".,;:!?)\]'’]""")

/**
 * [text] as Compose text whose links open in the browser: the links a
 * string resource marks up (`<a href>`, kept by `getText`) and any web
 * address written out in plain text, both drawn in [linkStyle]. Its bold and
 * italic spans are kept too, so a description reads as its resource does.
 */
internal fun linkedText(
    text: CharSequence,
    linkStyle: SpanStyle,
): AnnotatedString =
    buildAnnotatedString {
        append(text.toString())
        val styles = TextLinkStyles(style = linkStyle)
        val linked = mutableListOf<IntRange>()
        if (text is Spanned) {
            text.getSpans(0, text.length, URLSpan::class.java).forEach { span ->
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                addLink(LinkAnnotation.Url(span.url, styles), start, end)
                linked += start until end
            }
            text.getSpans(0, text.length, StyleSpan::class.java).forEach { span ->
                span.spanStyle()?.let { addStyle(it, text.getSpanStart(span), text.getSpanEnd(span)) }
            }
        }
        BARE_URL.findAll(text).forEach { match ->
            if (linked.none { match.range.first in it }) {
                addLink(LinkAnnotation.Url(match.value, styles), match.range.first, match.range.last + 1)
            }
        }
    }

private fun StyleSpan.spanStyle(): SpanStyle? =
    when (style) {
        Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
        else -> null
    }

/** [linkedText] in the theme's link look (primary, underlined), kept while [text] is unchanged. */
@Composable
internal fun rememberLinkedText(text: CharSequence): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return remember(text, color) {
        linkedText(text, SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    }
}
