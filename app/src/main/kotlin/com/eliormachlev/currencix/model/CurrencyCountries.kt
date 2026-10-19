package com.eliormachlev.currencix.model

import com.eliormachlev.currencix.util.normalizeForSearch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The countries that use each currency, by name — so searching the picker
 * for "Japan" finds JPY and "Germany" finds EUR. Names come from the
 * platform's locale data, in the app's language and in English (people
 * often type the English name whatever their UI language is).
 */
object CurrencyCountries {
    // Built once per display language; ~250 regions, so a few ms at most.
    private val cache = ConcurrentHashMap<Locale, Map<String, String>>()

    /**
     * Normalized ([normalizeForSearch]) country names that use [currency],
     * space-separated; empty when the platform knows none.
     */
    fun searchText(
        currency: Currency,
        displayLocale: Locale,
    ): String = cache.getOrPut(displayLocale) { build(displayLocale) }[currency.iso4217Alpha()].orEmpty()

    private fun build(displayLocale: Locale): Map<String, String> =
        Locale
            .getISOCountries()
            .mapNotNull { region ->
                val locale = Locale.Builder().setRegion(region).build()
                val code = currencyCodeOf(locale) ?: return@mapNotNull null
                code to setOf(locale.getDisplayCountry(displayLocale), locale.getDisplayCountry(Locale.ENGLISH))
            }.groupBy({ it.first }, { it.second })
            .mapValues { (_, names) -> names.flatten().toSet().joinToString(" ") { it.normalizeForSearch() } }

    // Regions without a currency of their own (Antarctica) throw or give null.
    private fun currencyCodeOf(locale: Locale): String? =
        runCatching {
            java.util.Currency
                .getInstance(locale)
                ?.currencyCode
        }.getOrNull()
}
