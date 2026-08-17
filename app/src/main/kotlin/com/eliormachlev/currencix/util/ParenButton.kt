package com.eliormachlev.currencix.util

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.eliormachlev.currencix.R

// Both paren glyphs are always drawn on the cycle-toggle button; only the
// spans change to hint which one will be inserted next.
private const val PAREN_BUTTON_LABEL = "()"

/**
 * Paint the shared `()` cycle-toggle keypad button so the glyph that would be
 * inserted by the *next* tap sits in bold + operator-green and the other in a
 * muted secondary tone. Reused by both the main and cart activities so the
 * highlight looks the same wherever the extended keypad is shown.
 */
fun TextView.paintParenCycle(next: Char) {
    val activeColor = ContextCompat.getColor(context, R.color.color_keypad_operators)
    val mutedColor = context.resolveThemeColor(android.R.attr.textColorSecondary)
    val activeIndex = if (next == '(') 0 else 1
    val mutedIndex = 1 - activeIndex
    text =
        SpannableString(PAREN_BUTTON_LABEL).apply {
            setSpan(ForegroundColorSpan(activeColor), activeIndex, activeIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), activeIndex, activeIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(mutedColor), mutedIndex, mutedIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
}

private fun Context.resolveThemeColor(attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
}
