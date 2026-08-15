# Privacy Policy

**Effective:** 2026-08-13
**Last reviewed:** 2026-08-13
**Controller:** Elior Machlev (individual maintainer of CurrenciX)
**Contact:** 26472689+EliorMachlev@users.noreply.github.com

CurrenciX is a currency-conversion app. It is designed to collect **no** personal data. This policy explains what that means in practice under the world's major privacy regimes.

## What the app does with your data

**On the device.** All configuration (fee settings, starred currencies, keyboard preferences, theme, API key if you supply one) is stored in Android SharedPreferences private to the app. Cached exchange rates are stored in the same private storage. Nothing in this storage leaves your device unless **you** initiate an export via **Settings → Backup & Restore**, and even then it goes to a file location **you** pick via the system file picker.

**Off the device.** The app makes network requests to whichever exchange-rate provider you have selected in **Settings → Exchange rate provider**. These requests carry your device's IP address, the standard HTTP headers a network client sends, and the currency codes you have starred. The provider — not CurrenciX — receives and processes those requests under **its own** privacy terms. See [`api-providers.md`](api-providers.md) for the list.

**No SDKs.** CurrenciX does not integrate any analytics SDK, crash-reporting SDK, advertising SDK, attribution SDK, or persistent-identifier SDK. There is no Google Analytics, no Firebase, no Crashlytics, no AdMob, no Facebook SDK.

**No account.** There is no login, no user account, no cloud sync, no server that CurrenciX operates.

**Permissions.** The app requests only `android.permission.INTERNET`. Nothing else.

## Categories of "personal data" processed (by us)

None on our servers — we don't operate servers.

The only personal-data-adjacent element that ever leaves your device by our doing is your IP address, and only to the extent that TCP requires the provider you selected to see it. We do not log it, receive it, or store it on any server we control.

## Legal bases (where applicable) and jurisdictional notes

This section documents CurrenciX's posture under specific privacy regimes. It is provided to satisfy the transparency obligations those regimes impose even when data collection is minimal.

### EU / EEA — GDPR (Regulation (EU) 2016/679)

- **Lawful basis** for the on-device processing you initiate: Art. 6(1)(b) — necessary to perform the service you requested (currency conversion). No processing takes place on the controller's infrastructure because the controller operates no infrastructure.
- **Data subject rights** (Arts. 12–22): access, rectification, erasure, restriction, portability, and objection are exercisable by (a) inspecting or clearing app storage on your device, and (b) contacting the address above for anything not covered by that.
- **International transfers**: none by the controller. Transfers to the provider you selected occur under **your** action and under **that provider's** terms.
- **Complaint channel**: your local Data Protection Authority. In the EU, a list is at edpb.europa.eu/about-edpb/board/members_en.
- **Retention**: on-device data is retained until you clear the app storage or uninstall the app. The controller retains nothing.
- **Automated decision-making**: none.

### UK — UK GDPR + Data Protection Act 2018

Materially identical posture to the EU section above. The UK supervisory authority is the ICO (ico.org.uk). CurrenciX does not meet the ICO fee-payer registration threshold because it processes no personal data on any infrastructure the controller operates.

### United States — state privacy laws (CCPA/CPRA, VCDPA, CPA, CTDPA, UCPA, TDPSA, OCPA, MTCDPA, and successors)

- **Categories of personal information collected** (using CCPA categories): none by CurrenciX. The exchange-rate provider you select receives your IP address (Category F — Internet or other electronic network activity information) as an inherent property of TCP.
- **Sources**: N/A.
- **Business/commercial purposes**: N/A.
- **Categories of third parties**: none disclosed to by CurrenciX.
- **"Sale" or "sharing"** as defined by CCPA §1798.140: CurrenciX does not sell or share personal information. There is no "Do Not Sell or Share My Personal Information" mechanism because there is nothing to opt out of.
- **Sensitive personal information**: none collected.
- **Consumer rights** (right to know / delete / correct / limit / opt-out of profiling): exercisable by (a) inspecting or clearing app storage on your device, and (b) contacting the address above. Non-discrimination for exercising rights is honored.
- **Global Privacy Control (GPC)**: the app operates no telemetry channel; GPC is not applicable to the app. The project website, if hosted with any storage on the client device, will honor GPC signals.

