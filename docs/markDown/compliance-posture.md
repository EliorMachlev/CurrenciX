# Compliance Posture

**Last reviewed:** 2026-08-13
**Maintainer:** Elior Machlev
**Contact:** 26472689+EliorMachlev@users.noreply.github.com

This document records CurrenciX's applicability posture under a set of regulatory frameworks that are commonly asked about but that do **not** currently apply to the project. Each section includes a review trigger — a change in the product that would require the analysis to be redone.

Privacy-law compliance is a separate document: [`privacy-policy.md`](privacy-policy.md). Accessibility is documented at [`accessibility-statement.md`](accessibility-statement.md). Security and supply-chain posture is at [`security.md`](security.md) and [`SECURITY.md`](../../SECURITY.md).

## EU AI Act — Regulation (EU) 2024/1689

**Posture:** **Not applicable.** CurrenciX ships no AI system as defined by Art. 3(1) of the Act. There is no machine-learning model, no LLM API call, no automated decision-making system, no biometric identification, no predictive analytics. All conversion is deterministic arithmetic against a rate table.

**Phased-application dates tracked for future reference:**

- Prohibited practices (Chapter II): February 2025.
- GPAI obligations (Chapter V): August 2025.
- Full application (most obligations): August 2026.
- High-risk systems listed in Annex I: August 2027.

**Review trigger:** any pull request that introduces (a) an on-device or server-side ML model, (b) an LLM API call for text generation or classification, (c) automated decision-making that produces legal or similarly significant effects on a user, (d) biometric categorisation or emotion recognition, must revisit this posture and classify the system per the Act's risk categories **before** merging.

## EU Digital Services Act — Regulation (EU) 2022/2065

**Posture:** **Not applicable.** CurrenciX is not an "intermediary service" (Art. 3(g)) — it does not act as a mere conduit, cache, or host for third-party information provided by users. There is no user-generated content, no user-to-user messaging, no marketplace, no comment functionality, no ranking or recommendation of third-party content.

**Review trigger:** if any of the following are ever added — a comments section, a user-content posting feature, a marketplace or classifieds surface, a UGC review feature — the DSA classification (mere conduit / caching / hosting / online platform / VLOP thresholds) must be redone.

## EU Digital Markets Act — Regulation (EU) 2022/1925

**Posture:** **Not applicable.** CurrenciX is not a "gatekeeper" under Art. 3 — it does not meet the platform-service, revenue, or user-count thresholds, and does not provide any of the core platform services listed in Art. 2(2).

**Review trigger:** none plausible given the product scope, but revisit if the project is ever acquired by an entity that could aggregate its user base into a designated core platform service.

## Financial-services regimes

CurrenciX **displays reference exchange rates**. It does not: hold customer funds, effect payments, exchange currency, custody crypto-assets, process card data, provide investment advice, act as a broker, or intermediate any financial transaction.

| Regime | Applicability | Notes |
|---|---|---|
| **PCI DSS v4.0** | Not applicable | No card data touched. If in-app donations or purchases are ever added, use platform billing (Google Play Billing / Apple IAP) to keep card data out of scope; do not accept PANs. |
| **PSD2 — Directive (EU) 2015/2366** | Not applicable | Not a payment service provider (PSP), account information service (AIS), or payment initiation service (PIS). |
| **PSD3 / PSR (proposed)** | Not applicable | Same reasoning as PSD2. |
| **MiCA — Regulation (EU) 2023/1114** | Not applicable | Not a crypto-asset service provider (CASP). CurrenciX does not currently list crypto tickers; if a crypto rate ever appears it will be display-only via a public rate feed, which by itself does not trigger MiCA CASP obligations, but any custody, exchange, or trading functionality would. |
| **US money transmission** (state MTL + FinCEN MSB) | Not applicable | Not transmitting money, not exchanging currency, not issuing stored value. |
| **Israeli Supervision of Financial Services Law 5776-2016** | Not applicable | Not a "supervised financial services provider." |

**Review trigger for all rows above:** revisit if any of these are ever added — in-app currency exchange or remittance, crypto wallet/custody/exchange functionality, fiat/crypto payment intermediation, investment advice or brokerage, stored-value features, invoicing with executable rates, or paid conversion services.

## Cryptography export controls

**Posture:** **Covered by broad license exceptions.**

CurrenciX's cryptography is limited to:

- Standard TLS provided by the Android platform (for all provider HTTPS calls).
- Argon2id v1.3 KDF and AES-256-GCM authenticated encryption used **only** to encrypt user-initiated backup files at the user's option (see `SECURITY.md` — Optional password encryption).

No proprietary cryptographic algorithms, no cryptanalytic tools, no key-management server, no controlled crypto functionality per EAR Category 5 Part 2 beyond mass-market use.

