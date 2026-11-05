# Exceptions to "No Suppressions"

[contributing.md](contributing.md#no-suppressions-no-cosmetic-workarounds) forbids hiding a warning instead of fixing its cause. This file is the complete list of places where the project does it anyway, and why. If a suppression is not listed here, it is not allowed.

## What qualifies

An exception is for a finding that is wrong about code that is right:

- the code is correct as written, and
- no change would satisfy the tool short of removing something that works, and
- the maintainer has decided to exclude it rather than leave it visible.

A finding that is merely hard, slow or unpleasant to fix does not qualify. Fix it, or leave the warning visible and say so in the PR.

## Current exceptions

Grouped by where the excluded finding lives. Each exception keeps its number for good, wherever it is listed: code and other docs refer to them by it.

### Build exceptions

The build's own tools and compilers: Gradle and the Android Gradle plugin, and code the Kotlin and Compose compilers generate that CodeQL reads as if it were source.

| # | Tool and rule | Excluded for | Marked in |
|---|---|---|---|
| [4](#4-to-6-incubating-android-gradle-plugin-apis) | Qodana / IntelliJ `UnstableApiUsage` | the Gradle Managed Device (`ManagedVirtualDevice`) | [`baselineprofile/build.gradle.kts`](../../../baselineprofile/build.gradle.kts) |
| [5](#4-to-6-incubating-android-gradle-plugin-apis) | Qodana / IntelliJ `UnstableApiUsage` | `testedApks` (the tested app's id for benchmarks) | `baselineprofile/build.gradle.kts` |
| [6](#4-to-6-incubating-android-gradle-plugin-apis) | Qodana / IntelliJ `UnstableApiUsage` | `generateLocaleConfig` | [`app/build.gradle.kts`](../../../app/build.gradle.kts) |
| [8](#8-codeql-javauseless-null-check-on-quickconversionssheet) | CodeQL `java/useless-null-check` | `QuickConversionsSheet` (a check the Kotlin compiler generates, line 1) | [`.github/codeql/exceptions.yml`](../../../.github/codeql/exceptions.yml), applied by [`.github/workflows/codeql.yaml`](../../../.github/workflows/codeql.yaml) |
| [9](#9-dependency-verification--sourcesjar-and--javadocjar) | Gradle dependency verification, `trusted-artifacts` | libraries' `-sources.jar` and `-javadoc.jar` (downloaded only by IDE syncs) | [`gradle/verification-metadata.xml`](../../../gradle/verification-metadata.xml) |
| [10](#10-codeql-javafield-masks-super-field-on-the-compose-compilers-stable) | CodeQL `java/field-masks-super-field` | the Compose compiler's `$stable` field, in 2 files (line 1) | `.github/codeql/exceptions.yml`, applied by `.github/workflows/codeql.yaml` |
| [11](#11-codeql-javalocal-variable-is-never-read-in-data-objects-equals) | CodeQL `java/local-variable-is-never-read` | the cast in data objects' generated `equals()`, in 11 files (line 1) | `.github/codeql/exceptions.yml`, applied by `.github/workflows/codeql.yaml` |
| [20](#20-androidx-benchmark-the-emulator-error) | AndroidX Benchmark `androidx.benchmark.suppressErrors` | `EMULATOR`: baseline profiles and benchmarks on CI's managed emulator | [`baselineprofile/build.gradle.kts`](../../../baselineprofile/build.gradle.kts) |

#### 4 to 6. Incubating Android Gradle Plugin APIs

- **What the rule says:** the build script calls an API AGP marks `@Incubating`, which may change in a later version.
- **Why it can't be fixed:** each is the only API AGP has for what it does, with no stable equivalent: declaring the managed emulator that generates baseline profiles and runs benchmarks (`ManagedVirtualDevice`: `localDevices`, `device`, `apiLevel`, `systemImageSource`), reading the tested app's id for the benchmark runner (`testedApks`), and generating the per-app language list from the translations (`generateLocaleConfig`). The alternatives are dropping the feature or a hand-kept copy: an emulator set up by hand in CI, a hard-coded app id, a hand-kept locale list.
- **Why the rule is wrong here:** incubating is not deprecated; these are the APIs AGP's own documentation uses. If AGP changes one, the build fails at once, so nothing breaks quietly.
- **What is excluded:** one `@Suppress("UnstableApiUsage")` on each of those three statements, never the file. Any other incubating API used later is still reported.

#### 8. CodeQL: `java/useless-null-check` on `QuickConversionsSheet`

- **What it reports:** "This check is useless. `tmpEnum` cannot be null at this check", at line 1 of [`view/main/compose/QuickConversionsSheet.kt`](../../../app/src/main/kotlin/com/eliormachlev/currencix/view/main/compose/QuickConversionsSheet.kt).
- **Why the finding is wrong:** the file has no such check, and no variable called `tmpEnum`. CodeQL reads Kotlin as the compiler lowers it, and the null check is one the Kotlin/Compose compiler generates there (the only enum in the file is the nullable `PillSide` that picks the currency picker's side); that's why it sits on line 1, where no code is. It first showed up when PR [#203](https://github.com/EliorMachlev/CurrenciX/pull/203) turned on CodeQL for Kotlin.
- **Why it can't be fixed:** the check isn't in the source, so no edit to the source removes it except restructuring working code until the compiler happens to generate something else, which is a cosmetic workaround.
- **What is excluded:** that one finding: the rule, that file, line 1, and a message naming `tmpEnum` must all match ([`.github/codeql/exceptions.yml`](../../../.github/codeql/exceptions.yml)). The Kotlin analysis writes its results, [`.github/scripts/codeql-exceptions.py`](../../../.github/scripts/codeql-exceptions.py) takes that finding out, and the rest is uploaded. The rule still runs everywhere, that file included: a real useless null check in it, on any other line, is still reported. The step fails when the entry stops matching (a newer Kotlin generating different code), so the exception can't outlive the finding.

#### 9. Dependency verification: `-sources.jar` and `-javadoc.jar`

- **What it reports:** an IDE's Gradle sync (Android Studio, and Qodana, which imports the project the same way) fails dependency verification: it downloads each library's `-sources.jar` (and `-javadoc.jar` where one exists), and [`gradle/verification-metadata.xml`](../../../gradle/verification-metadata.xml) lists no checksum for them.
- **Why it can't be fixed:** the build itself never resolves these files, so recording them takes an IDE sync against an empty cache, and which ones a sync fetches depends on the IDE and its version. Listing them would mean regenerating the file from every developer's IDE and on every IDE update. Not downloading sources (`idea { module { isDownloadSources = false } }`) avoids the failure only by taking library sources away in the IDE.
- **Why it's safe:** these files are documentation for the IDE. Nothing compiles against them, nothing runs them, and none goes into an APK, the SBOM or a test. Gradle's own documentation gives this exclusion for exactly this case.
- **What is excluded:** files named `*-sources.jar` and `*-javadoc.jar` are accepted without a checksum (`<trusted-artifacts>` in the file's configuration). Every other file the build downloads (JARs, AARs, POMs, Gradle module files, plugins) is still checked, and a missing or changed checksum still fails the build.

#### 10. CodeQL: `java/field-masks-super-field` on the Compose compiler's `$stable`

- **What it reports:** "This field shadows another field called `$stable` in a superclass", at line 1 of 2 files: `PrefStore`, `CurrencyWidget`.
- **Why the finding is wrong:** no class in the source declares `$stable`. The Compose compiler adds a static `$stable` field (its record of whether the class is stable) to every class it compiles, so a class whose superclass was also compiled by it (the app's `CachedStore`, Glance's `GlanceAppWidget` and `GlanceAppWidgetReceiver`) has one that "shadows" the superclass's. Static fields aren't inherited the way the rule worries about, and nothing reads them by name. Line 1 is where CodeQL puts generated code.
- **Why it can't be fixed:** the field isn't in the source. Removing it would mean these classes no longer extend those superclasses, rewriting working code so the compiler happens not to generate it.
- **What is excluded:** each finding by rule, file, line 1 and a message naming `$stable`. The rule still runs everywhere: a field in the source shadowing a superclass's, in these files or any other, is reported.

#### 11. CodeQL: `java/local-variable-is-never-read` in data objects' `equals()`

- **What it reports:** "Variable '… `tmp0_other_with_cast`' is never read", at line 1 of 11 files: `OcrEngine`, `BackupManager`, `CartExporter`, `SelectionConversion`, `ConverterLaunch`, `Screen`, `FeesScreen`, `PreferenceScreen`, `ReceiptLanguagesScreen`, `TripChecklist`, `BackupViewModel`.
- **Why the finding is wrong:** for a `data object` the Kotlin compiler generates `equals()`, which casts `other` to a temporary (`tmp0_other_with_cast`) and then has no properties to compare, so it never reads it. The variable exists only in the compiled code.
- **Why it can't be fixed:** the only way to remove the generated `equals()` would be turning the data objects (sealed states, navigation keys, results) into plain objects, which changes their `toString()` and `equals()` for no reason but the finding.
- **What is excluded:** each finding by rule, file, line 1 and a message naming `tmp0_other_with_cast`. An unread variable written in the source is still reported, in these files too.

#### 20. AndroidX Benchmark: the `EMULATOR` error

- **What the check says:** Macrobenchmark fails a run on an emulator (`EMULATOR`), which "isn't representative of real user devices".
- **Why it can't be fixed:** the fix is a physical device. CI has none: the baseline profiles are generated, and the benchmarks run, on a Gradle Managed Device (an API 34 emulator, [#4](#4-to-6-incubating-android-gradle-plugin-apis)), and a device farm would be a paid service outside the project's setup. Without the setting, neither job can run at all.
- **Why the check is wrong here:** the numbers are used to compare a build with and without its profile on the same emulator, and to catch large regressions, never as absolute timings. A baseline profile lists the code a run uses, which is the same on an emulator. The setting turns the error into a warning, which every run still prints ([Macrobenchmark instrumentation arguments](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args)).
- **What is excluded:** `EMULATOR` alone, the one argument in that file. Every other benchmark error that applies to a run (such as `DEBUGGABLE` or `LOW-BATTERY`) still fails it.

### Package exceptions

Code the app depends on but doesn't write: libraries and the phone's framework.

| # | Tool and rule | Excluded for | Marked in |
|---|---|---|---|
| [3](#3-leakcanary-resourcesimplmappcontext) | LeakCanary IgnoredReferenceMatcher (debug builds) | `ResourcesImpl.mAppContext` (vendor framework field) | [`app/src/debug/kotlin/com/eliormachlev/currencix/leaks/LeakDetection.kt`](../../../app/src/debug/kotlin/com/eliormachlev/currencix/leaks/LeakDetection.kt) |
| [7](#7-owasp-dependency-check-cve-2018-8909-and-cve-2023-22737-on-wire-runtime) | OWASP Dependency-Check `CVE-2018-8909`, `CVE-2023-22737` | `wire-runtime` (Square's Wire Protobuf library) | [`config/owasp/suppressions.xml`](../../../config/owasp/suppressions.xml), passed in by [`.github/workflows/owasp-dependency-check.yaml`](../../../.github/workflows/owasp-dependency-check.yaml) |
| [17](#17-licensee-bouncy-castles-license-named-by-url) | Licensee: a license named by URL only | `bcprov` (Bouncy Castle), its license page | `licensee {}` in [`app/build.gradle.kts`](../../../app/build.gradle.kts) (`allowUrl`) |
| [18](#18-licensee-tesseract4android-declares-no-license) | Licensee: no license declared | `tesseract4android` 4.8.0 (Tesseract4Android) | `licensee {}` in `app/build.gradle.kts` (`allowDependency`) |
| [19](#19-dependabot-mcp-1290-in-semgreps-packages) | Dependabot: `mcp` 1.29.0 (GHSA-rwrf-2pqf-9j8j, GHSA-5h93-6whr-6q8j, GHSA-w4fh-qvv9-3v23) | Semgrep's MCP SDK, in [`.github/semgrep/requirements.txt`](../../../.github/semgrep/requirements.txt) | Dismissed on GitHub: Dependabot alerts [#44](https://github.com/EliorMachlev/CurrenciX/security/dependabot/44), [#45](https://github.com/EliorMachlev/CurrenciX/security/dependabot/45) and [#46](https://github.com/EliorMachlev/CurrenciX/security/dependabot/46), "vulnerable code is not actually used" |

#### 3. LeakCanary: `ResourcesImpl.mAppContext`

- **What it reports:** a "library leak" in debug builds on Xiaomi's Android and other vendors' builds from the same fork: a static `ResourcesImpl.mAppContext` field, added by the vendor, keeps the context of the last service that set up resources, here WorkManager's `SystemJobService` after it finished (about 1 kB).
- **Why it can't be fixed:** the field is in the phone's framework, not in the app or WorkManager. The app holds no reference it could release; clearing a private framework field by reflection would be fragile, vendor-specific, and done only to quiet the report. It is held one context at a time and replaced by the next service, so it doesn't grow.
- **What is excluded:** `configureLeakDetection` (debug source set) takes LeakCanary's own known-leak list and turns that one static field from "report as a library leak" into "ignore". Every other known leak and every app leak is still reported; release builds have no LeakCanary.

#### 7. OWASP Dependency-Check: `CVE-2018-8909` and `CVE-2023-22737` on `wire-runtime`

- **What it reports:** two vulnerabilities in `wire-runtime-jvm-7.1.0.jar`, which it identifies as Wire Swiss's products: `CVE-2018-8909` (CVSS 7.5, `cpe:2.3:a:wire:wire`) and `CVE-2023-22737` (medium).
- **Why the finding is wrong:** CVE-2018-8909 is in the Wire secure messenger (Wire Swiss GmbH), and CVE-2023-22737 in `wire-server`, that messenger's backend (a missing permission check on removing bots from a conversation). The app's library is Square's Wire, the Protobuf runtime (`com.squareup.wire`) that reads and writes the saved carts, fees and alerts. Dependency-Check names a jar's product from evidence such as its file name, and both are called "Wire". OSV-Scanner, which matches the same library by its package URL, reports nothing for it.
- **Why it can't be fixed:** the match comes from the name, so no version of Square's Wire changes it; the only way to make it go away would be removing the library.
- **What is excluded:** those two CVEs, each in its own entry, only for files under `com.squareup.wire/` named `wire-runtime…` ([`config/owasp/suppressions.xml`](../../../config/owasp/suppressions.xml), handed to the scan with `--suppression`). Any other CVE matched to that jar, and either CVE matched to anything else, is still reported; OSV-Scanner still checks the library on every PR.

#### 17. Licensee: Bouncy Castle's license, named by URL

- **What it reports:** `org.bouncycastle:bcprov-jdk18on` declares its license only as `https://www.bouncycastle.org/licence.html`, which Licensee can't map to an SPDX identifier, so the F-Droid release's license check (`licenseeAndroidFdroidRelease`) fails.
- **Why the finding is wrong:** that page is the Bouncy Castle Licence, adapted from the MIT X11 license (the same terms, with Bouncy Castle's copyright line); the credits list it as MIT.
- **Why it can't be fixed:** the URL is what Bouncy Castle's own POM declares, in every release; only Bouncy Castle can change it.
- **What is excluded:** that one URL. A Bouncy Castle release that declares anything else, and every other library, is still checked against the allowed identifiers (Apache-2.0, BSD-3-Clause).

#### 18. Licensee: Tesseract4Android declares no license

- **What it reports:** `com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.8.0` (the receipt scanner's OCR) has no license in its POM, so the F-Droid release's license check fails.
- **Why the finding is wrong:** the project is Apache-2.0 (its repository's license and README); the POM, built by JitPack, just leaves it out. The Tesseract, Leptonica, libjpeg and libpng it bundles have their own licenses, which Licensee never saw either way.
- **Why it can't be fixed:** the POM is the project's own; replacing the library would remove receipt scanning.
- **What is excluded:** that library at 4.8.0 only. A new version is checked again, so the exception is reviewed on each update; every other library is checked as usual.

#### 19. Dependabot: `mcp` 1.29.0 in Semgrep's packages

- **What it reports:** three advisories in `mcp` 1.29.0, the Python MCP SDK, fixed in 1.30.0:
  - alert #44 (GHSA-rwrf-2pqf-9j8j): the client fetches `$ref` URLs chosen by the server while it validates tool results against output schemas;
  - alert #45 (GHSA-5h93-6whr-6q8j): the HTTP client transports follow cross-origin redirects, carrying custom headers and bodies;
  - alert #46 (GHSA-w4fh-qvv9-3v23): the server's bearer-token authentication accepts tokens issued for a different resource server.
- **Why it can't be fixed:** `mcp` comes in only as a requirement of Semgrep, and Semgrep 1.183.0 (its newest release) requires exactly `mcp==1.29.0`. Dependabot can't update it, and installing 1.30.0 past that pin would run Semgrep on a version it doesn't declare support for.
- **Why it's safe:** the affected code is the MCP client's HTTP transports and its `call_tool` result validation, and the MCP server's bearer-token check. Each runs only while an MCP client session is connected or an MCP server is serving requests. The Semgrep workflow runs only `semgrep scan` on this repository's code. Building Semgrep's CLI imports its `mcp` command module, but `scan` never opens a client session or starts a server, so none of that code is called. Semgrep runs only in CI, and the app doesn't ship it.
- **What is excluded:** those three alerts, dismissed on GitHub, and no other advisory. Scorecard's Vulnerabilities check still reports them (see "Not exceptions" below). When a Semgrep release allows `mcp` 1.30.0 or later, Dependabot recompiles the file; this exception then goes.

### Action exceptions

The GitHub Actions workflows in [`.github/workflows/`](../../../.github/workflows) and what their scans report about the repository on GitHub: a step allowed to fail without failing its job (`continue-on-error`), a finding left out by the workflow linters, actionlint and the ShellCheck it runs on each `run:` script (`# shellcheck disable=`, `-ignore`), or by zizmor (`# zizmor: ignore`), and an alert dismissed on GitHub's Security tab.

| # | Tool and rule | Excluded for | Marked in |
|---|---|---|---|
| [14](#14-scorecard-branch-protection-on-master) | OpenSSF Scorecard `BranchProtectionID` | the review requirements on `master` | Dismissed on GitHub: code scanning alert [#379](https://github.com/EliorMachlev/CurrenciX/security/code-scanning/379), "won't fix" |
| [15](#15-scorecard-code-review) | OpenSSF Scorecard `CodeReviewID` | changes merged without a second person's approval | Dismissed on GitHub: code scanning alert [#387](https://github.com/EliorMachlev/CurrenciX/security/code-scanning/387), "won't fix" |
| [16](#16-typos-infor-and-mis) | typos extend-words | "Infor" (InforEuro) and "mis" (mis-issued), in the docs | [`_typos.toml`](../../../_typos.toml), read by [`.github/workflows/docs-checks.yaml`](../../../.github/workflows/docs-checks.yaml) |

#### 14. Scorecard: Branch-Protection on `master`

- **What it reports:** "branch protection is not maximal" (score 5 of 10), from [`.github/workflows/scorecard.yaml`](../../../.github/workflows/scorecard.yaml). When it was dismissed: stale-review dismissal off, one approving review required rather than two, no code-owner review, no approval of the last push.
- **Why it can't be fixed:** each missing setting asks for review by someone other than the author, and the project has one maintainer. Requiring two reviewers, code owners, or approval of the last push would leave nobody able to approve, so no change could be merged.
- **What still guards `master`:** the "Protection" ruleset. Changes arrive only through pull requests; force pushes and deletion are blocked; each PR must be up to date with `master` and pass the required checks (build, tests, lint, detekt, spotless, screenshots, fuzzing, Semgrep, Gitleaks, OSV-Scanner, dependency review, Qodana, CodeQL); and code scanning blocks a merge on CodeQL, Qodana, Semgrep or detekt findings.
- **What is excluded:** that one alert, dismissed with the reason "Single dev, can't do a real review." Scorecard still runs every week and on every push to `master`, and still scores the check; a new Branch-Protection alert, after the setup changes, is reported again.

#### 15. Scorecard: Code-Review

- **What it reports:** "Found 0/28 approved changesets" (score 0 of 10): no recent change was approved by a reviewer, or merged by someone other than its author.
- **Why it can't be fixed:** the same: one maintainer, so no second person exists to review. A review by the author, or by an account set up for it, would only make the score claim a review that didn't happen.
- **What still guards the code:** the checks and code scanning above, on every PR, and the PR description, which records what changed and why.
- **What is excluded:** that one alert, dismissed with the reason "I am a single developer on the project, so it is impossible to perform a review with another person." The check keeps running; once a second maintainer reviews, the score rises by itself.

#### 16. typos: "Infor" and "mis"

- **What it reports:** `Infor` "should be `Info`, `Inform`" (15 times, wherever the docs name InforEuro) and `mis` "should be `miss`, `mist`" (once, "a certificate mis-issued" in [`security.md`](security.md)).
- **Why the finding is wrong:** InforEuro is the European Commission's exchange rate service, and typos reads the first part of the name as a word on its own. "Mis-issued" is the standard term for a certificate a CA issued wrongly; typos splits it at the hyphen.
- **Why it can't be fixed:** the name is the service's own, and rewording "mis-issued" only so the check stops matching is a cosmetic workaround.
- **What is excluded:** those two words, added to typos' dictionary in [`_typos.toml`](../../../_typos.toml) (`[default.extend-words]`). Every other word in the docs is still checked, and no file is left out. `NoSuppressionsTest` fails on any other typos dictionary entry or exclusion.

**Not checked by `NoSuppressionsTest`:** 14 and 15 are dismissals on GitHub, not markers in a file, so no test sees them. Check the dismissed alerts on the Security tab against this list.

### CurrenciX exceptions

The app's own code, written as it is on purpose.

| # | Tool and rule | Excluded for | Marked in |
|---|---|---|---|
| [1](#1-mainactivity-is-exported) | Semgrep `java.android.security.exported_activity.exported_activity` | `MainActivity` | [`app/src/main/AndroidManifest.xml`](../../../app/src/main/AndroidManifest.xml) |
| [2](#2-converttextactivity-is-exported) | Semgrep `java.android.security.exported_activity.exported_activity` | `ConvertTextActivity` | `app/src/main/AndroidManifest.xml` |
| [12](#12-codeql-javareference-equality-of-boxed-types-on-timelinechart) | CodeQL `java/reference-equality-of-boxed-types` | `TimelineChart` (`min == max` on two `Double`s) | [`.github/codeql/exceptions.yml`](../../../.github/codeql/exceptions.yml), applied by [`.github/workflows/codeql.yaml`](../../../.github/workflows/codeql.yaml) |
| [13](#13-codeql-javaandroidmissing-certificate-pinning-on-certificatepinning) | CodeQL `java/android/missing-certificate-pinning` | `CertificatePinning` (re-pin's request for a provider's current certificates) | `.github/codeql/exceptions.yml`, applied by `.github/workflows/codeql.yaml` |

#### 1. `MainActivity` is exported

- **What the rule says:** the application exports an activity, so any app on the device can start it.
- **Why it can't be fixed:** `MainActivity` is the launcher entry (`MAIN` / `LAUNCHER`). The home screen is another app, and Android starts an activity for another app only if it is exported. Without it the app has no icon to open.
- **Why the rule is wrong here:** it flags every exported activity without looking at what the activity is for. Any app with a launcher icon has this finding.
- **What still guards it:** everything the activity reads from its intent goes through `ConverterLaunch`, which validates each extra and drops what it can't read.

#### 2. `ConvertTextActivity` is exported

- **What the rule says:** the same.
- **Why it can't be fixed:** this is "Convert currency" in the text-selection menu of other apps (`ACTION_PROCESS_TEXT`). The app showing the selection starts it, so it has to be exported. The only alternative is removing the feature.
- **What still guards it:** it reads one thing from the caller, the selected text, cut to a fixed maximum length and handed to `PriceParser`. Beyond that it only reads the cached rates and the user's settings, and it makes no network request.

**What the exclusion does and doesn't cover, for 1 and 2:** each exclusion is one `nosemgrep` comment naming the rule, on the one activity it is for. The rule itself keeps running: an exported activity added later is reported, and fails the Semgrep job, until it is either not exported or added here. The widget's configure activity shows the first route: it used to be exported, did not need to be, and no longer is.

#### 12. CodeQL: `java/reference-equality-of-boxed-types` on `TimelineChart`

- **What it reports:** "Suspicious reference comparison of boxed numerical values" at [`view/timeline/TimelineChart.kt`](../../../app/src/main/kotlin/com/eliormachlev/currencix/view/timeline/TimelineChart.kt), `if (min == null || max == null || min == max) return`.
- **Why the finding is wrong:** `min` and `max` are `Double?`, smart-cast to `Double` by the null checks before it. Kotlin's `==` compares values, never references, and for statically known `Double`s it is IEEE equality. The rule is written for Java's `==` on `Double` objects, and CodeQL applies it to this Kotlin comparison.
- **Why it can't be fixed:** the comparison is already correct. Rewriting it only so the rule stops matching is a cosmetic workaround.
- **What is excluded:** that one finding: the rule, that file, its line and its message. The step fails when the line moves, so the entry is checked whenever the function changes.

#### 13. CodeQL: `java/android/missing-certificate-pinning` on `CertificatePinning`

- **What it reports:** "This network call does not implement certificate pinning", at [`util/CertificatePinning.kt`](../../../app/src/main/kotlin/com/eliormachlev/currencix/util/CertificatePinning.kt), the request in `authorityPinsOf`.
- **Why the finding is wrong:** this is the one connection that must not be pinned. **Re-pin to current certificates** (Settings → Security & privacy → Advanced, asked first) is for a provider that moved to a CA its pins don't list: every pinned request to it fails until an app update. Re-pin connects with the phone's own trust, takes the CA keys from the chain the phone verified, and makes them that host's pins. Every other request the app makes goes through the pinned clients ([security.md](security.md#certificate-pinning)).
- **Why it can't be fixed:** pinning this request would check the new chain against the old pins, which is the failure re-pin exists to recover from; it could never take a new CA. The alternative is removing re-pin, leaving a provider unusable until a release.
- **What still guards it:** the client (`HttpClientProvider.unpinnedClient`) is used only by `currentPins`. It follows no redirects, so the chain is the asked host's own, sends a `HEAD` and reads nothing but the handshake. It reaches only the main and fallback providers' hosts, and only when the user confirms the prompt, which says to do it on a trusted network. The pins it takes are shown as "pins taken on this phone", and **Use the built-in pins** drops them.
- **What is excluded:** that one finding: the rule, that file, its line and its message. The rule still runs everywhere, and the step fails when the line moves, so the entry is checked whenever the function changes.

### Decided

1 and 2 decided by the maintainer on 2026-10-06, 3 on 2026-10-12, 4 to 6 on 2026-10-18 (PR [#181](https://github.com/EliorMachlev/CurrenciX/pull/181)), 7 to 9 on 2026-10-25 (PR #203); `CVE-2023-22737` in 7, and 10 to 13, on 2026-11-04; 14 and 15, dismissed on GitHub, on 2026-10-23, and recorded here on 2026-11-04; 16 to 18 on 2026-11-04; 19, dismissed on GitHub, on 2026-11-05; 20 on 2026-11-05.

## Adding an exception

1. Establish that there is no real fix, and what the next-best outcome would cost (a removed feature, a broken platform contract).
2. Get the maintainer's decision. An exception is theirs to grant, not the author's.
3. Exclude the narrowest thing possible: the one finding, by rule id, where it occurs. Never a rule switched off for the project, a file or directory left out of a scan, or a baseline.
4. Add a row and a section above, in the group where the finding lives (the build, a package, a workflow, or CurrenciX's own code), under the next unused number: what the rule says, why it can't be fixed, what still guards the code.
5. Add the exact line to `EXCEPTIONS` in `NoSuppressionsTest`. (A finding dismissed on GitHub, like 14, 15 and 19, has no line to add: give its alert number and the reason it was dismissed with instead.) That test fails on any suppression marker not listed there, on a listed one that is no longer in the code or has moved off the thing it was granted for, and on one this file doesn't explain.

## Removing an exception

When the tool is fixed upstream, or the code changes so the finding no longer applies, delete the marker, the row and section here, and the entry in `NoSuppressionsTest`.

## Not exceptions: warnings left visible

These have no real fix either, but nothing is excluded for them. They show up in every report.

- **Scorecard's Vulnerabilities, five advisories in Semgrep's own packages** ([`.github/semgrep/requirements.txt`](../../../.github/semgrep/requirements.txt)): four in `mcp` 1.29.0 (fixed in 1.30.0) and one in `opentelemetry-api` 1.37.0 (fixed in 1.45.1). Semgrep 1.183.0 requires `mcp==1.29.0` and `opentelemetry-api~=1.37.0`, so neither fix installs without breaking Semgrep's own requirements. They go when a Semgrep release raises its pins (Dependabot recompiles the file). Three of the `mcp` advisories were also Dependabot alerts [#44 to #46](https://github.com/EliorMachlev/CurrenciX/security/dependabot?q=is%3Adismissed), dismissed as exception 19; Scorecard still shows all five. (OSV-Scanner doesn't: it reads only the app's SBOM, which has no Python packages.) Semgrep runs only in CI, on this repository's code; the app doesn't ship it.
- **zizmor `self-repository`** (low), on [`baseline-profile.yaml`](../../../.github/workflows/baseline-profile.yaml)'s call to [`commit-baseline-profiles.yaml`](../../../.github/workflows/commit-baseline-profiles.yaml): zizmor asks for GitHub's `$/` self-repository syntax instead of `./`. GitHub supports it, but actionlint (a required check) doesn't yet, and rejects the file. It goes when an actionlint release accepts `$/`. It's below the level that fails the zizmor job, and stays on the Security tab.
- **Lint `Typos`, twice, in `values-tr/strings_currencies.xml`:** "Gine" in the Turkish names of the Guinean franc and the Papua New Guinean kina. It is the Turkish word for Guinea; lint's word list takes it for a misspelling of "yine".
