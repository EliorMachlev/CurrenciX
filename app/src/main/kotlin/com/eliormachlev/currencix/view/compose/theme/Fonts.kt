package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.eliormachlev.currencix.R

val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

// Space Grotesk carries the wordmark. Its wider proportions and geometric
// terminals give "Currenci×" the identity Inter can't — Inter is our neutral
// UI face precisely because it disappears, which is wrong for a logotype.
val SpaceGrotesk =
    FontFamily(
        Font(R.font.space_grotesk_medium, FontWeight.Medium),
    )
