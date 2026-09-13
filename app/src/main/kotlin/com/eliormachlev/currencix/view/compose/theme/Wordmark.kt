package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// The final glyph is U+00D7 MULTIPLICATION SIGN, not the letter X — a
// semantic pun on currency conversion. Single face, single weight; only the
// colour of the × changes, so the pun still leads and the accent just tints it.
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val text: AnnotatedString =
        buildAnnotatedString {
            append("Currenci")
            withStyle(SpanStyle(color = accentColor)) {
                append("\u00D7")
            }
        }
    Text(
        modifier = modifier,
        text = text,
        color = color,
        style =
            TextStyle(
                fontFamily = SpaceGrotesk,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.01).em,
            ),
    )
}
