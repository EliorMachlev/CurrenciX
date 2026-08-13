package com.eliormachlev.currencix.view.preference.compose

data class Credit(
    val title: String,
    val subtitle: String,
    val url: String,
    // SPDX identifier (e.g. "Apache-2.0", "MIT", "GPL-3.0-or-later"). Null
    // means "no separate licence line" — used for project/author rows where
    // the licence row is elsewhere on the same screen.
    val license: String? = null,
)

data class CreditsSection(
    val headerRes: Int,
    val entries: List<Credit>,
)
