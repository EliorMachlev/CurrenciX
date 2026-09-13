package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// The final glyph is U+00D7 MULTIPLICATION SIGN, not the letter X — a
// semantic pun on currency conversion. Single face, single weight, single
// colour: the joke does the work, no italic/accent-colour costume needed.
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Text(
        modifier = modifier,
        text = "Currenci\u00D7",
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
