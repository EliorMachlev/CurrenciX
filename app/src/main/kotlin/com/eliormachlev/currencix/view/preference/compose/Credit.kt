package com.eliormachlev.currencix.view.preference.compose

data class Credit(
    val title: String,
    val subtitle: String,
    val url: String,
)

data class CreditsSection(
    val headerRes: Int,
    val entries: List<Credit>,
)