| Regime | Basis for eligibility |
|---|---|
| **US EAR — Category 5 Part 2** | Publicly available source code is eligible for license exception **TSU** under 15 CFR §740.13(e) (or equivalent notification path under §742.15(b)). The `play` flavor, distributed as compiled binary, is also eligible for license exception **ENC** under 15 CFR §740.17 as a mass-market item with standard encryption. |
| **EU Dual-Use — Regulation (EU) 2021/821** | The **General Software Note** (Annex I) exempts software that is generally available to the public and not restricted end-use. CurrenciX source is publicly available on GitHub. |
| **Wassenaar Cat. 5 Part 2** | Publicly available / open-source carve-outs apply. |

**Notification:** if and when a public binary is distributed via the Play Store from an entity subject to US export jurisdiction, a self-classification report to BIS may be filed as a matter of good practice, even though it is not strictly required for open-source software.

**Review trigger:** if the project ever adds proprietary cryptography, cryptanalytic tooling, a hardware security module integration with restricted end use, or begins distributing binaries into a US-embargoed destination.

## EU Cyber Resilience Act — Regulation (EU) 2024/2847

**Posture:** **Preparing.**

The CRA enters full application on **11 December 2027**, with vulnerability-reporting obligations from **11 September 2026**. It applies to "products with digital elements" placed on the EU market in the course of a commercial activity. Purely non-commercial FOSS is out of scope.

**CurrenciX classification:**

- Source-only distribution and F-Droid distribution: likely **out of scope** as non-commercial FOSS (see CRA Recital 15 and Art. 2(4)).
- Any future Play Store distribution: likely **in scope** as commercial activity. Play distribution is not planned but the readiness work below is done regardless so that a decision to publish is not blocked by CRA.

**Readiness work already done or being done:**

- **Coordinated Vulnerability Disclosure policy**: [`SECURITY.md`](../../SECURITY.md).
- **Support-period declaration**: recorded in `SECURITY.md`.
- **Software Bill of Materials (SBOM)**: generated by CI (see `docs/markDown/ci-cd.md`).
- **Secure-by-default posture**: HTTPS only (`network_security_config.xml` refuses cleartext), no cleartext credentials, no backup leakage (`android:allowBackup="false"`), least-privilege permissions (INTERNET only). See [`security.md`](security.md).
- **Vulnerability handling**: Dependabot + weekly OWASP Dependency Check + weekly Gitleaks + weekly OpenSSF Scorecard.
- **Actively exploited vulnerability reporting**: 24-hour early-warning / 14-day final-report obligation to ENISA once the September 2026 date is reached; runbook lives in `SECURITY.md`.
- **CE marking & Declaration of Conformity**: applicable only if the project distributes commercially in the EU; deferred until such distribution is contemplated.

**Review trigger:** any decision to distribute the `play` flavor into the EU market must confirm the CRA classification (default vs. "important" per Annex III) and complete the conformity assessment and CE marking before shipping.

## Google Play Data Safety declaration

**Posture:** **No Play Store listing exists.** CurrenciX is source-distributed via GitHub, with an `fdroid` flavor prepared for F-Droid inclusion. There is no Play Console entry to fill out.

**Review trigger:** if the `play` flavor is ever published to the Google Play Store, the Data Safety form must be completed with the following declarations (which follow from the privacy policy and this compliance posture):

- **Data types collected**: none by the app.
- **Data types shared**: none by the app. The user-selected exchange-rate provider receives HTTP requests, which is inherent to fetching data over the internet and is not "sharing" by the app.
- **Security practices**: data encrypted in transit (Yes — HTTPS-only via `network_security_config`); users can request data deletion (Yes — via app-storage clear on device); committed to Play Families policy (N/A — general audience, not directed to children).
- **Data-deletion URL**: the Privacy Policy documents that on-device data is deletable by clearing app storage; a "data deletion request" URL would point to the GitHub-hosted policy.

## F-Droid inclusion metadata

**Posture:** **In this repo:** reproducible-build target, fdroid flavor prepared, no non-free dependencies used by the fdroid flavor. **In the upstream `fdroiddata` metadata:** anti-features declarations live there, not here, and are the responsibility of the F-Droid maintainer / submitter at inclusion time.

**In-repo readiness:**

- The `fdroid` flavor excludes any Play-Store-specific APIs. See [`build-and-flavors.md`](build-and-flavors.md).
- Deterministic build inputs (pinned JDK, AGP, Kotlin) — see the same doc.
- Signing certificate fingerprint: published in `SECURITY.md` alongside the release process (to be added when the first release is cut).
- No trackers in the fdroid flavor (verifiable via Exodus Privacy or F-Droid's own scanner).

**Expected anti-features metadata** (for whoever submits to fdroiddata):

- `NonFreeNet` — applies if the user selects a rate provider whose upstream service is under a proprietary or restrictive licence. Configurable at runtime; document per-provider status in `api-providers.md`.
- No `Tracking`, no `Ads`, no `NonFreeAdd`, no `NonFreeDep`, no `NonFreeAssets` are expected to apply to the fdroid flavor.

**Review trigger:** any change that adds a proprietary dependency to the fdroid flavor, adds a tracker, or removes reproducibility must update both this doc and (if published) the fdroiddata metadata.

## Ongoing review

This document is reviewed at least annually and whenever a review trigger fires. The **Last reviewed** date at the top of this document reflects the most recent review.
