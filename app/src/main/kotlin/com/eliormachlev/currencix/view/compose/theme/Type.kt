package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Base = TextStyle(fontFamily = Inter)

val CurrenciXTypography =
    Typography(
        displayLarge = Base.copy(fontSize = 64.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em, lineHeight = 68.sp),
        displayMedium = Base.copy(fontSize = 52.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em, lineHeight = 56.sp),
        displaySmall = Base.copy(fontSize = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em, lineHeight = 44.sp),
        headlineLarge = Base.copy(fontSize = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em, lineHeight = 40.sp),
        headlineMedium = Base.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium, lineHeight = 34.sp),
        headlineSmall = Base.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
        titleLarge = Base.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
        titleMedium = Base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em, lineHeight = 22.sp),
        titleSmall = Base.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.01.em, lineHeight = 20.sp),
        bodyLarge = Base.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = Base.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = Base.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = Base.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.02.em, lineHeight = 20.sp),
        labelMedium = Base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em, lineHeight = 16.sp),
        labelSmall = Base.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.06.em, lineHeight = 14.sp),
    )

// Special-case styles the design uses in one place each — not part of the
// Typography scale because Material3 would try to apply them everywhere.

val WordmarkX =
    TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 26.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.em,
        geometricTransform = TextGeometricTransform(skewX = -0.05f),
    )

val SerifSymbol =
    TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
    )
