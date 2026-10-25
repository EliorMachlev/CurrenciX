# Build & Flavors

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 or newer (Temurin recommended; CI uses 25). The code targets Java 21; signing with the post-quantum hybrid signature needs 24+, which the build fetches as a toolchain |
| Gradle | 9.8.2 (the wrapper) |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.21 |
| Min SDK | 33 |
| Compile SDK | 37.2 (Compose 1.13 needs at least 37.1; set with AGP's `release(37) { minorApiLevel = 2 }` DSL) |
| Target SDK | 37 |

## Product Flavors

Two flavors are defined in `app/build.gradle.kts`:

| Flavor | Description |
|---|---|
| `fdroid` | F-Droid distribution — no Play Services dependency, fully open-source, a reproducible-build target |
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

Each per-CPU APK's version name can also name its type, for people (Settings and Android's app info show it): `1.23.0-arm32`, `-arm64`, `-x86`, `-x64`. Debug builds always do (`1.23.0-<commit>-arm64`); release builds only with `-PabiVersionNames`, which the release workflow passes for GitHub's APKs, so a release built without it, as F-Droid builds it, keeps the plain name (`1.23.0`, see below). The universal APK and the bundle keep the plain name. Settings reads the installed APK's name from the package manager, since `BuildConfig.VERSION_NAME` is one for all of a variant's APKs; the release-notes link keeps using `BuildConfig.VERSION_NAME`, the plain tag.

`bundle*` (Play) builds no splits: AGP can't make an app bundle and per-CPU APKs in one run, and Play splits the bundle by CPU itself. The bundle's version code is the universal one (123000). Version codes only go up, so this ×10 scheme is permanent once released.

For hand downloads (GitHub), the universal APK is the one that installs everywhere; the debug-APK workflow uploads only that one.

#### F-Droid recipe (draft, for fdroiddata)

F-Droid builds each entry from source and keeps the APK that `output` names; its client installs the one that fits the phone. One entry per type:

```yaml
Builds:
  - versionName: 1.23.0
    versionCode: 123001
    commit: v1.23.0
    subdir: app
    gradle:
      - fdroid
    output: build/outputs/apk/fdroid/release/*-fdroid-armeabi-v7a-release-unsigned.apk
  # … the same for 123002 (arm64-v8a), 123003 (x86) and 123004 (x86_64),
  # each with versionName 1.23.0

AutoUpdateMode: Version
UpdateCheckMode: Tags
VercodeOperation:
  - 10*%c+1
  - 10*%c+2
  - 10*%c+3
  - 10*%c+4
UpdateCheckData: app/build.gradle.kts|versionCode = (\d+)|.|versionName = "(.*)"
```

