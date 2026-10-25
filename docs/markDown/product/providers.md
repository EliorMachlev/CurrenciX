# Exchange rate providers

Picked in **Settings → Exchange rates → Data provider**; a change takes effect on the next refresh. How they're fetched and cached: [networking](../engineering/architecture/networking.md).

| Provider | Currencies | Updates | Base | Notes |
|---|---|---|---|---|
| [Frankfurter.app](https://frankfurter.dev/) | ~30 | Business days | EUR | European Central Bank data, back to 1999 |
| [OpenExchangerates](https://openexchangerates.org/) | 160+ | Hourly | USD | Needs a (free) API key |
| [AllRatesToday](https://allratestoday.com/) | ~160 | About every minute | EUR | Mid-market interbank; needs a key; the free plan is 300 requests ever |
| [InforEuro](https://commission.europa.eu/funding-tenders/procedures-guidelines-tenders/information-contractors-and-beneficiaries/exchange-rate-inforeuro_en) | ~150 | Monthly | EUR | EU accounting rates; unreliable (inaccurate data) |
| [Bank of Canada](https://www.bankofcanada.ca/rates/exchange/daily-exchange-rates/) | ~23 | Business days | CAD | |
| [Norges Bank](https://www.norges-bank.no/en/topics/Statistics/exchange_rates/) | ~40 | Business days | NOK | Unreliable (frequent downtime) |
| [Bank Rossii](https://cbr.ru/eng/currency_base/daily/) | ~44 | Business days | RUB | |
| [Bank of Israel](https://www.boi.org.il/en/economic-roles/statistics/foreign-exchange-market/exchange-rates/) | ~14 | Business days | ILS | |

Removed: fer.ee (domain gone; its id, 2, stays retired) and exchangerate.host (API shut down).

## Picker order

`ApiProvider.pickerOrder`: free providers under **Free**, then **Needs an API key**; within each, the more frequently updated first (`UpdateCadence`), then wider coverage and steadier service, which is the enum's order. Each row says how often it updates.

## Unreliable providers

Each provider has a `ProviderReliability`: `RELIABLE`, `DOWNTIME` (Norges Bank, with regular 503 outages) or `INACCURATE_DATA` (InforEuro's monthly accounting rates). Every picker hides unreliable ones unless **Show unreliable providers** is on (off by default), labelling them "Downtime" or "Inaccurate data", but always lists the provider already chosen, so a choice never vanishes from its own list. Nobody is switched away from one they already use.

## Fallback provider

**Settings → Exchange rates → Fallback provider** is used when the main one fails while online. Unset, it's the first free, reliable provider in picker order that isn't the main one (`defaultFallback`); the picker greys out the main provider. While its rates are on screen, the status pill says so ("Bank of Israel unavailable • Using Frankfurter.app") and names it as the source; every refresh tries the main provider first again.

## Historical rates

Every provider has some history. The converter's date picker reaches back to 2010 (`HISTORICAL_MIN_YEAR`), the chart's custom range ten years (`TIMELINE_MAX_YEARS`).

## Providers that need a key

Open Exchangerates and AllRatesToday take the user's own key, stored per provider and sealed ([security.md](../engineering/security.md#secrets)). Settings shows a key row, masked to its last four characters, for each provider in use that takes one, main or fallback; picking a keyed provider without a key, in any picker, goes straight to its key prompt. Without a key the provider fails at once, with no request. A 401, 403 or 429 reaches the user as "check your key", "your plan doesn't include this" or "you've used up your requests" (`explainKeyErrors`). AllRatesToday's description, hint and prompt say each refresh and chart range costs one request, and its refresh follows the user's interval.
