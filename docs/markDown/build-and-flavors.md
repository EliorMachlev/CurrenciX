# Build & Flavors

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 (Temurin recommended) |
| Android Gradle Plugin | see `build.gradle.kts` |
| Kotlin | 2.4.20 |
| Min SDK | 33 |
| Compile SDK | 37.2 (Compose 1.13 needs at least 37.1; set with AGP's `release(37) { minorApiLevel = 2 }` DSL) |
| Target SDK | 37 |

## Product Flavors

Two flavors are defined in `app/build.gradle.kts`:

| Flavor | Description |
|---|---|
| `fdroid` | F-Droid distribution — no Play Services dependency, fully open-source, reproducible |
| `play` | Google Play distribution — may use Play-specific APIs |

### Common Gradle tasks

```bash
# Debug builds
./gradlew assembleFdroidDebug
./gradlew assemblePlayDebug

# Release builds (requires signing config in secrets.properties)
./gradlew assembleFdroidRelease
./gradlew assemblePlayRelease

# Lint + unit tests + debug build (CI gate)
./gradlew check assembleDebug
```

### APKs per CPU type, and version codes

`assemble*` builds one APK per CPU type (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) plus a universal one (`splits.abi` in `app/build.gradle.kts`): receipt scanning's native OCR code is about 7 MB per type, so a phone that gets only its own is spared the rest. Native libraries are also stored compressed (`packaging.jniLibs.useLegacyPackaging`), so the download is smaller and Android unpacks them on install. Release sizes at 1.23.0: about 14.5 MB per type, 24 MB universal, 40.5 MB before either change.

Each APK's version code is `versionCode × 10 + digit`: 1 `armeabi-v7a`, 2 `arm64-v8a`, 3 `x86`, 4 `x86_64`, 0 universal (so 1.23.0 is 123001 … 123004, and 123000). The APKs of one release need different codes, and a phone that runs two types (64-bit ARM runs 32-bit too) is offered the higher, better fit. `defaultConfig.versionCode` stays the plain SemVer code (12300), which `checkVersion` and `checkFastlaneChangelog` check.

Each per-CPU APK's version name also names its type, for people (Settings and Android's app info show it): `1.23.0-arm32`, `-arm64`, `-x86`, `-x64` (debug: `1.23.0-<commit>-arm64`); the universal APK and the bundle keep the plain name. Settings reads the installed APK's name from the package manager, since `BuildConfig.VERSION_NAME` is one for all of a variant's APKs; the release-notes link keeps using `BuildConfig.VERSION_NAME`, the plain tag.

`bundle*` (Play) builds no splits: AGP can't make an app bundle and per-CPU APKs in one run, and Play splits the bundle by CPU itself. The bundle's version code is the universal one (123000). Version codes only go up, so this ×10 scheme is permanent once released.

For hand downloads (GitHub), the universal APK is the one that installs everywhere; the debug-APK workflow uploads only that one.

#### F-Droid recipe (draft, for fdroiddata)

F-Droid builds each entry from source and keeps the APK that `output` names; its client installs the one that fits the phone. One entry per type:

```yaml
Builds:
  - versionName: 1.23.0-arm32
    versionCode: 123001
    commit: v1.23.0
    subdir: app
    gradle:
      - fdroid
    output: build/outputs/apk/fdroid/release/*-fdroid-armeabi-v7a-release-unsigned.apk
  # … the same for 1.23.0-arm64 / 123002 (arm64-v8a), 1.23.0-x86 / 123003,
  # 1.23.0-x64 / 123004 (x86_64)

AutoUpdateMode: Version
UpdateCheckMode: Tags
VercodeOperation:
  - 10*%c+1
  - 10*%c+2
  - 10*%c+3
  - 10*%c+4
UpdateCheckData: app/build.gradle.kts|versionCode = (\d+)|.|versionName = "(.*)"
```

