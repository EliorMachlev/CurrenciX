package com.eliormachlev.currencix.model

/**
 * Bag of per-provider credentials threaded through
 * [ApiProvider.getRates] / [ApiProvider.Api.getRates]. Lives in the model
 * layer so the provider implementations don't need to import
 * `repository.Database` to fetch their own API keys (which would violate the
 * Konsist rule that forbids `model` → `repository` imports).
 *
 * The repository layer is the sole owner of the persisted secret store and
 * populates this at the call site (see `ExchangeRatesRepository`).
 *
 * Add a new field per provider that needs a secret. Nullable + defaulted so
 * callers that don't need a given key can construct via [EMPTY].
 */
data class ApiSecrets(
    val openExchangeRatesApiKey: String? = null,
) {
    companion object {
        val EMPTY = ApiSecrets()
    }
}
