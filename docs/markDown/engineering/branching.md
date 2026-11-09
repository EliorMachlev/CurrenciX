# Branches and the build pipeline

Work moves through four stages. Each stage has a long-lived branch except the first, and each one runs more checks than the one before, so a small change is merged fast and a release candidate gets the full set.

| Stage | Branch | What lands here | Checks that must pass to get in |
|---|---|---|---|
| **Dev** | any short-lived branch (`feat/…`, `fix/…`, `dependabot/…`) | one change | the **Dev** checks, on the PR into `Alpha` |
| **Alpha** | `Alpha` | squash-merged PRs | the **Alpha** checks, on the push and on the promotion PR into `PreRelease` |
| **Prerelease** | `PreRelease` | `Alpha`, promoted when it is stable | the **Prerelease** checks, on the promotion PR into `Release` |
| **Release** | `Release` (was `master`) | `PreRelease`, promoted when it is ready to ship | tag `v<versionName>`: signing, provenance, publishing ([release.md](release.md)) |

New features stay on their own branch until they are merged into `Alpha`. `Alpha` is the default branch (Dependabot version updates and security updates both open against `Alpha`, the default branch.

## Setup

The branches are `Alpha` (default), `PreRelease` and `Release`; GitHub matches branch names case-sensitively, so the workflow filters use exactly these spellings. Remaining by hand in Settings → Rules: the per-branch rulesets above, and the "Protection" ruleset pointed at all three branches.
