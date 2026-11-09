# Branches and the build pipeline

Work moves through four stages. Each stage has a long-lived branch except the first, and each one runs more checks than the one before, so a small change is merged fast and a release candidate gets the full set.

| Stage | Branch | What lands here | Checks that must pass to get in |
|---|---|---|---|
| **Dev** | any short-lived branch (`feat/…`, `fix/…`, `dependabot/…`) | one change | the **Dev** checks, on the PR into `alpha` |
| **Alpha** | `alpha` | squash-merged PRs | the **Alpha** checks, on the push and on the promotion PR into `prerelease` |
| **Prerelease** | `prerelease` | `alpha`, promoted when it is stable | the **Prerelease** checks, on the promotion PR into `release` |
| **Release** | `release` (was `master`) | `prerelease`, promoted when it is ready to ship | tag `v<versionName>`: signing, provenance, publishing ([release.md](release.md)) |

New features stay on their own branch until they are merged into `alpha`. `release` is the default branch and the only one a tag may be on.

## Which checks run where

A check that runs at a stage also runs at every later one, because promotion PRs carry the earlier stage's commit (its check runs are already on the SHA) and are re-run for the new base.

| Tier | Runs on | Workflows |
|---|---|---|
| Dev | every PR (any base), and pushes to `alpha`, `prerelease`, `release` | `build` (lint, tests, assemble), `detekt`, `gitleaks`, `semgrep`, `gradle-wrapper`, `zizmor`, `jactionlint`, `docs-checks`, `site`, `certificate-pins` |
| Alpha | PRs into `prerelease` or `release`; pushes to `alpha`, `prerelease`, `release` | `codeql`, `qodana`, `dependency-review`, `dependency-submission`, `osv-scanner`, `screenshots`, `live-providers` |
| Prerelease | PRs into `release`; pushes to `prerelease`, `release` | `apk-checks` (reproducible build, APK size against what shipped), `benchmarks` |
| Release | a `v*` tag on `release` | `release-apk` → `build-release` (signed APK, SBOMs, SLSA provenance), `tag-release`, `scorecard` |

Scheduled and manual runs are unchanged. Feature branches push no CI of their own beyond the PR (the debug APK in `apk-artifact` and the baseline profile run on any branch that is not one of the four long-lived ones).

Baselines: screenshots and coverage are compared with `alpha`'s latest push; the APK size with `release`'s latest.

## Rulesets

Required checks are set per branch in Settings → Rules. Names are the check names shown on a PR.

- `alpha`: pull request required, up to date, the Dev checks.
- `prerelease`: pull request from `alpha` only, up to date, the Dev and Alpha checks.
- `release`: pull request from `prerelease` only, up to date, the Dev, Alpha and Prerelease checks (today's required set).
- All three: no force pushes, no deletions, linear history; `alpha` squash-merges feature PRs, `prerelease` and `release` take promotions as a merge commit or a fast-forward so the SHA keeps its check runs.

## Moving to this layout (once)

1. Merge the pipeline PR into `master`. Until the rename, every workflow also lists `master`, so nothing is skipped.
2. Settings → Branches: rename `master` to `release` (GitHub redirects old URLs and moves open PRs); `git fetch origin && git branch -m master release && git branch -u origin/release release`.
3. Create `alpha` and `prerelease` from `release`.
4. Apply the rulesets above, and point the "Protection" ruleset at `release`.
5. Retarget open PRs to `alpha`; Dependabot already targets `alpha`.
6. In a follow-up, drop the leftover `master` entries from the trigger lists.
