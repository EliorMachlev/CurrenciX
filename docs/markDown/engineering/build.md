# Build

| Tool | Version |
|---|---|
| JDK | 21 or newer (CI uses 25). Bytecode targets 21; hybrid signing needs 24+, fetched as a toolchain |
| Gradle | 9.8.2 (wrapper) |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.21 |
| SDK | min 33, compile 37.2 (Compose 1.13 needs 37.1+: `release(37) { minorApiLevel = 2 }`), target 37 |

## Flavors

| Flavor | |
|---|---|
| `fdroid` | No Play Services, fully open source, a reproducible-build target. Carries the receipt recognizer |
| `play` | May use Play APIs; installs the receipt recognizer on demand (Play Feature Delivery) |

```bash
./gradlew assembleFdroidDebug            # or assemblePlayDebug
./gradlew assembleFdroidRelease          # signed only with secrets.properties (see release.md)
./gradlew check assembleDebug            # lint + unit tests + debug builds
```

Flavor- and build-type-specific code goes in `app/src/<flavor>/` behind a hook `main` asks for (see [architecture](architecture/README.md#rules-that-hold-everywhere)).

## APKs per CPU type

`assemble*` builds one APK per CPU type plus a universal one (`splits.abi`): receipt scanning's native code is ~7 MB per type. Native libraries are stored compressed (`useLegacyPackaging`). At 1.23.0: ~14.5 MB per type, 24 MB universal (40.5 MB before both). `bundle*` builds no splits (AGP can't do both in one run; Play splits bundles itself). Version codes and names per APK: [release.md](release.md#version-codes).

## Dependency choices

Versions live in the build files; Dependabot keeps them current. What's worth knowing:

- **Kotlin BOM** at the plugin's version aligns every Kotlin library, so no old `kotlin-stdlib-jdk7` / `-jdk8` arrives through Moshi or Reorderable.
- **No `com.google.android.material`:** appcompat-only chrome plus Compose Material 3.
- **Compose pre-release BOM** (`compose-bom-alpha`: Compose 1.13 beta, Material 3 1.5 beta), back to `compose-bom` once both are stable. Navigation 3 with `lifecycle-viewmodel-navigation3` for per-screen ViewModel stores.
- **Persistence:** DataStore (preferences and proto), with Wire generating the Protobuf Kotlin.
- **HTTP:** OkHttp and Retrofit with the Moshi converter; Moshi via KSP codegen, no reflection.
- **Calculator: no dependency.** `util/Arithmetic.kt` evaluates `+ − × ÷`, brackets and percent in ~100 lines of `BigDecimal` recursive descent (68 significant digits). EvalEx was dropped because its Lombok marker needed a `-dontwarn`; mXparser before it has a license F-Droid can't take.
- **Receipt scanning:** Tesseract4Android (Apache-2.0, native, in `:ocr-engine`) comes only from JitPack, which `settings.gradle` allows for that one group (`exclusiveContent`); `ocr-engine/consumer-rules.pro` keeps the packages its native code finds by name. `feature-delivery-ktx` is `playImplementation` only. `:ocr` is a dynamic feature with just a manifest, declared only for bundle builds. No OCR language data ships; English and others download on consent, checked against the SHA-256s in `ocr/TessLanguage.kt`.
- **Crypto:** Bouncy Castle (pure-Java Argon2id) and Tink (AES-GCM, Keystore AEAD); see [security.md](security.md).
- **Logging and debug tools:** Timber writes a local rotating file only. Chucker is debug-only (`library-no-op` in release); LeakCanary and JankStats are debug-only.
- **Build plugins:** Spotless (ktlint), detekt, `ben-manes.versions` (`dependencyUpdates`), `androidx.baselineprofile`, CycloneDX (the release SBOM).
- **Tests:** JUnit 4 and Jupiter (vintage engine), Mockito, Robolectric, Roborazzi (JVM screenshots of every Compose surface, no emulator), Konsist (layer boundaries), Jazzer (fuzzing).

## Fuzz tests

`FuzzTest` (Jazzer) fuzzes every parser of input the app doesn't control, each of which must answer rather than throw: `Currency.fromString`, `PriceParser`, `ReceiptParser`, `evaluateCalculatorExpression`, `parseDecimal` and `ConverterLaunch.parse`. Jazzer's instrumentation breaks Robolectric, so unit-test tasks leave it out and `-PfuzzTests` runs it alone; without `JAZZER_FUZZ` it only replays saved inputs:

```bash
JAZZER_FUZZ=1 ./gradlew :app:testFdroidDebugUnitTest -PfuzzTests \
  --tests "com.eliormachlev.currencix.FuzzTest.fuzzSelectedPrice"
```

CI fuzzes each target for two minutes per PR. A crash saves its input under `app/src/test/resources/.../FuzzTestInputs/<target>/`; commit it with the fix and it's replayed from then on. Scorecard's Fuzzing check doesn't recognise Jazzer, so it keeps reporting the project as unfuzzed.

## Dependency verification

`gradle/verification-metadata.xml` holds the SHA-256 of every file the build downloads, and Gradle refuses anything unlisted or mismatched. After a dependency change, run **Update dependency checksums** in Actions on the branch (it commits them), or locally:

```bash
RECORD=(--write-verification-metadata sha256 --no-configuration-cache)
./gradlew "${RECORD[@]}" \
  assembleFdroidDebug assemblePlayDebug \
  spotlessCheck detekt assembleFdroidRelease :app:cyclonedxDirectBom :app:owaspLibraries \
  :app:lintFdroidDebug :app:lintPlayDebug \
  :app:assembleFdroidBenchmarkRelease :baselineprofile:assembleFdroidBenchmarkRelease :app:bundlePlayRelease
./gradlew "${RECORD[@]}" :app:testFdroidDebugUnitTest :app:testPlayDebugUnitTest
./gradlew "${RECORD[@]}" :app:compileFdroidReleaseKotlin -PcomposeReports
./gradlew "${RECORD[@]}" :app:testFdroidDebugUnitTest -PfuzzTests --tests "com.eliormachlev.currencix.FuzzTest.fuzzLaunchIntent"
python3 .github/scripts/trust-mirror-copies.py ~/.gradle gradle/verification-metadata.xml
PROPS=gradle/wrapper/gradle-wrapper.properties
curl -fsSL --proto =https "$(python3 .github/scripts/gradle-sources-checksum.py url "$PROPS")" -o /tmp/gradle-src.zip.sha256
python3 .github/scripts/gradle-sources-checksum.py record "$PROPS" gradle/verification-metadata.xml /tmp/gradle-src.zip.sha256
```

Then read the diff: it's every new file the build will trust.

- `--no-configuration-cache`: with a stored configuration Gradle never resolves the plugins, so their files go unrecorded and every CI run fails on them. The tests are listed because running them resolves libraries compiling doesn't.
- `trust-mirror-copies.py`: some files differ between repositories (a plugin's marker POM on Maven Central vs the Plugin Portal), and a build takes whichever answers first, so every copy in Gradle's cache is trusted (`<also-trust>`).
- `gradle-sources-checksum.py`: Gradle's own source zip, which IDE syncs download and the build never does, recorded with Gradle's published checksum. Rerun after a Gradle update.
- Libraries' `-sources.jar` / `-javadoc.jar`, also IDE-only, are accepted without a checksum ([exceptions.md](exceptions.md) #9).
- `dependencyUpdates` asks about versions the build doesn't use, so it runs with `--dependency-verification lenient`. AGP's `aapt2` is per OS and the list is recorded on Linux; building on macOS or Windows adds theirs with the same command.

## Baseline profiles

`:baselineprofile` generates the baseline and startup profiles `ProfileInstaller` hands to ART, committed under `app/src/<flavor>Release/generated/baselineProfiles/`. The journeys (`Journeys.kt`) launch, type, fling the currency picker, visit the chart and cart, fill and fling a 15-row cart, and open each Settings category, finding elements by `UiTestTags`, never visible text. They run on a Gradle Managed Device (`pixel6Api34`, needs KVM):

```bash
./gradlew :app:generateFdroidReleaseBaselineProfile :app:generatePlayReleaseBaselineProfile \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Regenerate when hot paths shift materially. F-Droid APKs carry the profile too (`assets/dexopt/baseline.prof`); outside Play it's written on first launch and compiled at ART's next idle maintenance, so the first launches after an update run without it.

**Benchmarks:** `InteractionBenchmarks` measures cold start and frame timing (typing, picker, transitions, cart, Settings), with and without the profile; watch P90/P99 `frameDurationCpuMs` and `frameOverrunMs > 0`. Emulator numbers are for catching regressions, not absolute timings, so Macrobenchmark's emulator guard is suppressed.

```bash
./gradlew :baselineprofile:pixel6Api34FdroidBenchmarkReleaseAndroidTest \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

## Compose stability

`app/compose-stability.conf` declares JDK value types (`BigDecimal`, `java.time`, `Locale`, `DateTimeFormatter`) stable, so composables taking rates and dates skip on equal values; hot-path parameter objects are `@Immutable` values. CI fails on a composable that can't skip or a parameter the compiler can't prove stable:

```bash
./gradlew :app:compileFdroidReleaseKotlin -PcomposeReports
python3 .github/scripts/compose-stability.py app/build/compose-reports
```

## Known issue: `Configuration.setVisible(boolean)` deprecation

Gradle warns about it while configuring `:app`, and a build ends "incompatible with Gradle 10". It's inside AGP 9.4.1 (`BasePlugin.createAndroidJdkImageConfiguration`), not in any build script here. Left visible: AGP 9.5.0 no longer makes the call, so it goes with that upgrade, which must happen before Gradle 11. `org.gradle.warning.mode=none` would hide every other deprecation too.
