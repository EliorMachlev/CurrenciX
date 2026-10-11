package com.eliormachlev.currencix.view.compose

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLengthTrim

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
