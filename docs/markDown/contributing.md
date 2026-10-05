# Contributing

## What We Accept

| Type | Accepted? |
|---|---|
| Bug fixes | Yes |
| New exchange rate providers | Discuss first |
| New features | Discuss first |
| Refactors | Discuss first |

The project favours simplicity. Large feature additions are unlikely to be merged without prior discussion.

## Translations

The fork inherits translations for 20+ languages from upstream Currencies but doesn't yet run its own translation workflow. If you'd like to contribute a translation, open an issue first so we can coordinate.

## Code Contributions

### Setup

1. Fork and clone the repository.
2. Open in Android Studio (latest stable recommended).
3. JDK 21 is required.
4. `./gradlew assembleFdroidDebug` should build without errors.

### Branching Base

Every new branch must be forked from **remote master** (`origin/master`), not from the local current branch:

```sh
git fetch origin master
git switch -c <new-branch> origin/master
```

This ensures the branch starts from the latest upstream state and does not inherit stale local work or unrelated in-progress commits from another feature. If a PR intentionally depends on another unmerged branch, state that dependency explicitly.

### Branch Naming

Use descriptive branch names. The CI `apk-artifact.yaml` workflow runs on any non-master push and uploads a debug APK as an artifact for easy review.

### Code Style

- Kotlin only (no Java in `app/` or `helpers/`).
- Follow existing patterns — MVVM, Repository, Compose UI in a single Activity (new screens are a `Screen` key + a route in `AppNavHost`, not a new Activity) — see [architecture.md](architecture.md). Konsist tests enforce the View / ViewModel / Repository / Model layer boundaries; a PR that breaks them fails `test<Flavor>DebugUnitTest`.
- Run `./gradlew detekt` locally before opening a PR. CI enforces it: any finding not already in `config/detekt/baseline-<module>.xml` fails the build (see [ci-cd.md](ci-cd.md#detekt)).
- Run `./gradlew spotlessCheck` — CI will fail on formatting drift. Use `./gradlew spotlessApply` to auto-fix.
- Avoid `java.lang.*` qualifiers (Kotlin imports these automatically).
- Avoid swallowed exceptions: always use the caught exception variable in the catch block.

### No Suppressions, No Cosmetic Workarounds

Warnings and findings get fixed, not hidden.

- **No suppressions of any kind.** That covers `@Suppress`, `@file:Suppress`, `@SuppressLint`, `@SuppressWarnings`, `tools:ignore`, `//noinspection`, `ktlint-disable`, `nosemgrep`, new detekt or lint baseline entries, turning a rule off in `detekt.yml` / `lint.xml` / `.editorconfig`, `-dontwarn`, and compiler flags that silence warnings. (`@OptIn` for an experimental API is an explicit opt-in, not a suppression.)
- **No workaround whose only purpose is to make a warning go away.** If the code still has the problem, the warning must still be there. Don't route a deprecated call through a wrapper, reflection or a cast, rename or restructure something only so an analyzer stops matching it, or exclude a file from a check.
- **Fix the cause.** Migrate off the deprecated API, split the long function, make the parameter list a type, remove the unused code.
- **When no real fix exists yet,** leave the warning visible and say so in the PR. A visible warning is accurate; a hidden one is not.

The suppressions and baseline entries already in the tree predate this rule. They are debt, not precedent: never add one, and when you change code that one covers, fix the issue and delete it.

### Pull Request Checklist

- [ ] `./gradlew check assembleDebug` passes locally (includes the Konsist architecture tests)
- [ ] `./gradlew detekt` reports no new findings
- [ ] `./gradlew spotlessCheck` is clean
- [ ] No new suppressions or baseline entries, and no workaround that only hides a warning (see [No Suppressions, No Cosmetic Workarounds](#no-suppressions-no-cosmetic-workarounds))
- [ ] If you changed a Compose screen, look over its screenshots. They aren't gated: the Screenshots workflow re-renders them on every branch push and uploads the PNGs as an artifact. To render locally: `./gradlew :app:recordRoborazziFdroidDebug --tests "com.eliormachlev.currencix.screenshots.*"`. The `--tests` filter is required, because Jazzer's instrumentation breaks Robolectric if the fuzz tests run in the same task.
- [ ] If adding a dependency: check F-Droid licence compatibility

### Commit Message Convention

```
type(scope): short description

Examples:
feat(providers): add ECB historical fallback
fix(calculator): handle division by zero
chore(deps): bump moshi to 1.15.2
chore(ci): pin checkout action to SHA
```

## Reporting Issues

Open a GitHub Issue with:
- Android version
- App version (`Settings → About`)
- Selected exchange rate provider
- Steps to reproduce
- Expected vs. actual behaviour
