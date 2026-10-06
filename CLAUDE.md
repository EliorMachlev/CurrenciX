# Repo instructions for Claude Code

Read the project docs before making changes. Contributor rules — including branching, code style, PR checklist, and commit conventions — live in [`docs/markDown/contributing.md`](docs/markDown/contributing.md).

Other reference docs are in [`docs/markDown/`](docs/markDown/):

- [`overview.md`](docs/markDown/overview.md) — project overview
- [`architecture.md`](docs/markDown/architecture.md) — architecture
- [`build-and-flavors.md`](docs/markDown/build-and-flavors.md) — build variants
- [`api-providers.md`](docs/markDown/api-providers.md) — exchange rate providers
- [`ci-cd.md`](docs/markDown/ci-cd.md) — CI/CD pipelines
- [`security.md`](docs/markDown/security.md) — security posture
- [`exceptions.md`](docs/markDown/exceptions.md) — the only suppressions allowed, and why

Follow the guidance in those files. If a docs update is needed, edit the relevant `.md` there.

## Code-shape defaults

When touching code (especially during refactors), always look for and apply these where possible:

- **Extract functions** for any block that's non-trivial, used in 2+ places, or is a discrete action likely to be reused in a future change — even a small helper is worth it if it clarifies intent or unlocks reuse.
- **Deduplicate**: identical or near-identical logic in two places should become one helper (top-level `internal` fun, extension, or shared util in `model/adapter/AdapterUtils.kt` / similar).
- **Hoist to variables/constants/enums**: repeated expressions become locals; repeated literals (URLs, date patterns, magic numbers, keys) become named `const val` or `val` at file/package scope; a fixed set of related string/int values becomes an `enum class` or sealed hierarchy instead of stringly-typed code.
- **Always think about reusability, performance, and security** when making changes — favor shapes that can be reused, avoid unnecessary work on hot paths, and don't introduce injection / auth / data-exposure regressions.

Do this in-line with whatever task you're doing — don't gate it behind a separate "refactor" ask.

## No suppressions, no cosmetic workarounds

Full rule: [`contributing.md`](docs/markDown/contributing.md#no-suppressions-no-cosmetic-workarounds).

- **Never add a suppression of any kind**: `@Suppress`, `@file:Suppress`, `@SuppressLint`, `@SuppressWarnings`, `tools:ignore`, `//noinspection`, `ktlint-disable`, `nosemgrep`, a detekt or lint baseline entry, a disabled rule in a config file, `-dontwarn`, or a warning-silencing compiler flag.
- **Never write a workaround that hides a real issue just to quiet the compiler or an analyzer** (a wrapper, cast or reflection around a deprecated call, a rename or restructure done only so a rule stops matching, excluding a file from a check). If the problem is still there, the warning must be too.
- **Fix the cause instead.** If there is no real fix yet, leave the warning visible and tell the user what it is and why it can't be fixed now. Do not hide it and do not present a hidden warning as fixed.
- The only suppressions in the tree are the exceptions recorded in [`exceptions.md`](docs/markDown/exceptions.md), and `NoSuppressionsTest` fails the unit tests on any other. Never add an exception on your own: it is the user's decision. If you think one is warranted, say what the finding is and why it has no real fix, and wait for the answer.

## Import ordering

Spotless (ktlint) enforces `ij_kotlin_imports_layout = *,java.**,javax.**,kotlin.**,^`. When adding an import, place it so the file stays in this order:

1. All non-`java` / non-`javax` / non-`kotlin` imports, in strict alphabetical order (so `androidx.compose.*` comes before `androidx.core.*` — do not group by prefix beyond that).
2. Then `java.*`, then `javax.*`, then `kotlin.*` — each group alphabetical.

Add the new import in the right slot the first time; do not rely on a follow-up spotlessApply pass in CI.

## Screenshots (Roborazzi) CI

Don't wait for the Screenshots workflow to finish when nobody will review the pictures — carry on with the next task. The PR still can't be merged until that workflow passes, so check that it's green before treating the PR as ready.
