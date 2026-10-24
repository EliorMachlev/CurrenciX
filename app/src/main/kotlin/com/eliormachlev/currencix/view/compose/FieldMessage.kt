package com.eliormachlev.currencix.view.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * A text field's supporting line for [message], or none when it's null:
 * how an invalid entry says what's wrong in words (WCAG 3.3.1), which
 * TalkBack reads with the field, not only by turning the outline red.
 */
fun fieldMessage(message: String?): (@Composable () -> Unit)? = message?.let { { Text(it) } }

/** [message] when [text] was typed but didn't parse to a valid [value]; null when it's empty or valid. */
fun invalidUnless(
    text: CharSequence,
    value: Any?,
    message: String,
): String? = message.takeIf { text.isNotBlank() && value == null }