### Canada — PIPEDA (federal) and Quebec Law 25

CurrenciX does not collect, use, or disclose personal information in the course of commercial activities on any infrastructure the maintainer operates. Requests to exchange-rate providers occur under your direction. Contact channel above for any inquiries.

### Brazil — LGPD (Lei nº 13.709/2018)

No personal data (`dados pessoais`) is treated by the controller. The `titular` (data subject) may exercise rights under Art. 18 via the contact above.

### Japan — APPI

No personal information (`個人情報`) is retained by the operator (`個人情報取扱事業者`). Cross-border transfer notifications are not applicable because the controller performs no such transfers.

### South Africa — POPIA

No personal information is processed by the responsible party. Contact channel above.

### Singapore — PDPA

No personal data is collected, used, or disclosed by the organisation.

### Australia — Privacy Act 1988 + Australian Privacy Principles (APPs)

CurrenciX is not an APP entity for the purposes of processing personal information because no personal information is handled off-device.

### China — PIPL

CurrenciX does not target users in mainland China and does not process personal information (`个人信息`) of PRC residents on any infrastructure the handler operates. No separate handler within China is designated because none of PIPL Art. 3(2)(1)–(3) triggers apply.

### South Korea — PIPA

CurrenciX does not target users in the Republic of Korea and does not process personal information (`개인정보`) on any infrastructure the operator controls.

### Israel — Protection of Privacy Law 5741-1981 (as amended, incl. Amendment 13, in force August 2025)

- **Database**: CurrenciX does not maintain a `מאגר מידע` (database) on any infrastructure the controller operates. On-device SharedPreferences held on your own device are not a controller-operated database.
- **Registration**: because no controller-side database exists, PPA (`הרשות להגנת הפרטיות`) database registration under §8 does not apply.
- **DPO** (`ממונה על הגנת הפרטיות`): the Amendment 13 triggers (public body, large-scale processing, sensitive-data processing) do not apply. If circumstances change, a DPO will be designated and named here.
- **Data-subject rights**: right of access (§13), rectification (§14), and the expanded deletion right introduced by Amendment 13 are exercisable via the contact above.
- **Cross-border transfer** (Protection of Privacy Regulations (Transfer of Data Abroad), 5761-2001): none performed by the controller.
- **Data-security level** (Protection of Privacy Regulations (Data Security), 5777-2017): not applicable — no controller-side database. On-device storage is protected by Android's app-sandbox model.
- **Complaint channel**: PPA at gov.il/he/departments/the_privacy_protection_authority.

### Children — COPPA (US, under 13) and equivalents

CurrenciX is a general-audience currency converter. It is **not directed to children under 13**, is not marketed to children, and does not knowingly collect personal information from children under 13. No age-gating is present because the app collects no personal information from any user. The California AADC and UK AADC (ICO Children's Code) obligations apply only where a service is likely to be accessed by children; the general-audience posture applies here as well.

### EU ePrivacy Directive 2002/58/EC ("cookie law")

CurrenciX stores nothing on your device beyond what is **strictly necessary** to deliver the currency-conversion service you requested (your settings, your starred currencies, cached rates for offline use). All such storage falls within the "strictly necessary" exemption. Because no non-essential storage is used, no cookie/consent banner is presented in-app.

## Website

If a project website exists at `currencix.machlev.org` or a comparable domain, its privacy behavior is documented in its own footer. The same "no analytics, no tracking, no non-essential storage" posture is intended to apply there. Any deviation will be disclosed on the site itself.

## Changes to this policy

Material changes will be reflected by updating the **Effective** and **Last reviewed** dates at the top of this document and noting the change in the project changelog. Because CurrenciX has no user accounts and no notification channel, checking this file is the way to see the current policy.

## Contact

For any question about this policy or to exercise any right listed above, email `26472689+EliorMachlev@users.noreply.github.com`. Response target: 30 days.
