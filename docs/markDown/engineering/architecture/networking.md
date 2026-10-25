# Networking

## One client

Every request goes through one `OkHttpClient` (`HttpClientProvider`): a disk cache (5 MiB, `cacheDir/http-cache`), a per-provider `Cache-Control` rewrite, and a host allowlist (`AllowedHostsInterceptor`: the providers' `Api.hosts` plus the OCR language host, checked at every redirect). The cache-less client tests use and the OCR download client (no call timeout, no cache) are `newBuilder` views of it, sharing connections and threads. TLS, Certificate Transparency, Encrypted Client Hello and private requests: [security.md](../security.md#network).

## Retrofit for fixed-shape JSON, raw OkHttp for the rest

| Transport | For | Endpoints |
|---|---|---|
| **Retrofit** (`model/provider/api/*Api.kt`) | JSON a Moshi adapter decodes directly | Frankfurter, Open Exchange Rates, AllRatesToday, InforEuro, Bank of Canada, Bank of Israel `PublicApi` |
| **Raw OkHttp** (`HttpClientProvider.fetch`) | Feeds parsed by hand from the byte stream | Norges Bank (SDMX XML), Bank Rossii (XML), Bank of Israel SDMX-JSON (history, timeline) |

The split is permanent, not an unfinished migration: the XML feeds go through SAX parsers, and Bank of Israel's SDMX-JSON is keyed by dimension-index tuples (`BankOfIsraelSdmxParser` walks it as a JSON tree), so Retrofit would only replace a query string.

**One error contract** across both, so the repository never cares which: non-2xx → `ApiHttpError(statusCode)`; network failures keep their exception types; a body that decodes to `null` → `IOException("<provider>: empty JSON")`. On the Retrofit side, `retrofitApi<T>()` binds an interface to the provider's `baseUrl` and `fetchRetrofit { }` unwraps the call; interfaces return `Response<T>`, since Retrofit's suspend adapter throws an opaque `KotlinNullPointerException` on a null body. An adapter that bails on a non-object payload must consume it first (`JsonReader.skipIfNotObject()`), as the Moshi converter rejects leftover input. `ProviderRequestTest` pins every provider's exact URL and this contract, offline.

## Provider contract

Each provider is a class in `model/provider/` implementing `ApiProvider.Api`:

```kotlin
suspend fun getRates(context: Context?, date: LocalDate?, secrets: ApiSecrets): Result<ExchangeRates>
suspend fun getTimeline(context: Context?, base: Currency, symbol: Currency, startDate: LocalDate, endDate: LocalDate, secrets: ApiSecrets = ApiSecrets.EMPTY): Result<Timeline>
```

`ApiProvider` is an enum whose entries implement it, so switching provider is one DataStore write. Only each entry's `id` is stored, so reordering the enum is safe. Each provider maps its own schema to the shared model: a Moshi adapter (`model/adapter/`), a SAX parser (Norges Bank, Bank Rossii) or `BankOfIsraelSdmxParser`. A historical date queries the history endpoint instead of the live one.

**AllRatesToday** is built from its [OpenAPI spec](https://allratestoday.com/api/openapi.json), `GET /api/v1/rates`: latest is `?source=EUR` (a past day adds `time=<day>T12:00:00Z`); a timeline is `?group=day&source=&target=&from=&to=<end + 1 day>` (`to` is exclusive), FOK asked for as DKK.

## HTTP cache per provider

| Provider | Host(s) | Mode | TTL |
|---|---|---|---|
| Frankfurter.app | `api.frankfurter.dev` | Upstream | Its `Cache-Control` |
| OpenExchangerates | `openexchangerates.org` | Upstream | Its `Cache-Control` |
| AllRatesToday | `allratestoday.com` | Upstream | 60 s; the rate cache keeps its answer an hour, as each request spends the user's quota |
| InforEuro | `ec.europa.eu` | Rewritten | 6 h (monthly rates) |
| Bank of Canada | `www.bankofcanada.ca` | Rewritten | 1 h (sends `no-cache`) |
| Norges Bank | `data.norges-bank.no` | Rewritten | 1 h |
| Bank Rossii | `www.cbr.ru` | Rewritten | 1 h |
| Bank of Israel | `boi.org.il`, `edge.boi.gov.il` | Rewritten | 1 h |

"Rewritten" hosts get `Cache-Control: public, max-age=<TTL>` from `ProviderCacheRewriteInterceptor`, on a hand-picked allowlist so unaudited hosts are never cached by accident. Each TTL is shorter than the provider's publishing cadence, so an offline app never serves a cache older than a working day. Debug builds' Chucker shows `X-From-Cache` on hits.

## When the cache is skipped

- **Revalidating.** A refresh the user asks for (pull, back online, a new historical date) and the scheduled refresh run under `revalidating()`: `Cache-Control: max-age=0`, so an unchanged answer comes back as a few-byte `304`. Other loads let a fresh cached answer stand (`util/Revalidation.kt`).
- **Back online.** When the connection returns, the converter fetches at once (`ConverterStatus`), after any refresh started offline has ended, and a failure from the outage stops counting, so "provider unreachable" drops immediately. Connectivity comes from `NetworkStatusLiveData`, which tracks validated networks from the callbacks rather than asking `ConnectivityManager` inside them, where the answer lags.
- **Unreachable clears** on the next successful refresh, a `304` included.
- **Timelines in pieces** (`timelineFetchRanges`): the tail since the last cached day, then what's missing before the first, a year at a time going back. The chart shows each piece as it lands and the window is stored after each, so a longer period reuses a shorter one and nothing is fetched twice. History is kept up to 10 years.

## Fallback and slow providers

The latest rates are asked of the main provider and the fallback at once (`ExchangeRatesRepository.fetchRates`); the main one's win when it answers, and the fallback's fetch still finishes into the cache. The timeline asks the fallback only after the main one fails (each answer can be years of history). Rates from the fallback are stored with the provider they stand in for (`ExchangeRates.fallbackFrom`). Every refresh tries the main provider first; if both fail, the main one's error is reported; offline, the fallback's answer isn't used.

Two things keep a struggling main provider from holding the screen: after a failure it's passed over for the fallback for ten minutes or until it answers again (`ProviderCooldown`, in memory), and a refresh someone waits on uses the fallback if the main one hasn't answered in 10 s (`MAIN_PROVIDER_PATIENCE`), letting the main request finish into the cache. The background refresh gives it the network's full limits.

## Background refresh

WorkManager, off by default (`worker/RateRefreshScheduler.kt`), following the provider's `PublicationSchedule`:

- A provider that publishes at a known time is refreshed once, 30 minutes after each publication, as one-off work that queues the next: Frankfurter (16:00 Berlin), Norges Bank (16:00 Oslo), Bank of Canada (16:30 Toronto), Bank of Israel and Bank Rossii (15:30 local) daily, InforEuro on the 1st (06:00 Brussels).
- One that updates many times a day (Open Exchangerates, AllRatesToday) follows **Refresh every** (15 min to 24 h, default 1 h, `RefreshInterval`); for scheduled providers that row is greyed out.
- It runs on an unmetered network with the battery not low; while rate alerts wait, on any network at any charge, since alerts fire only when rates arrive (`refreshConstraints`).

## Startup

`CurrenciesApplication`, on a background thread: resolves the selected provider's host through the shared client's own resolver (`client(null).dns.lookup`, so the answer lands in the cache its requests use; `AndroidDns` on Android 17+), best-effort and silent on failure; warms the stores; installs the crash recorder; and in debug builds StrictMode, which logs main-thread disk and network work and leaks rather than crashing. `ApiProvider.getHost()` exposes only the host name, never the base URL.
