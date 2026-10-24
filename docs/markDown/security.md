# Security

## Supply Chain

### Actions Pinning

Every GitHub Actions `uses:` reference is pinned to a full 40-character commit SHA, with the human-readable tag preserved in a trailing comment:

```yaml
uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7
```

This prevents a compromised or reused tag from injecting malicious code into CI. Enforced by the Semgrep rule `yaml.github-actions.security.github-actions-mutable-action-tag` and verified by the OpenSSF Scorecard `Pinned-Dependencies` check.

### Dependabot Cooldown

The Gradle, GitHub Actions and pip (the CI scripts' and Semgrep's `requirements.txt`) ecosystems all enforce a **7-day cooldown** (`cooldown: default-days: 7`) before Dependabot proposes a version bump. This mitigates supply-chain attacks where a dependency is compromised shortly after publication.

### Dependency Review

The `dependency-review` workflow blocks any PR that introduces a dependency with a **high or critical** CVE (CVSS ≥ 7).

### OWASP Dependency Check

Weekly scan against the NVD database of every library the fdroid release ships (`:app:owaspLibraries` copies them out of Gradle's cache, named by their coordinates). Fails the workflow on CVSS ≥ 7 findings. Also flags retired/archived libraries.

## Secrets

- Release signing credentials: locally in `secrets.properties` (gitignored, like `*.jks`, `*.p12` and `/signing/`); in CI only as secrets of the `release` environment, which only the release workflow's build job can read (see [build-and-flavors.md](build-and-flavors.md#post-quantum-signing-android-17)).
- No API keys are stored in source code. The keys a user enters for Open Exchangerates or AllRatesToday stay in the app's private storage, one per provider. Both go as an `Authorization` header (`Token …` for Open Exchangerates, `Bearer …` for AllRatesToday), never in a URL, so never in the app's log file or in the URLs the HTTP cache stores; debug builds' HTTP inspector redacts that header.
- Gitleaks scans the full repository on every PR, every push to `master`, and weekly (Mon 07:00 UTC) for accidental credential commits.

## User-initiated Backup

Users can export their settings from **Settings → Backup & Restore** to a location of their choosing via the Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`). The backup file contains three DataStore preference namespaces (`prefs`, `last_state`, `starred_currencies`; `PersistenceKey.backupNamespaces`) and the saved carts, trips, fees and rate alerts (`carts`, `fees`, `alerts`; `SAVED_DATA_SECTIONS`, written as JSON from their Protobuf files), as versioned JSON: settings and any API keys entered, the converter's state, the starred currencies, the quick-conversion amounts and receipt-language choices (in `prefs`), the carts and trips, the fees, and the rate alerts. All of it is inside the encrypted block when a password is set. The cached rates and rate histories are deliberately excluded.

The app requests **no storage permission** — the SAF picker returns a scoped `content://` URI that the user has explicitly granted for that single file.

### Optional password encryption

At export time the user may tick **Encrypt with a password**. When set:

- Password → 256-bit AES key via **Argon2id v1.3** (`m=32 MiB, t=3, p=1`) with a random 32-byte salt. Argon2id is memory-hard, which neutralises the √N speedup a quantum attacker gets from Grover's algorithm — parallelism gains don't help when memory bandwidth dominates.
- The `namespaces` block is encrypted with **AES-256-GCM** (Tink's `AesGcmJce`, in `BackupCrypto`) using a random 12-byte IV and 128-bit auth tag. AES-256 is itself quantum-resistant: Grover reduces effective security to ~128 bits, which remains out of reach.
- KDF, cipher, and Argon2 cost parameters (`memoryKib`, `iterations`, `parallelism`) are recorded in the wrapper JSON so future readers can reject unknown algorithms instead of silently mis-decrypting.
- Passwords are held as `CharArray` and zeroed by both UI and manager after use; UTF-8 encoding for Argon2 goes through NIO to avoid a `String` allocation that would linger in the pool.

On import the app detects the `encryption` block, prompts for the password, and re-prompts on GCM tag failure. Plaintext files still import unchanged; an earlier revision that used PBKDF2-HMAC-SHA256 (210 000 iterations) is still readable via a fallback branch keyed on the file's `kdf` field.

Reading the file, deriving the key and writing the namespaces back all run off the main thread (`BackupManager`'s calls are `suspend`, on the IO dispatcher), with a progress bar on the screen meanwhile, so a slow phone or a big file can't freeze the app; the restart after a restore waits until every namespace is on disk.

Automatic Android backup remains disabled (`android:allowBackup="false"`) — user-initiated export is the only supported path off-device.

## Crash and freeze reports

Nothing about crashes or freezes leaves the phone by itself. Crash stack traces are written to the app's own private storage (`filesDir/problems/`, the latest five), and Android's record of freezes and native crashes is read on the phone; **About this app → Report a problem** shares them, with the app and Android versions and the phone model, only when the user taps it and picks where to send them. The traces hold code locations, not the user's data.

## Runtime Permissions

The app declares these permissions (all normal, install-time ones except `POST_NOTIFICATIONS`, which is asked for at runtime):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
```

Noticing travel reads the mobile network's country (`TelephonyManager.networkCountryIso`), which needs no permission; it isn't stored beyond the last country asked about, nor sent anywhere. `ACCESS_NETWORK_STATE` only lets the app check connectivity before firing a request; it grants no access to network identifiers or traffic content. `POST_NOTIFICATIONS` is for rate alerts only: it's requested at runtime when the user sets their first alert (never at launch), and alerts are evaluated on the device against the rates already downloaded, so they add no network traffic or data. Denying it leaves the alerts waiting; nothing else in the app needs it. `USE_BIOMETRIC` lets the optional app lock show the system's biometric / screen-lock prompt (`BiometricPrompt`); the prompt is the system's, and the app learns only whether it succeeded. No location, contacts, storage, camera, or microphone access (receipt photos come from the system camera app or photo picker, which need no permission).

## App lock and private requests

Settings → Security & privacy has two switches, both off by default (`PrivacySettings`):

- **App lock** (`view/lock/AppLock.kt`, `LockGate`): with it on, the app is covered by a lock screen when it starts and when it comes back after more than a minute away, until the person passes the system prompt (`BiometricPrompt`, strong biometrics or the phone's screen lock, `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`). The minute's grace lets the camera (receipts) and the file pickers (backup, export) come and go without asking again. The screens stay composed underneath but can't be seen, touched or read by TalkBack, and Recents shows no picture of the app (`setRecentsScreenshotEnabled(false)`); screenshots still work. Turning it on asks for the prompt first, so nobody locks themselves out; without a screen lock on the phone the switch is disabled and nothing locks (a restored backup with it on included). The text-selection converter, the widget and the Quick Settings tile stay outside the lock.
- **Private requests** (`util/PrivateRequests.kt`): every request leaves the phone the same as everyone else's: the `User-Agent` is a fixed `CurrenciX` (no app or OkHttp version, nothing about the phone) and no cache identifiers are sent (`If-None-Match` / `If-Modified-Since`, which a server could make unique per phone and read back). A network interceptor, innermost, so it shapes exactly what goes out. The cost: an unchanged answer comes back in full instead of as a `304`. Not covered: the IP address, the TLS handshake (Android's own, shared by most apps; on Android 17+ it hides the host name where the host supports it, see Encrypted Client Hello below) and when requests are made.

## Network Security

`res/xml/network_security_config.xml` refuses cleartext HTTP globally. Every configured exchange-rate provider serves HTTPS only, so the config closes off downgrade / MITM avenues without breaking real traffic.

**Forward secrecy.** The shared client (`HttpClientProvider`) accepts only TLS 1.3 and TLS 1.2 with an ECDHE key exchange and an AEAD cipher (`FORWARD_SECRET_TLS`, checked by `ForwardSecrecyTest`), so each connection's keys are ephemeral and a provider's private key, if it ever leaked, couldn't decrypt recorded traffic. OkHttp's default would also accept RSA key exchange and 3DES as a fallback; every provider offers TLS 1.3 or ECDHE, so nothing needs them.

**Encrypted Client Hello (Android 17+).** The TLS handshake normally names the host in the clear (SNI), so anyone on the network can see which provider the app asks for rates, HTTPS or not. On Android 17 and later the shared client resolves through the platform's `DnsResolver` (OkHttp's `AndroidDns`, `HttpClientProvider.encryptedClientHelloDns`), which also fetches the host's DNS `HTTPS` record with its ECH keys, and the handshake then encrypts the host name. It covers the hosts that publish ECH keys (typically those behind Cloudflare); the others connect as before, as ECH isn't enforced in the network security config, which would make them fail. Earlier Android versions can't do ECH and keep the system resolver. The IP address stays visible either way.

Receipt scanning adds one host: `raw.githubusercontent.com`, for the OCR language data (`tessdata_fast` at its fixed `4.1.0` tag), fetched once per language, and only after the user agrees to that language by name and size (English with the first scan's offer; the app's own language, or a trip's, as a separate opt-in, remembered either way in `ReceiptLanguageSettings.allowed` / `declined`). No language ships with the app. Settings → Receipt languages lists what's on the phone; each can be deleted, or kept only until a date and time and then deleted by `TessdataExpiryWorker`. In the Play app the recognizer itself comes from Google Play as an on-demand feature module (Play Feature Delivery): code is only ever installed by Play, never downloaded by the app. The request carries nothing about the user (a fixed file URL). Each file is streamed to a temporary file, hashed, and kept only if its SHA-256 matches the one compiled into the app (`TessLanguage.sha256`, `Tessdata.install`); a mismatch is discarded and not retried. Receipt photos are read on the device and never uploaded; the camera photo is written to the app's cache through its `FileProvider` and deleted once read. The app has no camera permission: the photo comes from the system camera app (`TakePicture`) or the system photo picker.

## APK signing

Release APKs are signed with the release key (`KEYSTORE_*` in `secrets.properties`) and, once its keys are made, with Android 17's post-quantum hybrid signature too (APK Signature Scheme v3.2: a classical key plus ML-DSA-65), which Android 17+ verifies so a future quantum computer couldn't forge an update. Older Android versions verify the release key as before. How to set it up, and why its keys must never be lost: [build-and-flavors.md](build-and-flavors.md#post-quantum-signing-android-17). F-Droid signs its builds with its own key and Play re-signs bundles, so neither changes.

## Backups

`android:allowBackup="false"` and `android:fullBackupContent="false"` are set in the manifest, backed by an explicit `res/xml/data_extraction_rules.xml` that excludes every domain (`root`, `database`, `sharedpref`, `external`, `file`) from both **cloud backups** and **device-to-device transfers** on Android 12+.

Rationale: the app's DataStore files contain the user's API keys for Open Exchangerates or AllRatesToday (if any), their fees and carts, and starred currencies — none of which should leak into an off-device Google backup by default. Users who want backups get an in-app, opt-in mechanism (see the Backup feature in Settings) with optional password encryption.

## Code Analysis

| Tool | Scope | Frequency |
|---|---|---|
| Detekt | Kotlin source (app, helpers, ocr-engine, baselineprofile; production and tests) | Every PR and master push |
| Android Lint | App code, resources and manifest, per flavor (fdroid, play) | Every PR and master push |
| Qodana JVM Community | Kotlin / Java | Every PR, master push, weekly |
| CodeQL | GitHub Actions YAML | Every PR, master push, weekly |
| Semgrep | Security patterns | Every PR, master push, weekly |
| Gitleaks | Secret / credential scanning | Every PR, master push, weekly |
| Jazzer fuzzing | Parsers of text the app doesn't control (selected text, receipt scans, calculations, codes, amounts) | Every PR and master push, two minutes per parser |

## OpenSSF Scorecard

The repository is evaluated by the [OpenSSF Scorecard](https://securityscorecards.dev/) on every push to `master` and weekly. Results are uploaded to the GitHub Security tab, and open findings show there as code scanning alerts. Key checks:

- **Pinned-Dependencies** — every CI action uses a SHA pin; the CI scripts' Python packages and Semgrep itself (with every package it needs) are installed with `--require-hashes`, from `.github/scripts/requirements.txt` and `.github/semgrep/requirements.txt`
- **Branch-Protection** — master branch is protected
- **SAST** — multiple static analysis tools are active
- **Dependency-Update-Tool** — Dependabot is configured
- **Token-Permissions** — workflows use least-privilege permissions
