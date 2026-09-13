package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// U+00D7 MULTIPLICATION SIGN, not the letter X — a semantic pun on
// conversion. Split into two Text nodes so the × can be larger and
// center-aligned against the cap-height of "Currenci" (SpanStyle can't
// change the box height an AnnotatedString glyph is measured in, so it
// couldn't optically center on its own).
private const val WORDMARK_X_SCALE = 1.35f

@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val base =
        TextStyle(
            fontFamily = SpaceGrotesk,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.01).em,
        )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Currenci", color = color, style = base)
        Text(
            text = "\u00D7",
            color = accentColor,
            style = base.copy(fontSize = fontSize * WORDMARK_X_SCALE),
        )
    }
}
