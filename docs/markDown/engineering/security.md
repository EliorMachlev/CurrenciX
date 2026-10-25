# Security

How each scanner and workflow is configured is in [ci-cd.md](ci-cd.md); this is what protects what, and why.

## Supply chain

- **Pinned actions.** Every `uses:` is a full commit SHA, so a moved or compromised tag can't inject code; Semgrep's `github-actions-mutable-action-tag` and Scorecard's Pinned-Dependencies check it. CI's Python packages, Semgrep included, install with `--require-hashes`.
- **Dependency verification.** Gradle refuses any downloaded file whose SHA-256 isn't in `gradle/verification-metadata.xml`, so a tampered or swapped artifact fails the build. The file is regenerated, never hand-edited ([build.md](build.md#dependency-verification)). Signatures aren't checked: many artifacts are unsigned, and a pinned checksum is the stronger check for a fixed version.
- **Wrapper.** `gradle-wrapper.jar` is validated against Gradle's releases on every PR.
- **New and known vulnerabilities.** Dependabot waits 7 days after a release (a compromise is often caught in that window); dependency review blocks a PR adding a CVSS ≥ 7 dependency; OSV-Scanner checks the release SBOM on every PR; Dependency-Check scans the shipped libraries weekly.
- **Release provenance.** Each APK and the SBOM are attested (`actions/attest-build-provenance`, on Sigstore's transparency log); `gh attestation verify <file> --repo EliorMachlev/CurrenciX` checks a download.
- **Code analysis.** detekt, Lint, Qodana, CodeQL (data flow from intents and other input into files, URLs and logs), Semgrep and Gitleaks on every PR; Jazzer fuzzes every parser of outside input.

## Secrets

- **Signing keys** are local in `secrets.properties` (git-ignored with `*.jks`, `*.p12`, `/signing/`) and in CI only as `release` environment secrets, readable by the release build job alone ([release.md](release.md)).
- **No API keys in source.** A user's key for Open Exchangerates or AllRatesToday is stored per provider, sealed with AES-256-GCM under an Android Keystore key (`KeystoreSecretBox`, via Tink's `AndroidKeystore` AEAD, which draws a fresh nonce per seal). The key never leaves the Keystore, so a copy of the app's files, a rooted phone or a device transfer gets nothing usable; if the Keystore key is lost, the API key reads as missing and is entered again. Keys travel in the `Authorization` header, never a URL, so never in logs or cached URLs; Chucker redacts the header.
- **Gitleaks** scans the repository on every PR, every `master` push and weekly.

## Backups

Android backup is off (`allowBackup="false"`, `fullBackupContent="false"`, and `data_extraction_rules.xml` excluding every domain from cloud backup and device transfer), since the stores hold API keys, fees and carts. The only way data leaves is the user's own export:

- **Settings → Backup & Restore** writes versioned JSON through the Storage Access Framework (no storage permission): the `prefs`, `last_state` and `starred_currencies` namespaces and the carts, trips, fees and alerts. API keys are included only in a password-protected backup (the prompt says so) and sealed again for the phone on restore. Cached rates and histories are left out.
- **Encrypt with a password:** Argon2id v1.3 (`m=32 MiB, t=3, p=1`, random 32-byte salt) → a 256-bit key; the payload is AES-256-GCM (Tink's `AesGcmJce`, random 12-byte IV, 128-bit tag). Both are quantum-resistant: Argon2id is memory-hard, so Grover's speed-up and parallelism don't help, and AES-256 keeps ~128-bit security under Grover. Algorithms and costs are recorded in the file, and a file naming any other is refused. Passwords stay `CharArray`s, encoded through NIO (no lingering `String`) and zeroed after use. A wrong password re-prompts on the GCM tag failure.
- All of it runs off the main thread, with progress shown; the restart after a restore waits until every namespace is on disk.

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE` and `USE_BIOMETRIC` (install-time), and `POST_NOTIFICATIONS`, asked for only when the first rate alert is set; denying it leaves alerts waiting. No location, contacts, storage, camera or microphone: receipt photos come from the system camera app or photo picker. Noticing travel reads the mobile network's country, which needs no permission and isn't sent anywhere. The biometric prompt is the system's; the app learns only success or failure.

## Privacy switches

Settings → Security & privacy, all off by default (`PrivacySettings`):

- **App lock** (`AppLock`, `LockGate`): a lock screen at start and after more than a minute away (the grace lets the camera and file pickers come and go), passed with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`. Screens underneath can't be seen, touched or read by TalkBack, and Recents shows no picture. Turning it on asks for the prompt first, so nobody locks themselves out; without a phone screen lock it's disabled. The text-selection sheet, widget and tile stay outside it.
- **Block screenshots:** `FLAG_SECURE` on the converter's and text-selection sheet's windows, blanking screenshots, recordings, casting and Recents.
- **Private requests** (`PrivateRequests`): every request leaves the phone like everyone else's: `User-Agent: CurrenciX` (no versions, nothing about the phone) and no `If-None-Match` / `If-Modified-Since`, which a server could make unique per phone. An innermost network interceptor, so it shapes exactly what goes out; the cost is full answers instead of `304`s. Not covered: the IP address, the TLS handshake, and when requests are made.

## Network

- **HTTPS only:** `network_security_config.xml` refuses cleartext everywhere.
- **Certificate Transparency** is on in the same config: a certificate mis-issued for a provider's host by any trusted CA, but missing from the public logs, is refused. Android 17 does this by default; Android 16 when asked, as here.
- **Only known hosts:** `AllowedHostsInterceptor` refuses any host that isn't a provider's (`ApiProvider.allHosts`) or the OCR language host, at every redirect hop, so a bug or redirect can't send a request or a key elsewhere (`AllowedHostsTest`).
- **Forward secrecy:** TLS 1.3, or 1.2 with ECDHE and an AEAD cipher (`FORWARD_SECRET_TLS`, `ForwardSecrecyTest`), so a leaked server key can't decrypt recorded traffic. Every provider supports it, so OkHttp's RSA and 3DES fallbacks aren't needed.
- **Encrypted Client Hello (Android 17+):** the client resolves through the platform's `DnsResolver` (`AndroidDns`), which fetches a host's ECH keys, so the handshake hides the host name where the host publishes keys; others connect as before (ECH isn't enforced, which would make them fail). The IP stays visible.
- **OCR language data** is the one extra host (`raw.githubusercontent.com`, tessdata_fast at its fixed 4.1.0 tag), fetched only after the user agrees to that language by name and size. Each file is streamed to a temporary file and kept only if its SHA-256 matches the one compiled into the app; a mismatch is discarded. In the Play app the recognizer comes from Play Feature Delivery: code is only ever installed by Play. Photos are read on the phone, never uploaded, and deleted once read.

## Signing

Release APKs are signed with the release key and Android 17's post-quantum hybrid signature (v3.2: a classical key plus ML-DSA-65), so a future quantum computer couldn't forge an update on Android 17+; older versions verify the release key. Setup and why the keys must never be lost: [release.md](release.md#post-quantum-signing-android-17).

## Crash reports

Nothing about crashes leaves the phone by itself. Traces stay in private storage (latest five) and hold code locations, not the user's data; **Report a problem** shares them only when the user taps it and picks where.
