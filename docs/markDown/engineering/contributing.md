# Contributing

| Type | Accepted? |
|---|---|
| Bug fixes | Yes |
| New providers, features, refactors | Discuss first, in an issue |

The project favours simplicity; large additions are unlikely to be merged without discussion.

## Translations

32 languages: English and 31 translations (`res/values-*`; `values-en-rGB` only sets British formats), most inherited from upstream Currencies via Weblate. The fork runs no translation workflow yet: to add or correct a translation, open an issue first.

Every translatable string exists in all 31, and lint fails on a missing one (`MissingTranslation`), so a new string arrives with its translations; bulk additions were machine translated, and native corrections are welcome. A string with nothing to translate (`%1$s → %2$s`) is `translatable="false"`.

## Setup

Fork, clone and open in Android Studio. JDK 21 or newer (CI uses 25). `./gradlew assembleFdroidDebug` should build cleanly.

Branch from **remote master**, not your local branch, so nothing stale comes along; say so in the PR if it depends on another unmerged branch:

```sh
git fetch origin master
git switch -c <new-branch> origin/master
```

Any non-master push builds a debug APK artifact for review.

## Code style

- Kotlin only. Follow the existing patterns ([architecture](architecture/README.md)): a new screen is a `Screen` key and a route, not an Activity. Konsist fails the unit tests on a broken layer boundary.
- `./gradlew detekt` and `./gradlew spotlessCheck` (`spotlessApply` fixes formatting). detekt has no baseline; any finding fails CI.
- No `java.lang.*` qualifiers; never swallow an exception (use the caught variable).

## No suppressions, no cosmetic workarounds

Warnings and findings get fixed, not hidden.

- **No suppressions of any kind:** `@Suppress`, `@file:Suppress`, `@SuppressLint`, `@SuppressWarnings`, `tools:ignore`, `//noinspection`, `ktlint-disable`, `nosemgrep`, a Dependency-Check `<suppress>`, a CodeQL exclusion, a Gradle verification trust entry, detekt or lint baseline entries, a rule turned off in `detekt.yml` / `lint.xml` / `.editorconfig`, `-dontwarn`, warning-silencing compiler flags. (`@OptIn` is an explicit opt-in, not a suppression.)
- **No workaround that only makes a warning go away:** no wrapper, reflection or cast around a deprecated call, no rename or restructure just so an analyzer stops matching, no file excluded from a check. If the problem is still there, so is the warning.
- **Fix the cause:** migrate off the deprecated API, split the function, make the parameters a type, remove the dead code.
- **No real fix yet?** Leave the warning visible and say so in the PR.

The only exceptions are in [exceptions.md](exceptions.md), each with the reason no fix exists. Adding one is the maintainer's decision; `NoSuppressionsTest` fails the unit tests on any suppression not recorded there.

## Pull request checklist

- [ ] `./gradlew check assembleDebug` passes (includes the Konsist tests)
- [ ] New behaviour has tests; a bug fix has a test that fails without it (Robolectric for Android and Compose, plain JUnit otherwise)
- [ ] `detekt` and `spotlessCheck` are clean, and nothing is suppressed beyond [exceptions.md](exceptions.md)
- [ ] Changed a Compose screen? Look over its screenshots (the Screenshots workflow uploads them per push; locally `./gradlew :app:recordRoborazziFdroidDebug --tests "com.eliormachlev.currencix.screenshots.*"`)
- [ ] Added a dependency? Check its licence works for F-Droid, and record its checksums ([build.md](build.md#dependency-verification))
- [ ] Changed the website? `python3 .github/scripts/site-assets.py stamp docs` ([website.md](website.md))

## Commits and PR titles

PRs are squash-merged and the title becomes the commit, `type(scope): description` (`feat(providers): add ECB historical fallback`, `fix(calculator): handle division by zero`). Release notes are built from those titles ([release.md](release.md#releasing-is-pushing-a-tag)), so write them for users: "show the rate's change since yesterday", not "add DeltaRow composable".

## Reporting issues

Open an issue with the Android version, app version (Settings → About this app), provider, steps to reproduce, and expected vs. actual behaviour.
