package com.eliormachlev.currencix.model

import java.math.BigDecimal

data class Rate(
    val currency: Currency,
    val value: BigDecimal,
)
