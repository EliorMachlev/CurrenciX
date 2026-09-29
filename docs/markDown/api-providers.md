# Exchange Rate Providers

CurrenciX supports multiple exchange-rate data sources. The active provider is selected in **Settings → Exchange rate provider**. Switching takes effect on the next refresh.

## Picker order

The picker (`ProviderPickerDialog`) lists `ApiProvider.pickerOrder`: free providers under **Free**, then the ones that need a key under **Needs an API key**; within each, the more frequently updated first (`UpdateCadence`: hourly, business-daily, monthly), then the more useful — wider coverage, steadier service — which is the enum's declaration order. Each row says how often it updates. Today that's: Frankfurter.app, Bank Rossii, Norges Bank, Bank of Canada, Bank of Israel, InforEuro; then OpenExchangerates. Reordering the enum is safe: only each entry's `id` is stored.

## Fallback provider

**Settings → Fallback provider** picks a second provider for when the main one fails. The picker greys out the main provider (it can't stand in for itself); unset, or set to what later became the main provider, it's the first free provider in the picker order that isn't the main one (`ApiProvider.defaultFallback`).

When the main provider fails while the device is online (a timeout, an HTTP error, an error payload), `ExchangeRatesRepository` fetches from the fallback: rates and the timeline alike. Rates that came from the fallback are stored with the main provider they stand in for (`ExchangeRates.fallbackFrom`), so the converter shows "Bank of Israel unavailable • Using Frankfurter.app" and names the fallback as the source. If the fallback fails too, the main provider's error is the one reported. Offline, there's no point asking a second provider, so it isn't tried.

## Comparison

| Provider | Currencies | Update freq | Base | Notes |
|---|---|---|---|---|
| [Frankfurter.app](https://frankfurter.app/) | ~33 | Business days | EUR | European Central Bank data |
| [OpenExchangerates](https://openexchangerates.org/) | 160+ | Hourly | USD | Free tier requires API key |
| [InforEuro](https://commission.europa.eu/funding-tenders/procedures-guidelines-tenders/information-contractors-and-beneficiaries/exchange-rate-inforeuro_en) | ~150 | Monthly | EUR | EU Commission accounting rates |
| [Bank of Canada](https://www.bankofcanada.ca/rates/exchange/daily-exchange-rates/) | ~23 | Business days | CAD | Canadian Central Bank |
| [Norges Bank](https://www.norges-bank.no/en/topics/Statistics/exchange_rates/) | ~40 | Business days | NOK | Norwegian Central Bank |
| [Bank Rossii](https://cbr.ru/eng/currency_base/daily/) | ~44 | Business days | RUB | Russian Central Bank |
| [Bank of Israel](https://www.boi.org.il/en/economic-roles/statistics/foreign-exchange-market/exchange-rates/) | ~14 | Business days | ILS | Israeli Central Bank |

## Deactivated / Removed Providers

| Provider | Reason |
|---|---|
| fer.ee | Persistent API instability |
| exchangerate.host | API shutdown |

## Cache behaviour

Every rate-provider request goes through a shared `OkHttpClient` with an
on-disk `Cache` (~5 MiB under `cacheDir/http-cache`). How each provider's
responses are cached depends on the upstream headers it returns:

| Provider | Host(s) | Cache mode | TTL | Rationale |
|---|---|---|---|---|
| Frankfurter.app | `api.frankfurter.dev` | Cooperative | Upstream `Cache-Control` | Publishes daily; upstream sends usable `max-age`. |
| OpenExchangerates | `openexchangerates.org` | Cooperative | Upstream `Cache-Control` | Publishes hourly; upstream sends usable `max-age`. |
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
