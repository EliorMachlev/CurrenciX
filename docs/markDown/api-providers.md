# Exchange Rate Providers

CurrenciX supports multiple exchange-rate data sources. The active provider is selected in **Settings → Exchange rates → Data provider**. Switching takes effect on the next refresh.

## Picker order

The picker (`ProviderPickerSheet`) lists `ApiProvider.pickerOrder`: free providers under **Free**, then the ones that need a key under **Needs an API key**; within each, the more frequently updated first (`UpdateCadence`: hourly, business-daily, monthly), then the more useful — wider coverage, steadier service — which is the enum's declaration order. Each row says how often it updates. Today that's: Frankfurter.app, Bank Rossii, Norges Bank, Bank of Canada, Bank of Israel, InforEuro; then OpenExchangerates and AllRatesToday. Reordering the enum is safe: only each entry's `id` is stored.

## Unreliable providers

Each `ApiProvider` carries a `ProviderReliability`: `RELIABLE`, `DOWNTIME` (often down — Norges Bank, with regular HTTP 503 outages) or `INACCURATE_DATA` (answers, but far from the market — InforEuro's monthly accounting rates). Every picker (Settings' provider and fallback, the converter's, the timeline's) lists `ApiProvider.listed(showUnreliable, keep)`: the unreliable ones only when **Settings → Exchange rates → Show unreliable providers** is on (`ProviderSettings.showUnreliable`, off by default), each labelled "Downtime" or "Inaccurate data" in the error color — but the provider already chosen (and the main provider, greyed out in the fallback picker) is always listed, so a choice never vanishes from its own list. The About row on **Providers** shows the same label. A user who already uses an unreliable provider keeps it; nothing is switched for them.

## Fallback provider

