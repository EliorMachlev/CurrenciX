package com.eliormachlev.currencix.view.compose

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.maxLengthTrim
import androidx.compose.foundation.text.input.then

/**
 * Longest name a user can give a cart, a cart item or a fee. Room for a
 * descriptive name on a phone-width row; longer just gets ellipsized.
 */
internal const val MAX_NAME_LENGTH = 50

/**
 * For every name field: typing stops at [MAX_NAME_LENGTH], and a longer
 * paste is cut to fit rather than refused whole.
 */
internal val NameInputTransformation: InputTransformation = InputTransformation.maxLengthTrim(MAX_NAME_LENGTH)

private const val LINE_BREAK = '\n'
private const val SPACE = " "

// A pasted line break becomes a space, so a multi-line field stays one paragraph.
private object NoLineBreaks : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        for (index in length - 1 downTo 0) {
            if (charAt(index) == LINE_BREAK) replace(index, index + 1, SPACE)
        }
    }
}

/**
 * For a name field that wraps onto more lines: [NameInputTransformation],
 * and no line breaks (a pasted one becomes a space).
 */
internal val WrappingNameInputTransformation: InputTransformation = NoLineBreaks.then(NameInputTransformation)
