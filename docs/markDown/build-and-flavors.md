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

F-Droid's build checks that the APK's version name and code both match the entry ("Unexpected version/version code in output"), so each entry carries its labelled name. Whether its auto-update can write those suffixed names for new releases is to confirm with the F-Droid reviewers; if not, the labels can be left off the release builds F-Droid makes.

Open question for the F-Droid submission: fastlane changelogs are named after the version code (`changelogs/12300.txt`); whether F-Droid shows that file for the per-type codes (123001 …) or needs one per code hasn't been confirmed.

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
| `com.github.adaptech-cz.Tesseract4Android:tesseract4android` | 4.8.0 | Tesseract OCR with Leptonica, native, Apache-2.0. Published only on JitPack, which `settings.gradle` allows for this one group (`exclusiveContent`). Its native libraries are about 7 MB per ABI (four ABIs), so per-ABI splits or an App Bundle keep the install small. It has no R8 rules of its own; `app/proguard-rules.pro` keeps its two packages, whose native code finds Java members by name |
| `androidx.exifinterface:exifinterface` | 1.4.2 | A receipt photo's orientation, so it's read upright |

English OCR data (`assets/tessdata/eng.traineddata`, tessdata_fast 4.1.0, 4.1 MB) ships in the APK; the other languages download on demand and are checked against SHA-256s in `ocr/TessLanguage.kt`.

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
| `com.google.crypto.tink:tink-android` | 1.23.0 | AES-256-GCM for the backup's payload (`BackupCrypto`) — see [security.md](security.md) |
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
storeFile=path/to/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

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
launch, type on the keypad, fling through the currency picker, and visit
Timeline and Cart from the drawer. A startup-only collection produces
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

### Frame-timing benchmarks

`InteractionBenchmarks` (same module, same journeys) measures cold-start time
and `FrameTimingMetric` for keypad typing, currency-picker scrolling and screen
transitions, each with and without the baseline profile. Watch the P90/P99
`frameDurationCpuMs` and any `frameOverrunMs > 0` (frames that missed their
deadline, i.e. visible jank). CI runs them on the same emulator. Emulator
numbers are for comparison and catching regressions, not absolute timings, so
Macrobenchmark's emulator guard is suppressed.

```bash
./gradlew :baselineprofile:pixel6Api34FdroidBenchmarkReleaseAndroidTest \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

## Known Issues

### Gradle deprecation: "Project object as dependency notation" (AGP bug)

**Symptom:** During configuration of `:app`, Gradle prints:

```
Using a Project object as a dependency notation has been deprecated.
This will fail with an error in Gradle 10.
```

**Root cause:** This originated inside **AGP 9.2.1** (`VariantDependenciesBuilder.java:279/333` → `VariantManager.createTestComponents`), not from any build script in this repository. The stack trace confirmed no call site in our code.

**Status:** The project has since moved to **AGP 9.3.2**. Upstream bug, expected to resolve once AGP uses `project(String)` internally — needs a fresh check on 9.3.2 to confirm whether it still reproduces before this section is trusted as current.
