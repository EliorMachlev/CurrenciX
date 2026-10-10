# Exceptions to "No Suppressions"

[contributing.md](contributing.md#no-suppressions-no-cosmetic-workarounds) forbids hiding a warning instead of fixing its cause. This file is the complete list of places where the project does it anyway, and why. If a suppression is not listed here, it is not allowed.

## What qualifies

An exception is for a finding that is wrong about code that is right:

- the code is correct as written, and
- no change would satisfy the tool short of removing something that works, and
- the maintainer has decided to exclude it rather than leave it visible.

A finding that is merely hard, slow or unpleasant to fix does not qualify. Fix it, or leave the warning visible and say so in the PR.

## Current exceptions

| # | Tool and rule | Excluded for | Marked in |
|---|---|---|---|
| 1 | Semgrep `java.android.security.exported_activity.exported_activity` | `MainActivity` | `app/src/main/AndroidManifest.xml` |
| 2 | Semgrep `java.android.security.exported_activity.exported_activity` | `ConvertTextActivity` | `app/src/main/AndroidManifest.xml` |

Both decided by the maintainer on 2026-10-06 (PR #181).

### 1. `MainActivity` is exported

- **What the rule says:** the application exports an activity, so any app on the device can start it.
- **Why it can't be fixed:** `MainActivity` is the launcher entry (`MAIN` / `LAUNCHER`). The home screen is another app, and Android starts an activity for another app only if it is exported. Without it the app has no icon to open.
- **Why the rule is wrong here:** it flags every exported activity without looking at what the activity is for. Any app with a launcher icon has this finding.
- **What still guards it:** everything the activity reads from its intent goes through `ConverterLaunch`, which validates each extra and drops what it can't read.

### 2. `ConvertTextActivity` is exported

- **What the rule says:** the same.
- **Why it can't be fixed:** this is "Convert currency" in the text-selection menu of other apps (`ACTION_PROCESS_TEXT`). The app showing the selection starts it, so it has to be exported. The only alternative is removing the feature.
- **What still guards it:** it reads one thing from the caller, the selected text, cut to a fixed maximum length and handed to `PriceParser`. Beyond that it only reads the cached rates and the user's settings, and it makes no network request.

### What the exclusion does and doesn't cover

Each exclusion is one `nosemgrep` comment naming the rule, on the one activity it is for. The rule itself keeps running: an exported activity added later is reported, and fails the Semgrep job, until it is either not exported or added here. The widget's configure activity shows the first route: it used to be exported, did not need to be, and no longer is.

## Adding an exception

1. Establish that there is no real fix, and what the next-best outcome would cost (a removed feature, a broken platform contract).
2. Get the maintainer's decision. An exception is theirs to grant, not the author's.
3. Exclude the narrowest thing possible: the one finding, by rule id, where it occurs. Never a rule switched off for the project, a file or directory left out of a scan, or a baseline.
4. Add a row and a section above: what the rule says, why it can't be fixed, what still guards the code.
5. Add the exact line to `EXCEPTIONS` in `NoSuppressionsTest`. That test fails on any suppression marker not listed there, on a listed one that is no longer in the code or has moved off the thing it was granted for, and on one this file doesn't explain.

## Removing an exception

When the tool is fixed upstream, or the code changes so the finding no longer applies, delete the marker, the row and section here, and the entry in `NoSuppressionsTest`.

## Not exceptions: warnings left visible

These have no real fix either, but nothing is excluded for them. They show up in every report.

- **Lint `Typos`, twice, in `values-tr/strings_currencies.xml`:** "Gine" in the Turkish names of the Guinean franc and the Papua New Guinean kina. It is the Turkish word for Guinea; lint's word list takes it for a misspelling of "yine".
- **LeakCanary, debug builds on some manufacturers' devices:** a leak inside the vendor's Android framework (`ResourcesImpl.mAppContext`) that pins a WorkManager service context. The app holds no reference it could release.
