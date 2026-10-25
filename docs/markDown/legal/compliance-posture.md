# Compliance Posture

**Last reviewed:** 2026-10-25
**Maintainer:** Elior Machlev
**Contact:** [GitHub issues](https://github.com/EliorMachlev/CurrenciX/issues)

This document records CurrenciX's applicability posture under a set of regulatory frameworks that are commonly asked about. Most do **not** apply to the project; the EU AI Act is treated as applying, to be on the safe side, and its one obligation is met. Each section includes a review trigger — a change in the product that would require the analysis to be redone.

Privacy-law compliance is a separate document: [`privacy-policy.md`](privacy-policy.md). Accessibility is documented at [`accessibility-statement.md`](accessibility-statement.md). Security and supply-chain posture is at [`engineering/security.md`](../engineering/security.md) and [`SECURITY.md`](../../../SECURITY.md).

## EU AI Act — Regulation (EU) 2024/1689

**Posture:** **Treated as in scope, by the maintainer's decision, though the open-source exclusion would likely apply; minimal risk; the only obligation, AI literacy, is met, with further measures taken voluntarily.** All conversion is deterministic arithmetic against a rate table, and there is no LLM API call, no automated decision-making system, no biometric identification or categorisation, no emotion recognition and no predictive analytics. There is one machine-learning model: **receipt scanning** reads photos with Tesseract, whose `tessdata_fast` language data are LSTM neural-network text-recognition models.

**Classification (decided 2026-10-25, the stricter reading).**

- **AI system (Art. 3(1)): yes, accepted.** The OCR infers text from an image with a trained neural network, so it is treated as an AI system and CurrenciX as its provider.
- **Open-source exclusion (Art. 2(12)): likely available, not relied on.** The Regulation does not apply to AI systems released under free and open-source licences unless they are placed on the market or put into service as high-risk systems or as systems under Art. 5 or Art. 50. CurrenciX is released under GPL-3.0 and Tesseract and its language data under Apache-2.0, and the OCR is none of those three, so the exclusion would most likely take the app out of the Regulation altogether. To be on the safe side, the project does not depend on it: the classification below, and the measures after it, hold whether or not the exclusion applies.
- **Prohibited practices (Art. 5): none.** No manipulation, social scoring, biometric identification or categorisation, emotion recognition or predictive policing.
- **High-risk (Art. 6, Annexes I and III): no.** Reading the text of a receipt is in none of the listed areas and is not a safety component of a regulated product.
- **Transparency obligations (Art. 50): none triggered.** It interacts with no one as a chatbot, generates or manipulates no image, audio, video or text presented as authentic, and recognises no emotions; it reads the text already on the user's own photo.
- **General-purpose AI models (Chapter V): no.** Tesseract's per-language recognition models are task-specific, not general-purpose models.
- **Result: minimal risk.** The one obligation that applies to every provider regardless of risk is AI literacy (Art. 4).

**What the project does (each in place today):**

- **AI literacy (Art. 4):** the maintainer, the only person who builds and releases the app, knows what the model does, where it can fail (blurred, curved or low-contrast photos, unusual fonts, languages without downloaded data) and how its output is used; this section and [`architecture/features.md`](../engineering/architecture/features.md#receipt-scanning) (receipt scanning) record it for any contributor.
- **Transparency to users (voluntary, beyond Art. 50):** the review screen says the receipt was read on the phone and asks the user to check the names and prices; the setup and language sheets say what is downloaded, its size, and that reading then works offline; nothing is downloaded until the user agrees.
- **Human oversight (voluntary, beyond Art. 14):** nothing recognised is used until the user has reviewed it: the review sheet lists every item found, and only the ones left ticked are added to the cart, as ordinary rows the user can then edit or delete, so the model's output never acts on its own.
- **Data minimisation:** the model runs on the phone only, on photos the user picks; the photo is deleted once read and never leaves the device (see [`privacy-policy.md`](privacy-policy.md)). The app never has camera access.
- **Model provenance and integrity:** the language data come from Tesseract's `tessdata_fast` at a fixed tag (4.1.0) and are installed only when their SHA-256 matches the recorded value; the libraries are listed in the release SBOM.

The app lock's biometric prompt is Android's own (`BiometricPrompt`): the app receives only whether it succeeded, so it is not a biometric system of the app's.

**Phased-application dates tracked for future reference** (as published; check for later amendments, such as the Commission's 2025 "Digital Omnibus" proposals on Art. 4 and on the timelines):

- Prohibited practices (Chapter II) and AI literacy (Art. 4): February 2025.
- GPAI obligations (Chapter V): August 2025.
- Full application (most obligations, including Art. 50): August 2026.
- High-risk systems listed in Annex I: August 2027.

**Review trigger:** any pull request that (a) adds or replaces an on-device or server-side ML model, or changes how the OCR's output is used (for example adding it without the review step), (b) introduces an LLM API call for text generation or classification, (c) adds automated decision-making that produces legal or similarly significant effects on a user, (d) adds biometric categorisation or emotion recognition, or (e) generates synthetic content, must revisit this classification **before** merging.

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
| **MiCA — Regulation (EU) 2023/1114** | Not applicable | Not a crypto-asset service provider (CASP). The currency list includes Bitcoin (`BTC`), shown display-only when a provider's public rate feed carries it, which by itself does not trigger MiCA CASP obligations; any custody, exchange, or trading functionality would. |
| **US money transmission** (state MTL + FinCEN MSB) | Not applicable | Not transmitting money, not exchanging currency, not issuing stored value. |
| **Israeli Supervision of Financial Services Law 5776-2016** | Not applicable | Not a "supervised financial services provider." |

**Review trigger for all rows above:** revisit if any of these are ever added — in-app currency exchange or remittance, crypto wallet/custody/exchange functionality, fiat/crypto payment intermediation, investment advice or brokerage, stored-value features, invoicing with executable rates, or paid conversion services.

## Cryptography export controls

**Posture:** **Covered by broad license exceptions.**

CurrenciX's cryptography is limited to:

- Standard TLS provided by the Android platform (for all provider HTTPS calls), restricted to forward-secret cipher suites.
- Argon2id v1.3 KDF (Bouncy Castle) and AES-256-GCM authenticated encryption (Tink) used **only** to encrypt user-initiated backup files at the user's option (see [`engineering/security.md`](../engineering/security.md#backups)).
- SHA-256 to check downloaded OCR language data, and the app lock's use of Android's `BiometricPrompt` (authentication only, no encryption).
- AES-256-GCM under an Android Keystore key (the platform's), to keep the user's API keys sealed on the device.
- Release signing (EC P-521 and ML-DSA-65 signatures) is done at build time, not by the app.

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

- **Coordinated Vulnerability Disclosure policy**: [`SECURITY.md`](../../../SECURITY.md).
- **Support-period declaration**: recorded in `SECURITY.md`.
- **Build provenance and dependency checksums**: each release file is attested (`actions/attest-build-provenance`), and the build only accepts dependencies whose SHA-256 is recorded (`gradle/verification-metadata.xml`).
- **Software Bill of Materials (SBOM)**: CycloneDX JSON of the release APKs' runtime dependencies, generated by CI and attached to each GitHub release (see [`SECURITY.md`](../../../SECURITY.md#software-bill-of-materials-sbom) and [`engineering/ci-cd.md`](../engineering/ci-cd.md)).
- **Secure-by-default posture**: HTTPS only (`network_security_config.xml` refuses cleartext), no cleartext credentials, no backup leakage (`android:allowBackup="false"`), least-privilege permissions (`INTERNET`, `ACCESS_NETWORK_STATE` and `USE_BIOMETRIC`, all normal, plus `POST_NOTIFICATIONS`, asked for only when the first rate alert is set). See [`engineering/security.md`](../engineering/security.md).
- **Vulnerability handling**: Dependabot + OSV-Scanner on the release SBOM (every PR and weekly) + weekly OWASP Dependency Check + weekly Gitleaks + weekly OpenSSF Scorecard.
- **Actively exploited vulnerability reporting**: 24-hour early-warning / 72-hour notification / 14-day final-report obligation to ENISA, in force since 11 September 2026 for products in scope; runbook lives in `SECURITY.md`.
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

- The `fdroid` flavor excludes any Play-Store-specific APIs. See [`engineering/build.md`](../engineering/build.md).
- Deterministic build inputs (pinned JDK, AGP, Kotlin) — see the same doc.
- Signing certificate fingerprint: the release key's SHA-256 is in [`engineering/release.md`](../engineering/release.md#post-quantum-signing-android-17) and in each GitHub release's notes. F-Droid signs its own builds with its own key.
- No trackers in the fdroid flavor (verifiable via Exodus Privacy or F-Droid's own scanner).

**Expected anti-features metadata** (for whoever submits to fdroiddata):

- `NonFreeNet` — applies if the user selects a rate provider whose upstream service is under a proprietary or restrictive licence. Configurable at runtime; document per-provider status in `product/providers.md`.
- No `Tracking`, no `Ads`, no `NonFreeAdd`, no `NonFreeDep`, no `NonFreeAssets` are expected to apply to the fdroid flavor.

**Review trigger:** any change that adds a proprietary dependency to the fdroid flavor, adds a tracker, or removes reproducibility must update both this doc and (if published) the fdroiddata metadata.

## Ongoing review

This document is reviewed at least annually and whenever a review trigger fires. The **Last reviewed** date at the top of this document reflects the most recent review.
