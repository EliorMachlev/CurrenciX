package com.eliormachlev.currencix.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

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
