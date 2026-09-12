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
import androidx.compose.ui.unit.sp

@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Currenci",
            color = color,
            style =
                TextStyle(
                    fontFamily = Inter,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                ),
        )
        Text(
            text = "X",
            color = accentColor,
            style = WordmarkX.copy(fontSize = fontSize * 1.2f),
        )
    }
}
