package com.eliormachlev.currencix.viewmodel.timeline

import androidx.compose.runtime.Immutable
import com.eliormachlev.currencix.model.Rate
import java.time.LocalDate

/**
 * One of the chart's readouts: the rate at the range's start (or the scrub)
 * and now, its high and low, its average and median. [rate] is null when
 * the range has none; [date] is the day it's for, where it has one (not the
 * average or median); [decimals] is how many places to show it with.
 */
@Immutable
data class RateReadout(
    val rate: Rate?,
    val date: LocalDate?,
    val decimals: Int,
)

/** The readout for one day's entry of the range, or none. */
internal fun Map.Entry<LocalDate, Rate?>?.toReadout(decimals: Int): RateReadout = RateReadout(this?.value, this?.key, decimals)
