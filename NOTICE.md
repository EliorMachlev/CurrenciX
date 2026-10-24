# NOTICE

**CurrenciX** is a fork of **Currencies** by Maximilian Salomon.

- Upstream project: <https://github.com/sal0max/currencies>
- Upstream copyright: © 2020 Maximilian Salomon
- Upstream license: GNU General Public License v3.0 or later (see [`COPYING`](COPYING))
- **Fork base commit**: [`2a4fde25`](https://github.com/sal0max/currencies/commit/2a4fde257ead7f65ca8bf411bb3890f59ba3890b) — "fix typo" by Maximilian Salomon, 2025-07-21 (one commit past upstream tag `1.23.0`)

## Modifications by the fork

Since **2026-07**, Elior Machlev maintains this fork, renamed **CurrenciX** on 2026-08-08 (#89), with the following changes relative to upstream (non-exhaustive summary — see the Git history for the authoritative record):

- Renamed the app: display name `Currencies` → `CurrenciX`; Android `applicationId` and `namespace` `de.salomax.currencies` → `com.eliormachlev.currencix`; Kotlin/Java package tree moved accordingly; a new brand mark, launcher icon and splash.
- Rewrote the UI in Jetpack Compose (Material 3) in a single Activity: a new converter and keypad, a cart, rate charts, settings, bottom-sheet prompts, two-pane layouts for tablets and foldables, and right-to-left layouts (#181).
- Added features: a fallback provider and two new providers (Bank of Israel, AllRatesToday), rate alerts, the cart (quantities, tip / tax, split, budget, trips, receipt scanning with on-device OCR, exports), quick conversions, "Convert currency" from other apps' text selection, a Quick Settings tile, launcher shortcuts, a configurable widget, an app lock, private requests, password-encrypted backups and on-device problem reports.
- Replaced the data layer: SharedPreferences → DataStore, Retrofit over one shared OkHttp client, an in-house rate cache, and WorkManager background refresh.
- Bundled a shared debug signing keystore so debug builds from any machine share a signature; release APKs carry a post-quantum hybrid signature (APK Signature Scheme v3.2).
- CI: lint, unit, screenshot and fuzz tests, static analysis and supply-chain checks (detekt, Qodana, Semgrep, CodeQL, Gitleaks, OpenSSF Scorecard), baseline profiles and benchmarks, and a tag-driven release workflow; `apk-artifact.yaml` stamps each debug APK's `versionName` with the short commit SHA and uploads it.
- Bulk dependency and CI-action upgrades.
- Documentation (`README.md`, `docs/**`) rebranded and rewritten, upstream attribution preserved.

## License

This fork continues to be distributed under **GPL-3.0-or-later**. The complete license text is in [`COPYING`](COPYING). Redistributing the app (source or binary) obliges you to comply with GPL-3.0 §5 and §6, including making the corresponding source available to recipients.

The upstream copyright notice and this fork's modification notice must both be preserved in derivative works.
