# CurrenciX — Overview

**CurrenciX** is a simple, privacy-focused Android currency converter designed as a travel companion rather than a financial trading tool. It is a fork of the upstream [Currencies](https://github.com/sal0max/currencies) app by Maximilian Salomon.

- **Package**: `com.eliormachlev.currencix`
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 37
- **License**: GNU General Public License v3+
- **Language**: Kotlin

## What It Does

Convert between 30–160+ world currencies using live exchange rates fetched from your chosen provider. All conversions happen on-device with no ads, no analytics, and no user tracking.

## Core Features

| Feature | Details |
|---|---|
| Exchange rate providers | 7 active providers (ECB via Frankfurter, OER, InforEuro, Bank of Canada, Norges Bank, Bank Rossii, Bank of Israel) |
| Built-in calculator | Full arithmetic (+, −, ×, ÷) before conversion |
| Fee manager | Global exchange/bank fees plus per-pair overrides, with "true cost" alongside the mid-market rate |
| Historical rates | Access rates back to 2010 |
| Rate charts | 1-year historical timeline visualization with configurable overlays |
| Backup & restore | Local export of settings via SAF, optionally encrypted with Argon2id + AES-256-GCM |
| Starred currencies | Favourite/filter currencies for quick access |
| Themes | Light, dark, and pure-black modes; follows system setting |
| Foldable support | Adaptive multi-pane layout via WindowInfoTracker |
| Predictive back gesture | Opted in via `android:enableOnBackInvokedCallback` (API 33+) |
| DNS prewarm | Selected provider's host is resolved at app startup on a background thread |
| Internationalization | Inherited translations for 20+ languages from the upstream Currencies project |

## Distribution

CurrenciX is built from source in this repository; there is no public store listing. The upstream **Currencies** app by Maximilian Salomon is available on Google Play (`play.google.com/store/apps/details?id=de.salomax.currencies`) and F-Droid (`f-droid.org/packages/de.salomax.currencies/`) under its original package name.

The `fdroid` build flavor excludes any Play-Store-specific APIs and is reproducible.

## Privacy

The app requests only the `INTERNET` permission. No analytics SDK, no crash reporter, no advertising ID access. Exchange rates are fetched directly from public central-bank or open-data APIs.

## Version Scheme

Versions follow [Semantic Versioning](https://semver.org/). The Android `versionCode` is derived automatically: `1.23.0 → 12300`.
