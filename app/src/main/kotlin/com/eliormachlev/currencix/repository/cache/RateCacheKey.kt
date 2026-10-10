package com.eliormachlev.currencix.repository.cache

import java.time.LocalDate

/**
 * Provider-agnostic cache key. Kept sealed so the compiler enforces exhaustive
 * handling in [SourceOfTruth] implementations, and so the on-disk filename
 * builder can be a single `when` with no fallthrough.
 *
 * Provider identity is carried as the stable [Int] id from
 * [com.eliormachlev.currencix.model.ApiProvider.id] rather than the enum
 * itself, so a future SoT implementation (e.g. Room in #164) never has to
 * pull the enum through its schema.
 */
sealed class RateCacheKey {
    /**
     * Stable filesystem-safe identity. Used verbatim as the disk filename
     * (with a `.json` suffix) and as the map key for in-memory / dedupe
     * tiers. All components are ASCII — no locale-sensitive formatting.
     */
    abstract val stableId: String

    data class RatesLatest(
        val providerId: Int,
        val baseIso: String,
        val date: LocalDate?,
    ) : RateCacheKey() {
        override val stableId: String
            get() = "rates_${providerId}_${baseIso}_${date?.toString() ?: "latest"}"
    }

    /**
     * Per-pair timeline key. The date range is deliberately NOT part of the
     * stable id — the repo dynamically shifts `startDate` forward to fetch
     * only the missing tail on each call, and the on-disk merge lives in
     * `ExchangeRatesRepository.mergeTimeline`. Keying on the pair means
     * repeated fetches for the same pair overwrite a single disk file
     * instead of littering the cache dir with per-range fragments.
     */
    data class TimelineRange(
        val providerId: Int,
        val baseIso: String,
        val symbolIso: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
    ) : RateCacheKey() {
        override val stableId: String
            get() = "timeline_${providerId}_${baseIso}_$symbolIso"
    }
}
