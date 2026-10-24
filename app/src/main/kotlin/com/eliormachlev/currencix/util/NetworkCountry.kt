package com.eliormachlev.currencix.util

import android.content.Context
import android.telephony.TelephonyManager

/**
 * The country (ISO 3166, upper case) of the mobile network the phone is on,
 * which needs no permission; null without one (Wi-Fi only, no SIM, a tablet).
 * Where the phone is, not where its user lives: roaming abroad shows the
 * country visited.
 */
fun networkCountry(context: Context): String? =
    context
        .getSystemService(TelephonyManager::class.java)
        ?.networkCountryIso
        ?.uppercase()
        ?.takeIf { it.isNotBlank() }
