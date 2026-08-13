package com.eliormachlev.currencix.view.preference.compose

import com.eliormachlev.currencix.R

private val PROJECT_CREDITS =
    listOf(
        Credit(
            title = "CurrenciX",
            subtitle = "Fork by Elior Machlev",
            url = "https://github.com/EliorMachlev/CurrenciX",
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

// Runtime dependencies of the app — kept in sync with app/build.gradle.kts.
// SPDX identifiers per each project's declared licence. The CI-generated SBOM
// is the machine-readable source of truth; this list is the user-facing
// attribution surface required by Apache-2.0 §4(d), MIT notice clause, and
// similar terms in the other permissive licences below.
private val LIBRARY_CREDITS =
    listOf(
        Credit(
            title = "AndroidX",
            subtitle = "Jetpack (core, appcompat, lifecycle, preference, window, activity, constraintlayout, swiperefreshlayout) — Google",
            url = "https://developer.android.com/jetpack/androidx",
            license = "Apache-2.0",
        ),
        Credit(
            title = "Jetpack Compose",
            subtitle = "UI toolkit + Material 3 — Google",
            url = "https://developer.android.com/jetpack/compose",
            license = "Apache-2.0",
        ),
        Credit(
            title = "Material Components for Android",
            subtitle = "Material Design components — Google",
            url = "https://github.com/material-components/material-components-android",
            license = "Apache-2.0",
        ),
        Credit(
            title = "Vico",
            subtitle = "Charting — Patryk & Patrick",
            url = "https://github.com/patrykandpatrick/vico",
            license = "Apache-2.0",
        ),
        Credit(
            title = "OkHttp",
            subtitle = "HTTP client — Square",
            url = "https://square.github.io/okhttp/",
            license = "Apache-2.0",
        ),
        Credit(
            title = "Moshi",
            subtitle = "JSON parser — Square",
            url = "https://github.com/square/moshi",
            license = "Apache-2.0",
        ),
        Credit(
            title = "Timber",
            subtitle = "Logging — Jake Wharton",
            url = "https://github.com/JakeWharton/timber",
            license = "Apache-2.0",
        ),
        Credit(
            title = "EvalEx",
            subtitle = "Math expression evaluator — Udo Klimaschewski",
            url = "https://github.com/ezylang/EvalEx",
            license = "Apache-2.0",
        ),
        Credit(
            title = "Bouncy Castle",
            subtitle = "Cryptography provider (Argon2id + AES-GCM for backup encryption)",
            url = "https://www.bouncycastle.org/",
            license = "MIT",
        ),
        Credit(
            title = "Kotlin",
            subtitle = "Language runtime + standard library — JetBrains",
            url = "https://kotlinlang.org/",
            license = "Apache-2.0",
        ),
        Credit(
            title = "kotlinx.coroutines",
            subtitle = "Async primitives — JetBrains (transitive)",
            url = "https://github.com/Kotlin/kotlinx.coroutines",
            license = "Apache-2.0",
        ),
        Credit(
            title = "Okio",
            subtitle = "I/O primitives — Square (transitive via OkHttp / Moshi)",
            url = "https://square.github.io/okio/",
            license = "Apache-2.0",
        ),
    )

val CREDITS_SECTIONS: List<CreditsSection> =
    listOf(
        CreditsSection(R.string.credits_section_project, PROJECT_CREDITS),
        CreditsSection(R.string.credits_section_license, LICENSE_CREDITS),
        CreditsSection(R.string.credits_section_libraries, LIBRARY_CREDITS),
    )
