# NOTICE

**CurrenciX** is a fork of **Currencies** by Maximilian Salomon.

- Upstream project: <https://github.com/sal0max/currencies>
- Upstream copyright: © 2020 Maximilian Salomon
- Upstream license: GNU General Public License v3.0 or later (see [`COPYING`](COPYING))
- **Fork base commit**: [`2a4fde25`](https://github.com/sal0max/currencies/commit/2a4fde257ead7f65ca8bf411bb3890f59ba3890b) — "fix typo" by Maximilian Salomon, 2025-07-21 (one commit past upstream tag `1.23.0`)

## Modifications by the fork

Starting **2026-08**, Elior Machlev maintains this fork under the name **CurrenciX** with the following changes relative to upstream (non-exhaustive summary — see the Git history for the authoritative record):

- Renamed the app: display name `Currencies` → `CurrenciX`; Android `applicationId` and `namespace` `de.salomax.currencies` → `com.eliormachlev.currencix`; Kotlin/Java package tree moved accordingly.
- Bundled a shared debug signing keystore so debug builds from any machine share a signature.
- CI (`.github/workflows/apk-artifact.yaml`) stamps each debug APK's `versionName` with the short commit SHA and uploads the artifact.
- Bulk dependency and CI-action upgrades.
- Documentation (`README.md`, `docs/**`) rebranded, upstream attribution preserved.

## License

This fork continues to be distributed under **GPL-3.0-or-later**. The complete license text is in [`COPYING`](COPYING). Redistributing the app (source or binary) obliges you to comply with GPL-3.0 §5 and §6, including making the corresponding source available to recipients.

The upstream copyright notice and this fork's modification notice must both be preserved in derivative works.
