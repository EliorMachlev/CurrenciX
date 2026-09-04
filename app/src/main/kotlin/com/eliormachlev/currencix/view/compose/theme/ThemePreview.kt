package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliormachlev.currencix.view.compose.AppTheme

@Preview(name = "Theme · Light", widthDp = 400, heightDp = 900, showBackground = true)
@Composable
private fun ThemeLightPreview() {
    AppTheme(dark = false) { ThemePreviewBody() }
}

@Preview(name = "Theme · Dark", widthDp = 400, heightDp = 900, showBackground = true, backgroundColor = 0xFF131311)
@Composable
private fun ThemeDarkPreview() {
    AppTheme(dark = true) { ThemePreviewBody() }
}

@Composable
private fun ThemePreviewBody() {
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Wordmark(fontSize = 26.sp)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            AmountPreview()
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            ColorSwatches()
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            ShapeSwatches()
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            TypeStack()
        }
    }
}

@Composable
private fun AmountPreview() {
    val symbolStyle = SerifSymbol.copy(fontSize = 40.sp, color = Brass)
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
        Text(
            text =
                buildAnnotatedString {
                    withStyle(symbolStyle.toSpanStyle()) { append("$ ") }
                    append("1,234.56")
                },
            style = MaterialTheme.typography.displayMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text =
                buildAnnotatedString {
                    withStyle(symbolStyle.copy(fontSize = 26.sp).toSpanStyle()) { append("€ ") }
                    append("1,150.32")
                },
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorSwatches() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Palette", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Swatch(MaterialTheme.colorScheme.background, "bg")
            Swatch(MaterialTheme.colorScheme.surface, "surf")
            Swatch(MaterialTheme.colorScheme.surfaceContainerHigh, "high")
            Swatch(MaterialTheme.colorScheme.primary, "bill")
            Swatch(Brass, "brass")
            Swatch(MaterialTheme.colorScheme.error, "err")
        }
    }
}

@Composable
private fun Swatch(
    color: Color,
    name: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(36.dp)
                .background(color, RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ShapeSwatches() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Shapes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBox(CurrenciXShapes.small, "sm")
            ShapeBox(CurrenciXShapes.medium, "md")
            ShapeBox(CurrenciXShapes.large, "lg")
            ShapeBox(CurrenciXShapes.extraLarge, "xl")
            ShapeBox(PillShape, "pill")
        }
    }
}

@Composable
private fun ShapeBox(
    shape: Shape,
    name: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 56.dp, height = 32.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
        )
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TypeStack() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Ag Currency exchange", style = MaterialTheme.typography.headlineSmall)
        Text("The quick brown fox", style = MaterialTheme.typography.titleMedium)
        Text("Sample body copy in Inter regular.", style = MaterialTheme.typography.bodyMedium)
        Text("LABEL · UPPERCASE 12PX", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "italic Instrument Serif — reserved for X and symbols",
            style = WordmarkX.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = Brass),
        )
    }
}
