# Security

How each scanner and workflow is configured is in [ci-cd.md](ci-cd.md); this is what protects what, and why.

## Supply chain

- **Pinned actions.** Every `uses:` is a full commit SHA, so a moved or compromised tag can't inject code; Semgrep's `github-actions-mutable-action-tag` and Scorecard's Pinned-Dependencies check it. CI's Python packages, Semgrep included, install with `--require-hashes`.
- **Dependency verification.** Gradle refuses any downloaded file whose SHA-256 isn't in [`gradle/verification-metadata.xml`](../../../gradle/verification-metadata.xml), so a tampered or swapped artifact fails the build. The file is regenerated, never hand-edited ([build.md](build.md#dependency-verification)). Signatures aren't checked: many artifacts are unsigned, and a pinned checksum is the stronger check for a fixed version.
- **Wrapper.** [`gradle-wrapper.jar`](../../../gradle/wrapper/gradle-wrapper.jar) is validated against Gradle's releases on every PR.
- **Runner activity.** Every job starts with StepSecurity's Harden-Runner in audit mode: it records each outbound connection, process and file the job changes, linked from the job's log. It blocks none of the job's own traffic; the one exception is StepSecurity's global block list of known-malicious hosts, which Harden-Runner enforces in audit mode too, so a job failing on a connection there is that list at work. Once the audits show which hosts each workflow needs, a job can switch to `egress-policy: block` with those hosts listed, so a compromised tool can't send anything elsewhere.
- **New and known vulnerabilities.** Dependabot waits 7 days after a release (a compromise is often caught in that window); dependency review blocks a PR adding a CVSS ≥ 7 dependency; OSV-Scanner checks the release SBOM on every push to `Alpha` and every promotion PR (into `PreRelease` or `Release`); the build tools' own vulnerable libraries are raised to their fixes ([build.md](build.md#build-tools-own-libraries)); Dependency-Check scans the shipped libraries twice a week.
- **Reproducible builds.** [`apk-checks.yaml`](../../../.github/workflows/apk-checks.yaml) builds the unsigned fdroid release twice, from two checkouts with no build cache, and fails if any APK differs, so a change that makes the build depend on where or when it runs is caught before F-Droid's own rebuild would be ([ci-cd.md](ci-cd.md#workflows)).
- **Release provenance.** Each APK and both SBOMs are attested (`actions/attest-build-provenance`, on Sigstore's transparency log); `gh attestation verify <file> --repo EliorMachlev/CurrenciX --signer-workflow EliorMachlev/CurrenciX/.github/workflows/build-release.yaml` checks a download. The build and its attestation run in a reusable workflow, so the provenance names that workflow as the builder, and the job that holds the signing keys can't sign provenance. The attestations are also attached to the release as a provenance bundle, which is what OpenSSF Scorecard's Signed-Releases check counts as provenance.
- **Code analysis.** detekt, Lint, Qodana and CodeQL (data flow from intents and other input into files, URLs and logs) on every push to `Alpha` and every promotion PR, Semgrep and Gitleaks on every PR; Jazzer fuzzes every parser of outside input.
- **Workflow analysis.** jactionlint checks every workflow change, and CodeQL's `actions` analysis and zizmor look for template injection, leaked credentials, cache poisoning and over-broad permissions; zizmor fails on a medium or worse finding ([ci-cd.md](ci-cd.md#workflows)).

## Secrets

- **Signing keys** are local in `secrets.properties` (git-ignored with `*.jks`, `*.p12`, `/signing/`) and in CI only as `Release` environment secrets, readable by the release build job alone ([release.md](release.md)).
- **No API keys in source.** A user's key for Open Exchangerates or AllRatesToday is stored per provider, sealed with AES-256-GCM under an Android Keystore key (`KeystoreSecretBox`, via Tink's `AndroidKeystore` AEAD, which draws a fresh nonce per seal). The key never leaves the Keystore, so a copy of the app's files, a rooted phone or a device transfer gets nothing usable; if the Keystore key is lost, the API key reads as missing and is entered again. Keys travel in the `Authorization` header, never a URL, so never in logs or cached URLs; Chucker redacts the header.
- **Gitleaks** scans the repository on every PR, every push to `Alpha` and weekly.

