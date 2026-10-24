package com.eliormachlev.currencix.model

/**
 * The phone is in [country] (ISO 3166), where [local] is paid, away from
 * home, where [home] is: the converter offers [local] → [home].
 */
data class AbroadOffer(
    val country: String,
    val local: Currency,
    val home: Currency,
)

/**
 * Whether to offer the local currency, from the country of the mobile
 * network the phone is on ([networkCountry], no permission needed; null on
 * Wi-Fi only or without a SIM) and its [homeRegion]: abroad, in a country
 * with a currency of its own that the converter's [pair] doesn't show yet,
 * and not asked about on this visit ([askedCountry]). [currencyOf] gives a
 * region's currency.
 */
fun abroadOffer(
    networkCountry: String?,
    homeRegion: String?,
    pair: CurrencyPair?,
    askedCountry: String?,
    currencyOf: (String) -> Currency?,
): AbroadOffer? {
    val country = networkCountry?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
    val homeCountry = homeRegion?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
    if (country == homeCountry || country == askedCountry) return null
    val local = currencyOf(country) ?: return null
    val home = currencyOf(homeCountry) ?: return null
    if (local == home || pair?.from == local || pair?.to == local) return null
    return AbroadOffer(country, local, home)
}

/** Whether the phone is back in [homeRegion], so the next trip is asked about afresh. */
fun isHome(
    networkCountry: String?,
    homeRegion: String?,
): Boolean = networkCountry != null && networkCountry.equals(homeRegion, ignoreCase = true)
