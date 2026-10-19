package com.eliormachlev.currencix.view.preference.compose

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import com.eliormachlev.currencix.model.RefreshInterval
import java.util.Locale

private const val MINUTES_PER_HOUR = 60L

/**
 * "15 minutes", "1 hour", "24 hours" — in [locale]'s own words, from ICU, so
 * every language reads right without a string per interval.
 */
internal fun RefreshInterval.label(locale: Locale): String {
    val measure =
        if (minutes % MINUTES_PER_HOUR == 0L) {
            Measure(minutes / MINUTES_PER_HOUR, MeasureUnit.HOUR)
        } else {
            Measure(minutes, MeasureUnit.MINUTE)
        }
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).format(measure)
}