## Backups

Android backup is off (`allowBackup="false"`, `fullBackupContent="false"`, and [`data_extraction_rules.xml`](../../../app/src/main/res/xml/data_extraction_rules.xml) excluding every domain from cloud backup and device transfer), since the stores hold API keys, fees and carts. The only way data leaves is the user's own export:

- **Settings → Backup & Restore** writes versioned JSON through the Storage Access Framework (no storage permission): the `prefs`, `last_state` and `starred_currencies` namespaces and the carts, trips, fees and alerts. API keys are included only in a password-protected backup (the prompt says so) and sealed again for the phone on restore. Cached rates and histories are left out.
- **Encrypt with a password:** Argon2id v1.3 (`m=32 MiB, t=3, p=1`, random 32-byte salt) → a 256-bit key; the payload is AES-256-GCM (Tink's `AesGcmJce`, random 12-byte IV, 128-bit tag). Both are quantum-resistant: Argon2id is memory-hard, so Grover's speed-up and parallelism don't help, and AES-256 keeps ~128-bit security under Grover. Algorithms and costs are recorded in the file, and a file naming any other is refused. Passwords stay `CharArray`s, encoded through NIO (no lingering `String`) and zeroed after use. A wrong password re-prompts on the GCM tag failure.
- All of it runs off the main thread, with progress shown; the restart after a restore waits until every namespace is on disk.

## Permissions

`INTERNET`, `ACCESS_NETWORK_STATE` and `USE_BIOMETRIC` (install-time), and `POST_NOTIFICATIONS`, asked for only when the first rate alert is set; denying it leaves alerts waiting. No location, contacts, storage, camera or microphone: receipt photos come from the system camera app or photo picker. Noticing travel reads the mobile network's country, which needs no permission and isn't sent anywhere. The biometric prompt is the system's; the app learns only success or failure.

## Privacy switches

Settings → Security & privacy, all off by default (`PrivacySettings`; certificate pinning has no switch: it holds until the pins' expiry date, see [Network](#network)):

- **App lock** (`AppLock`, `LockGate`): a lock screen at start and after more than a minute away (the grace lets the camera and file pickers come and go), passed with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`. The prompt carries a Keystore key (`UnlockKey`: HMAC-SHA256, usable once each time the person authenticates, with strong biometrics or the screen lock) and the app opens only when that key then signs, which the phone's secure hardware allows only after a real unlock: code injected on a rooted phone can fake the prompt's success, not the key. A key spoiled by newly enrolled biometrics is made again; a phone whose Keystore can't make one can't turn the lock on. Screens underneath can't be seen, touched or read by TalkBack, and Recents shows no picture. Turning it on asks for the prompt first, so nobody locks themselves out; without a phone screen lock it's disabled. The text-selection sheet, widget and tile stay outside it.
- **Block screenshots:** `FLAG_SECURE` on the converter's and text-selection sheet's windows, blanking screenshots, recordings, casting and Recents.
- **Private requests** (`PrivateRequests`): every request leaves the phone like everyone else's: `User-Agent: CurrenciX` (no versions, nothing about the phone) and no `If-None-Match` / `If-Modified-Since`, which a server could make unique per phone. An innermost network interceptor, so it shapes exactly what goes out; the cost is full answers instead of `304`s. Not covered: the IP address, the TLS handshake, and when requests are made.

## Network

- **HTTPS only:** [`network_security_config.xml`](../../../app/src/main/res/xml/network_security_config.xml) refuses cleartext everywhere.
- **Certificate Transparency** is on in the same config: a certificate mis-issued for a provider's host by any trusted CA, but missing from the public logs, is refused. Android 17 does this by default; Android 16 when asked, as here.
- **Only known hosts:** `AllowedHostsInterceptor` refuses any host that isn't a provider's (`ApiProvider.allHosts`) or the OCR language host, at every redirect hop, so a bug or redirect can't send a request or a key elsewhere (`AllowedHostsTest`).
- **Certificate pinning** (no switch; until the pins' expiry date, renewed in every release): see [below](#certificate-pinning).
- **Forward secrecy:** TLS 1.3, or 1.2 with ECDHE and an AEAD cipher (`FORWARD_SECRET_TLS`, `ForwardSecrecyTest`), so a leaked server key can't decrypt recorded traffic. Every provider supports it, so OkHttp's RSA and 3DES fallbacks aren't needed.
- **Encrypted Client Hello (Android 17+):** the client resolves through the platform's `DnsResolver` (`AndroidDns`), which fetches a host's ECH keys, so the handshake hides the host name where the host publishes keys; others connect as before (ECH isn't enforced, which would make them fail). The IP stays visible.
- **OCR language data** is the one extra host (`raw.githubusercontent.com`, tessdata_fast at its fixed 4.1.0 tag), fetched only after the user agrees to that language by name and size. Each file is streamed to a temporary file and kept only if its SHA-256 matches the one compiled into the app; a mismatch is discarded. In the Play app the recognizer comes from Play Feature Delivery: code is only ever installed by Play. Photos are read on the phone, never uploaded, and deleted once read.

## Certificate pinning

A trusted CA can still be made to issue a certificate for a provider's host (compelled, compromised or mistaken); Certificate Transparency catches one that isn't logged. Pinning narrows it to the CAs each host actually uses: a connection is accepted only when its verified chain holds a key pinned for that host in [`network_security_config.xml`](../../../app/src/main/res/xml/network_security_config.xml) (a `<pin-set>` per host). Android's trust manager checks them, so they hold for every connection the app makes, OkHttp's included; there's no setting to turn them off.

- **What is pinned:** for each allowed host (`HttpClientProvider.allowedHosts`), the public keys of its chain's certificate authorities, intermediates and root, never the server's own certificate, which changes at every renewal. Pinning the root as well keeps a host working when its CA picks another intermediate, as Let's Encrypt does at random. Each host's pins hold for its subdomains too (`includeSubdomains`), as the allowed hosts do.
- **Keeping them current:** the pins between the file's `PINS` markers are written by [`.github/scripts/certificate-pins.py`](../../../.github/scripts/certificate-pins.py), which connects to every host and takes the keys of the chain it verifies. The **Certificate pins** workflow checks them weekly and on any PR changing them, failing when a host serves none of its pins (the app would refuse it), so a CA change shows up before users meet it. Run by hand with *update*, it rewrites the pins and commits them. A new host is added to the file by hand with no pins, and `CertificatePinningTest` fails until it has some.
- **Expiry:** Android stops checking a `<pin-set>` after its `expiration` date, so pins left in an old install can't lock it out of a host forever, but an install that outlives them is no longer pinned. *update* sets the date a year ahead; the weekly check, and the release workflow, fail once it is less than 90 days away, so no release goes out with pins about to lapse. Every pin set carries the same date (`CertificatePinningTest`).
- **When a host changes CA anyway:** its requests fail with "The server's certificate isn't one the app has pinned for it…" (an `SSLHandshakeException` from the platform's "Pin verification failed", told apart by `isPinFailure` and not retried), until the next release with new pins; the message says to update the app. A fallback provider on another host can still answer. There is no way around it in the app: that is what pinning is for.
- **Pins come only with the app:** the app never takes pins from a network. Versions up to 1.23 had **Re-pin to current certificates**, which connected without pins and made the chain it saw that host's pins: run behind an interception proxy whose CA the phone trusts, it would have pinned the proxy. It was removed, and so was the **Certificate pinning** switch; what either left on the phone is deleted on the first start after updating (`PrivacySettings.removeRetiredSettings`).

## Signing

Release APKs are signed with the release key and Android 17's post-quantum hybrid signature (v3.2: a classical key plus ML-DSA-65), so a future quantum computer couldn't forge an update on Android 17+; older versions verify the release key. Setup and why the keys must never be lost: [release.md](release.md#post-quantum-signing-android-17).

## Crash reports

Nothing about crashes leaves the phone by itself. Traces stay in private storage (latest five) and hold code locations, not the user's data; **Report a problem** shares them only when the user taps it and picks where.
