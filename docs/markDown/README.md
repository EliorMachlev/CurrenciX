# CurrenciX documentation

The user-facing side is the [website](https://currencix.machlev.org/) and the [README](../../README.md); this folder is for contributors and anyone auditing the project.

## Product

- [Providers](product/providers.md): every rate source, the fallback, unreliable providers, API keys

## Engineering

- [Contributing](engineering/contributing.md): setup, rules, PR checklist, translations (short version: [CONTRIBUTING.md](../../CONTRIBUTING.md))
- [Architecture](engineering/architecture/README.md): layers and rules, then [data](engineering/architecture/data.md), [networking](engineering/architecture/networking.md), [UI](engineering/architecture/ui.md) and [features](engineering/architecture/features.md)
- [Build](engineering/build.md): toolchain, flavors, dependencies, tests and coverage, licence checks, fuzzing, dependency verification, baseline profiles
- [Release](engineering/release.md): tags, signing (post-quantum), provenance, version codes, F-Droid; releases are on [GitHub](https://github.com/EliorMachlev/CurrenciX/releases)
- [CI / CD](engineering/ci-cd.md): workflows, scanners, Dependabot, permissions
- [Branches and the pipeline](engineering/branching.md): Dev, Alpha, Prerelease, Release, and which checks run where
- [Security](engineering/security.md): supply chain, secrets, backups, network, signing
- [Exceptions](engineering/exceptions.md): the only suppressed findings, and why
- [Website](engineering/website.md): the GitHub Pages site

## Legal and policy

- [Privacy policy](legal/privacy-policy.md), [terms of service](legal/terms-of-service.md), [accessibility statement](legal/accessibility-statement.md), [compliance posture](legal/compliance-posture.md)
- [Security policy](../../SECURITY.md): reporting a vulnerability, supported versions, SBOM
- [NOTICE](../../NOTICE.md): the upstream project and what this fork changed
