package com.eliormachlev.currencix.model

import java.math.BigDecimal

/**
 * A trip: saved carts grouped under a [name], their spending added up in the
 * [currency] of home, against an optional [budget] in it.
 */
data class Trip(
    val id: String,
    val name: String,
    val currency: Currency,
    val budget: BigDecimal? = null,
)
