# CurrenciX

<div align="right">
   <img alt="build status" height="20" src="https://github.com/EliorMachlev/CurrenciX/actions/workflows/build.yaml/badge.svg">
</div>

<div align="center">
   <img alt="Logo" height="200" src="art/ic_launcher/ic_launcher_foreground.svg">
</div>

**CurrenciX** is a [simple](https://en.wikipedia.org/wiki/KISS_principle) currency converter — a handy travel companion, not a tool for realtime financial business.

It's a fork of [Currencies](https://github.com/sal0max/currencies) by Maximilian Salomon. See [NOTICE.md](NOTICE.md) for a summary of what changed in the fork.

## Features

* Multiple exchange rate providers to choose from:
   * [frankfurter.app](https://frankfurter.app/) — 30+ currencies, provided by the European Central Bank
   * [OpenExchangerates](https://openexchangerates.org/) — 160+ currencies with hourly updates (requires a free API key)
   * [InforEuro](https://commission.europa.eu/funding-tenders/procedures-guidelines-tenders/information-contractors-and-beneficiaries/exchange-rate-inforeuro_en) — the European Commission's monthly accounting rates for 150+ pairs
   * [Bank of Canada](https://www.bankofcanada.ca/rates/exchange/daily-exchange-rates/) — ~23 CAD rates
   * [Norges Bank](https://www.norges-bank.no/en/topics/Statistics/exchange_rates/) (Norway) — ~40 rates
   * [Bank Rossii](https://cbr.ru/eng/currency_base/daily/) (Russia) — ~44 rates against the Ruble
* Built-in calculator for on-the-fly conversions (e.g. splitting a restaurant bill).
* Fee manager: global exchange/bank fees plus per-pair overrides — see the "true cost" alongside the mid-market rate.
* Rate-history chart with configurable overlays (grid, axis labels, min/max highlights).
* Historical rates: convert against rates from any prior date.
* Encrypted local backup & restore of settings.
* Material 3 UI with light, dark, and OLED themes.
* Ad-free and telemetry-free.

Written in Kotlin, min SDK 26 (Android 8.0), targeting current Android.

## Screenshots

<div align="center">
   <img src="art/screenshots/screen01.png" width="45%" alt="screenshot 1">
   <img src="art/screenshots/screen03.png" width="45%" alt="screenshot 2">
</div>

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md). Deeper docs — architecture, build flavors, CI/CD, security — live in [`docs/markDown/`](docs/markDown/).

## Legal & compliance

- [Privacy Policy](docs/markDown/privacy-policy.md) — the app collects no personal data; see the policy for the per-jurisdiction posture (GDPR, UK GDPR, CCPA family, LGPD, PIPEDA, APPI, POPIA, PDPA, PIPL, Australia Privacy Act, Israeli PPL incl. Amendment 13, COPPA, ePrivacy).
- [Terms of Service](docs/markDown/terms-of-service.md) — usage terms, warranty disclaimer, rate-accuracy notice. Governed by the laws of Israel.
- [Accessibility Statement](docs/markDown/accessibility-statement.md) — WCAG 2.1 AA target covering IS 5568, EN 301 549 / EAA, ADA / §508.
- [Compliance Posture](docs/markDown/compliance-posture.md) — applicability under EU AI Act, DSA/DMA, financial-services regimes, EAR / EU Dual-Use, EU CRA, Play Data Safety, F-Droid.
- [SECURITY.md](SECURITY.md) — vulnerability reporting, coordinated disclosure, support period, SBOM.

## License

Copyright © 2020 Maximilian Salomon (upstream Currencies) · CurrenciX fork © 2026 Elior Machlev.

Licensed under [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html).
