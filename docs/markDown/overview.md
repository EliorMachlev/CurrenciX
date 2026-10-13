# CurrenciX — Overview

**CurrenciX** is a simple, privacy-focused Android currency converter designed as a travel companion rather than a financial trading tool. It is a fork of the upstream [Currencies](https://github.com/sal0max/currencies) app by Maximilian Salomon.

- **Package**: `com.eliormachlev.currencix`
- **Min SDK**: 33 (Android 13)
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
| Cart (bill splitting) | Multi-item running total in one currency, converted against a chosen pair — a full-Compose screen, separate from the converter |
| Fee manager | Global exchange/bank fees plus per-pair overrides, with "true cost" alongside the mid-market rate |
| Historical rates | Access rates back to 2010 |
| Rate charts | 1-year historical timeline visualization with configurable overlays |
| Backup & restore | Local export of settings via SAF, optionally encrypted with Argon2id + AES-256-GCM |
| Starred currencies | Favourite/filter currencies for quick access |
| Themes | Light, dark, and pure-black modes; follows system setting |
| Foldable support | Adaptive multi-pane layout via WindowInfoTracker |
| Predictive back gesture | Opted in via `android:enableOnBackInvokedCallback` (API 33+) |
| Background rate refresh | Optional, off by default — periodic refresh via WorkManager with a provider-aware TTL |
| Home-screen widget | Glance-based widget showing a chosen currency pair's rate |
| DNS prewarm | Selected provider's host is resolved at app startup on a background thread |
| Internationalization | 31 locales, every string translated in each (lint enforces it); most inherited from the upstream Currencies project. The system's per-app language list is generated from the `res/values-*` folders (`generateLocaleConfig`), so it always matches them |

## Distribution

CurrenciX is built from source in this repository; there is no public store listing. The upstream **Currencies** app by Maximilian Salomon is available on Google Play (`play.google.com/store/apps/details?id=de.salomax.currencies`) and F-Droid (`f-droid.org/packages/de.salomax.currencies/`) under its original package name.

The `fdroid` build flavor excludes any Play-Store-specific APIs and is reproducible.

## Privacy

The app requests only `INTERNET` and `ACCESS_NETWORK_STATE` (both normal, non-dangerous permissions). No analytics SDK, no crash reporter, no advertising ID access. Exchange rates are fetched directly from public central-bank or open-data APIs.

## Version Scheme

Versions follow [Semantic Versioning](https://semver.org/). The Android `versionCode` is derived automatically: `1.23.0 → 12300`.
