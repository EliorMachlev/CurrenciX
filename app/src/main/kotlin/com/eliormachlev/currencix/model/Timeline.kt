package com.eliormachlev.currencix.model

import java.time.LocalDate

data class Timeline(
    val success: Boolean?,
    val error: String?,
    val base: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val rates: Map<LocalDate, Rate>?,
    val provider: ApiProvider? = null,
)
