# Contributing

The full guide is [`docs/markDown/engineering/contributing.md`](docs/markDown/engineering/contributing.md): setup, branching, code style, the PR checklist, commits and translations. The short version:

## Before you start

- **Bug fixes** are welcome. **New features and refactors:** open an [issue](https://github.com/EliorMachlev/CurrenciX/issues) first.
- Kotlin only; branch from `origin/master`. Build with `./gradlew assembleFdroidDebug` ([build guide](docs/markDown/engineering/build.md)); the code is laid out in [architecture](docs/markDown/engineering/architecture/README.md).

## Before you open a PR

- Run `./gradlew spotlessCheck detekt`; CI runs the rest ([CI / CD](docs/markDown/engineering/ci-cd.md)).
- Warnings get fixed, not suppressed. The few agreed exceptions are in [exceptions.md](docs/markDown/engineering/exceptions.md).
- Title the PR `type(scope): short description` (`fix(calculator): handle division by zero`); release notes are built from these titles.

## Reporting a bug

Open a [GitHub issue](https://github.com/EliorMachlev/CurrenciX/issues) with your Android version, the app version (Settings → About this app), the exchange rate provider, and the steps to reproduce.

## Reporting a vulnerability

Don't open a public issue: follow [SECURITY.md](SECURITY.md).
