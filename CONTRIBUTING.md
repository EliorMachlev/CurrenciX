# Contributing

Full contributor guide lives at [`docs/markDown/contributing.md`](docs/markDown/contributing.md) — branching, code style, PR checklist, and commit conventions.

Quick summary:

- Bug fixes are welcome. New features and refactors: open an issue first to discuss.
- Kotlin only. Run `./gradlew spotlessCheck detekt` before opening a PR.
- Base branches on `origin/master`.
- Follow the commit convention: `type(scope): short description` (e.g. `fix(calculator): handle division by zero`).

Report bugs via [GitHub Issues](https://github.com/EliorMachlev/CurrenciX/issues) with your Android version, app version (`Settings → About`), selected exchange rate provider, and steps to reproduce.

Security reports: see [SECURITY.md](SECURITY.md) — do not open a public issue.
