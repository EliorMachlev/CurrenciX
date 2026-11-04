# CI / CD

Everything is in `.github/workflows/`. Every action is pinned to a full commit SHA with its release in a comment (`# v7.0.1`); every Java job runs JDK 25. Every job's first step is Harden-Runner in audit mode ([security.md](security.md#supply-chain)).

## Workflows

| Workflow | Runs on | Does |
|---|---|---|
| `build.yaml` | PR, push → `master`, manual | The gate (below) |
| `release-apk.yaml` | `v*` tag, manual | Signed release APKs, SBOM, checksums, provenance, GitHub release ([release.md](release.md)) |
| `apk-artifact.yaml` | Push → non-master, manual | fdroid debug APK as an artifact, linked in the run summary |
| `apk-checks.yaml` | PR touching the app or the build, push → `master`, weekly, manual | The unsigned fdroid release built twice, from two checkouts with no build cache, and compared byte for byte (any difference fails, with `diffoscope`'s account as an artifact); on PRs, `diffuse` compares the universal APK with master's latest in the run summary. Not part of the gate |
| `screenshots.yaml` | Push → non-master, manual | Roborazzi screenshots of every Compose surface (plus 200 % font scale), uploaded as an artifact; records, doesn't compare. Times out at 20 min |
| `baseline-profile.yaml` | Push → non-master touching `baselineprofile/**`, manual | Profiles on an API 34 emulator, one job per flavor; by hand with **commit**, pushes them through `commit-baseline-profiles.yaml` |
| `benchmarks.yaml` | Push → non-master touching app code, the build or benchmarks, manual | Startup and frame-timing benchmarks; fails on a slowdown |
| `live-providers.yaml` | PR changing `model/`, `repository/`, `util/` or the app's build file, daily, manual | Calls each provider's live API (`ExchangeRatesServiceTest`, `-PliveProviders`); not a required check, so a provider's outage is reported without blocking merges |
| `detekt.yaml` | PR, push → `master`, manual | Kotlin static analysis |
| `qodana.yaml` | PR, push → `master`, weekly, manual | JetBrains Qodana; when its project import fails dependency verification, lists the refused files (`verification-report.py`) |
| `codeql.yaml` | PR, push → `master`, weekly, manual | CodeQL on the Kotlin and the workflows |
| `semgrep.yaml` | PR, push → `master`, weekly, manual | SAST patterns |
| `gitleaks.yaml` | PR, push → `master`, weekly, manual | Secrets in the repository |
| `osv-scanner.yaml` | PR, push → `master`, weekly, manual | The release SBOM against OSV.dev |
| `owasp-dependency-check.yaml` | Mondays and Thursdays, manual | Shipped libraries against the NVD |
| `dependency-submission.yaml` | PR from this repository, push → `master`, manual | Sends GitHub's dependency graph every library the Gradle build resolves (`gradle/actions/dependency-submission`), the build tools' included, which GitHub can't read from Gradle files itself; Dependabot alerts and Dependency Review rely on it |
| `dependency-review.yaml` | PR | Blocks new dependencies with high-severity CVEs; waits up to 10 min for the PR's submitted graph |
| `gradle-wrapper.yaml` | PR, push → `master`, manual | Validates `gradle-wrapper.jar`, the one binary in the repo, against Gradle's releases; its own workflow so Scorecard sees it pass |
| `scorecard.yaml` | Push → `master`, weekly, manual | OpenSSF Scorecard (waits for the wrapper check on a push) |
| `update-verification-metadata.yaml` | Manual | Records dependency checksums from an empty cache, commits them, restarts the checks |
| `certificate-pins.yaml` | Weekly, PR changing the pins, manual | Checks every host still serves a pinned key ([security.md](security.md#certificate-pinning)); by hand with *update*, rewrites `BuiltInPins.kt` from the chains served now, commits it and restarts the checks |
| `code-scanning-report.yaml` | Manual | Saves the open code scanning alerts on `master`, from every tool, as an artifact and a summary table on the run page |
| `zizmor.yaml` | PR or push → `master` touching workflows, weekly, manual | zizmor (pinned by hash, `.github/zizmor`) audits the workflows for what actionlint doesn't: template injection, credentials a checkout leaves, cache poisoning, broad permissions, impostor actions. Every finding goes to code scanning; medium and above fail the job |
| `docs-checks.yaml` | PR or push → `master` touching the docs, weekly, manual | lychee: every local link and section anchor in `docs/` and the top-level Markdown must resolve (offline, each run), and external links weekly; typos: spelling, with the two correct words it misreads in `_typos.toml` ([exceptions.md](exceptions.md) #16) |
| `actionlint.yaml` | PR, push → `master`, on workflows | Workflow syntax |
| `site.yaml` | PR, push → `master`, on `docs/**`, manual | Website asset versions and CSP hashes ([website.md](website.md)) |

## The build gate

`build.yaml`'s jobs run side by side; a PR takes about five minutes.

- **`spotless`**: ktlint via Spotless.
- **`fuzz`**: one job per `FuzzTest` target, two minutes each with Jazzer; a crash uploads its input ([build.md](build.md#fuzz-tests)).
- **`compose-stability`**: fails on a composable that can't skip or a parameter the compiler can't prove stable, which strong skipping compares by identity, so an equal fresh value recomposes every time. Fix the type (immutable, `@Stable` / `@Immutable` when true, or `app/compose-stability.conf`). `Any` keys, `Context` and receivers are identity-compared on purpose; Glance composables compose once per update.
- **`build`**: `lint`, `test` and `assemble` for each flavor, six jobs. Lint has nothing disabled: a string missing from any locale fails it. Failed tests become error annotations.
- **`fdroid-release-build`**: the unsigned fdroid release APK and its SBOM, guarding the F-Droid build path.

**Speed.** Every PR and branch workflow has a `concurrency` group, so a new push cancels the previous run (`master` runs always finish). `setup-gradle` keeps Gradle's home and build cache: `master` writes it, PRs read it. Build cache and parallel execution are on; the configuration cache is off, as on CI it made lint + test + build about 70 s slower.

## Profiles and benchmarks

**Baseline Profile** runs one job per flavor (together they nearly hit the 90-minute limit), each on a Gradle Managed Device with KVM, uploading `baseline-profiles-<flavor>`. Commit the files, or run it by hand with **commit**: `commit-baseline-profiles.yaml` pushes them to the branch and starts Screenshots and the debug APK by `workflow_dispatch`, since a push with the workflow's own token starts no push workflows. **commit_run_id** commits an earlier run's profiles.

**Benchmarks** runs `InteractionBenchmarks` ten times each (five let a median wander 30 % on unchanged code), then posts the medians in the summary and as annotations (`benchmark-summary.py`) and fails a metric that is both 25 % and 5 ms slower than `baselineprofile/benchmark-baseline.json` (`benchmark-gate.py`). To accept a deliberate slowdown, commit the new medians as the baseline. The software-GPU emulator sometimes can't confirm a launch ("Unable to confirm activity launch completion"): the benchmark step then runs once more, with a warning, and any other failure, or that one twice, fails the run. It doesn't always report frame stats either; with no numbers the check says so and passes.

## Scanners

| Tool | Configuration |
|---|---|
| **detekt** | 2.0.0-alpha.6 (1.23.8, the last stable, is incompatible with Gradle 10). `config/detekt/detekt.yml` encodes the CLAUDE.md code-shape rules; size limits apply to composables too (six or more arguments → a state type and an actions type). No baseline. Type-aware rules run only per variant, so `./gradlew detekt` runs a set of variant tasks covering every source folder once (`detektVariantTasks`). One SARIF per task to the Security tab |
| **Qodana** | `qodana-jvm-community:2026.2`, pinned by digest. Production and test sources; inline PR comments; SARIF to the Security tab |
| **CodeQL** | `actions` (no build) and `java-kotlin`, extracted from a fresh in-process compile of the fdroid debug build (`--no-daemon --no-build-cache --rerun-tasks -Pkotlin.compiler.execution.strategy=in-process`). `security-and-quality` queries; build output and generated code excluded. A PR is analysed at its head commit, not GitHub's test merge, which GitHub can rebuild after the run starts, leaving the merge rule waiting for results on the new one (master requires PRs to be up to date, so the head holds master). Semgrep, Qodana and detekt upload their results the same way (`SARIF_REF`, `SARIF_SHA`). The Kotlin SARIF drops the findings in `.github/codeql/exceptions.yml` before upload ([exceptions.md](exceptions.md) #8). CodeQL trails Kotlin releases slightly; an unknown Kotlin shows as a failed analysis |
| **Semgrep** | `p/default`, `p/security-audit`, `p/kotlin`, `p/java`, `p/github-actions`. Installed from `.github/semgrep/requirements.txt`, every package pinned by hash (`pip-compile --generate-hashes`), not the unpinned image. Any finding fails. The two `nosemgrep` exceptions pass Semgrep but still show as open in code scanning, which ignores SARIF suppressions, until dismissed there |
| **OSV-Scanner** | Writes the release SBOM and checks every library in it by package URL; any known vulnerability fails. No API key |
| **OWASP Dependency-Check** | CLI from Maven Central, version and SHA-256 pinned in the workflow (update both by hand; Dependabot doesn't track it), not the action's `latest` image. `:app:owaspLibraries` copies each shipped library out of Gradle's cache named by its coordinates, since the tool reads files, not Gradle's graph; aars are opened. `--failOnCVSS 7 --enableRetired`, OSS Index off, NVD data cached between runs: the first load from NVD's yearly JSON feeds (the API keeps no progress, so one error over its hour-plus of pages loses it all), then each run's changes through the API with `NVD_API_KEY`; only a completed load is cached. Twice a week so the cache never goes unread for GitHub's 7-day eviction. `--suppression config/owasp/suppressions.xml` ([exceptions.md](exceptions.md) #7) |
| **Scorecard** | Publishes to the public Scorecard API; SARIF to the Security tab |

## Dependabot

Weekly, Mondays, 7-day cooldown, in `.github/dependabot.yml`:

| Ecosystem | Directories | Open PRs |
|---|---|---|
| `gradle` | `/` | 10 |
| `github-actions` | `/` | 5 |
| `pip` | `/.github/scripts`, `/.github/semgrep`, `/.github/zizmor`, `/.github/docs-checks` (hash-pinned; one grouped PR) | 5 |

Grouped: `androidx.*`; Kotlin + KSP; Moshi; OkHttp; test dependencies; all actions. A Gradle update fails dependency verification until its checksums are recorded: run **Update dependency checksums** on its branch once the update looks right.

## Permissions

Every workflow starts at `contents: read` (`scorecard.yaml`: `read-all`, as Scorecard's template does); jobs get more only where needed:

| Workflow | Extra |
|---|---|
| detekt, semgrep, zizmor, owasp, osv-scanner | `security-events: write` |
| qodana | `security-events`, `pull-requests`, `checks: write` |
| codeql | `security-events: write`, `actions: read`, `packages: read` |
| scorecard | `security-events: write`, `id-token: write`, `actions: read` |
| release-apk | `contents`, `id-token`, `attestations: write` on the publish job only, which never sees the keys |
| baseline-profile | `contents`, `actions: write` on its commit job only |
| commit-baseline-profiles, update-verification-metadata, certificate-pins (*update* only) | `contents`, `actions: write`: push to the branch, then dispatch the checks; no build in the commit workflow |
| dependency-review | `pull-requests: write` |
| code-scanning-report | `security-events: read` only (no checkout) |

Every checkout sets `persist-credentials: false`, so no later step finds the token in `.git/config`. The three workflows that push give the token to `git` only for the push itself, as a one-off `http.extraheader` on that `git push`. That keeps it out of the repository's config, not out of the job: other steps can still be given `github.token` (dispatching a workflow, downloading an artifact), and what the token may do is set by the job's `permissions`, which `persist-credentials` doesn't change. The release build runs without Gradle's cache (`cache-disabled`), so nothing a branch's run cached can reach the signed APKs.
