# Build

| Tool | Version |
|---|---|
| JDK | 21 or newer (CI uses 25). Bytecode targets 21. Hybrid signing runs on a JDK 25 toolchain Gradle fetches itself, since reading ML-DSA keys needs 24+ ([release.md](release.md#post-quantum-signing-android-17)) |
| Gradle | 9.8.2 (wrapper; its download, from services.gradle.org, retries 5 times, waiting 2, 4, 8, 16 and 32 s (the delay doubles), with a 30 s network timeout: a CI job once failed on a single HTTP 504, as the wrapper's default is one attempt) |
| Android Gradle Plugin | 9.5.0 |
| Kotlin | 2.4.21 |
| SDK | min 33, compile 37.2 (Compose 1.13 needs 37.1+: `release(37) { minorApiLevel = 2 }`), target 37 |

## Flavors

| Flavor | |
|---|---|
| `fdroid` | No Play Services, fully open source, a reproducible-build target ([`apk-checks.yaml`](../../../.github/workflows/apk-checks.yaml) builds it twice and compares the APKs byte for byte) |
| `play` | May use Play APIs |

```bash
./gradlew assembleFdroidDebug            # or assemblePlayDebug
./gradlew assembleFdroidRelease          # signed only with secrets.properties (see release.md)
./gradlew check assembleDebug            # lint + unit tests + debug builds
```

Flavor- and build-type-specific code goes in `app/src/<flavor>/` behind a hook `main` asks for (see [architecture](architecture/README.md#rules-that-hold-everywhere)).

## One APK for every phone, 64-bit only

`assemble*` builds one APK per variant (`assembleDebug` builds both flavors), never one per CPU type. Both flavors carry receipt scanning's native code (`:ocr-engine`) for `arm64-v8a` only (phones, tablets, Arm Chromebooks), about 7.5 MB; the list is `currencix.abis` in [`gradle.properties`](../../../gradle.properties). `x86_64` (Intel and AMD Chromebooks, Windows emulators) isn't supported: the app installs nowhere that runs only x86_64 code. The CI emulators (baseline profiles, benchmarks) are x86_64, so those two workflows pass `-Pcurrencix.abis=arm64-v8a,x86_64` to build the app they install; no release does.

The native libraries are stored uncompressed (`useLegacyPackaging = false`) and 16 KB page-aligned, so Android loads them straight from the APK instead of unpacking a copy on install: the device keeps one copy and installs faster. The cost is download size: the APK carries the code at full size, about 7.5 MB, instead of about 3.2 MB compressed, so about 4 MB more (v0.1.1, which carried both CPU types compressed: 16.6 MB APK). Every library is built for 16 KB memory pages, which Play requires and which loading from the APK needs. Play gets the app bundle and builds each device's APK from it.

**64-bit only.** The 32-bit types (`armeabi-v7a`, `x86`) were dropped at 1.23.0, before the first release. Neither Android's manifest nor the Play Console can say "64-bit only" (the Console's device catalog excludes by model, RAM, chip or Android Go edition, not by CPU type), but both go by the native code an app carries: with code for 64-bit types only, Android refuses the APK on a phone that runs only 32-bit apps (some low-cost Android Go phones), and Play doesn't offer it there. The receipt reader used to be an on-demand Play module, which left the Play app itself with no native code, installable on such a phone with scanning broken; it's now in the app for both flavors, so every store refuses those phones alike, at ~7 MB more per Play download.

Release builds are shrunk, optimized and obfuscated by R8 with AGP's `proguard-android-optimize.txt` and no rules of the app's own: full mode (the default since AGP 8.0), access modification (in that file) and class repackaging (the default for apps since AGP 9.1) are already on, so extra flags for them change nothing. The libraries' consumer rules keep what must keep its name.

One APK means one version code, one F-Droid build and one file for F-Droid to reproduce and publish with this project's signature ([release.md](release.md#f-droid-draft-recipe-for-fdroiddata)), which stays true with one CPU type. Version codes: [release.md](release.md#version-codes).

## Dependency choices

Versions live in the build files; Dependabot keeps them current. What's worth knowing:

- **Kotlin BOM** at the plugin's version aligns every Kotlin library, so no old `kotlin-stdlib-jdk7` / `-jdk8` arrives through Reorderable or another library.
- **No `com.google.android.material`, no AppCompat:** Compose Material 3 draws everything, and the platform sets the app's dark mode (`UiModeManager`) and language (`LocaleManager`).
- **Compose pre-release BOM** (`compose-bom-alpha`: Compose 1.13 beta, Material 3 1.5 beta), back to `compose-bom` once both are stable. Navigation 3, with a retain decorator (`RetainedValuesStoreNavEntryDecorator`) giving each screen its own `retain` store (`runtime-retain`).
- **Persistence:** DataStore (preferences and proto), with Wire generating the Protobuf Kotlin.
- **HTTP and JSON:** OkHttp and Retrofit with its kotlinx.serialization converter: each provider's response decodes into `@Serializable` classes, their serializers generated by the Kotlin serialization plugin, so no field is read by reflection (and there is no KSP). Retrofit itself still builds each API interface as a runtime proxy and finds the response's serializer by its type.
- **Calculator: no dependency.** [`util/Arithmetic.kt`](../../../app/src/main/kotlin/com/eliormachlev/currencix/util/Arithmetic.kt) evaluates `+ − × ÷`, brackets and percent in ~100 lines of `BigDecimal` recursive descent (68 significant digits). EvalEx was dropped because its Lombok marker needed a `-dontwarn`; mXparser before it has a license F-Droid can't take.
- **Receipt scanning:** Tesseract4Android (Apache-2.0, native, in `:ocr-engine`) comes only from JitPack, which [`settings.gradle`](../../../settings.gradle) allows for that one group (`exclusiveContent`); [`ocr-engine/consumer-rules.pro`](../../../ocr-engine/consumer-rules.pro) keeps the packages its native code finds by name. Both flavors carry it, 64-bit only ([above](#one-apk-for-every-phone-64-bit-only)). No OCR language data ships and none is chosen by default, English included: each language downloads only once the user agrees, checked against the SHA-256s in [`ocr/TessLanguage.kt`](../../../app/src/main/kotlin/com/eliormachlev/currencix/ocr/TessLanguage.kt) ([features.md](architecture/features.md#receipt-scanning)).
- **Crypto:** Bouncy Castle (pure-Java Argon2id) and Tink (AES-GCM, Keystore AEAD); see [security.md](security.md).
- **Logging and debug tools:** Timber writes a local rotating file only. Chucker is debug-only (`library-no-op` in release); LeakCanary and JankStats are debug-only.
- **Build plugins:** Spotless (ktlint), detekt, `ben-manes.versions` (`dependencyUpdates`), `androidx.baselineprofile`, CycloneDX (the release SBOM), Wire (Protobuf), the Kotlin Compose and serialization compiler plugins, Roborazzi, Kover and Licensee.
- **Tests:** JUnit 4 and Jupiter (vintage engine), Mockito, Robolectric, Roborazzi (JVM screenshots of every Compose surface, no emulator), Konsist (layer boundaries), Jazzer (fuzzing), `kotlinx-coroutines-test` and Turbine (coroutines and Flows on a virtual clock).
- **Robolectric runs offline.** The Android jar it runs tests on (`android-all-instrumented`, SDK 34) is resolved by Gradle like any other dependency, so it's cached and checked against [`verification-metadata.xml`](../../../gradle/verification-metadata.xml). The tests then read it from `app/build/robolectric-jars` and never download it at test time; the fuzz and live-provider runs, which don't use Robolectric, don't fetch it. A new Robolectric release can need a new jar: update `robolectricAndroidAll` in [`app/build.gradle.kts`](../../../app/build.gradle.kts) with it (Dependabot ignores that coordinate, since its "newer" versions are other SDKs).
- **Coverage and licenses:** Kover measures the unit tests' line and branch coverage (`koverXmlReport<Variant>`; CI puts a summary on each test run and keeps the report as a `coverage-<flavor>` artifact for 14 days; a pull request also gets a comment with the F-Droid figures and their change from master's last run, [`coverage-summary.py`](../../../.github/scripts/coverage-summary.py)); it stays off for the fuzz runs, which Jazzer instruments. Licensee (`licenseeAndroidFdroidRelease`, in the F-Droid lint job) fails on a library whose license isn't Apache-2.0 or BSD-3-Clause; two libraries are allowed by name ([exceptions.md #17](exceptions.md#17-licensee-bouncy-castles-license-named-by-url), [exceptions.md #18](exceptions.md#18-licensee-tesseract4android-declares-no-license)).

## Live provider tests

`ExchangeRatesServiceTest` calls each provider's real API and checks the rates and timeline it returns. Unit-test tasks leave it out, so a provider's outage doesn't fail the build gate (the same parsing is checked on recorded responses in `ProviderDecodingTest`); `-PliveProviders` runs it alone:

```bash
./gradlew :app:testFdroidDebugUnitTest -PliveProviders
```

CI runs it in [`live-providers.yaml`](../../../.github/workflows/live-providers.yaml): on a PR that changes [`model/`](../../../app/src/main/kotlin/com/eliormachlev/currencix/model), [`repository/`](../../../app/src/main/kotlin/com/eliormachlev/currencix/repository), [`util/`](../../../app/src/main/kotlin/com/eliormachlev/currencix/util) or the app's build file, so a change that breaks a live integration shows on that PR, and daily, so a provider's API change or outage shows without one. It isn't a required check; read it on such a PR before merging.

## Fuzz tests

`FuzzTest` (Jazzer) fuzzes every parser of input the app doesn't control, each of which must answer rather than throw: `Currency.fromString`, `PriceParser`, `ReceiptParser`, `evaluateCalculatorExpression`, `parseDecimal` and `ConverterLaunch.parse`. Jazzer's instrumentation breaks Robolectric, so unit-test tasks leave it out and `-PfuzzTests` runs it alone; without `JAZZER_FUZZ` it only replays saved inputs:

```bash
JAZZER_FUZZ=1 ./gradlew :app:testFdroidDebugUnitTest -PfuzzTests \
  --tests "com.eliormachlev.currencix.FuzzTest.fuzzSelectedPrice"
```

CI fuzzes each target for two minutes per PR. A crash saves its input under `app/src/test/resources/.../FuzzTestInputs/<target>/`; commit it with the fix and it's replayed from then on. Scorecard's Fuzzing check doesn't recognise Jazzer, so it keeps reporting the project as unfuzzed.

## Dependency verification

[`gradle/verification-metadata.xml`](../../../gradle/verification-metadata.xml) holds the SHA-256 of every file the build downloads, and Gradle refuses anything unlisted or mismatched. After a dependency change, run **Update dependency checksums** in Actions on the branch (it commits them), or locally:

```bash
RECORD=(--write-verification-metadata sha256 --no-configuration-cache)
./gradlew "${RECORD[@]}" \
  assembleFdroidDebug assemblePlayDebug \
  spotlessCheck detekt assembleFdroidRelease :app:cyclonedxDirectBom :app:spdxSbom :app:owaspLibraries \
  :app:lintFdroidDebug :app:lintPlayDebug \
  :app:assembleFdroidBenchmarkRelease :baselineprofile:assembleFdroidBenchmarkRelease :app:bundlePlayRelease
./gradlew "${RECORD[@]}" :app:testFdroidDebugUnitTest :app:testPlayDebugUnitTest \
  :app:koverXmlReportFdroidDebug :app:koverXmlReportPlayDebug :app:licenseeAndroidFdroidRelease
./gradlew "${RECORD[@]}" :app:compileFdroidReleaseKotlin -PcomposeReports
./gradlew "${RECORD[@]}" :app:testFdroidDebugUnitTest -PfuzzTests --tests "com.eliormachlev.currencix.FuzzTest.fuzzLaunchIntent"
python3 .github/scripts/trust-mirror-copies.py ~/.gradle gradle/verification-metadata.xml
PROPS=gradle/wrapper/gradle-wrapper.properties
curl -fsSL --proto =https "$(python3 .github/scripts/gradle-sources-checksum.py url "$PROPS")" -o /tmp/gradle-src.zip.sha256
python3 .github/scripts/gradle-sources-checksum.py record "$PROPS" gradle/verification-metadata.xml /tmp/gradle-src.zip.sha256
```

Then read the diff: it's every new file the build will trust.

- `--no-configuration-cache`: with a stored configuration Gradle never resolves the plugins, so their files go unrecorded and every CI run fails on them. The tests are listed because running them resolves libraries compiling doesn't.
- [`trust-mirror-copies.py`](../../../.github/scripts/trust-mirror-copies.py): some files differ between repositories (a plugin's marker POM on Maven Central vs the Plugin Portal), and a build takes whichever answers first, so every copy in Gradle's cache is trusted (`<also-trust>`).
- [`gradle-sources-checksum.py`](../../../.github/scripts/gradle-sources-checksum.py): Gradle's own source zip, which IDE syncs download and the build never does, recorded with Gradle's published checksum. Rerun after a Gradle update.
- Libraries' `-sources.jar` / `-javadoc.jar`, also IDE-only, are accepted without a checksum ([exceptions.md #9](exceptions.md#9-dependency-verification--sourcesjar-and--javadocjar)).
- **From scratch:** recording keeps every entry already there, so a version nothing asks for any more stays listed, and Scorecard and OSV-Scanner, which read this file, still count it. Running the workflow with *from scratch* ([`verification-metadata-reset.py`](../../../.github/scripts/verification-metadata-reset.py)) empties the list first; macOS and Windows `aapt2` entries go too until someone builds there again.
- `dependencyUpdates` asks about versions the build doesn't use, so it runs with `--dependency-verification lenient`. AGP's `aapt2` is per OS and the list is recorded on Linux; building on macOS or Windows adds theirs with the same command.

## Build tools' own libraries

The build's tools (the Android Gradle plugin, lint, detekt, CycloneDX and the like) bring in libraries of their own, some at versions with published vulnerabilities: old Guava, Jackson, Bouncy Castle, Logback, jdom2, jose4j, httpclient, commons-lang3, Wire 6 and okio 2. None ships in the app; they run only at build time, on CI's own sources. [`settings.gradle`](../../../settings.gradle) raises each one to the first release that fixes it (`patchedLibraries`), on the plugins' classpath, in every project's configurations, and in what each library itself asks for (a component metadata rule, which also reaches tools that resolve outside any configuration, such as the ktlint Spotless fetches), and leaves a newer version alone. An entry can go once nothing asks for a version older than it. To check, scan the verification metadata as Scorecard does:

```bash
osv-scanner scan source -L gradle/verification-metadata.xml
```

## Baseline profiles

`:baselineprofile` generates the baseline and startup profiles `ProfileInstaller` hands to ART, committed under `app/src/<flavor>Release/generated/baselineProfiles/`. The journeys ([`Journeys.kt`](../../../baselineprofile/src/main/kotlin/com/eliormachlev/currencix/baselineprofile/Journeys.kt)) launch, type, fling the currency picker, visit the chart and cart, fill and fling a 15-row cart, and open each Settings category, finding elements by `UiTestTags`, never visible text. They run on a Gradle Managed Device (`pixel6Api34`, needs KVM):

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

[`app/compose-stability.conf`](../../../app/compose-stability.conf) declares JDK value types (`BigDecimal`, `java.time`, `Locale`, `DateTimeFormatter`) stable, so composables taking rates and dates skip on equal values; hot-path parameter objects are `@Immutable` values. CI fails on a composable that can't skip or a parameter the compiler can't prove stable:

```bash
./gradlew :app:compileFdroidReleaseKotlin -PcomposeReports
python3 .github/scripts/compose-stability.py app/build/compose-reports
```

## Unit tests on the profile build types

The baseline profile plugin turns unit tests off for the build types it adds (`benchmarkRelease`, `nonMinifiedRelease`). Since AGP 9.5.0, every variant with Compose gets a `generate<Variant>ComposePreviewRunfiles` task, which fails to configure without unit tests, and an IDE sync (Android Studio, Qodana's project import) configures every task, so the sync failed. Neither AGP nor the plugin has a setting for it. [`app/build.gradle.kts`](../../../app/build.gradle.kts) turns unit tests back on for those build types (`enableUnitTest`, in `beforeVariants`) and disables their unit-test tasks (`test` and `compile` …`UnitTest`…), so they still compile and run none.

## Known issue: `Configuration.setVisible(boolean)` deprecation

A build still ends with Gradle's "incompatible with Gradle 10" line. The deprecation was inside AGP 9.4.1 (`BasePlugin.createAndroidJdkImageConfiguration`), not in any build script here; which one AGP 9.5.0 reports hasn't been checked (`./gradlew help --warning-mode all` lists each and where it comes from). It stays visible: `org.gradle.warning.mode=none` would hide every other deprecation too ([contributing.md](contributing.md#no-suppressions-no-cosmetic-workarounds)).
