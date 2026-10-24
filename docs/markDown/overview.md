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
| Exchange rate providers | 8 (Frankfurter.app with ECB data, Open Exchangerates, AllRatesToday, InforEuro, Bank of Canada, Norges Bank, Bank Rossii, Bank of Israel), plus a fallback provider used when the main one fails — see [api-providers.md](api-providers.md) |
| Converter and calculator | `+ − × ÷`, brackets and percent before converting; recent pairs; paste an amount from the clipboard; compare your cards' fees |
| Fee manager | Global exchange/bank fees plus per-pair overrides, with "true cost" alongside the mid-market rate |
| Historical rates | Convert at any past date's rates (back to 2010) |
| Rate chart | Week, month, year, 5 years or a custom range (up to 10 years), with min/max/average/median, scrubbing, and convert or set an alert at a touched day |
| Rate alerts | A notification when a pair reaches a rate, checked on the device after each update |
| Cart | Items in one currency converted into another, with quantities, tip / tax, split by person, budget, shopping-list mode, saved carts grouped into trips, receipt scanning (on-device OCR), and export as text, CSV, PDF or image |
| Quick conversions | The pair at round amounts scaled to the currency (or your own), with fees, to copy or share |
| Outside the app | "Convert currency" in any app's text-selection menu, launcher shortcuts, a Quick Settings tile, and a configurable home-screen widget |
| Backup & restore | Local export of settings via SAF, optionally encrypted with Argon2id + AES-256-GCM |
| Starred currencies | Favourites in your own order, with a starred-only filter |
| Look | Light, dark and pure-black themes or follow the system; colors from the wallpaper (Material You, on by default) |
| Large screens | Two panes on tablets and unfolded foldables; a one-handed keypad on wide screens |
| Privacy & security | Optional app lock (fingerprint / screen lock), private requests, nothing sent anywhere about you; crash reports stay on the phone until you share them |
| Background rate refresh | Optional, off by default — WorkManager, timed to each provider's publication |
| Accessibility | TalkBack labels and summaries, 48 dp targets, font scaling — see [accessibility-statement.md](accessibility-statement.md) |
| Internationalization | 33 languages (English plus 32 translations), every string translated in each (lint enforces it); most inherited from the upstream Currencies project. Hebrew, Arabic and Farsi lay out right to left where it reads better. The system's per-app language list is generated from the `res/values-*` folders (`generateLocaleConfig`), so it always matches them |

## Distribution

CurrenciX is built from source in this repository; there is no public store listing yet. Signed APKs are published as GitHub releases, starting with the first `v*` tag (see [build-and-flavors.md](build-and-flavors.md#post-quantum-signing-android-17)). The upstream **Currencies** app by Maximilian Salomon is available on Google Play (`play.google.com/store/apps/details?id=de.salomax.currencies`) and F-Droid (`f-droid.org/packages/de.salomax.currencies/`) under its original package name.

The `fdroid` build flavor excludes any Play-Store-specific APIs and is a reproducible-build target. The `play` flavor installs the receipt reader on demand through Play Feature Delivery.

## Privacy

The app requests `INTERNET`, `ACCESS_NETWORK_STATE` and `USE_BIOMETRIC` (normal permissions), and `POST_NOTIFICATIONS` only when the first rate alert is set. No analytics SDK, no crash-reporting service, no advertising ID access. Exchange rates are fetched directly from the providers' APIs. See [privacy-policy.md](privacy-policy.md) and [security.md](security.md).

## Version Scheme

Versions follow [Semantic Versioning](https://semver.org/). The Android `versionCode` is derived automatically: `1.23.0 → 12300`.
