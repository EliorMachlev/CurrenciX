# Release

## Releasing is pushing a tag

Tag a commit of `master` `v<versionName>` (`v1.23.0`) and push it. `release-apk.yaml`:

1. checks the tag names the version being built and is on `master`;
2. decodes the keystores from the `release` environment's secrets into the runner's temp folder, builds `assembleFdroidRelease -PabiVersionNames` (signing and verifying every APK), deletes the keys;
3. writes the SBOM (`:app:cyclonedxDirectBom`, CycloneDX 1.6 of the fdroid release runtime classpath) and `SHA256SUMS`, and uploads the five APKs with them;
4. a second job, the only one allowed to write and one that never sees the keys, attests the files' build provenance and publishes the GitHub release "CurrenciX <version>".

A version with a `-` (`1.24.0-beta1`) is a pre-release. Run by hand instead, the workflow only builds and uploads the signed APKs and previews the notes. Nothing else can start it, so PRs never see the keys, and its Gradle cache is read-only.

**Release notes** come from `.github/scripts/release-notes.py <tag>`: the squashed PR titles since the previous `v*` tag (for the first release, since the rebrand, #89), grouped **New** (`feat`), **Fixes** (`fix`), **Faster** (`perf`), **Translations** (`i18n`) and **Other changes**; `chore`, `ci`, `build`, `deps`, `refactor`, `test`, `docs`, `style` and `revert` are left out. So title PRs for users. The fastlane changelog (`changelogs/<versionCode>.txt`, ≤ 500 characters) is written by hand.

## Signing

`secrets.properties` in the project root (git-ignored, with `*.jks`, `*.p12` and `/signing/`):

```properties
KEYSTORE_FILE=path/to/release.jks
KEYSTORE_PASSWORD=...
KEYSTORE_KEY_ALIAS=...
KEYSTORE_KEY_PASSWORD=...
```

Without it, release builds are unsigned, which is what F-Droid builds (it signs with its own key). Play bundles use it as the upload key; Play App Signing re-signs them.

### Post-quantum signing (Android 17+)

Release APKs also carry Android 17's hybrid signature (APK Signature Scheme v3.2): a classical key plus an ML-DSA-65 key that a quantum computer couldn't forge. Android 17+ verifies it; older versions verify the release key, which still signs every APK. Only APKs the project signs are affected (F-Droid signs its own; Play re-signs bundles).

**The keys exist** (made 2026-10-23): the release key (`release.p12`, alias `currencix-release`, EC P-521, `CN=CurrenciX`, SHA-256 `B0:87:B0:31:48:D7:73:B4:CE:DA:C0:24:79:F5:0B:2F:EB:C5:C8:08:AF:17:2E:16:7B:2E:D0:BB:68:E1:E0:59`) and the hybrid pair (`hybrid.p12`: `hybrid-classical`, EC P-521, `BF:9A:96:0A:…:21:FB:C3`; `hybrid-pqc`, ML-DSA-65, `45:47:5F:B9:…:A0:03:E6`). Their certificate-only lineages are in `signing-lineages/`. Keystores and passwords are not in the repository: the maintainer keeps them in an encrypted backup and as secrets of the `release` environment (deployments limited to `v*` tags and `master`), which only the release build job names: `RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `HYBRID_KEYSTORE_B64`, `HYBRID_KEYSTORE_PASSWORD`.

**Once released, it's permanent.** A hybrid-signed install on Android 17 accepts only updates signed with the hybrid keys (no rollback), so losing `signing/` or its password strands it. Back it up with the release keystore.

To make new keys (only before the first release):

1. `scripts/create-hybrid-signing-keys.sh path/to/release.jks <alias>` (JDK 24+, build-tools 37+): the release key if missing, the hybrid keys in `signing/hybrid.p12`, and a lineage for each rotating from the release key, without rollback.
2. Add what it prints to `secrets.properties` (`HYBRID_KEYSTORE_FILE`, `HYBRID_KEYSTORE_PASSWORD`, `HYBRID_CLASSICAL_LINEAGE`, `HYBRID_PQC_LINEAGE`; aliases default to `hybrid-classical` / `hybrid-pqc`).
3. `assemble<Flavor>Release` then runs `signApksHybrid<Flavor>Release`, a transform of the APK artifact (`SignApksHybrid`), so the output folder and `install` only ever get hybrid-signed APKs. `apksigner` runs on a JDK 25 toolchain (ML-DSA keys need 24+) with passwords in its environment, not its command line. Some but not all `HYBRID_*` lines fails the build.

Check with `apksigner verify --verbose --print-certs` (JDK 24+): "v3.2 … true", the hybrid classical and PQC signers, and the v3.0 signer.

## Version codes

`versionCode` follows `versionName`: `1.23.0 → 1×10000 + 23×100 + 0 = 12300`, checked by `checkVersion`. F-Droid's update check and the release workflow read the `versionName = "…"` and `versionCode = …` lines of `app/build.gradle.kts` as text, so keep them literal.

Each APK's code is `versionCode × 10 + digit`: 1 `armeabi-v7a`, 2 `arm64-v8a`, 3 `x86`, 4 `x86_64`, 0 universal and the bundle (123001…123004, 123000). One release's APKs need different codes, and a phone running two types is offered the higher. Codes only go up, so this scheme is permanent once released. Release builds name the type in the version (`1.23.0-arm64`) only with `-PabiVersionNames`, which the release workflow passes; F-Droid builds without it and keeps the plain name. Debug builds always do (`1.23.0-<commit>-arm64`). Settings reads the installed APK's name from the package manager.

## F-Droid (draft recipe for fdroiddata)

One entry per CPU type, each keeping the APK its `output` names:

```yaml
Builds:
  - versionName: 1.23.0
    versionCode: 123001
    commit: v1.23.0
    subdir: app
    gradle:
      - fdroid
    output: build/outputs/apk/fdroid/release/*-fdroid-armeabi-v7a-release-unsigned.apk
  # … the same for 123002 (arm64-v8a), 123003 (x86), 123004 (x86_64)

AutoUpdateMode: Version
UpdateCheckMode: Tags
VercodeOperation:
  - 10*%c+1
  - 10*%c+2
  - 10*%c+3
  - 10*%c+4
UpdateCheckData: app/build.gradle.kts|versionCode = (\d+)|.|versionName = "(.*)"
```

Every entry carries the plain version name: F-Droid checks the APK's name and code match the entry, and `checkupdates` gives every auto-generated copy the same name, so a per-type suffix would fail on each. F-Droid's app page shows `changelogs/<CurrentVersionCode>.txt`, the release's highest code (123004), else `default.txt`; `changelogs/12300.txt` matches no build and wouldn't show.
