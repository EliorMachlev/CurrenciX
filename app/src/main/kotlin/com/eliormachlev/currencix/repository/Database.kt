package com.eliormachlev.currencix.repository

import android.content.Context

/**
 * Everything the app keeps on the device, one store per concern. Each store
 * reads and writes its own slice of the preference files; this class only
 * hands them out.
 */
class Database(
    context: Context,
) {
    val rates = RateStore(context)
    val lastState = LastStateStore(context)
    val stars = StarStore(context)
    val providers = ProviderSettings(context)
    val fees = FeeStore(context)
    val display = DisplaySettings(context)
    val chart = ChartSettings(context)
    val carts = CartStore(context)
}
