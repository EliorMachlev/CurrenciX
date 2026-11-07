# Release

## Releasing is pushing a tag

Tag a commit of `master` `v<versionName>` (`v0.1`) and push it. [`release-apk.yaml`](../../../.github/workflows/release-apk.yaml):

1. checks the tag names the version being built and is on `master`, and that the certificate pins are at least 90 days from expiry (Android stops checking them after it; run the **Certificate pins** workflow with *update* first, see [security.md](security.md#certificate-pinning));
2. decodes the keystores from the `release` environment's secrets into the runner's temp folder, builds `assembleFdroidRelease` (signing and verifying the APK), deletes the keys;
3. writes the SBOM (`:app:cyclonedxDirectBom`, CycloneDX 1.6 of the fdroid release runtime classpath) and `SHA256SUMS`, and uploads the APK with them;
4. a second job, the only one allowed to write and one that never sees the keys, attests the files' build provenance and publishes the GitHub release "CurrenciX <version>".

A tag whose version has a `-` (`v1.24.0-beta1`), or any `0.x` version (everything before 1.0), publishes a GitHub pre-release instead of a release. Run by hand instead (no tag), the workflow builds the signed APK, uploads it with the SBOM and `SHA256SUMS` as a run artifact, and previews the notes a tag would get; it publishes nothing. Nothing else can start it, so PRs never see the keys, and it builds without Gradle's cache (`cache-disabled`), so nothing a branch's run cached can reach the signed APKs.

**Release notes** come from `.github/scripts/release-notes.py <tag>`: the squashed PR titles since the previous `v*` tag (for the first release, since the rebrand, [#89](https://github.com/EliorMachlev/CurrenciX/pull/89)), grouped **New** (`feat`), **Fixes** (`fix`), **Faster** (`perf`), **Translations** (`i18n`) and **Other changes**; `chore`, `ci`, `build`, `deps`, `refactor`, `test`, `docs`, `style` and `revert` are left out. So title PRs for users. The fastlane changelog ([`fastlane/metadata/android/en-US/changelogs/`](../../../fastlane/metadata/android/en-US/changelogs), ≤ 500 characters, one file per version code; see [F-Droid](#f-droid-draft-recipe-for-fdroiddata) for which code it must be named after) is written by hand.

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

**The keys exist** (made 2026-10-23): the release key (`release.p12`, alias `currencix-release`, EC P-521, `CN=CurrenciX`, SHA-256 `B0:87:B0:31:48:D7:73:B4:CE:DA:C0:24:79:F5:0B:2F:EB:C5:C8:08:AF:17:2E:16:7B:2E:D0:BB:68:E1:E0:59`) and the hybrid pair (`hybrid.p12`: `hybrid-classical`, EC P-521, `BF:9A:96:0A:…:21:FB:C3`; `hybrid-pqc`, ML-DSA-65, `45:47:5F:B9:…:A0:03:E6`). Their certificate-only lineages are in [`signing-lineages/`](../../../signing-lineages). Keystores and passwords are not in the repository: the maintainer keeps them in an encrypted backup and as secrets of the `release` environment (deployments limited to `v*` tags and `master`), which only the release build job names: `RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `HYBRID_KEYSTORE_B64`, `HYBRID_KEYSTORE_PASSWORD`.

**Once released, it's permanent.** A hybrid-signed install on Android 17 accepts only updates signed with the hybrid keys (no rollback), so losing `signing/` or its password strands it. Back it up with the release keystore.

To make new keys (only before the first release):

1. `scripts/create-hybrid-signing-keys.sh path/to/release.jks <alias>` (JDK 24+, build-tools 37+): the release key if missing, the hybrid keys in `signing/hybrid.p12`, and a lineage for each rotating from the release key, without rollback.
2. Add what it prints to `secrets.properties` (`HYBRID_KEYSTORE_FILE`, `HYBRID_KEYSTORE_PASSWORD`, `HYBRID_CLASSICAL_LINEAGE`, `HYBRID_PQC_LINEAGE`; aliases default to `hybrid-classical` / `hybrid-pqc`).
3. `assemble<Flavor>Release` then runs `signApksHybrid<Flavor>Release`, a transform of the APK artifact (`SignApksHybrid`), so the output folder and `install` only ever get hybrid-signed APKs. `apksigner` runs on a JDK 25 toolchain (ML-DSA keys need 24+) with passwords in its environment, not its command line. Some but not all `HYBRID_*` lines fails the build.

Check with `apksigner verify --verbose --print-certs` (JDK 24+): "v3.2 … true", the hybrid classical and PQC signers, and the v3.0 signer.

## Version codes

`versionCode` follows `versionName`, each part two digits: `0.1 → 0×100 + 1 = 1`, `1.2.3 → 1×10000 + 2×100 + 3 = 10203`, checked by `checkVersion`. The first release is `0.1` (code 1); the upstream app's versions and changelogs aren't carried over. F-Droid's update check reads the `versionName = "…"` and `versionCode = …` lines of [`app/build.gradle.kts`](../../../app/build.gradle.kts) as text, and the release workflow reads the `versionName` one (with `sed`) to check the tag, so keep them literal.

The APK and the app bundle both carry that code as it is (one APK for both 64-bit CPU types, see [build.md](build.md#one-apk-for-every-phone-64-bit-only)). Codes only go up, so the scheme is permanent once released. Debug builds add the commit to the name (`0.1-<commit>`); Settings reads the installed APK's name from the package manager.

## F-Droid (draft recipe for fdroiddata)

One entry per release, building the one APK:

```yaml
Builds:
  - versionName: '0.1'
    versionCode: 1
    commit: v0.1
    subdir: app
    gradle:
      - fdroid
    output: build/outputs/apk/fdroid/release/*-fdroid-release-unsigned.apk

# Optional: publish the APK signed here, hybrid signature included, once
# F-Droid's build of the tag reproduces it byte for byte (see below).
Binaries: https://github.com/EliorMachlev/CurrenciX/releases/download/v%v/com.eliormachlev.currencix-v%c-fdroid-release.apk
AllowedAPKSigningKeys: <the release key's SHA-256 above, lowercase, without colons>

AutoUpdateMode: Version
UpdateCheckMode: Tags
UpdateCheckData: app/build.gradle.kts|versionCode = (\d+)|.|versionName = "(.*)"
```

F-Droid's app page shows `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` (`1.txt`), the file `checkFastlaneChangelog` (run by `./gradlew build`) requires, else `default.txt`.

**Publishing this project's signature.** With `Binaries:`, F-Droid builds the tag itself, compares it with the GitHub release's APK, and when they match byte for byte apart from the signature it publishes ours instead of signing its own ([Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/)); without a match it skips that version. The signature is carried over as the whole APK Signing Block, where the v3.2 hybrid signature sits, so F-Droid and GitHub installs would share one key and update from each other. Not yet tried: F-Droid's server reproducing the APK (CI only shows our own two builds agree, [`apk-checks.yaml`](../../../.github/workflows/apk-checks.yaml)), and F-Droid's tools accepting a v3.2 block, which their documentation covers only up to v3. Check both on the first release before relying on it.
