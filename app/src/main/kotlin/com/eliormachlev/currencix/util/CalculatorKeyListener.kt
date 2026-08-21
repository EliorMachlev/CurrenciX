package com.eliormachlev.currencix.util

import android.text.InputType
import android.text.method.NumberKeyListener

// Every character the system-IME calculator input allows through. Digits,
// both decimal separators (locale-dependent), operators, percent, and
// parentheses — nothing else. Stated once here so [CalculatorKeyListener]
// and the routing path in `MainActivity.handleCharKey` agree on the same
// allow-list.
const val CALCULATOR_ALLOWED_CHARS = "0123456789.,+-*/%()"

/**
 * KeyListener for the system-IME calculator input. Reports [TYPE_CLASS_NUMBER]
 * with decimal + signed flags so IMEs (Gboard, SwiftKey) open in numpad mode,
 * while [getAcceptedChars] widens the accepted set to the full calculator
 * allow-list — so operators, `%`, and parentheses aren't stripped before they
 * reach the app. Reused between the main screen and the cart editor.
 */
object CalculatorKeyListener : NumberKeyListener() {
    private val accepted = CALCULATOR_ALLOWED_CHARS.toCharArray()

    override fun getInputType(): Int =
        InputType.TYPE_CLASS_NUMBER or
            InputType.TYPE_NUMBER_FLAG_DECIMAL or
            InputType.TYPE_NUMBER_FLAG_SIGNED

    override fun getAcceptedChars(): CharArray = accepted
}
