package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val BillGreen = Color(0xFF85BB65)
val BillGreenInk = Color(0xFF0F2A05)
val Brass = Color(0xFFB8985B)
val Vermillion = Color(0xFFC1443A)
val Amber = Color(0xFFE0A144)

private val PaperBg = Color(0xFFF3EEE5)
private val PaperSurface = Color(0xFFFFFFFF)
private val PaperSurfaceHigh = Color(0xFFF7F3EC)
private val PaperSurfaceHigher = Color(0xFFEEE7DB)
private val PaperInk = Color(0xFF1C1B1F)
private val PaperInkMuted = Color(0xFF6B675F)
private val PaperInkFaint = Color(0xFF98938A)
private val PaperOutline = Color(0x1F1C1B1F)

private val InkBg = Color(0xFF131311)
private val InkSurface = Color(0xFF1F1E1C)
private val InkSurfaceHigh = Color(0xFF26251F)
private val InkSurfaceHigher = Color(0xFF302E28)
private val InkForeground = Color(0xFFEFEAE0)
private val InkForegroundMuted = Color(0xFFA9A49A)
private val InkForegroundFaint = Color(0xFF726E66)
private val InkOutline = Color(0x33EFEAE0)

val CurrenciXLightColors =
    lightColorScheme(
        primary = BillGreen,
        onPrimary = BillGreenInk,
        primaryContainer = Color(0xFFCFE6BC),
        onPrimaryContainer = BillGreenInk,
        secondary = Brass,
        onSecondary = Color(0xFF251C08),
        background = PaperBg,
        onBackground = PaperInk,
        surface = PaperSurface,
        onSurface = PaperInk,
        surfaceVariant = PaperSurfaceHigh,
        onSurfaceVariant = PaperInkMuted,
        surfaceContainer = PaperSurfaceHigh,
        surfaceContainerHigh = PaperSurfaceHigher,
        surfaceContainerLow = PaperBg,
        outline = PaperOutline,
        outlineVariant = Color(0x141C1B1F),
        error = Vermillion,
        onError = Color.White,
    )

val CurrenciXDarkColors =
    darkColorScheme(
        primary = BillGreen,
        onPrimary = BillGreenInk,
        primaryContainer = Color(0xFF3B5A2A),
        onPrimaryContainer = Color(0xFFCFE6BC),
        secondary = Brass,
        onSecondary = Color(0xFF251C08),
        background = InkBg,
        onBackground = InkForeground,
        surface = InkSurface,
        onSurface = InkForeground,
        surfaceVariant = InkSurfaceHigh,
        onSurfaceVariant = InkForegroundMuted,
        surfaceContainer = InkSurfaceHigh,
        surfaceContainerHigh = InkSurfaceHigher,
        surfaceContainerLow = InkBg,
        outline = InkOutline,
        outlineVariant = Color(0x14EFEAE0),
        error = Vermillion,
        onError = Color.White,
    )
