package com.eliormachlev.currencix.util

// Android's Locale silently rewrites three modern ISO 639-1 codes to their
// legacy forms (he→iw, id→in, yi→ji) — Locale.getLanguage() returns the
// legacy code, per-app storage roundtrips through it, and the resource
// loader keys values-* folders off of it. Any BCP-47 tag we compare with,
// hand to Android, or store alongside an Android-sourced value must be
// canonicalised through this helper first so both sides agree.
private val LEGACY_LANGUAGE_ALIASES =
    mapOf(
        "he" to "iw",
        "id" to "in",
        "yi" to "ji",
    )

private const val REGION_SEPARATOR = '_'

/**
 * Return the language code Android will actually use at runtime for [iso].
 * Preserves any region suffix ("pt_BR" stays "pt_BR"); only the language
 * subtag is rewritten. Idempotent: `androidLanguageCode("iw") == "iw"`.
 */
fun androidLanguageCode(iso: String): String {
    val (lang, rest) =
        iso
            .split(REGION_SEPARATOR, limit = 2)
            .let { it[0] to it.getOrNull(1) }
    val canonical = LEGACY_LANGUAGE_ALIASES[lang] ?: lang
    return if (rest != null) "$canonical$REGION_SEPARATOR$rest" else canonical
}
