# CI / CD

All automation lives in `.github/workflows/`. Every workflow pins its GitHub Actions to a full 40-character commit SHA to prevent supply-chain attacks.

## Workflow Summary

| Workflow | Trigger | Purpose |
|---|---|---|
| `build.yaml` | Push → `master`, PR | Spotless, lint, test, build debug APK for both flavors (matrix) + fdroid release APK |
| `apk-artifact.yaml` | Push → non-master, manual | Build fdroid debug APK and upload as artifact |
| `screenshots.yaml` | Push → non-master, manual | Record Roborazzi screenshots of every Compose surface (JVM, no emulator), plus 200 % font-size captures of the densest screens, and upload the PNGs as an artifact — not a gate, nothing is verified. `ScreenshotRule` renders on a manual clock, so the suite takes about a minute; the job times out at 20 min so a capture that never settles fails fast |
| `baseline-profile.yaml` | Push → non-master touching `baselineprofile/**` or the workflow, manual | Generate baseline + startup profiles and run frame-timing benchmarks on an API 34 emulator; upload both as artifacts |
| `detekt.yaml` | PR, push → `master` | Kotlin static analysis |
| `qodana.yaml` | PR, push → `master`, weekly | JetBrains Qodana JVM analysis |
| `codeql.yaml` | PR, push → `master`, weekly | GitHub CodeQL (Actions YAML) |
| `semgrep.yaml` | PR, push → `master`, weekly (Mon 09:00 UTC) | SAST security pattern scanning |
| `gitleaks.yaml` | PR, push → `master`, weekly (Mon 07:00 UTC) | Secret / credential scanning |
| `owasp-dependency-check.yaml` | Weekly (Mon 08:00 UTC) | Dependency vulnerability scan (CVSS ≥ 7) |
| `dependency-review.yaml` | PR | Block high-severity new dependencies |
| `scorecard.yaml` | Push → `master`, weekly | OpenSSF Scorecard supply-chain score |
| `actionlint.yaml` | PR/push on `.github/workflows/**` | Validate workflow YAML syntax |

## Build Gate (`build.yaml`)

Runs on both PRs and pushes to `master`. Two jobs:

- **`build`** — matrix over `Fdroid` and `Play` flavors. Steps:
  - `spotlessCheck` — ktlint via Spotless (see [Code Style](contributing.md#code-style))
  - `lint<Flavor>Debug` — Android Lint. Nothing is disabled: a string missing from any of the app's locales fails the build.
  - `test<Flavor>DebugUnitTest` — JUnit unit tests
  - `assemble<Flavor>Debug` — compile debug APK for the matrix flavor
- **`fdroid-release-build`** — assembles the fdroid *release* APK unsigned and uploads it as an artifact (14-day retention). Reproducibility guard: catches breakage of the fdroid release build path before it blocks an F-Droid release.

## Baseline Profiles & Benchmarks (`baseline-profile.yaml`)

Boots a Gradle Managed Device (`pixel6Api34`, API 34 AOSP emulator, software GPU) on a KVM-enabled hosted runner, then:

1. Generates the fdroid and play baseline + startup profiles, and uploads them as the `baseline-profiles` artifact *before* benchmarking so a benchmark failure can't lose them. Commit the files under `app/src/<flavor>Release/generated/baselineProfiles/` to ship them.
2. Runs `InteractionBenchmarks` (startup time plus frame timing for typing, picker scrolling and screen transitions, each with and without the profile), and uploads `benchmarkData.json` as `benchmark-results`. This step is advisory (`continue-on-error`): the software-GPU emulator doesn't report frame stats, so Macrobenchmark can fail to confirm launches there. Trust benchmark numbers from a physical device.

Tens of minutes of emulator time, so it triggers only when `baselineprofile/**` or the workflow itself changes on a non-master push, or on demand. See [build-and-flavors.md](build-and-flavors.md#baseline-profiles).

## Security Scans

### Detekt
- Version: 1.23.8 (pinned in root `build.gradle.kts` via the `io.gitlab.arturbosch.detekt` Gradle plugin)
- Config: `config/detekt/detekt.yml` (tuned to enforce the `CLAUDE.md` code-shape defaults). The size limits apply to `@Composable` functions like any other: one that needs six or more arguments takes them as a state type and an actions type.
- No baseline: every finding counts, in old code and new.
- Inputs: `app/src`, `helpers/src` (`src/**/*.kt` per subproject) — tests included
- JVM target: 21
- Runs via `./gradlew detekt` — the step is enforced (no `continue-on-error`); any finding fails the build.
- Output: SARIF uploaded to GitHub Security tab (category `detekt` for the app, `detekt-helpers` for the helpers) + HTML/XML artifact retained 14 days

### Qodana
- Image: `qodana-jvm-community:2025.1`
- Scans production and test sources alike (only build output is excluded)
- Posts inline PR comments on findings
- SARIF uploaded to GitHub Security tab

### CodeQL
- Language scope: `actions` (YAML workflows only — Kotlin delegated to Qodana)

### Semgrep
- Rule sets: `p/default`, `p/security-audit`, `p/kotlin`, `p/java`, `p/github-actions`
- Every finding is printed in the job log and uploaded as SARIF to the GitHub Security tab. No rule is switched off and no path is left out. Two findings are excluded where they occur, both recorded in [exceptions.md](exceptions.md): `exported_activity` on the launcher and on the text-selection ("Convert currency") activity.
- Any finding fails the job, whatever its severity.

### OWASP Dependency Check
- CVSS threshold: 7 (high+)
- Scans runtime classpath only
- Detects retired/archived dependencies
- Uploads HTML + XML reports as artifact

### OpenSSF Scorecard
- Evaluates pinned-dependencies, branch-protection, SAST, etc.
- Results written to `scorecard-results.sarif` and uploaded to Security tab

## Dependabot

Configured in `.github/dependabot.yml` with weekly Monday schedule.

**Ecosystems:**

| Ecosystem | Directories | Open PR limit | Cooldown |
|---|---|---|---|
| `gradle` | `/`, `/app`, `/helpers` | 10 | 7 days |
| `github-actions` | `/` | 5 | 7 days |

**Grouped updates** keep related libraries in one PR:
- `androidx.*`
- `com.google.android.material:*`
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
| `qodana.yaml` | `security-events: write`, `pull-requests: write`, `checks: write` |
| `codeql.yaml` | `security-events: write`, `actions: read` |
| `scorecard.yaml` | `security-events: write`, `id-token: write` |
| `dependency-review.yaml` | `pull-requests: write` |
| `owasp-dependency-check.yaml` | `security-events: write` |