F-Droid's build checks that the APK's version name and code both match the entry ("Unexpected version/version code in output", `fdroidserver/build.py`), so each entry carries its labelled name. Its auto-update can't keep those names for new releases: `checkupdates` copies the last builds and gives every copy the same name, the tag's version plus one optional suffix from `AutoUpdateMode: Version +<suffix> <pattern>`, in which only `%c` (that copy's code) changes (`fdroidserver/checkupdates.py`, 2.4.5). A per-type word like `arm64` can't come out of that, so with the labels every auto-generated build would fail the check. Before submitting, the release builds F-Droid makes need the plain name (`1.23.0`), and the recipe's entries with it.

Changelogs: F-Droid's app page shows `changelogs/<CurrentVersionCode>.txt`, the highest code of the release (the `x86_64` one, 123004), else `changelogs/default.txt`; a file named after any other build's code becomes that build's own notes (`fdroidserver/update.py`). `changelogs/12300.txt` (the plain code) matches no build, so F-Droid wouldn't show it.

## Key Dependencies

### Android / AndroidX

| Dependency | Version |
|---|---|
| `androidx.core:core-ktx` | 1.19.1 |
| `androidx.appcompat:appcompat` | 1.8.0 |
| `androidx.core:core-splashscreen` | 1.2.0 |
| `androidx.lifecycle:lifecycle-*` (livedata/runtime/viewmodel/viewmodel-compose/runtime-compose) | 2.11.0 |
| `androidx.constraintlayout:constraintlayout` | 2.2.2 |
| `androidx.window:window` | 1.5.1 |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable` | 0.5.2 |
| `com.materialkolor:material-color-utilities` | 6.0.0 |

`com.google.android.material` has been **dropped** — the app is appcompat-only chrome plus Compose Material 3 now (see [architecture.md](architecture.md)).

### Persistence

| Dependency | Version |
|---|---|
| `androidx.datastore:datastore-preferences` | 1.2.1 |

Replaces SharedPreferences across every namespace — see [architecture.md](architecture.md).

### Compose

| Dependency | Version |
|---|---|
| `androidx.compose:compose-bom-alpha` | 2026.10.00 — the pre-release BOM: Compose 1.13.0-beta01, Material 3 1.5.0-beta01. Back to `compose-bom` once both are stable |
| `androidx.compose.material3:material3` / `ui` / `foundation` / `runtime` / `runtime-livedata` | via BOM |
| `androidx.activity:activity-compose` | 1.13.0 |
| `androidx.navigation3:navigation3-runtime` / `navigation3-ui` | 1.2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | 2.11.0 (per-screen ViewModel stores) |
| `sh.calvin.reorderable:reorderable` | 3.1.0 |

Hosts the Vico chart plus the rest of the app's UI — every screen is Compose, in one Activity (see [architecture.md](architecture.md)).

### HTTP & Serialisation

| Dependency | Version |
|---|---|
| `com.squareup.okhttp3:okhttp` | 5.5.0 |
| `com.squareup.okhttp3:logging-interceptor` | 5.5.0 |
| `com.squareup.retrofit2:retrofit` | 3.0.0 |
| `com.squareup.retrofit2:converter-moshi` | 3.0.0 |
| `com.squareup.moshi:moshi-kotlin` | 1.15.2 |
| `com.google.devtools.ksp:*` | 2.3.11 |

Retrofit is layered on the shared OkHttp client and serves every fixed-shape JSON endpoint; the XML and SDMX feeds stay on raw OkHttp by design — see [architecture.md](architecture.md).

### Calculator

No dependency. The keypad's expressions are `+ − × ÷`, brackets and percent, so `util/Arithmetic.kt` evaluates them in about a hundred lines of `BigDecimal` recursive descent (68 significant digits, as its predecessor EvalEx used). EvalEx was dropped because it is built with Lombok, whose `lombok.Generated` marker R8 could only get past with a `-dontwarn` rule; before it came mXparser, whose v5+ dual license isn't F-Droid compatible.

### Receipt scanning

| Dependency | Version | Note |
|---|---|---|
| `com.github.adaptech-cz.Tesseract4Android:tesseract4android` | 4.8.0 | Tesseract OCR with Leptonica, native, Apache-2.0, in the `:ocr-engine` module. Published only on JitPack, which `settings.gradle` allows for this one group (`exclusiveContent`). Its native libraries are about 7 MB per ABI (four ABIs). It has no R8 rules of its own; `ocr-engine/consumer-rules.pro` keeps its two packages, whose native code finds Java members by name, and `TesseractRecognizer`, which the Play app loads by name |
| `com.google.android.play:feature-delivery-ktx` | 2.1.0 | **Play flavor only** (`playImplementation`): installs the `:ocr` feature module on demand. F-Droid's scanner checks only the flavors it builds, and the F-Droid app has no Play code |
| `androidx.exifinterface:exifinterface` | 1.4.2 | A receipt photo's orientation, so it's read upright |

Modules: `:ocr-engine` (an Android library: Tesseract and `TesseractRecognizer`) and `:ocr` (a dynamic feature: just a manifest and a build file). The F-Droid app depends on `:ocr-engine` directly; the Play bundle carries it in `:ocr`, on demand. `:ocr` is declared in `:app` (`dynamicFeatures`) and has variants only in a bundle build: AGP can't make per-CPU APKs with a feature module, and only Play installs features.

English OCR data (`src/fdroid/assets/tessdata/eng.traineddata`, tessdata_fast 4.1.0, 4.1 MB) ships in the F-Droid APKs; the Play app downloads it with the recognizer. The other languages download on demand in both, all checked against SHA-256s in `ocr/TessLanguage.kt`.

### Charts

| Dependency | Version |
|---|---|
| `com.patrykandpatrick.vico:compose` | 3.3.1 |

### Background & Widgets

| Dependency | Version | Note |
|---|---|---|
| `androidx.work:work-runtime-ktx` | 2.12.0 | Periodic background rate refresh, off by default (opt-in via Settings) |
| `androidx.glance:glance-appwidget` | 1.2.0 | Home-screen widget content, replacing hand-rolled `RemoteViews` |

### Crypto & Logging

| Dependency | Version | Note |
|---|---|---|
| `org.bouncycastle:bcprov-jdk18on` | 1.86 | Pure-Java Argon2id for password-based backup encryption — see [security.md](security.md) |
| `com.google.crypto.tink:tink-android` | 1.24.0 | AES-256-GCM for the backup's payload (`BackupCrypto`) — see [security.md](security.md) |
| `com.jakewharton.timber:timber` | 5.0.1 | Local-only rotating file log, no remote crash/analytics sink |
| `com.github.chuckerteam.chucker:library` (debug) / `library-no-op` (release) | 4.3.1 | In-app HTTP inspector, debug-only via source-set split |

### Debug Tooling (not shipped in release)

| Dependency | Version | Note |
|---|---|---|
| `com.squareup.leakcanary:leakcanary-android` | 3.0-alpha-9 | Debug-only leak detection, auto-installs via its own `ContentProvider` |
| `androidx.metrics:metrics-performance` | 1.0.0 | JankStats — per-Activity jank logging via Timber, debug-only |

### Testing

| Dependency | Version |
|---|---|
| `junit:junit` | 4.13.2 |
| `org.mockito:mockito-core` | 5.24.0 |
| `androidx.arch.core:core-testing` | 2.2.0 |
| `org.junit.jupiter:junit-jupiter-*` | 6.1.3 |
| `com.code-intelligence:jazzer-junit` | 0.30.0 (fuzz testing) |
| `io.github.takahirom.roborazzi:roborazzi` / `roborazzi-compose` | 1.76.0 |
| `org.robolectric:robolectric` | 4.17 |
| `androidx.compose.ui:ui-test-junit4` / `ui-test-manifest` | via BOM |
| `com.lemonappdev:konsist` | 0.17.3 |

Roborazzi + Robolectric give pure-JVM screenshot testing for every Compose surface (no emulator needed), running under the existing `test<Flavor>DebugUnitTest` task via the JUnit vintage engine. Konsist encodes the View / ViewModel / Repository / Model layer boundaries as JUnit tests on the plain JVM, so a refactor can't silently break MVVM separation.

## Signing (Release)

Create `secrets.properties` in the project root (not committed):

```properties
KEYSTORE_FILE=path/to/release.jks
KEYSTORE_PASSWORD=...
KEYSTORE_KEY_ALIAS=...
KEYSTORE_KEY_PASSWORD=...
```

Without it, release builds come out unsigned (what F-Droid builds: it signs with its own key). Play bundles are signed with this key as the upload key; Play App Signing re-signs them.

### Post-quantum signing (Android 17+)

Release APKs can also carry Android 17's hybrid signature (APK Signature Scheme v3.2): a new classical key plus an ML-DSA-65 key, which a quantum computer couldn't forge. Android 17 and later verify it; Android 16 and earlier keep verifying the release key, which still signs every APK. Only APKs the project signs itself are affected (GitHub releases, `install`): F-Droid signs with its own key, and Play re-signs bundles (it plans its own hybrid upgrade).

**The keys exist** (made 2026-10-17): the release key (`release.p12`, alias `currencix-release`, EC P-521, certificate SHA-256 `5c:f5:cb:30:0f:0d:a0:76:f7:bc:fd:36:5e:f8:3b:67:c6:46:d6:2a:d8:64:7a:6d:f4:6b:95:85:df:a3:00:99`) and the hybrid pair (`hybrid.p12`: `hybrid-classical`, EC P-521, `3e:10:0d:de:…:d5:99`; `hybrid-pqc`, ML-DSA-65, `9c:18:29:84:…:dc:66`). Their lineages hold certificates only and are in the repository (`signing-lineages/`). The keystores and passwords are not: the maintainer keeps them in an encrypted backup and as repository secrets.

**Releasing is pushing a tag.** Tag a commit of `master` `v<versionName>` (`v1.23.0`) and push the tag: `release-apk.yaml` checks that the tag names the version being built and is on `master`, decodes the keystores from the secrets `RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `HYBRID_KEYSTORE_B64` and `HYBRID_KEYSTORE_PASSWORD` into the runner's temp folder, writes `secrets.properties`, builds `assembleFdroidRelease` (which signs and verifies every APK, below), deletes the keys and uploads the five APKs with a `SHA256SUMS`. A second job, the only one allowed to write to the repository and one that never sees the keys, then publishes the GitHub release "CurrenciX <version>": the APKs, the checksums and notes written automatically, followed by which APK to pick and the release certificate's fingerprint.

The notes come from `.github/scripts/release-notes.py <tag>`: the commits from the previous `v*` tag (for the first release, the fork's rebrand to CurrenciX, #89, as earlier history is the upstream app's) to this one. `master` takes each pull request as one squashed commit with a Conventional Commits title, so they group by type: **New** (`feat`), **Fixes** (`fix`), **Faster** (`perf`), **Translations** (`i18n`, plus one line naming the languages Weblate updated), and **Other changes** for a title in no known style. `chore`, `ci`, `build`, `refactor`, `test`, `docs`, `style` and `revert` are left out as not user-facing. Each entry keeps its PR number, which GitHub links, and the notes end with the compare link to the previous release. The PR title is the release note, so title PRs for users. Run by hand, the workflow shows the notes a tag would get in its summary. The fastlane changelog (`changelogs/<versionCode>.txt`, what F-Droid shows, 500 characters at most) is still written by hand. A version with a `-` (`1.24.0-beta1`) is marked a pre-release. F-Droid builds the same tag (`UpdateCheckMode: Tags`). Started by hand instead (Actions → Release → Run workflow), it only builds and uploads the signed APKs, to try a build before tagging it. Nothing else can start it, so pull requests never see the keys, and its Gradle cache is read-only, so nothing from a run that held them is cached.

To make new keys instead (only before the first release; after it they're permanent):

1. `scripts/create-hybrid-signing-keys.sh path/to/release.jks <release-key-alias>` (JDK 24+, build-tools 37+). It makes the release key if there isn't one yet, the two hybrid keys in `signing/hybrid.p12`, and a lineage for each (`signing/classical-lineage`, `signing/pqc-lineage`) that rotates from the release key to it, without rollback.
2. Add what it prints to `secrets.properties`:

   ```properties
   HYBRID_KEYSTORE_FILE=/…/signing/hybrid.p12
   HYBRID_KEYSTORE_PASSWORD=...
   HYBRID_CLASSICAL_LINEAGE=/…/signing/classical-lineage
   HYBRID_PQC_LINEAGE=/…/signing/pqc-lineage
   # optional, these are the defaults:
   HYBRID_CLASSICAL_KEY_ALIAS=hybrid-classical
   HYBRID_PQC_KEY_ALIAS=hybrid-pqc
   ```

3. `assemble<Flavor>Release` then runs `signApksHybrid<Flavor>Release` after packaging: `apksigner` re-signs each APK with the release key and the hybrid pair and verifies it. It is wired as a transform of the APK artifact (`SignApksHybrid` in `app/build.gradle.kts`), so the output folder and `install` only ever get hybrid-signed APKs. `apksigner` runs on a JDK 25 toolchain whatever JDK runs Gradle, as reading ML-DSA keys needs 24+; passwords reach it through its environment, not its command line. Some but not all of the `HYBRID_*` lines fails the build, so a release can't quietly go out without the signature.

Check a signed APK with `apksigner verify --verbose --print-certs` (on JDK 24+): it lists "v3.2 … true", the V3.2 Hybrid Classical and PQC signers and the V3.0 signer.

**Once released, it's permanent.** A hybrid-signed app on Android 17 only accepts updates signed with the hybrid keys (the lineage has no rollback to the release key alone), so lose `signing/` or its password and that install can't be updated. Back up `signing/` with the release keystore, and keep them out of the repository (`.gitignore` covers `*.jks`, `*.p12` and `/signing/`).

## Version Code Generation

`versionCode` is derived automatically from `versionName`:

```
1.23.0  →  1 * 10000 + 23 * 100 + 0  =  12300
```

A pre-build consistency check verifies that `versionName` and `versionCode` are in sync.

## Baseline Profiles

The `:baselineprofile` module (`com.android.test` + `androidx.baselineprofile`
plugin) generates the baseline + startup profiles that `ProfileInstaller`
hands to ART at install time. The generated `baseline-prof.txt` and
`startup-prof.txt` live under
`app/src/<flavor>Release/generated/baselineProfiles/` and are baked into the
release APK/AAB automatically.

The generator drives real user journeys (`baselineprofile/.../Journeys.kt`):
launch, type on the keypad, fling through the currency picker, visit
Timeline and Cart from the drawer, fill a cart with 15 rows and fling
through it, and open each of Settings' categories. A startup-only collection produces
`startup-prof.txt`, which also drives DEX layout; the full journey produces
`baseline-prof.txt`. Journeys find elements by the app's `UiTestTags`
(exposed as resource ids), never by visible text. `UiTags.kt` in the module
mirrors them and must be kept in sync.

Generation runs on a **Gradle Managed Device** (`pixel6Api34`, an API 34
AOSP emulator that Gradle downloads and boots headless), so no physical
device or manual emulator is needed. It does need hardware virtualization
(KVM).

- **CI:** `baseline-profile.yaml` generates both flavors' profiles and uploads
  them as the `baseline-profiles` artifact. See [ci-cd.md](ci-cd.md).
- **Locally:**

```bash
./gradlew :app:generateFdroidReleaseBaselineProfile :app:generatePlayReleaseBaselineProfile \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Commit the regenerated `baseline-prof.txt` / `startup-prof.txt`. Re-run when
hot paths shift materially (major redesign, new startup dependency).

**F-Droid builds carry the profile too.** The F-Droid APKs have
`assets/dexopt/baseline.prof` and `ProfileInstallerInitializer` runs at
startup (checked in the merged manifest), so a sideloaded or F-Droid install
gets the same profile as a Play one. The difference is timing: Play can apply
cloud profiles at install, while elsewhere `ProfileInstaller` writes the
profile on the first launch and ART compiles it in its next idle maintenance
(usually overnight on charge), so the first launches after an update run
without it. The benchmarks' `None` vs `Partial` runs show what that costs.

### Frame-timing benchmarks

`InteractionBenchmarks` (same module, same journeys) measures cold-start time
and `FrameTimingMetric` for keypad typing, currency-picker scrolling, screen
transitions, scrolling a 15-row cart and browsing Settings, each with and
without the baseline profile. Watch the P90/P99
`frameDurationCpuMs` and any `frameOverrunMs > 0` (frames that missed their
deadline, i.e. visible jank). CI runs them on the same emulator
(`benchmarks.yaml`) and fails when a median is well past the saved baseline,
`baselineprofile/benchmark-baseline.json` (see [ci-cd.md](ci-cd.md#benchmarks-benchmarksyaml)).
Emulator numbers are for comparison and catching regressions, not absolute
timings, so Macrobenchmark's emulator guard is suppressed.

```bash
./gradlew :baselineprofile:pixel6Api34FdroidBenchmarkReleaseAndroidTest \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

### Compose stability

`app/compose-stability.conf` tells the Compose compiler that JDK value types
(`BigDecimal`, `java.time`, `DateTimeFormatter`, `Locale`) never change, so
composables taking rates and dates compare them by `equals` and skip
recomposing for an equal value. Hot-path parameter objects are values too
(`ConverterBody` is an `@Immutable` data class; the currency picker's
conversion preview is remembered). To look again, add
`reportsDestination` / `metricsDestination` to the `composeCompiler` block
and compile a release variant: the reports list each composable's unstable
parameters.

## Known Issues

### Gradle deprecation: "Project object as dependency notation" (AGP bug)

**Symptom:** During configuration of `:app`, Gradle prints:

```
Using a Project object as a dependency notation has been deprecated.
This will fail with an error in Gradle 10.
```

**Root cause:** This originated inside **AGP 9.2.1** (`VariantDependenciesBuilder.java:279/333` → `VariantManager.createTestComponents`), not from any build script in this repository. The stack trace confirmed no call site in our code.

**Status:** The project has since moved to **AGP 9.3.2**. Upstream bug, expected to resolve once AGP uses `project(String)` internally — needs a fresh check on 9.3.2 to confirm whether it still reproduces before this section is trusted as current.
