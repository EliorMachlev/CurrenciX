# Exceptions to "No Suppressions"

[contributing.md](contributing.md#no-suppressions-no-cosmetic-workarounds) forbids hiding a warning instead of fixing its cause. This file is the complete list of places where the project does it anyway, and why. If a suppression is not listed here, it is not allowed.

## What qualifies

An exception is for a finding that is wrong about code that is right:

- the code is correct as written, and
- no change would satisfy the tool short of removing something that works, and
- the maintainer has decided to exclude it rather than leave it visible.

A finding that is merely hard, slow or unpleasant to fix does not qualify. Fix it, or leave the warning visible and say so in the PR.

## Current exceptions

| # | Tool and rule | Excluded for | Marked in |
|---|---|---|---|
| 1 | Semgrep `java.android.security.exported_activity.exported_activity` | `MainActivity` | `app/src/main/AndroidManifest.xml` |
| 2 | Semgrep `java.android.security.exported_activity.exported_activity` | `ConvertTextActivity` | `app/src/main/AndroidManifest.xml` |
| 3 | LeakCanary IgnoredReferenceMatcher (debug builds) | `ResourcesImpl.mAppContext` (vendor framework field) | `app/src/debug/kotlin/com/eliormachlev/currencix/leaks/LeakDetection.kt` |
| 4 | Qodana / IntelliJ `UnstableApiUsage` | the Gradle Managed Device (`ManagedVirtualDevice`) | `baselineprofile/build.gradle.kts` |
| 5 | Qodana / IntelliJ `UnstableApiUsage` | `testedApks` (the tested app's id for benchmarks) | `baselineprofile/build.gradle.kts` |
| 6 | Qodana / IntelliJ `UnstableApiUsage` | `generateLocaleConfig` | `app/build.gradle.kts` |
| 7 | OWASP Dependency-Check `CVE-2018-8909` | `wire-runtime` (Square's Wire Protobuf library) | `config/owasp/suppressions.xml`, passed in by `.github/workflows/owasp-dependency-check.yaml` |
| 8 | CodeQL `java/useless-null-check` | `QuickConversionsSheet` (a check the Kotlin compiler generates, line 1) | `.github/codeql/exceptions.yml`, applied by `.github/workflows/codeql.yaml` |

1 and 2 decided by the maintainer on 2026-10-06, 3 on 2026-10-12, 4 to 6 on 2026-10-18 (PR #181), 7 and 8 on 2026-10-25 (PR #203).

### 1. `MainActivity` is exported

- **What the rule says:** the application exports an activity, so any app on the device can start it.
- **Why it can't be fixed:** `MainActivity` is the launcher entry (`MAIN` / `LAUNCHER`). The home screen is another app, and Android starts an activity for another app only if it is exported. Without it the app has no icon to open.
- **Why the rule is wrong here:** it flags every exported activity without looking at what the activity is for. Any app with a launcher icon has this finding.
- **What still guards it:** everything the activity reads from its intent goes through `ConverterLaunch`, which validates each extra and drops what it can't read.

### 2. `ConvertTextActivity` is exported

- **What the rule says:** the same.
- **Why it can't be fixed:** this is "Convert currency" in the text-selection menu of other apps (`ACTION_PROCESS_TEXT`). The app showing the selection starts it, so it has to be exported. The only alternative is removing the feature.
- **What still guards it:** it reads one thing from the caller, the selected text, cut to a fixed maximum length and handed to `PriceParser`. Beyond that it only reads the cached rates and the user's settings, and it makes no network request.

### 3. LeakCanary: `ResourcesImpl.mAppContext`

- **What it reports:** a "library leak" in debug builds on Xiaomi's Android and other vendors' builds from the same fork: a static `ResourcesImpl.mAppContext` field, added by the vendor, keeps the context of the last service that set up resources, here WorkManager's `SystemJobService` after it finished (about 1 kB).
- **Why it can't be fixed:** the field is in the phone's framework, not in the app or WorkManager. The app holds no reference it could release; clearing a private framework field by reflection would be fragile, vendor-specific, and done only to quiet the report. It is held one context at a time and replaced by the next service, so it doesn't grow.
- **What is excluded:** `configureLeakDetection` (debug source set) takes LeakCanary's own known-leak list and turns that one static field from "report as a library leak" into "ignore". Every other known leak and every app leak is still reported; release builds have no LeakCanary.

### 4 to 6. Incubating Android Gradle Plugin APIs

- **What the rule says:** the build script calls an API AGP marks `@Incubating`, which may change in a later version.
- **Why it can't be fixed:** each is the only API AGP has for what it does, with no stable equivalent: declaring the managed emulator that generates baseline profiles and runs benchmarks (`ManagedVirtualDevice`: `localDevices`, `device`, `apiLevel`, `systemImageSource`), reading the tested app's id for the benchmark runner (`testedApks`), and generating the per-app language list from the translations (`generateLocaleConfig`). The alternatives are dropping the feature or a hand-kept copy: an emulator set up by hand in CI, a hard-coded app id, a hand-kept locale list.
- **Why the rule is wrong here:** incubating is not deprecated; these are the APIs AGP's own documentation uses. If AGP changes one, the build fails at once, so nothing breaks quietly.
- **What is excluded:** one `@Suppress("UnstableApiUsage")` on each of those three statements, never the file. Any other incubating API used later is still reported.

### 7. OWASP Dependency-Check: `CVE-2018-8909` on `wire-runtime`

- **What it reports:** a CVSS 7.5 vulnerability in `wire-runtime-jvm-7.1.0.jar`, which it identifies as the product `cpe:2.3:a:wire:wire`.
- **Why the finding is wrong:** CVE-2018-8909 is in the Wire secure messenger (Wire Swiss GmbH). The app's library is Square's Wire, the Protobuf runtime (`com.squareup.wire`) that reads and writes the saved carts, fees and alerts. Dependency-Check names a jar's product from evidence such as its file name, and both are called "Wire". OSV-Scanner, which matches the same library by its package URL, reports nothing for it.
- **Why it can't be fixed:** the match comes from the name, so no version of Square's Wire changes it; the only way to make it go away would be removing the library.
- **What is excluded:** that one CVE, only for files under `com.squareup.wire/` named `wire-runtime…` (`config/owasp/suppressions.xml`, handed to the scan with `--suppression`). Any other CVE matched to that jar, and that CVE matched to anything else, is still reported; OSV-Scanner still checks the library on every PR.

### 8. CodeQL: `java/useless-null-check` on `QuickConversionsSheet`

- **What it reports:** "This check is useless. `tmpEnum` cannot be null at this check", at line 1 of `view/main/compose/QuickConversionsSheet.kt`.
- **Why the finding is wrong:** the file has no such check, and no variable called `tmpEnum`. CodeQL reads Kotlin as the compiler lowers it, and the null check is one the Kotlin/Compose compiler generates there (the only enum in the file is the nullable `PillSide` that picks the currency picker's side); that's why it sits on line 1, where no code is. It first showed up when PR #203 turned on CodeQL for Kotlin.
- **Why it can't be fixed:** the check isn't in the source, so no edit to the source removes it except restructuring working code until the compiler happens to generate something else, which is a cosmetic workaround.
- **What is excluded:** that one finding: the rule, that file, line 1, and a message naming `tmpEnum` must all match (`.github/codeql/exceptions.yml`). The Kotlin analysis writes its results, `.github/scripts/codeql-exceptions.py` takes that finding out, and the rest is uploaded. The rule still runs everywhere, that file included: a real useless null check in it, on any other line, is still reported. The step fails when the entry stops matching (a newer Kotlin generating different code), so the exception can't outlive the finding.

### What the exclusion does and doesn't cover

For 1 and 2, each exclusion is one `nosemgrep` comment naming the rule, on the one activity it is for. The rule itself keeps running: an exported activity added later is reported, and fails the Semgrep job, until it is either not exported or added here. The widget's configure activity shows the first route: it used to be exported, did not need to be, and no longer is.

## Adding an exception

1. Establish that there is no real fix, and what the next-best outcome would cost (a removed feature, a broken platform contract).
2. Get the maintainer's decision. An exception is theirs to grant, not the author's.
3. Exclude the narrowest thing possible: the one finding, by rule id, where it occurs. Never a rule switched off for the project, a file or directory left out of a scan, or a baseline.
4. Add a row and a section above: what the rule says, why it can't be fixed, what still guards the code.
5. Add the exact line to `EXCEPTIONS` in `NoSuppressionsTest`. That test fails on any suppression marker not listed there, on a listed one that is no longer in the code or has moved off the thing it was granted for, and on one this file doesn't explain.

## Removing an exception

When the tool is fixed upstream, or the code changes so the finding no longer applies, delete the marker, the row and section here, and the entry in `NoSuppressionsTest`.

## Not exceptions: warnings left visible

These have no real fix either, but nothing is excluded for them. They show up in every report.

- **Lint `Typos`, twice, in `values-tr/strings_currencies.xml`:** "Gine" in the Turkish names of the Guinean franc and the Papua New Guinean kina. It is the Turkish word for Guinea; lint's word list takes it for a misspelling of "yine".
