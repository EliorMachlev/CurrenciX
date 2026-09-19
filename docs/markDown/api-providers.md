# Exchange Rate Providers

CurrenciX supports multiple exchange-rate data sources. The active provider is selected in **Settings → Exchange rate provider**. Switching takes effect on the next refresh.

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

Each provider is implemented as an object inside `app/src/main/kotlin/com/eliormachlev/currencix/model/provider/`. They all conform to the `Api` abstract interface defined in `ApiProvider.kt`:

```kotlin
abstract fun getRates(base: Currency, date: LocalDate?): Call<ExchangeRates?>
abstract fun getTimeline(base: Currency, quote: Currency): Call<Timeline?>
```

Response parsing is handled by provider-specific Moshi adapters (`model/adapter/`) or SAX XML parsers (for Norges Bank and Bank Rossii, which return XML).
