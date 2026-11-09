## What and why

<!-- Title: `type(scope): description`, written for users (see contributing.md). Link the issue: Fixes #123. -->

## Checklist

- [ ] `./gradlew check assembleDebug` passes
- [ ] New behaviour has tests; a bug fix has a test that fails without it
- [ ] `detekt` and `spotlessCheck` are clean, and nothing is suppressed beyond `docs/markDown/engineering/exceptions.md`
- [ ] Changed a Compose screen? I looked over its screenshots
- [ ] Added a dependency? Its licence works for F-Droid, and its checksums are recorded
- [ ] Changed the website? Ran `python3 .github/scripts/site-assets.py stamp docs`
- [ ] Docs updated where the change needs it
