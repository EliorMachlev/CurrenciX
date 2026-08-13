# Security Policy

## Supported Versions

Only the latest release is actively supported with security fixes.

| Version | Supported |
|---------|-----------|
| Latest  | Yes       |
| Older   | No        |

### Support-period declaration (EU CRA readiness)

For the purposes of the EU Cyber Resilience Act (Regulation (EU) 2024/2847) — which enters full application on 11 December 2027 — the declared **support period** for security updates on the latest release is **at least 24 months** from that release's publication date. Security-relevant fixes may be back-ported to the immediately preceding minor version on a best-effort basis; users are expected to upgrade to the latest release as their supported path.

## Reporting a Vulnerability

Please **do not** open a public GitHub Issue for security vulnerabilities.

Report vulnerabilities privately via [GitHub's private vulnerability reporting](https://github.com/EliorMachlev/CurrenciX/security/advisories/new).

Include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Android version and app version (`Settings → About`)

You will receive a response within 7 days. If the vulnerability is confirmed, a fix will be released as soon as possible, and you will be credited in the release notes unless you prefer otherwise.

### Coordinated Vulnerability Disclosure

CurrenciX follows a **coordinated disclosure** model:

1. Report privately via the channel above.
2. Acknowledgement target: **7 days**.
3. Triage and severity assessment: **14 days**.
4. Fix + coordinated public disclosure: as soon as a fix is available. Reporter is credited unless anonymity is requested.
5. If a reported issue is not a vulnerability, the reporter is told why within the triage window.

Public disclosure before a fix is available is discouraged. Reporters who wish to disclose on a fixed schedule should say so in the initial report; a 90-day default disclosure window is honoured.

### Actively exploited vulnerability reporting (EU CRA, from 11 September 2026)

Once the CRA's vulnerability-reporting obligations are in force, any actively exploited vulnerability affecting a CurrenciX build distributed into the EU market in the course of a commercial activity will be reported to ENISA:

- **Early warning**: within **24 hours** of the maintainer becoming aware.
- **Vulnerability notification**: within **72 hours**.
- **Final report**: within **14 days** of a fix or mitigation being available.

Users affected by the vulnerability will be notified via the GitHub release notes and, where feasible, an in-app changelog entry.

## Software Bill of Materials (SBOM)

An SBOM is generated as part of the CI pipeline (see [`docs/markDown/ci-cd.md`](docs/markDown/ci-cd.md)) and attached to each release. It enumerates all direct and transitive runtime dependencies with version and licence metadata to satisfy CRA Annex I Part II §1 and downstream supply-chain audit requests.

## Scope

This app:
- Requests only the `INTERNET` permission
- Stores no personal data
- Contains no authentication or payment flows
- Communicates only with the exchange rate provider selected by the user

Issues related to third-party exchange rate APIs should be reported directly to those providers.

## Related documents

- [`docs/markDown/security.md`](docs/markDown/security.md) — full security posture (supply chain, secrets, network, code analysis).
- [`docs/markDown/compliance-posture.md`](docs/markDown/compliance-posture.md) — regulatory applicability posture (EU AI Act, DSA/DMA, financial regimes, EAR/EU Dual-Use, CRA readiness).
- [`docs/markDown/privacy-policy.md`](docs/markDown/privacy-policy.md) — privacy notice.
- [`docs/markDown/accessibility-statement.md`](docs/markDown/accessibility-statement.md) — accessibility statement.
- [`docs/markDown/terms-of-service.md`](docs/markDown/terms-of-service.md) — terms of service.
