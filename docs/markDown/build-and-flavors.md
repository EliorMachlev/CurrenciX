# Build & Flavors

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 (Temurin recommended) |
| Android Gradle Plugin | see `build.gradle.kts` |
| Kotlin | 2.4.10 |
| Min SDK | 26 |
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

## Key Dependencies

### Android / AndroidX

| Dependency | Version |
|---|---|
| `androidx.appcompat:appcompat` | 1.8.0 |
| `androidx.lifecycle:lifecycle-*` | 2.11.0 |
| `androidx.constraintlayout:constraintlayout` | 2.2.2 |
| `com.google.android.material:material` | 1.14.0 |
| `androidx.window:window` | 1.5.1 |

### Compose

| Dependency | Version |
|---|---|
| `androidx.compose:compose-bom` | 2026.08.00 |
| `androidx.compose.material3:material3` | 1.4.0 (pinned newer than the BOM ships) |
| `androidx.compose.material:material-icons-extended` | via BOM |
| `androidx.compose.ui` / `foundation` / `runtime` / `runtime-livedata` | via BOM |
| `androidx.activity:activity-compose` | 1.13.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 |

Hosts the Vico chart plus the other UI surfaces migrated to Compose via `ComposeView` (see [architecture.md](architecture.md)).

### HTTP & Serialisation

| Dependency | Version |
|---|---|
| `com.squareup.okhttp3:okhttp` | 5.5.0 |
| `com.squareup.okhttp3:logging-interceptor` | 5.5.0 |
| `com.squareup.moshi:moshi-kotlin` | 1.15.2 |
| `com.google.devtools.ksp:*` | 2.3.11 |

### Calculator

| Dependency | Version | Note |
|---|---|---|
| `com.ezylang:EvalEx` | 3.7.0 | Apache-2.0, BigDecimal-native. Replaced mXparser (v5+ dual license isn't F-Droid compatible). |

### Charts

| Dependency | Version |
|---|---|
| `com.patrykandpatrick.vico:compose` | 3.3.0 |

### Crypto & Logging

| Dependency | Version | Note |
|---|---|---|
| `org.bouncycastle:bcprov-jdk18on` | 1.85.2 | Pure-Java Argon2id for password-based backup encryption — see [security.md](security.md) |
| `com.jakewharton.timber:timber` | 5.0.1 | Local-only rotating file log, no remote crash/analytics sink |

### Testing

| Dependency | Version |
|---|---|
| `junit:junit` | 4.13.2 |
| `org.mockito:mockito-core` | 5.23.0 |
| `androidx.arch.core:core-testing` | 2.2.0 |
| `org.junit.jupiter:junit-jupiter-*` | 6.1.3 |
| `com.code-intelligence:jazzer-junit` | 0.30.0 (fuzz testing) |

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

Generation requires a connected device or emulator (API 28+, rooted / userdebug
build). CI does not run this today — no emulator-based instrumentation job
exists — so profiles are regenerated locally on demand:

```bash
# Requires an emulator or device with `adb root` available.
./gradlew :app:generateFdroidReleaseBaselineProfile
./gradlew :app:generatePlayReleaseBaselineProfile
```

Commit the regenerated `baseline-prof.txt` / `startup-prof.txt`. Re-run when
hot paths shift materially (major redesign, new startup dependency).

## Known Issues

### Gradle deprecation: "Project object as dependency notation" (AGP bug)

**Symptom:** During configuration of `:app`, Gradle prints:

```
Using a Project object as a dependency notation has been deprecated.
This will fail with an error in Gradle 10.
```

**Root cause:** This originated inside **AGP 9.2.1** (`VariantDependenciesBuilder.java:279/333` → `VariantManager.createTestComponents`), not from any build script in this repository. The stack trace confirmed no call site in our code.

**Status:** The project has since moved to **AGP 9.3.2**. Upstream bug, expected to resolve once AGP uses `project(String)` internally — needs a fresh check on 9.3.2 to confirm whether it still reproduces before this section is trusted as current.
