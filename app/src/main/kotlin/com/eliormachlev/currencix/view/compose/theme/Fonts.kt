package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.eliormachlev.currencix.R

val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

// Instrument Serif Italic is bundled solo — the design uses it only for the
// wordmark's leaning "X" and for currency-symbol ornaments. Loading upright
// weights would ship ~250 KB per weight for glyphs we never render.
val InstrumentSerif =
    FontFamily(
        Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
    )
