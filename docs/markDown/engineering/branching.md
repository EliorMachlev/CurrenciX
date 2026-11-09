# Branches and the build pipeline

Work moves through four stages. Each stage has a long-lived branch except the first, and each one runs more checks than the one before, so a small change is merged fast and a release candidate gets the full set.

| Stage | Branch | What lands here | Checks that must pass to get in |
|---|---|---|---|
| **Dev** | any short-lived branch (`feat/…`, `fix/…`, `dependabot/…`) | one change | the **Dev** checks, on the PR into `Alpha` |
| **Alpha** | `Alpha` | squash-merged PRs | the **Alpha** checks, on the push and on the promotion PR into `PreRelease` |
| **Prerelease** | `PreRelease` | `Alpha`, promoted when it is stable | the **Prerelease** checks, on the promotion PR into `Release` |
| **Release** | `Release` (was `master`) | `PreRelease`, promoted when it is ready to ship | tag `v<versionName>`: signing, provenance, publishing ([release.md](release.md)) |

New features stay on their own branch until they are merged into `Alpha`. `Alpha` is the default branch (Dependabot version updates and security updates both open against `Alpha`, the default branch).

## Checks per stage

- **Dev** (every PR, and pushes to `Alpha`): build (lint, test, assemble per flavor, fdroid-release-build, spotless, compose-stability, fuzzers), detekt, gitleaks, semgrep, gradle-wrapper, zizmor, jactionlint, docs-checks, site, certificate-pins.
- **Alpha** (promotion PRs into `PreRelease` or `Release`, and pushes to `Alpha`): CodeQL, Qodana, dependency-review, dependency-submission, OSV-Scanner, screenshots, live-providers. These never run on a PR into `Alpha`, so the `Alpha` ruleset must not require them.
- **Prerelease** (PRs into `Release`): apk-checks (reproducible build, APK size against `Alpha`) and benchmarks.
- **Release**: a `v*` tag on `Release` runs `release-apk.yaml`.

Rulesets: `Alpha` requires the Dev checks; `PreRelease` those plus the Alpha checks; `Release` those plus the Prerelease checks. All three require a pull request, block force pushes and deletion, and take the promotion as a merge commit (no linear history on `PreRelease` or `Release`).

## Setup

The three branches exist: `Alpha` (the default branch), `PreRelease` and `Release` (the former `master`). GitHub matches branch names case-sensitively, so the workflow filters use exactly these spellings. Scheduled workflows run, and the dependency cache is saved, only on the default branch.

Remaining by hand in Settings → Rules: the per-branch rulesets above, and the "Protection" ruleset pointed at all three branches.
