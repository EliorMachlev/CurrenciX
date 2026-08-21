package com.eliormachlev.currencix.util

import android.text.InputType
import android.text.method.NumberKeyListener

// Single source of truth for the calculator allow-list. Shared with
// `MainActivity.handleCharKey` so hardware and IME paths agree.
const val CALCULATOR_ALLOWED_CHARS = "0123456789.,+-*/%()"

/**
 * KeyListener for the system-IME calculator input. Reports numpad flags so
 * IMEs (Gboard, SwiftKey) open in numeric mode, and widens the accepted set
 * to the full calculator allow-list so operators, `%`, and parentheses aren't
 * stripped before they reach the app.
 */
object CalculatorKeyListener : NumberKeyListener() {
    private val accepted = CALCULATOR_ALLOWED_CHARS.toCharArray()
    private val inputType =
        InputType.TYPE_CLASS_NUMBER or
            InputType.TYPE_NUMBER_FLAG_DECIMAL or
            InputType.TYPE_NUMBER_FLAG_SIGNED

    override fun getInputType(): Int = inputType

    override fun getAcceptedChars(): CharArray = accepted
}
