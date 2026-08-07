package com.eliormachlev.currencix.view.preference.compose

import com.eliormachlev.currencix.R

private val PROJECT_CREDITS =
    listOf(
        Credit(
            title = "CurrenciX",
            subtitle = "Fork by Elior Machlev",
            url = "https://github.com/EliorMachlev/currencies",
        ),
        Credit(
            title = "Currencies",
            subtitle = "Original app by Maximilian Salomon (sal0max)",
            url = "https://github.com/sal0max/currencies",
        ),
    )

private val LICENSE_CREDITS =
    listOf(
        Credit(
            title = "GPL-3.0-or-later",
            subtitle = "GNU General Public License v3.0 or later",
            url = "https://www.gnu.org/licenses/gpl-3.0.html",
        ),
    )

private val LIBRARY_CREDITS =
    listOf(
        Credit(
            title = "Vico",
            subtitle = "Charting — Patryk & Patrick",
            url = "https://github.com/patrykandpatrick/vico",
        ),
        Credit(
            title = "OkHttp",
            subtitle = "HTTP client — Square",
            url = "https://square.github.io/okhttp/",
        ),
        Credit(
            title = "Moshi",
            subtitle = "JSON parser — Square",
            url = "https://github.com/square/moshi",
        ),
        Credit(
            title = "Timber",
            subtitle = "Logging — Jake Wharton",
            url = "https://github.com/JakeWharton/timber",
        ),
        Credit(
            title = "mXparser",
            subtitle = "Math expression parser",
            url = "https://mathparser.org/",
        ),
        Credit(
            title = "Bouncy Castle",
            subtitle = "Cryptography provider",
            url = "https://www.bouncycastle.org/",
        ),
        Credit(
            title = "Material Components",
            subtitle = "Android UI — Google",
            url = "https://github.com/material-components/material-components-android",
        ),
    )

val CREDITS_SECTIONS: List<CreditsSection> =
    listOf(
        CreditsSection(R.string.credits_section_project, PROJECT_CREDITS),
        CreditsSection(R.string.credits_section_license, LICENSE_CREDITS),
        CreditsSection(R.string.credits_section_libraries, LIBRARY_CREDITS),
    )
