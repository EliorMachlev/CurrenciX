package com.eliormachlev.currencix.model

import android.content.res.Resources
import java.util.IllformedLocaleException
import java.util.Locale
import java.util.Currency as JavaCurrency

/**
 * The converter's pair before the user picks one: into the currency of the
 * phone's region, from the dollar (or from the euro where the dollar is
 * home). Without a region the app knows a currency for, USD → EUR.
 */
object HomeCurrency {
    private val FALLBACK = CurrencyPair(Currency.USD, Currency.EUR)

    /** The first-run pair for the phone's region (its system setting, not the app's language). */
    val defaultPair: CurrencyPair by lazy { pairFor(systemRegion()) }

    /** The first-run pair for [region] (ISO 3166 code such as "IL"). */
    fun pairFor(region: String?): CurrencyPair {
        val home = currencyOf(region) ?: return FALLBACK
        val from = if (home == Currency.USD) Currency.EUR else Currency.USD
        return CurrencyPair(from, home)
    }

    // The currency used in [region], when the app has it.
    private fun currencyOf(region: String?): Currency? {
        if (region.isNullOrBlank()) return null
        // A malformed code, or a region with no currency of its own (Antarctica).
        val code =
            try {
                JavaCurrency.getInstance(Locale.Builder().setRegion(region).build())?.currencyCode
            } catch (_: IllformedLocaleException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        return code?.let(Currency::fromString)
    }

    // The region of the phone's first language that has one.
    private fun systemRegion(): String? {
        val locales = Resources.getSystem().configuration.locales
        return (0 until locales.size()).map { locales[it].country }.firstOrNull { it.isNotBlank() }
    }
}
