# CI / CD

Everything is in [`.github/workflows/`](../../../.github/workflows). Every action is pinned to a full commit SHA with its release in a comment (`# v7.0.1`); every Java job runs JDK 25. Every job's first step is Harden-Runner in audit mode ([security.md](security.md#supply-chain)).

## Workflows

Which workflow runs at which stage (Dev, Alpha, Prerelease, Release) is in [branching.md](branching.md); the "Runs on" column below says what triggers each.

| Workflow | Runs on | Does |
|---|---|---|
| [`build.yaml`](../../../.github/workflows/build.yaml) | PR, push → `alpha`, manual | The gate (below) |
| [`release-apk.yaml`](../../../.github/workflows/release-apk.yaml) | `v*` tag, manual | Calls `build-release.yaml`, then publishes the GitHub release ([release.md](release.md)) |
| [`build-release.yaml`](../../../.github/workflows/build-release.yaml) | Called by `release-apk.yaml` | Signed release APK, SBOMs (CycloneDX and three SPDX files from different generators), checksums and the provenance attestation |
| [`tag-release.yaml`](../../../.github/workflows/tag-release.yaml) | Manual, on `release` | Tags `release` `v<versionName>` (an existing tag fails it) and starts `release-apk.yaml` on the tag ([release.md](release.md#releasing-is-pushing-a-tag)) |
| [`apk-artifact.yaml`](../../../.github/workflows/apk-artifact.yaml) | Push → a feature branch, manual | fdroid debug APK as an artifact, linked in the run summary |
| [`apk-checks.yaml`](../../../.github/workflows/apk-checks.yaml) | PR into `release` (or `master` until the rename) touching the app or the build, weekly, manual | The unsigned fdroid release built twice, from two checkouts with no build cache, and compared byte for byte (any difference fails, with `diffoscope`'s account as an artifact); on PRs, `diffuse` compares the APK with master's latest in the run summary. Not part of the gate |
| [`screenshots.yaml`](../../../.github/workflows/screenshots.yaml) | Push → `alpha`, manual | Roborazzi screenshots of every Compose surface (plus 200 % font scale), uploaded as an artifact. On a branch, compared byte for byte with alpha's latest ([`screenshot-diff.py`](../../../.github/scripts/screenshot-diff.py)): the run summary lists what changed, is new or is gone, with a warning, and the changed and new ones are uploaded with master's beside each; a difference never fails it. Times out at 20 min |
| [`baseline-profile.yaml`](../../../.github/workflows/baseline-profile.yaml) | Push → a feature branch touching `baselineprofile/**`, manual | Profiles on an API 34 emulator, one job per flavor; by hand with **commit**, pushes them through [`commit-baseline-profiles.yaml`](../../../.github/workflows/commit-baseline-profiles.yaml) |
| [`benchmarks.yaml`](../../../.github/workflows/benchmarks.yaml) | PR into `release` touching app code, the build or benchmarks; Sundays; manual | Startup and frame-timing benchmarks; fails on a slowdown |
| [`live-providers.yaml`](../../../.github/workflows/live-providers.yaml) | PR changing [`model/`](../../../app/src/main/kotlin/com/eliormachlev/currencix/model), [`repository/`](../../../app/src/main/kotlin/com/eliormachlev/currencix/repository), [`util/`](../../../app/src/main/kotlin/com/eliormachlev/currencix/util) or the app's build file, daily, manual | Calls each provider's live API (`ExchangeRatesServiceTest`, `-PliveProviders`); not a required check, so a provider's outage is reported without blocking merges |
| [`detekt.yaml`](../../../.github/workflows/detekt.yaml) | PR, push → `alpha`, manual | Kotlin static analysis |
| [`qodana.yaml`](../../../qodana.yaml) | PR, push → `alpha`, weekly, manual | JetBrains Qodana; when its project import fails dependency verification, lists the refused files ([`verification-report.py`](../../../.github/scripts/verification-report.py)) |
| [`codeql.yaml`](../../../.github/workflows/codeql.yaml) | PR, push → `alpha`, weekly, manual | CodeQL on the Kotlin and the workflows |
| [`semgrep.yaml`](../../../.github/workflows/semgrep.yaml) | PR, push → `alpha`, weekly, manual | SAST patterns |
| [`gitleaks.yaml`](../../../.github/workflows/gitleaks.yaml) | PR, push → `alpha`, weekly, manual | Secrets in the repository (the whole history, with gitleaks built from a pinned, verified commit and cached) |
| [`osv-scanner.yaml`](../../../.github/workflows/osv-scanner.yaml) | PR, push → `alpha`, weekly, manual | The release SBOM against OSV.dev |
| [`owasp-dependency-check.yaml`](../../../.github/workflows/owasp-dependency-check.yaml) | Mondays and Thursdays, manual | Shipped libraries against the NVD |
| [`dependency-submission.yaml`](../../../.github/workflows/dependency-submission.yaml) | PR from this repository, push → `alpha`, manual | Sends GitHub's dependency graph every library the Gradle build resolves (`gradle/actions/dependency-submission`), the build tools' included, which GitHub can't read from Gradle files itself; Dependabot alerts and Dependency Review rely on it |
| [`dependency-review.yaml`](../../../.github/workflows/dependency-review.yaml) | PR | Blocks new dependencies with high-severity CVEs; waits up to 10 min for the PR's submitted graph |
| [`gradle-wrapper.yaml`](../../../.github/workflows/gradle-wrapper.yaml) | PR, push → `alpha`, manual | Validates [`gradle-wrapper.jar`](../../../gradle/wrapper/gradle-wrapper.jar), the one binary in the repo, against Gradle's releases; its own workflow so Scorecard sees it pass |
| [`scorecard.yaml`](../../../.github/workflows/scorecard.yaml) | Push → `release`, weekly, manual | OpenSSF Scorecard (waits for the wrapper check on a push) |
| [`dependency-cache.yaml`](../../../.github/workflows/dependency-cache.yaml) | Push → `release` changing the build files, PR changing it or its action, weekly, manual | Builds the read-only dependency cache the other Gradle jobs restore (below) |
| [`update-verification-metadata.yaml`](../../../.github/workflows/update-verification-metadata.yaml) | Manual | Records dependency checksums from an empty cache, commits them, then starts build, screenshots, APK, detekt, OSV, CodeQL, gitleaks, gradle-wrapper, Qodana and Semgrep on the new commit (`dependency-review` runs only on a PR event, so it stays unreported until someone pushes or closes and reopens the PR) |
| [`certificate-pins.yaml`](../../../.github/workflows/certificate-pins.yaml) | Weekly, PR changing the pins, manual | Checks every host still serves a pinned key, and that the pins are at least 90 days from expiry ([security.md](security.md#certificate-pinning)); by hand with *update*, rewrites the pins in [`network_security_config.xml`](../../../app/src/main/res/xml/network_security_config.xml) from the chains served now, a year ahead, commits it and restarts the checks |
| [`code-scanning-report.yaml`](../../../.github/workflows/code-scanning-report.yaml) | Manual | Saves the open code scanning alerts on `release`, from every tool, as an artifact and a summary table on the run page |
| [`zizmor.yaml`](../../../.github/workflows/zizmor.yaml) | PR or push → `alpha` touching workflows, weekly, manual | zizmor (pinned by hash, [`.github/zizmor`](../../../.github/zizmor)) audits the workflows for what jactionlint doesn't: template injection, credentials a checkout leaves, cache poisoning, broad permissions, impostor actions. Every finding goes to code scanning; medium and above fail the job |
| [`docs-checks.yaml`](../../../.github/workflows/docs-checks.yaml) | PR or push → `alpha` touching the docs, weekly, manual | lychee: every local link and section anchor in [`docs/`](../..) and the top-level Markdown must resolve (offline, each run), and external links weekly; typos: spelling, with the two correct words it misreads in [`_typos.toml`](../../../_typos.toml) ([exceptions.md #16](exceptions.md#16-typos-infor-and-mis)) |
| [`jactionlint.yaml`](../../../.github/workflows/jactionlint.yaml) | PR, push → `alpha`, on workflows, local actions and `dependabot.yml` | [jactionlint](https://github.com/jdx/jactionlint), the maintained fork of actionlint: syntax, expressions and ShellCheck, plus its `default` profile (a timeout on every job, `concurrency:`, least permissions, `pipefail`). Any finding fails it, warnings and notes too (`--strict-exit`), and no ignore comment, config-file ignore or baseline applies (`--no-ignores`), nor any config file that could turn a rule off (`--config-file=/dev/null`). Its release is pinned by version and SHA-256 in the workflow (update both by hand; Dependabot doesn't track it, and its own action downloads the binary without checking it) |
| [`site.yaml`](../../../.github/workflows/site.yaml) | PR, push → `alpha`, on `docs/**`, manual | Website asset versions, CSP hashes and the `llms-full.txt` copy for AI assistants ([website.md](website.md)) |

## The build gate

[`build.yaml`](../../../.github/workflows/build.yaml)'s jobs run side by side; a PR takes about five minutes.

- **`spotless`**: ktlint via Spotless.
- **`fuzz`**: one job per `FuzzTest` target, two minutes each with Jazzer; a crash uploads its input ([build.md](build.md#fuzz-tests)).
- **`compose-stability`**: fails on a composable that can't skip or a parameter the compiler can't prove stable, which strong skipping compares by identity, so an equal fresh value recomposes every time. Fix the type (immutable, `@Stable` / `@Immutable` when true, or [`app/compose-stability.conf`](../../../app/compose-stability.conf)). `Any` keys, `Context` and receivers are identity-compared on purpose; Glance composables compose once per update.
- **`build`**: `lint`, `test` and `assemble` for each flavor, six jobs. Lint has nothing disabled: a string missing from any locale fails it. The fdroid `lint` job also runs Licensee on the release's libraries. Each `test` job runs Robolectric offline on a Gradle-verified Android jar ([build.md](build.md#dependency-choices)) and puts a Kover coverage summary on the run, with the report kept as an artifact. `coverage-comment` (own job, `pull-requests: write`, same-repo pull requests only) posts the F-Droid figures on the pull request with the change from alpha's last run. Failed tests become error annotations.
- **`fdroid-release-build`**: the unsigned fdroid release APK and its SBOMs, guarding the F-Droid build path.

**Speed.** Every PR and branch workflow has a `concurrency` group, so a new push cancels the previous run (the long-lived branches' runs always finish). `setup-gradle` keeps Gradle's home and build cache: `master` writes it, PRs read it. Under it, every Gradle job but two first restores a read-only dependency cache ([`.github/actions/dependency-cache`](../../../.github/actions/dependency-cache/action.yml), at `GRADLE_RO_DEP_CACHE`): every library, plugin and test jar the build resolves, Robolectric's among them, as of `master`'s last change to the build files. Maven Central rate-limits GitHub's runners (HTTP 429 and 403), and a job whose own cache missed one jar used to fail over it; now it downloads only what changed since. [`dependency-cache.yaml`](../../../.github/workflows/dependency-cache.yaml) builds it from an empty Gradle home (every resolvable configuration, plus Spotless's ktlint and AGP's aapt2, which no configuration lists), fails rather than save a cache a failed download left incomplete, and saves it only from `master`, so a branch can't write what other runs read, and Gradle checks every file taken from it against [`verification-metadata.xml`](../../../gradle/verification-metadata.xml) as it does a download. The release build doesn't use it (no caches at all), nor do `update-verification-metadata.yaml` (which records checksums from an empty cache) and the reproducible build in `apk-checks.yaml` (two fresh checkouts, as F-Droid builds). Build cache and parallel execution are on; the configuration cache is off, as on CI it made lint + test + build about 70 s slower.

## Profiles and benchmarks

**Baseline Profile** runs one job per flavor (together they nearly hit the 90-minute limit), each on a Gradle Managed Device with KVM, uploading `baseline-profiles-<flavor>`. Commit the files, or run it by hand with **commit**: [`commit-baseline-profiles.yaml`](../../../.github/workflows/commit-baseline-profiles.yaml) pushes them to the branch and starts Screenshots and the debug APK by `workflow_dispatch`, since a push with the workflow's own token starts no push workflows. **commit_run_id** commits an earlier run's profiles.

**Benchmarks** runs `InteractionBenchmarks` ten times each (five let a median wander 30 % on unchanged code), then posts the medians in the summary and as annotations ([`benchmark-summary.py`](../../../.github/scripts/benchmark-summary.py)) and fails a metric that is both 25 % and 5 ms slower than [`baselineprofile/benchmark-baseline.json`](../../../baselineprofile/benchmark-baseline.json) ([`benchmark-gate.py`](../../../.github/scripts/benchmark-gate.py)). To accept a deliberate slowdown, commit the new medians as the baseline. The software-GPU emulator sometimes can't confirm a launch ("Unable to confirm activity launch completion"): the benchmark step then runs once more, with a warning, and any other failure, or that one twice, fails the run. It doesn't always report frame stats either; with no numbers the check says so and passes.

## Scanners

| Tool | Configuration |
|---|---|
| **detekt** | 2.0.0-alpha.6 (1.23.8, the last stable, is incompatible with Gradle 10). [`config/detekt/detekt.yml`](../../../config/detekt/detekt.yml) encodes the CLAUDE.md code-shape rules; size limits apply to composables too (six or more arguments → a state type and an actions type). No baseline. Type-aware rules run only per variant, so `./gradlew detekt` runs a set of variant tasks covering every source folder once (`detektVariantTasks`). One SARIF per task to the Security tab |
| **Qodana** | `qodana-jvm-community:2026.2`, pinned by digest. Production and test sources; inline PR comments; SARIF to the Security tab |
| **CodeQL** | `actions` (no build) and `java-kotlin`, extracted from a fresh in-process compile of the fdroid debug build (`--no-daemon --no-build-cache --rerun-tasks -Pkotlin.compiler.execution.strategy=in-process`). `security-and-quality` queries; build output and generated code excluded. A PR is analysed at its head commit, not GitHub's test merge, which GitHub can rebuild after the run starts, leaving the merge rule waiting for results on the new one (master requires PRs to be up to date, so the head holds master). Semgrep, Qodana and detekt upload their results the same way (`SARIF_REF`, `SARIF_SHA`). The Kotlin SARIF drops the findings in [`.github/codeql/exceptions.yml`](../../../.github/codeql/exceptions.yml) before upload ([exceptions.md #8](exceptions.md#8-codeql-javauseless-null-check-on-quickconversionssheet)). CodeQL trails Kotlin releases slightly; an unknown Kotlin shows as a failed analysis |
| **Semgrep** | `p/default`, `p/security-audit`, `p/kotlin`, `p/java`, `p/github-actions`. Installed from [`.github/semgrep/requirements.txt`](../../../.github/semgrep/requirements.txt), every package pinned by hash (`pip-compile --generate-hashes`), not the unpinned image. Any finding fails. The two `nosemgrep` exceptions pass Semgrep but still show as open in code scanning, which ignores SARIF suppressions, until dismissed there |
| **OSV-Scanner** | Writes the release SBOM and checks every library in it by package URL; any known vulnerability fails. No API key |
| **OWASP Dependency-Check** | CLI from Maven Central, version and SHA-256 pinned in the workflow (update both by hand; Dependabot doesn't track it), not the action's `latest` image. `:app:owaspLibraries` copies each shipped library out of Gradle's cache named by its coordinates, since the tool reads files, not Gradle's graph; aars are opened. `--failOnCVSS 7 --enableRetired`, OSS Index off, NVD data cached between runs: the first load from NVD's yearly JSON feeds (the API keeps no progress, so one error over its hour-plus of pages loses it all), then each run's changes through the API with `NVD_API_KEY`; only a completed load is cached. Twice a week so the cache never goes unread for GitHub's 7-day eviction. `--suppression config/owasp/suppressions.xml` ([exceptions.md #7](exceptions.md#7-owasp-dependency-check-cve-2018-8909-and-cve-2023-22737-on-wire-runtime)) |
| **Scorecard** | Publishes to the public Scorecard API; SARIF to the Security tab |

## Dependabot

Weekly, Mondays, 7-day cooldown, in [`.github/dependabot.yml`](../../../.github/dependabot.yml):

| Ecosystem | Directories | Open PRs |
|---|---|---|
| `gradle` | `/` | 10 |
| `github-actions` | `/` | 5 |
| `pip` | `/.github/scripts`, `/.github/semgrep`, `/.github/zizmor`, `/.github/docs-checks` (hash-pinned; one grouped PR) | 5 |

Grouped: `androidx.*`; Kotlin; OkHttp; test dependencies; all actions; all Python packages. Robolectric's `android-all-instrumented` is ignored: it is updated by hand with Robolectric ([build.md](build.md#dependency-choices)). A Gradle update fails dependency verification until its checksums are recorded: run **Update dependency checksums** on its branch once the update looks right.

## Permissions

Every workflow starts at `contents: read` ([`scorecard.yaml`](../../../.github/workflows/scorecard.yaml): `read-all`, as Scorecard's template does); jobs get more only where needed:

| Workflow | Extra |
|---|---|
| detekt, semgrep, zizmor, owasp, osv-scanner | `security-events: write` |
| qodana | `security-events`, `pull-requests`, `checks: write` |
| codeql | `security-events: write`, `actions: read`, `packages: read` |
| screenshots, apk-checks (`size` job) | `actions: read`, to fetch alpha's latest screenshots or release's latest APK |
| dependency-submission | `contents: write` on its submit job, which the dependency-graph API requires |
| scorecard | `security-events: write`, `id-token: write`, `actions: read` |
| release-apk | the caller: `contents: write` on the publish job only, which never sees the keys; `id-token` and `attestations: write` only so the called workflow's `attest` job can have them |
| build-release | reusable: `contents: read` for the build job, which holds the keys; `id-token` and `attestations: write` for the attest job only, which never sees them |
| baseline-profile | `contents`, `actions: write` on its commit job only |
| commit-baseline-profiles, update-verification-metadata, certificate-pins (*update* only) | `contents`, `actions: write`: push to the branch, then dispatch the checks; no build in the commit workflow |
| tag-release | `contents`, `actions: write`: create the tag through the API, then dispatch Release (`release-apk.yaml`) on it |
| dependency-review | `pull-requests: write` |
| code-scanning-report | `security-events: read` only (no checkout) |

Every checkout sets `persist-credentials: false`, so no later step finds the token in `.git/config`. The three workflows that push give the token to `git` only for the push itself, as a one-off `http.extraheader` on that `git push`. That keeps it out of the repository's config, not out of the job: other steps can still be given `github.token` (dispatching a workflow, downloading an artifact), and what the token may do is set by the job's `permissions`, which `persist-credentials` doesn't change. The release build runs without Gradle's cache (`cache-disabled`), so nothing a branch's run cached can reach the signed APKs.
