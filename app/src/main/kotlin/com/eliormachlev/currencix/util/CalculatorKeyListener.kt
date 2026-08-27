package com.eliormachlev.currencix.util

import android.text.InputType
import android.text.method.NumberKeyListener
import com.eliormachlev.currencix.model.KeyboardType

// Single source of truth for the calculator allow-list. Shared with
// `MainActivity.handleCharKey` so hardware and IME paths agree.
const val CALCULATOR_ALLOWED_CHARS = "0123456789.,+-*/%()"

// TYPE_TEXT_FLAG_NO_SUGGESTIONS silences autocorrect / suggestion strip on
// the full-text IME so operators aren't "helpfully" replaced mid-expression.
private const val FULL_TEXT_INPUT_TYPE =
    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

private const val NUMPAD_INPUT_TYPE =
    InputType.TYPE_CLASS_NUMBER or
        InputType.TYPE_NUMBER_FLAG_DECIMAL or
        InputType.TYPE_NUMBER_FLAG_SIGNED

/**
 * KeyListener for the system-IME calculator input. Reports numpad or full-text
 * flags so IMEs (Gboard, SwiftKey) open in the right mode, and widens the
 * accepted set to the full calculator allow-list so operators, `%`, and
 * parentheses aren't stripped before they reach the app.
 */
class CalculatorKeyListener private constructor(
    private val inputType: Int,
) : NumberKeyListener() {
    override fun getInputType(): Int = inputType

    override fun getAcceptedChars(): CharArray = ACCEPTED

    companion object {
        private val ACCEPTED = CALCULATOR_ALLOWED_CHARS.toCharArray()

        // Numeric IME (numpad layout with decimal + sign).
        val NUMPAD: CalculatorKeyListener = CalculatorKeyListener(NUMPAD_INPUT_TYPE)

        // Full text IME (letter keyboard); still filtered to the allow-list.
        val FULL_TEXT: CalculatorKeyListener = CalculatorKeyListener(FULL_TEXT_INPUT_TYPE)

        // Non-null iff [type] is a system-IME variant — callers can treat the
        // returned value as both a "should host the inline EditText" flag and
        // the listener to attach, avoiding parallel signals.
        fun forKeyboardType(type: KeyboardType): CalculatorKeyListener? =
            when (type) {
                KeyboardType.SYSTEM_NUMPAD -> NUMPAD
                KeyboardType.SYSTEM_FULL -> FULL_TEXT
                KeyboardType.BASIC, KeyboardType.EXPANDED -> null
            }
    }
}
