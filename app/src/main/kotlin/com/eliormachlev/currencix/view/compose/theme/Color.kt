package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val BillGreen = Color(0xFF85BB65)
val BillGreenInk = Color(0xFF0F2A05)
val Brass = Color(0xFFB8985B)

// The logo's arrows on paper (BrandMark): bill green and brass darkened so
// they keep their contrast; on ink they're BillGreen and Brass. Kept in sync
// with the brandMark* colours in res/values*/colors.xml (the splash's mark).
val BrandGreenOnPaper = Color(0xFF5E9A40)
val BrandBrassOnPaper = Color(0xFF9C7C3E)

// The light palette's green and brass where they're read as text or as a
// control (buttons, links, focused fields, headings, the keypad's brass keys,
// and the "=" key and pills they fill): bill green and brass darkened to WCAG
// AA on paper and white (4.5:1 for text; 4.4:1 brass on the darkest tile,
// above the 3:1 its large keys need). On ink, BillGreen and Brass already pass.
private val GreenOnPaper = Color(0xFF3E6B28)
private val BrassOnPaper = Color(0xFF7A5E2B)

// Error text and its fill: on paper a deeper red (5.2:1), on ink a light one
// (4.8:1 even on the brightest tile), each with its own text on top.
private val ErrorOnPaper = Color(0xFFB3372E)
private val ErrorOnInk = Color(0xFFF28B82)
private val OnErrorOnInk = Color(0xFF410E0B)
val Amber = Color(0xFFE0A144)

// Amber-tinted status pill used inside the RateFooter to flag that the
// device is online but the rate provider's endpoint failed. Placed
// between OFFLINE (red) and HISTORICAL (purple) in severity. Not part of
// the ColorScheme so it stays one-off and consistent across light/dark.
val AmberContainer = Color(0xFFFFE7B8)
val OnAmberContainer = Color(0xFF3A2A08)

// Revenue-stamp crimson: darker than the error red so the fee "stamp" on
// the hero receipt reads as ink pressed into paper rather than a bright
// alert. Used for the fee-row stamp border/text; deliberately not part
// of the Material color scheme so it stays a one-off signature accent.
val Stamp = Color(0xFF8B3A3A)

// Brass-tinted containers for secondary emphasis (tonal buttons, the
// selected segment). Unset, Material falls back to its default lavender.
private val BrassContainerLight = Color(0xFFEFE3C8)
private val BrassContainerDark = Color(0xFF4A3F26)
private val OnBrassContainerLight = Color(0xFF251C08)
private val OnBrassContainerDark = Color(0xFFF1E3C0)

// Tertiary: the provider picker's warnings ("updated once a month",
// "regular downtimes"). The fee stamp's crimson rather than Material's
// default purple; lightened for ink.
private val StampLight = Color(0xFFE8B4B4)

/**
 * The fee stamp's crimson for the current background: [Stamp] on paper
 * (7.6:1), [StampLight] on ink (9.2:1), where Stamp would be 2.2:1. Picked by
 * the background's lightness, so it stays the stamp's own colour under any
 * palette, wallpaper colours included.
 */
@Composable
@ReadOnlyComposable
fun stampInk(): Color = if (MaterialTheme.colorScheme.surface.luminance() > HALF) Stamp else StampLight

private const val HALF = 0.5f

// Error containers (the keypad's C, error chips): the stamp's crimson, warm
// rather than Material's default red; deep on ink, pale on paper.
private val ErrorContainerLight = Color(0xFFF6DAD5)
private val OnErrorContainerLight = Color(0xFF5C1A14)
private val ErrorContainerDark = Color(0xFF5A2A27)
private val OnErrorContainerDark = Color(0xFFF2B8B5)

private val PaperBg = Color(0xFFF3EEE5)
private val PaperSurface = Color(0xFFFFFFFF)
private val PaperSurfaceHigh = Color(0xFFF7F3EC)
private val PaperSurfaceHigher = Color(0xFFEEE7DB)
private val PaperSurfaceHighest = Color(0xFFE4DCCD)
private val PaperInk = Color(0xFF1C1B1F)

// Muted text: 4.7:1 even on the darkest paper tile.
private val PaperInkMuted = Color(0xFF625E57)

// Field and control borders: 3:1 on every paper surface (WCAG 1.4.11);
// decorative lines use outlineVariant instead.
private val PaperOutline = Color(0xFF827C71)

private val InkBg = Color(0xFF131311)
private val InkSurface = Color(0xFF1F1E1C)
private val InkSurfaceHigh = Color(0xFF26251F)
private val InkSurfaceHigher = Color(0xFF302E28)
private val InkSurfaceHighest = Color(0xFF3B3932)
private val InkForeground = Color(0xFFEFEAE0)
private val InkForegroundMuted = Color(0xFFA9A49A)
private val InkOutline = Color(0xFF8A857B)

val CurrenciXLightColors =
    lightColorScheme(
        primary = GreenOnPaper,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCFE6BC),
        onPrimaryContainer = BillGreenInk,
        secondary = BrassOnPaper,
        onSecondary = Color.White,
        secondaryContainer = BrassContainerLight,
        onSecondaryContainer = OnBrassContainerLight,
        tertiary = Stamp,
        onTertiary = Color.White,
        background = PaperBg,
        onBackground = PaperInk,
        surface = PaperSurface,
        onSurface = PaperInk,
        surfaceVariant = PaperSurfaceHigh,
        onSurfaceVariant = PaperInkMuted,
        surfaceContainer = PaperSurfaceHigh,
        surfaceContainerHigh = PaperSurfaceHigher,
        // Unset, the highest level falls back to Material's lavender.
        surfaceContainerHighest = PaperSurfaceHighest,
        surfaceContainerLow = PaperBg,
        surfaceContainerLowest = PaperSurface,
        outline = PaperOutline,
        outlineVariant = Color(0x141C1B1F),
        error = ErrorOnPaper,
        onError = Color.White,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
    )

val CurrenciXDarkColors =
    darkColorScheme(
        primary = BillGreen,
        onPrimary = BillGreenInk,
        primaryContainer = Color(0xFF3B5A2A),
        onPrimaryContainer = Color(0xFFCFE6BC),
        secondary = Brass,
        onSecondary = Color(0xFF251C08),
        secondaryContainer = BrassContainerDark,
        onSecondaryContainer = OnBrassContainerDark,
        tertiary = StampLight,
        onTertiary = Color(0xFF3A1010),
        background = InkBg,
        onBackground = InkForeground,
        surface = InkSurface,
        onSurface = InkForeground,
        surfaceVariant = InkSurfaceHigh,
        onSurfaceVariant = InkForegroundMuted,
        surfaceContainer = InkSurfaceHigh,
        surfaceContainerHigh = InkSurfaceHigher,
        surfaceContainerHighest = InkSurfaceHighest,
        surfaceContainerLow = InkBg,
        surfaceContainerLowest = InkBg,
        outline = InkOutline,
        outlineVariant = Color(0x14EFEAE0),
        error = ErrorOnInk,
        onError = OnErrorOnInk,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
    )

/**
 * A tile, chip or unpicked button drawn straight on the screen's background
 * (or a sheet): the deepest container tone, so it reads as a shape on paper
 * (1.18:1) as Quick conversions' tiles do on a sheet. The lighter containers
 * all but vanish on paper (1.05:1).
 */
val ColorScheme.tile: Color get() = surfaceContainerHighest
