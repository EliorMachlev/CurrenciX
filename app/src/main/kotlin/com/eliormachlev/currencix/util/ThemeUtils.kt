package com.eliormachlev.currencix.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

/**
 * Alpha for a row that is present in the list but cannot be selected in the
 * current context — e.g. a currency already picked on the opposite side of a
 * pair, or an inactive saved fee. Paired with a text marker so state is not
 * conveyed by alpha alone (WCAG 1.4.1).
 */
const val DISABLED_ROW_ALPHA = 0.4f

/**
 * Resolve a theme colour attribute (e.g. `?attr/colorOnSurfaceVariant`) to an
 * `@ColorInt`. Handles both direct colour values and colour-state-list refs.
 */
@ColorInt
fun Context.resolveThemeColor(
    @AttrRes attr: Int,
): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
}
