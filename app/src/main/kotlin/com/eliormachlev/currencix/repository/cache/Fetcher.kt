package com.eliormachlev.currencix.repository.cache

/**
 * Upstream data source for a single [RateCacheKey] shape.
 *
 * Returns [Result] so provider errors (`SocketTimeoutException`,
 * `ApiHttpError`, malformed payloads) surface as `isFailure` without
 * throwing across the cache boundary — the retry/dedupe orchestration
 * inspects `exceptionOrNull()` and decides whether to back off or give up.
 *
 * Kept as a `fun interface` so provider adapters can be one-liner lambdas
 * (`Fetcher { key -> service.getRates(...) }`) without the ceremony of
 * declaring a nominal subtype per provider.
 */
fun interface Fetcher<K : RateCacheKey, V : Any> {
    suspend fun fetch(key: K): Result<V>
}

/**
 * Converts a typed value into a stable on-disk representation and back.
 *
 * The [SourceOfTruth] hierarchy is deliberately typed on `V` rather than on
 * `String` — so the disk tier can persist whatever encoding it wants (JSON
 * today, Room rows tomorrow) without forcing the memory tier to pay a
 * serialize/deserialize round trip per read.
 *
 * A Converter is only wired into the disk tier; the memory tier holds live
 * `V` instances directly. Keep implementations pure and side-effect-free —
 * they're called on the coroutine's dispatcher without extra synchronization.
 */
interface Converter<V : Any> {
    fun encode(value: V): String

    fun decode(encoded: String): V?
}
