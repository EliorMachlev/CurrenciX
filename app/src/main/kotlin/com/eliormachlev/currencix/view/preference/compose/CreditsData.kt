package com.eliormachlev.currencix.view.preference.compose

import android.content.Context
import androidx.annotation.StringRes
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.URL_DOCS_BASE
import com.eliormachlev.currencix.util.URL_REPO
import com.eliormachlev.currencix.util.inReadingOrder

private const val URL_PRIVACY_POLICY = "${URL_DOCS_BASE}privacy-policy.md"
private const val URL_TERMS_OF_SERVICE = "${URL_DOCS_BASE}terms-of-service.md"
private const val URL_ACCESSIBILITY_STATEMENT = "${URL_DOCS_BASE}accessibility-statement.md"

// Names and the licence identifier: never translated.
internal const val ORIGINAL_AUTHOR = "Maximilian Salomon"
internal const val FORK_AUTHOR = "Elior Machlev"
internal const val APP_LICENSE = "GPL-3.0-or-later"
private const val ORIGINAL_FIRST_YEAR = 2020
private const val FORK_FIRST_YEAR = 2026

private val LICENSE_CREDITS =
    listOf(
        Credit(
            title = APP_LICENSE,
            // The licence's own name: not translated.
            subtitle = "GNU General Public License v3.0 or later",
            url = "https://www.gnu.org/licenses/gpl-3.0.html",
        ),
    )

// A third-party library: what it does for the app ([role], translated) and
// who makes it ([author], a name, not translated).
private class Library(
    val title: String,
    @StringRes val role: Int,
    val author: String?,
    val url: String,
    val license: String,
)

private const val GOOGLE = "Google"
private const val SQUARE = "Square"
private const val JETBRAINS = "JetBrains"
private const val APACHE_2 = "Apache-2.0"

// Runtime dependencies of the app — kept in sync with app/build.gradle.kts.
// SPDX identifiers per each project's declared licence. The CI-generated SBOM
// is the machine-readable source of truth; this list is the user-facing
// attribution surface required by Apache-2.0 §4(d), MIT notice clause, and
// similar terms in the other permissive licences below.
private val LIBRARIES =
    listOf(
        Library("AndroidX", R.string.credit_lib_androidx, GOOGLE, "https://developer.android.com/jetpack/androidx", APACHE_2),
        Library("Jetpack Compose", R.string.credit_lib_compose, GOOGLE, "https://developer.android.com/jetpack/compose", APACHE_2),
        Library(
            "Material Components for Android",
            R.string.credit_lib_material_components,
            GOOGLE,
            "https://github.com/material-components/material-components-android",
            APACHE_2,
        ),
        Library("Material Symbols", R.string.credit_lib_material_symbols, GOOGLE, "https://fonts.google.com/icons", APACHE_2),
        Library("Vico", R.string.credit_lib_vico, "Patryk & Patrick", "https://github.com/patrykandpatrick/vico", APACHE_2),
        Library("OkHttp", R.string.credit_lib_okhttp, SQUARE, "https://square.github.io/okhttp/", APACHE_2),
        Library("Moshi", R.string.credit_lib_moshi, SQUARE, "https://github.com/square/moshi", APACHE_2),
        Library("Timber", R.string.credit_lib_timber, "Jake Wharton", "https://github.com/JakeWharton/timber", APACHE_2),
        Library("Bouncy Castle", R.string.credit_lib_bouncy_castle, null, "https://www.bouncycastle.org/", "MIT"),
        Library("Tink", R.string.credit_lib_tink, GOOGLE, "https://github.com/tink-crypto/tink-java", APACHE_2),
        Library("Kotlin", R.string.credit_lib_kotlin, JETBRAINS, "https://kotlinlang.org/", APACHE_2),
        Library("kotlinx.coroutines", R.string.credit_lib_coroutines, JETBRAINS, "https://github.com/Kotlin/kotlinx.coroutines", APACHE_2),
        Library("Okio", R.string.credit_lib_okio, SQUARE, "https://square.github.io/okio/", APACHE_2),
    )

fun creditsSections(context: Context): List<CreditsSection> =
    listOf(
        CreditsSection(
            R.string.credits_section_legal,
            listOf(
                context.credit(R.string.credit_privacy_policy_title, R.string.credit_privacy_policy_subtitle, URL_PRIVACY_POLICY),
                context.credit(R.string.credit_terms_of_service_title, R.string.credit_terms_of_service_subtitle, URL_TERMS_OF_SERVICE),
                context.credit(R.string.credit_accessibility_title, R.string.credit_accessibility_subtitle, URL_ACCESSIBILITY_STATEMENT),
            ),
        ),
        CreditsSection(
            R.string.credits_section_source,
            listOf(context.credit(R.string.credit_source_title, R.string.credit_source_subtitle, URL_REPO)),
        ),
        CreditsSection(
            R.string.credits_section_project,
            listOf(
                Credit(title = "CurrenciX", subtitle = context.getString(R.string.credit_fork_by, FORK_AUTHOR), url = URL_REPO),
                Credit(
                    title = "Currencies",
                    subtitle = context.getString(R.string.credit_original_by, "$ORIGINAL_AUTHOR (sal0max)"),
                    url = "https://github.com/sal0max/currencies",
                ),
            ),
        ),
        CreditsSection(R.string.credits_section_license, LICENSE_CREDITS),
        CreditsSection(R.string.credits_section_libraries, LIBRARIES.map { context.credit(it) }),
    )

private fun Context.credit(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    url: String,
) = Credit(
    title = getString(titleRes),
    subtitle = getString(subtitleRes),
    url = url,
)

// "Charting — Patryk & Patrick": the role in the app's language, then the maker.
private fun Context.credit(library: Library) =
    Credit(
        title = library.title,
        subtitle = library.author?.let { "${getString(library.role)} — $it" } ?: getString(library.role),
        url = library.url,
        license = library.license,
    )

/**
 * The version row's lines, in the app's language: who wrote the original
 * and who the fork, with their years, then the licence. Each line keeps the
 * language's reading order around the Latin names.
 */
fun versionSummary(
    context: Context,
    year: Int,
): String =
    listOf(
        "${context.getString(R.string.credit_original_by, ORIGINAL_AUTHOR)} · © $ORIGINAL_FIRST_YEAR–$year",
        "${context.getString(R.string.credit_fork_by, FORK_AUTHOR)} · © $FORK_FIRST_YEAR",
        APP_LICENSE,
    ).joinToString("\n") { it.inReadingOrder(context) }
