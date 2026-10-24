# CI / CD

All automation lives in `.github/workflows/`. Every workflow pins its GitHub Actions to a full 40-character commit SHA to prevent supply-chain attacks. Each pin's comment names the exact release it points at (`# v7.0.1`). Every Java job runs on JDK 25 (Temurin); the app's bytecode targets Java 21.

## Workflow Summary

| Workflow | Trigger | Purpose |
|---|---|---|
| `build.yaml` | Push → `master`, PR, manual | Spotless, Compose stability and fuzzing (own jobs), lint, test, build debug APK for both flavors (matrix) + fdroid release APK; failed tests become error annotations |
| `release-apk.yaml` | Tag `v*` push, manual | The release: on a `v<versionName>` tag of a `master` commit, build the fdroid release APKs signed with the release key and the post-quantum hybrid signature (keys from the `release` environment's secrets, which only this job can read, removed after the build; Gradle cache read-only) and publish them as a GitHub release with their SBOM (`currencix-<version>-sbom.cdx.json`, CycloneDX), `SHA256SUMS` and notes generated from the Conventional Commit titles since the previous tag (`.github/scripts/release-notes.py`); publishing is a separate job with `contents: write` that never sees the keys. By hand it only builds and uploads the signed APKs, and previews the notes. See build-and-flavors.md, "Post-quantum signing" |
| `apk-artifact.yaml` | Push → non-master, manual | Build fdroid debug APK, upload it as an artifact, and put a download link in the run summary |
| `screenshots.yaml` | Push → non-master, manual | Record Roborazzi screenshots of every Compose surface (JVM, no emulator), plus 200 % font-size captures of the densest screens, and upload the PNGs as an artifact — not a gate, nothing is verified. `ScreenshotRule` renders on a manual clock, so the suite takes about a minute; the job times out at 20 min so a capture that never settles fails fast |
| `baseline-profile.yaml` | Push → non-master touching `baselineprofile/**` or the workflow, manual | Generate baseline + startup profiles on an API 34 emulator; upload them as an artifact (and, run by hand with **commit**, commit them through `commit-baseline-profiles.yaml`) |
| `commit-baseline-profiles.yaml` | Called by Baseline Profile, manual | Put a profile run's artifacts back in their folders and push them to the branch; then start Screenshots and the debug APK build on that commit |
| `benchmarks.yaml` | Push → non-master touching app code, the build, `baselineprofile/**` or the benchmark scripts, manual | Run the startup and frame-timing benchmarks on the same emulator; fail on a slowdown against the saved baseline |
| `detekt.yaml` | PR, push → `master` | Kotlin static analysis |
| `qodana.yaml` | PR, push → `master`, weekly | JetBrains Qodana JVM analysis |
| `codeql.yaml` | PR, push → `master`, weekly | GitHub CodeQL (Actions YAML) |
| `semgrep.yaml` | PR, push → `master`, weekly (Mon 09:00 UTC) | SAST security pattern scanning |
| `gitleaks.yaml` | PR, push → `master`, weekly (Mon 07:00 UTC) | Secret / credential scanning |
| `owasp-dependency-check.yaml` | Weekly (Mon 08:00 UTC) | Dependency vulnerability scan (CVSS ≥ 7) |
| `dependency-review.yaml` | PR | Block high-severity new dependencies |
| `gradle-wrapper.yaml` | PR, push → `master`, manual | `gradle/actions/wrapper-validation`: checks `gradle/wrapper/gradle-wrapper.jar`, the one binary in the repository, against the checksums of Gradle's own releases, so a swapped jar can't come in through a PR. Its own quick workflow so Scorecard (below) can see it succeed on the latest commit, which is what makes Binary-Artifacts count the jar as validated |
| `scorecard.yaml` | Push → `master`, weekly | OpenSSF Scorecard supply-chain score. On a push it first waits for `gradle-wrapper.yaml` to pass on that commit (`wrapper-validated` job), since Binary-Artifacts only accepts the wrapper jar once that run has succeeded |
| `actionlint.yaml` | PR/push on `.github/workflows/**` | Validate workflow YAML syntax |

## Build Gate (`build.yaml`)

Runs on PRs and pushes to `master` (and by hand). Its jobs run side by side:

- **`spotless`** — `spotlessCheck`, ktlint via Spotless (see [Code Style](contributing.md#code-style)).
- **`fuzz`** — one job per fuzz target (`FuzzTest`: currency codes, selected-text prices, receipt text, calculations, typed amounts), each fuzzed for two minutes with Jazzer (`-PfuzzTests`, `JAZZER_FUZZ=1`). A crash fails the job and uploads its input (`fuzz-crash-<target>`). See [build-and-flavors.md](build-and-flavors.md#fuzz-tests).
- **`compose-stability`** — compiles with `-PcomposeReports` (the Compose compiler's report of each composable, `app/build/compose-reports/`) and runs `.github/scripts/compose-stability.py`, which fails on a composable that can't skip, or one taking a parameter the compiler can't prove stable: with strong skipping that parameter is compared by identity, so a fresh but equal value recomposes it every time. Fix it by making the type stable (immutable, `@Stable` / `@Immutable` when that's true, or listed in `app/compose-stability.conf`). Keys typed `Any`, an Android `Context` and receivers are compared by identity on purpose and aren't flagged; nor are the widget's Glance composables, which compose once per update.
- **`build`** — matrix over `Fdroid` and `Play` flavors × the three checks below, six jobs in all, each running one of:
  - `lint<Flavor>Debug` — Android Lint. Nothing is disabled: a string missing from any of the app's locales fails the build.
  - `test<Flavor>DebugUnitTest` — JUnit unit tests
  - `assemble<Flavor>Debug` — compile debug APK for the matrix flavor
- **`fdroid-release-build`** — assembles the fdroid *release* APK unsigned and writes its SBOM (`:app:cyclonedxDirectBom`, as the release does), and uploads both as an artifact (14-day retention). Reproducibility guard: catches breakage of the fdroid release build path before it blocks an F-Droid release.

## Speed

A PR's checks take about five minutes, all jobs running at once; the longest are the two build legs and the fdroid release build.

- **Superseded runs are cancelled.** Every workflow that runs on PRs or branch pushes has a `concurrency` group per PR (or branch): a new push cancels the run still going for the previous one. Runs on `master` always finish.
- **More jobs, side by side**: each flavor's lint, unit tests and debug build are separate jobs, as is Spotless, instead of one job per flavor running them one after another.
- **Gradle** (`gradle.properties`): the build cache and parallel project execution are on, with a 3 GB daemon heap and a 2 GB Kotlin daemon. `setup-gradle` keeps Gradle's home, build cache included, between runs: `master` writes it, PRs read it, so a PR reuses whatever `master` already built. The configuration cache is off: measured on CI, it made lint + test + build about 70 s slower, by running one module's tasks side by side on 4 cores (the build script is compatible with it, should that change).

## Baseline Profiles (`baseline-profile.yaml`)

One job per flavor, side by side (`profile (fdroid)`, `profile (play)`): together, one after the other, they took about 80 of the 90 minutes a job may run. Each boots a Gradle Managed Device (`pixel6Api34`, API 34 AOSP emulator, software GPU) on a KVM-enabled hosted runner, generates its flavor's baseline + startup profiles and uploads them as `baseline-profiles-<flavor>` (the contents of that flavor's profile folder). Commit the files under `app/src/<flavor>Release/generated/baselineProfiles/` to ship them, or run the workflow by hand with **commit** ticked: `commit-baseline-profiles.yaml` then puts each flavor's artifact back in its folder (or, for a run from before the split, the single `baseline-profiles` artifact) and pushes them to the branch it ran on, and starts Screenshots and the debug APK build on that commit by `workflow_dispatch` (a push made with the workflow's own token starts no push-triggered workflows). To commit an earlier run's profiles without generating them again, run Baseline Profile by hand with **commit_run_id** set to that run's ID (or `commit-baseline-profiles.yaml` directly, once it is on the default branch).

Each flavor's profiles take most of an hour of emulator time, so it triggers only when `baselineprofile/**` or the workflow itself changes on a non-master push, or on demand. See [build-and-flavors.md](build-and-flavors.md#baseline-profiles).

## Benchmarks (`benchmarks.yaml`)

Its own workflow (it used to share the profile job and ran out of its 90 minutes). On the same emulator it runs `InteractionBenchmarks` (startup time plus frame timing for typing, picker scrolling and screen transitions, each with and without the profile), then:

1. Puts the numbers in the run summary and as notice annotations (`.github/scripts/benchmark-summary.py`), so two runs compare without downloading anything.
2. Checks for slowdowns (`.github/scripts/benchmark-gate.py`): each benchmark's median against `baselineprofile/benchmark-baseline.json`. A metric fails the run when it's both 25% and 5 ms slower; the emulator's numbers wander, so smaller swings pass. Each regression is an error annotation.
3. Uploads `benchmark-current.json` (this run's medians) with `benchmarkData.json` as `benchmark-results`, and posts the same medians as one annotation ("Benchmark medians"), readable through the API without downloading the artifact. To accept a slowdown that's deliberate, or to add benchmarks the baseline lacks, commit them as `baselineprofile/benchmark-baseline.json`. The baseline is the middle value of the first CI runs, with and without the profile. Each benchmark runs ten times: with five, a median moved by up to 30% between runs of unchanged code, enough to fail the check on noise.

The benchmark step itself is `continue-on-error`: the software-GPU emulator doesn't always report frame stats, so Macrobenchmark can fail to confirm launches there. With no numbers, or no baseline yet, the check says so and passes. Trust absolute numbers from a physical device; the check is for catching large regressions. It runs when app code, the build, `baselineprofile/**` or the benchmark scripts change on a non-master push (a newer push cancels a running one), or on demand.

## Security Scans

### Detekt
- Version: 2.0.0-alpha.6 (pinned in root `build.gradle.kts` via the `dev.detekt` Gradle plugin). Still an alpha: 1.23.8 is the last stable release, but it is incompatible with Gradle 10 and 2.0 is the line with the fix.
- Config: `config/detekt/detekt.yml` (tuned to enforce the `CLAUDE.md` code-shape defaults). The size limits apply to `@Composable` functions like any other: one that needs six or more arguments takes them as a state type and an actions type. detekt 2 names each limit as what's allowed (`allowedLines: 59`, `allowedFunctionParameters: 5`), one below 1.x's trigger thresholds, so the limits are the same as before.
- No baseline: every finding counts, in old code and new.
- Type resolution: detekt 2 runs its type-aware rules (`LongParameterList`, `UnsafeCallOnNullableType`, `InjectDispatcher`, …) only in its per-variant tasks. `./gradlew detekt` runs a set of them that covers every source folder once — `:app:detektFdroidDebug` (main, fdroid, debug), `:app:detektPlayRelease` (play, release), `:app:detektFdroidDebugUnitTest` (unit tests), `:helpers:detektMain` / `detektTest` and `:baselineprofile:detektFdroidBenchmarkRelease` — and skips its own pass, which would leave those rules out (`detektVariantTasks` in the root build script).
- JVM target: 21
- Runs via `./gradlew detekt` — the step is enforced (no `continue-on-error`); any finding fails the build.
- Output: one SARIF per variant task, uploaded to the GitHub Security tab under its own category (`detekt` for the app's main one, `detekt-release`, `detekt-tests`, `detekt-helpers`, `detekt-baselineprofile`) + HTML/checkstyle/SARIF artifact retained 14 days

### Qodana
- Image: `qodana-jvm-community:2026.2`
- Scans production and test sources alike (only build output is excluded)
- Posts inline PR comments on findings
- SARIF uploaded to GitHub Security tab

### CodeQL
- Language scope: `actions` (YAML workflows only — Kotlin delegated to Qodana)

### Semgrep
- Rule sets: `p/default`, `p/security-audit`, `p/kotlin`, `p/java`, `p/github-actions`
- Installed on Python 3.14 (the newest Semgrep supports; its wheels stop at 3.14) from `.github/semgrep/requirements.txt`, Semgrep and every package it needs pinned by hash (`--require-hashes`), not the unpinned `semgrep/semgrep` image; that file is compiled from `requirements.in` with `pip-compile --generate-hashes`, and Dependabot recompiles it
- Every finding is printed in the job log and uploaded as SARIF to the GitHub Security tab. No rule is switched off and no path is left out. Two findings are excluded where they occur, both recorded in [exceptions.md](exceptions.md): `exported_activity` on the launcher and on the text-selection ("Convert currency") activity.
- Semgrep honours those `nosemgrep` comments (the job passes) and marks both results suppressed in the SARIF, but GitHub code scanning doesn't read SARIF suppressions, so it still lists them as open alerts until they're dismissed there.
- Any finding fails the job, whatever its severity.

### OWASP Dependency Check
- CVSS threshold: 7 (high+)
- Scans runtime classpath only
- Detects retired/archived dependencies
- Uploads HTML + XML reports as artifact

### OpenSSF Scorecard
- Evaluates pinned-dependencies, branch-protection, SAST, etc., and publishes the score to the public Scorecard API (`publish_results`)
- Results written to `results.sarif`, uploaded to the Security tab and kept 5 days as the `scorecard-sarif` artifact
- On a push it waits for `gradle-wrapper.yaml` to pass on the commit first (`wrapper-validated`), so Binary-Artifacts counts the wrapper jar as validated

## Dependabot

Configured in `.github/dependabot.yml` with weekly Monday schedule.

**Ecosystems:**

| Ecosystem | Directories | Open PR limit | Cooldown |
|---|---|---|---|
| `gradle` | `/` (every module) | 10 | 7 days |
| `github-actions` | `/` | 5 | 7 days |
| `pip` | `/.github/scripts` (the CI scripts' `requirements.txt`) and `/.github/semgrep` (Semgrep's), both pinned by hash; one grouped PR | 5 | 7 days |

**Grouped updates** keep related libraries in one PR:
- `androidx.*`
- `org.jetbrains.kotlin*` + `com.google.devtools.ksp*`
- `com.squareup.moshi:*`
- `com.squareup.okhttp3:*`
- Test dependencies
- All GitHub Actions (single PR)

## Permission Model

All workflows use `permissions: contents: read` by default. Additional permissions are granted only when needed:

| Workflow | Extra permissions |
|---|---|
| `detekt.yaml` | `security-events: write` |
| `semgrep.yaml` | `security-events: write` |
| `qodana.yaml` | `security-events: write`, `pull-requests: write`, `checks: write` |
| `codeql.yaml` | `security-events: write`, `actions: read`, `packages: read` |
| `scorecard.yaml` | `security-events: write`, `id-token: write`, `actions: read` (the analysis); `actions: read` (waiting for the wrapper check) |
| `release-apk.yaml` | `contents: write` for the `publish` job only, which never sees the signing keys; the `build` job that holds them reads only |
| `baseline-profile.yaml` | `contents: write`, `actions: write` for the `commit` job only (it calls `commit-baseline-profiles.yaml`) |
| `dependency-review.yaml` | `pull-requests: write` |
| `owasp-dependency-check.yaml` | `security-events: write` |
| `commit-baseline-profiles.yaml` | `contents: write`, `actions: write`: pushes a Baseline Profile run's profiles to the branch (called by that workflow on a manual run with "commit" ticked, or run by hand with the run's ID), then dispatches Screenshots and the debug APK build on that commit; runs no build itself |
