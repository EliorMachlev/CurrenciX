package com.eliormachlev.currencix.util

import android.text.InputFilter
import android.text.Spanned

// Every character the system-IME calculator input allows through. Digits,
// both decimal separators (locale-dependent), operators, percent, and
// parentheses — nothing else. Stated once here so [CalculatorInputFilter],
// the routing path in `MainActivity.handleCharKey`, and the picker-summary
// string agree on the same allow-list.
const val CALCULATOR_ALLOWED_CHARS = "0123456789.,+-*/%()"

/**
 * Rejects any character outside [CALCULATOR_ALLOWED_CHARS] before it lands
 * in the EditText. Returns null when the whole slice passes (Android's
 * "no change" signal) and a filtered CharSequence otherwise.
 */
class CalculatorInputFilter : InputFilter {
    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int,
    ): CharSequence? {
        if ((start until end).all { source[it] in CALCULATOR_ALLOWED_CHARS }) return null
        val filtered = StringBuilder(end - start)
        for (i in start until end) {
            val c = source[i]
            if (c in CALCULATOR_ALLOWED_CHARS) filtered.append(c)
        }
        return filtered
    }
}