F-Droid's build checks that the APK's version name and code both match the entry ("Unexpected version/version code in output", `fdroidserver/build.py`), so each entry carries the name its build produces: the plain one, as F-Droid builds without `-PabiVersionNames`. That's also the only name its auto-update can keep: `checkupdates` copies the last builds and gives every copy the same name, the tag's version plus one optional suffix from `AutoUpdateMode: Version +<suffix> <pattern>`, in which only `%c` (that copy's code) changes (`fdroidserver/checkupdates.py`, 2.4.5), so a per-type word like `arm64` would fail the check on every auto-generated build.

Changelogs: F-Droid's app page shows `changelogs/<CurrentVersionCode>.txt`, the highest code of the release (the `x86_64` one, 123004), else `changelogs/default.txt`; a file named after any other build's code becomes that build's own notes (`fdroidserver/update.py`). `changelogs/12300.txt` (the plain code) matches no build, so F-Droid wouldn't show it.

## Key Dependencies

### Android / AndroidX

| Dependency | Version |
|---|---|
| `androidx.core:core-ktx` | 1.19.1 |
| `androidx.appcompat:appcompat` / `appcompat-resources` | 1.8.0 |
| `androidx.core:core-splashscreen` | 1.2.0 |
| `androidx.lifecycle:lifecycle-*` (livedata/runtime/process/viewmodel/viewmodel-compose/runtime-compose) | 2.11.0 |
| `androidx.constraintlayout:constraintlayout` | 2.2.2 |
| `androidx.window:window` | 1.5.1 |
| `org.jetbrains.kotlin:kotlin-bom` | the Kotlin plugin's version (2.4.21): every Kotlin library aligned to it, so no old `kotlin-stdlib-jdk7` / `-jdk8` comes in through Moshi or Reorderable |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable` | 0.5.2 |
| `com.materialkolor:material-color-utilities` | 6.0.0 |
| `androidx.profileinstaller:profileinstaller` | 1.4.1 (installs the baseline profile) |

`com.google.android.material` has been **dropped** — the app is appcompat-only chrome plus Compose Material 3 now (see [architecture.md](architecture.md)).

### Persistence

| Dependency | Version |
|---|---|
| `androidx.datastore:datastore-preferences` / `datastore` | 1.3.0 |
| `com.squareup.wire` (Gradle plugin and runtime) | 7.1.0 |

Preferences DataStore replaces SharedPreferences across every namespace; the working cart, saved carts, trips, fees and rate alerts are Proto DataStore files, their Kotlin generated by Wire from `src/main/proto/currencix/saved.proto` — see [architecture.md](architecture.md).

### Compose

| Dependency | Version |
|---|---|
| `androidx.compose:compose-bom-alpha` | 2026.10.01 — the pre-release BOM: Compose 1.13.0-beta02, Material 3 1.5.0-beta02. Back to `compose-bom` once both are stable |
| `androidx.compose.material3:material3` / `ui` / `foundation` / `runtime` / `runtime-livedata` | via BOM |
| `androidx.activity:activity-compose` | 1.13.0 |
| `androidx.navigation3:navigation3-runtime` / `navigation3-ui` | 1.2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | 2.11.0 (per-screen ViewModel stores) |
| `sh.calvin.reorderable:reorderable` | 3.1.0 |

Hosts the Vico chart plus the rest of the app's UI — every screen is Compose, in one Activity (see [architecture.md](architecture.md)).

### HTTP & Serialisation

| Dependency | Version |
|---|---|
| `com.squareup.okhttp3:okhttp` | 5.5.1 |
| `com.squareup.okhttp3:logging-interceptor` | 5.5.1 |
| `com.squareup.retrofit2:retrofit` | 3.0.0 |
| `com.squareup.retrofit2:converter-moshi` | 3.0.0 |
| `com.squareup.moshi:moshi` / `moshi-kotlin-codegen` (KSP; no reflection) | 1.15.2 |
| `com.google.devtools.ksp` (Gradle plugin) | 2.3.12 |

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

No OCR language data ships in either app: English (tessdata_fast 4.1.0, 4.1 MB) and the app's language download on first use, once the user agrees to each, all checked against the SHA-256s and sizes in `ocr/TessLanguage.kt`.

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

### Release tooling (build plugin, nothing shipped)

| Plugin | Version | Note |
|---|---|---|
| `org.cyclonedx.bom` | 3.5.1 | The release's SBOM: `:app:cyclonedxDirectBom` writes CycloneDX 1.6 JSON (and XML) of `fdroidReleaseRuntimeClasspath` to `app/build/reports/sbom/` |

### Build tooling (Gradle plugins, nothing shipped)

| Plugin | Version | Note |
|---|---|---|
| `org.jetbrains.kotlin.plugin.compose` | 2.4.21 | The Compose compiler, in step with Kotlin |
| `com.diffplug.spotless` | 8.10.4 | ktlint formatting (see [contributing.md](contributing.md#code-style)) |
| `dev.detekt` | 2.0.0-alpha.6 | Static analysis (see [ci-cd.md](ci-cd.md#detekt)) |
| `io.github.ben-manes.versions` | 0.66.0 | `dependencyUpdates`: which dependencies have newer releases |
| `androidx.baselineprofile` | 1.5.0 | Baseline and startup profiles (see [Baseline Profiles](#baseline-profiles)) |

### Testing

| Dependency | Version |
|---|---|
| `junit:junit` | 4.13.2 |
| `org.mockito:mockito-core` | 5.24.0 |
| `androidx.arch.core:core-testing` | 2.2.0 |
| `androidx.work:work-testing` | 2.12.0 |
| `org.junit.jupiter:junit-jupiter-*` / `org.junit.vintage:junit-vintage-engine` | 6.1.3 |
| `com.code-intelligence:jazzer-junit` | 0.30.0 (fuzz testing) |
| `io.github.takahirom.roborazzi:roborazzi` / `roborazzi-compose` | 1.77.0 |
| `org.robolectric:robolectric` | 4.17 |
| `androidx.compose.ui:ui-test-junit4` / `ui-test-manifest` | via BOM |
| `com.lemonappdev:konsist` | 0.18.1 |

Roborazzi + Robolectric give pure-JVM screenshot testing for every Compose surface (no emulator needed), running under the existing `test<Flavor>DebugUnitTest` task via the JUnit vintage engine. Konsist encodes the View / ViewModel / Repository / Model layer boundaries as JUnit tests on the plain JVM, so a refactor can't silently break MVVM separation.

#### Fuzz tests

`FuzzTest` (Jazzer) fuzzes the parsers that take text the app doesn't control, each of which must answer rather than throw on anything: a currency code (`Currency.fromString`), text selected in another app (`PriceParser`), a receipt's scanned text (`ReceiptParser`), a calculation, typed or in an imported cart (`evaluateCalculatorExpression`), a typed amount (`parseDecimal`), and another app's intent to the exported converter (`ConverterLaunch.parse`, `fuzzLaunchIntent`). Jazzer's instrumentation breaks Robolectric, so the unit-test tasks leave `FuzzTest` out, and `-PfuzzTests` runs it alone. Without `JAZZER_FUZZ` only the saved inputs are replayed; with it, one target is fuzzed for two minutes (one per run):

```bash
JAZZER_FUZZ=1 ./gradlew :app:testFdroidDebugUnitTest -PfuzzTests \
  --tests "com.eliormachlev.currencix.FuzzTest.fuzzSelectedPrice"
```

CI does this for each target on every PR (`build.yaml`'s `fuzz` job). A crash fails the run and saves its input under `app/src/test/resources/com/eliormachlev/currencix/FuzzTestInputs/<target>/`; commit it there with the fix, and it's replayed from then on. OpenSSF Scorecard's Fuzzing check doesn't recognise Jazzer (it looks for OSS-Fuzz, ClusterFuzzLite, or fuzzing in Go and a few other languages), so it keeps reporting the project as not fuzzed.

## Dependency verification

`gradle/verification-metadata.xml` lists the SHA-256 of every file the build downloads (libraries, plugins, their POMs and Gradle module files, the tools detekt, ktlint and lint fetch), and Gradle refuses one that doesn't match or isn't listed. After adding or updating a dependency, record the new checksums, either with **Update dependency checksums** in Actions (run from the branch; it commits them) or locally:

```bash
./gradlew --write-verification-metadata sha256 \
  assembleFdroidDebug assemblePlayDebug \
  :app:compileFdroidDebugUnitTestKotlin :app:compilePlayDebugUnitTestKotlin \
  spotlessCheck detekt assembleFdroidRelease :app:cyclonedxDirectBom :app:owaspLibraries \
  :app:lintFdroidDebug :app:lintPlayDebug \
  :app:assembleFdroidBenchmarkRelease :baselineprofile:assembleFdroidBenchmarkRelease :app:bundlePlayRelease
./gradlew --write-verification-metadata sha256 :app:compileFdroidReleaseKotlin -PcomposeReports
```

Then read the diff: it's every new file the build will trust. `dependencyUpdates` asks the repositories about versions the build doesn't use, so it runs with `--dependency-verification lenient`. The list is recorded on Linux; AGP's `aapt2` comes per operating system, so building on macOS or Windows adds that platform's `aapt2` with the same command.

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

**The keys exist** (made 2026-10-23, replacing a first set from 2026-10-17 whose passwords were lost before any release): the release key (`release.p12`, alias `currencix-release`, EC P-521, certificate `CN=CurrenciX`, SHA-256 `B0:87:B0:31:48:D7:73:B4:CE:DA:C0:24:79:F5:0B:2F:EB:C5:C8:08:AF:17:2E:16:7B:2E:D0:BB:68:E1:E0:59`) and the hybrid pair (`hybrid.p12`: `hybrid-classical`, EC P-521, `BF:9A:96:0A:…:21:FB:C3`; `hybrid-pqc`, ML-DSA-65, `45:47:5F:B9:…:A0:03:E6`). Their lineages hold certificates only and are in the repository (`signing-lineages/`). The keystores and passwords are not: the maintainer keeps them in an encrypted backup and as secrets of the `release` environment (Settings → Environments → release, deployment limited to `v*` tags and `master`), which only the release workflow's build job names, so no other workflow or branch can read them.

**Releasing is pushing a tag.** Tag a commit of `master` `v<versionName>` (`v1.23.0`) and push the tag: `release-apk.yaml` checks that the tag names the version being built and is on `master`, decodes the keystores from the secrets `RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `HYBRID_KEYSTORE_B64` and `HYBRID_KEYSTORE_PASSWORD` into the runner's temp folder, writes `secrets.properties`, builds `assembleFdroidRelease` (which signs and verifies every APK, below), deletes the keys, writes the SBOM (`:app:cyclonedxDirectBom`: CycloneDX 1.6 JSON of the fdroid release runtime classpath, every library the APKs ship with its version and licence) and uploads the five APKs, the SBOM as `currencix-<version>-sbom.cdx.json` and a `SHA256SUMS` covering them all. A second job, the only one allowed to write to the repository and one that never sees the keys, then publishes the GitHub release "CurrenciX <version>": the APKs, the SBOM, the checksums and notes written automatically, followed by which APK to pick and the release certificate's fingerprint.

The notes come from `.github/scripts/release-notes.py <tag>`: the commits from the previous `v*` tag (for the first release, the fork's rebrand to CurrenciX, #89, as earlier history is the upstream app's) to this one. `master` takes each pull request as one squashed commit with a Conventional Commits title, so they group by type: **New** (`feat`), **Fixes** (`fix`), **Faster** (`perf`), **Translations** (`i18n`, plus one line naming the languages Weblate updated, should translations come through Weblate again as they did upstream), and **Other changes** for a title in no known style. `chore`, `ci`, `build`, `deps`, `refactor`, `test`, `docs`, `style` and `revert` are left out as not user-facing. Each entry keeps its PR number, which GitHub links, and the notes end with the compare link to the previous release. The PR title is the release note, so title PRs for users. Run by hand, the workflow shows the notes a tag would get in its summary. The fastlane changelog (`changelogs/<versionCode>.txt`, what F-Droid shows, 500 characters at most) is still written by hand. A version with a `-` (`1.24.0-beta1`) is marked a pre-release. F-Droid builds the same tag (`UpdateCheckMode: Tags`). Started by hand instead (Actions → Release → Run workflow), it only builds and uploads the signed APKs, to try a build before tagging it. Nothing else can start it, so pull requests never see the keys, and its Gradle cache is read-only, so nothing from a run that held them is cached.

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

- **CI:** `baseline-profile.yaml` generates each flavor's profiles in a job of
  its own and uploads them as `baseline-profiles-<flavor>`. See [ci-cd.md](ci-cd.md).
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
conversion preview is remembered). CI keeps it that way: the `compose-stability`
job compiles with `-PcomposeReports` (the compiler's report of each composable,
in `app/build/compose-reports/`, also restored from the build cache) and
`.github/scripts/compose-stability.py` fails on a composable that can't skip or
a parameter the compiler can't prove stable (see [ci-cd.md](ci-cd.md)). Locally:

```bash
./gradlew :app:compileFdroidReleaseKotlin -PcomposeReports
python3 .github/scripts/compose-stability.py app/build/compose-reports
```

## Known Issues

### Gradle deprecation: `Configuration.setVisible(boolean)` (AGP)

**Symptom:** During configuration of `:app`, Gradle (9.8.2) prints the line below, and a build ends with "Deprecated Gradle features were used in this build, making it incompatible with Gradle 10":

```
The Configuration.setVisible(boolean) method has been deprecated. This is scheduled to be removed in Gradle 11.
```

**Root cause:** inside **AGP 9.4.1** (`BasePlugin.createAndroidJdkImageConfiguration`, `BasePlugin.kt:362`), not in any build script here (`./gradlew help --warning-mode all -Dorg.gradle.deprecation.trace=true` shows the stack).

**Status:** left visible, as nothing in this repository can fix it. AGP 9.5.0-rc01 no longer makes the call (checked in its `BasePlugin` classes), so it goes away with the move to AGP 9.5.0 once that's stable; that move must happen before Gradle 11. Silencing it (`org.gradle.warning.mode=none`) would hide every other deprecation too, so it isn't done. The earlier AGP warning about using a `Project` object as dependency notation (AGP 9.2.1) no longer appears on AGP 9.4.1.