**Settings → Exchange rates → Fallback provider** picks a second provider for when the main one fails. The picker greys out the main provider (it can't stand in for itself); unset, or set to what later became the main provider, it's the first free, reliable provider in the picker order that isn't the main one (`ApiProvider.defaultFallback`).

When the main provider fails while the device is online (a timeout, an HTTP error, an error payload), `ExchangeRatesRepository` uses the fallback: rates and the timeline alike. For rates both are asked at once, so the fallback's answer is ready the moment the main one fails instead of only being requested then; when the main provider answers, its rates win and the fallback's fetch still finishes into the rate cache. The timeline asks the fallback only after the main provider fails, as each answer can be a year or more of history. Rates that came from the fallback are stored with the main provider they stand in for (`ExchangeRates.fallbackFrom`), so the converter shows "Bank of Israel unavailable • Using Frankfurter.app" and names the fallback as the source; tapping that pill explains it and offers to change provider. Every refresh tries the main provider first again. If the fallback fails too, the main provider's error is the one reported. Offline, the fallback's answer (a failure too) isn't used.

## Comparison

| Provider | Currencies | Update freq | Base | Notes |
|---|---|---|---|---|
| [Frankfurter.app](https://frankfurter.app/) | ~33 | Business days | EUR | European Central Bank data |
| [OpenExchangerates](https://openexchangerates.org/) | 160+ | Hourly | USD | Free tier requires API key |
| [AllRatesToday](https://allratestoday.com/) | ~160 | About every minute | EUR (asked for) | Mid-market interbank rates; needs an API key; the free plan is 300 requests in total, ever |
| [InforEuro](https://commission.europa.eu/funding-tenders/procedures-guidelines-tenders/information-contractors-and-beneficiaries/exchange-rate-inforeuro_en) | ~150 | Monthly | EUR | EU Commission accounting rates; unreliable (inaccurate data) |
| [Bank of Canada](https://www.bankofcanada.ca/rates/exchange/daily-exchange-rates/) | ~23 | Business days | CAD | Canadian Central Bank |
| [Norges Bank](https://www.norges-bank.no/en/topics/Statistics/exchange_rates/) | ~40 | Business days | NOK | Norwegian Central Bank; unreliable (downtime) |
| [Bank Rossii](https://cbr.ru/eng/currency_base/daily/) | ~44 | Business days | RUB | Russian Central Bank |
| [Bank of Israel](https://www.boi.org.il/en/economic-roles/statistics/foreign-exchange-market/exchange-rates/) | ~14 | Business days | ILS | Israeli Central Bank |

## Deactivated / Removed Providers

| Provider | Reason |
|---|---|
| fer.ee | Domain no longer resolves and its source repo is gone (it served the same ECB rates as Frankfurter.app). Its id, 2, stays retired |
| exchangerate.host | API shutdown |

## Cache behaviour

Every rate-provider request goes through a shared `OkHttpClient` with an
on-disk `Cache` (~5 MiB under `cacheDir/http-cache`). How each provider's
responses are cached depends on the upstream headers it returns:

| Provider | Host(s) | Cache mode | TTL | Rationale |
|---|---|---|---|---|
| Frankfurter.app | `api.frankfurter.dev` | Cooperative | Upstream `Cache-Control` | Publishes daily; upstream sends usable `max-age`. |
| OpenExchangerates | `openexchangerates.org` | Cooperative | Upstream `Cache-Control` | Publishes hourly; upstream sends usable `max-age`. |
| AllRatesToday | `allratestoday.com` | Cooperative | Upstream `Cache-Control` (60 s); the app's own rate cache keeps its answer an hour | Every request spends the user's quota. |
| InforEuro | `ec.europa.eu` | Rewritten | 6 h (`21600s`) | Rates change on the first of the month — leisurely TTL is fine. |
| Bank of Canada | `www.bankofcanada.ca` | Rewritten | 1 h (`3600s`) | Valet API sends `no-cache`; upstream cadence is business-day. |
| Norges Bank | `data.norges-bank.no` | Rewritten | 1 h (`3600s`) | SDMX endpoint headers unreliable; business-day cadence. |
| Bank Rossii | `www.cbr.ru` | Rewritten | 1 h (`3600s`) | XML feed omits usable cache headers; business-day cadence. |
| Bank of Israel | `boi.org.il`, `edge.boi.gov.il` | Rewritten | 1 h (`3600s`) | PublicApi + SDMX endpoints omit usable cache headers; business-day cadence. |

The "Rewritten" rows are handled by
`util/ProviderCacheRewriteInterceptor.kt`, which stamps
`Cache-Control: public, max-age=<TTL>` onto responses whose host matches a
hand-picked allowlist. The allowlist exists so we never accidentally poison
responses from unaudited hosts. Rewritten TTLs are deliberately *shorter* than
each provider's publish cadence — an offline app never serves cache that
outlives a working day.

In debug builds, Chucker surfaces `X-From-Cache` / `X-Response-Source` on
subsequent hits, which is the easiest way to confirm the cache is warm.

## Historical Rate Support

All active providers support historical rates to varying degrees. Frankfurter.app data goes back to **2010-01-04** (ECB reference start date). Bank Rossii and InforEuro have their own historical archives.

When a historical date is selected in the app, the provider's history endpoint is queried instead of the live-rates endpoint.

## Provider Implementation

Each provider is implemented as a class inside `app/src/main/kotlin/com/eliormachlev/currencix/model/provider/`. They all conform to the `Api` abstract class defined in `ApiProvider.kt`:

```kotlin
abstract suspend fun getRates(context: Context?, date: LocalDate?, secrets: ApiSecrets): Result<ExchangeRates>
abstract suspend fun getTimeline(context: Context?, base: Currency, symbol: Currency, startDate: LocalDate, endDate: LocalDate): Result<Timeline>
```

Response parsing is handled by provider-specific Moshi adapters (`model/adapter/`), SAX XML parsers (Norges Bank and Bank Rossii, which return XML), or `BankOfIsraelSdmxParser` (Bank of Israel's SDMX-JSON feed).

Fixed-shape JSON endpoints are declared as Retrofit interfaces in `model/provider/api/`; the XML and SDMX feeds call the shared OkHttp client directly. See [architecture.md](architecture.md#networking-one-okhttp-client-retrofit-for-fixed-shape-json-raw-okhttp-for-the-rest) for why the split is permanent.

## Network behavior

- **One client.** Every request goes through one OkHttp client (`HttpClientProvider`): the cached client, the cache-less one tests use and the OCR language download (`downloadClient`, no call time limit, no cache) are views of it (`newBuilder`), sharing its connections and threads.
- **Checking with the server.** A refresh the user asks for (pull to refresh, coming back online, a new historical date) and the scheduled refresh skip the rate cache's freshness window and run under `revalidating()`: each request carries `Cache-Control: max-age=0`, so OkHttp sends the cached answer's ETag or date (`If-None-Match` / `If-Modified-Since`) and an unchanged answer comes back as a `304` of a few bytes. Other loads let a fresh cached answer stand without a request (`util/Revalidation.kt`).
- **Back online.** When the connection returns after being lost, the converter fetches new rates at once (`ConverterStatus`), instead of waiting for a pull.
- **Timelines in pieces.** A timeline is fetched as date ranges (`timelineFetchRanges`): the tail since the last cached day, then whatever the cached window lacks before its first day, a year at a time going back. The chart shows each piece as it lands, and the window is stored after each one, so a longer period reuses what a shorter one fetched and never asks for a cached stretch again.
- **Background refresh.** Auto-refresh (off by default) follows the provider's `PublicationSchedule`. A provider that publishes at a known time is refreshed once, 30 minutes after each publication: one-off work timed to it (`RateRefreshScheduler`), which queues the next when it finishes. Today that's Frankfurter.app (16:00 Berlin), Norges Bank (16:00 Oslo), Bank of Canada (16:30 Toronto), Bank of Israel (15:30 Jerusalem) and Bank Rossii (15:30 Moscow) every day, and InforEuro on the 1st of each month (06:00 Brussels). A day with no new table costs one request that brings back the last one. Only a provider that updates many times a day (Open Exchangerates, AllRatesToday) follows **Settings → Exchange rates → Refresh every**: 15 minutes, 30 minutes, or 1, 3, 6, 12 or 24 hours, an hour by default (`RefreshInterval`, stored in minutes as `ProviderSettings.autoRefreshInterval`); slower intervals spend less of its request quota. For a scheduled provider that row is greyed out and says "Once a day / a month, just after <provider> publishes". The scheduled refresh runs on Wi-Fi (or another unmetered network) with the battery not low; while rate alerts are waiting it runs on any network at any charge, since alerts fire only when rates arrive (`RateRefreshScheduler.refreshConstraints`).

## Providers that need an API key

Open Exchangerates and AllRatesToday each take the user's own key, each stored under its own setting (`ProviderSettings.apiKeys`, `setApiKey`). Settings → Exchange rates shows a key row for every provider in use that takes one, the main provider's and the fallback's alike (`ApiProvider.apiKeyTexts`), the key masked to its last four characters. Picking a keyed provider that has no key yet, in any provider picker (Settings' main and fallback, the converter's, the chart's), goes straight on to its key prompt (`ProviderPickerWithKeyPrompt`). Every fetch, rates and timeline alike, gets all keys as `ApiSecrets`. Without a key a provider fails at once, without a request. A 401, 403 or 429 reaches the user as "check your key", "your plan doesn't include this" or "you've used up your requests" (`explainKeyErrors`).

**AllRatesToday** ([docs](https://allratestoday.com/docs), [OpenAPI spec](https://allratestoday.com/api/openapi.json)) is built from its published spec, against `GET /api/v1/rates`:

- Latest: `?source=EUR` with no target returns every rate from the euro. A past day: the same with `time=<day>T12:00:00Z`.
- Timeline: `?group=day&source=<base>&target=<symbol>&from=<start>&to=<end + 1 day>` (`to` is exclusive), one rate per day, FOK asked for as DKK.
- The key goes as `Authorization: Bearer art_live_…`, never in the URL, and debug builds' Chucker redacts that header.
- The free plan is 300 requests in total, never reset; paid plans have monthly quotas. Each refresh and each timeline range costs one, so the provider's description, hint and key prompt all say so, and its refresh follows the user's interval. Whether the free plan answers dated and range requests isn't stated in the spec: a 403 there shows as "your plan doesn't include this".
