# Accessibility Statement

**Effective:** 2026-08-13
**Last reviewed:** 2026-08-15
**Accessibility coordinator:** Elior Machlev
**Contact:** 26472689+EliorMachlev@users.noreply.github.com

CurrenciX is committed to making its Android application and any associated project website usable by the widest possible audience, including users with disabilities. This statement describes the standards the project applies, what has been done, known limitations, and how to reach us if you encounter an accessibility barrier.

## Standards applied

CurrenciX targets **WCAG 2.1 Level AA** as the baseline for both the app and the site. The following jurisdiction-specific frameworks are covered by that target:

| Jurisdiction | Framework | Standard referenced |
|---|---|---|
| Israel | Equal Rights for Persons with Disabilities Law 5758-1998; Service Accessibility Regulations 5773-2013 (Reg. 35) | IS 5568 (aligned with WCAG 2.0 AA) — CurrenciX exceeds this by targeting WCAG 2.1 AA |
| European Union | European Accessibility Act (Directive (EU) 2019/882), in force 28 June 2025 | EN 301 549 V3.2.1, which references WCAG 2.1 AA for web and mobile |
| United Kingdom | Public Sector Bodies (Websites and Mobile Applications) Accessibility Regulations 2018 (referenced as best practice — CurrenciX is not a public sector body) | WCAG 2.1 AA |
| United States | Americans with Disabilities Act (ADA), Title III; Section 508 of the Rehabilitation Act (referenced as best practice — CurrenciX is not a federal agency) | Revised 508 Standards, which reference WCAG 2.0 AA — CurrenciX exceeds this by targeting WCAG 2.1 AA |

Where a jurisdiction requires WCAG 2.0 AA and CurrenciX ships WCAG 2.1 AA, the higher standard is applied.

## Scope

This statement covers:

- The **CurrenciX Android application** (package `com.eliormachlev.currencix`) in both its `fdroid` and `play` build flavors.
- Any **project website** operated by the maintainer for CurrenciX.

It does **not** cover:

- Third-party exchange-rate provider websites reachable from in-app "About" links.
- The Google Play Store or F-Droid listing pages, which are governed by the respective store's own accessibility posture.

## What has been done

### App (Android)

- **Screen readers**: Every interactive control has a text label reachable by TalkBack. Purely decorative icons are marked with `importantForAccessibility="no"` or `contentDescription="@null"`. Actionable icon-only buttons (search, clear search, delete cart item, swap currencies, fee-side toggle) have `contentDescription` set to a translated string.
- **Merged focus stops**: Multi-line list rows — currency picker rows, quick-conversion rows, cart items, and About/Credits entries — use Compose `semantics(mergeDescendants = true)` so TalkBack reads each row as a single node instead of forcing a swipe per sub-line.
- **Section headings**: The Credits list marks section titles with `semantics { heading() }` so TalkBack users can jump between "Project / Legal / Source / Libraries" with heading navigation.
- **Live regions**: Dynamic numeric readouts (converted amount, fee badge, true cost, original value) declare `accessibilityLiveRegion="polite"` so TalkBack announces value changes without interrupting the user's current focus.
- **State descriptions**: The fee-side toggle publishes its current state ("Converted" / "Original") via `ViewCompat.setStateDescription`, so its selection is announced independent of its visible label.
- **Chart summary**: The exchange-rate line chart is inherently visual and its data model is opaque to accessibility services. A wrapping `contentDescription` announces the chart and directs users to the accessible MIN / AVG / MAX / current-rate readout rendered immediately below.
- **Colour and contrast**: Light, dark, and pure-black themes are provided. Text and essential UI elements target the WCAG AA contrast minima (4.5:1 for normal text, 3:1 for large text and non-text UI). Semantic tokens `app_error` (`#B3261E` light / `#F2B8B5` dark) and `rate_diff_positive` (`#1B7343` light / `#85BB65` dark) are tuned for AA on both themes; opaque colour alone is used for state cues that were previously partially conveyed via reduced alpha.
- **Non-colour cues**: Rate-difference percentages carry an explicit `+` / `-` sign so the direction is conveyed without relying on the red/green colour distinction.
- **Touch targets**: Interactive elements target a minimum of **48 dp × 48 dp** per the Material accessibility guidance, matching the WCAG 2.1 target-size AA guidance for mobile. The fee-side toggle and the numeric-keypad icon buttons carry explicit `minWidth` / `minHeight` to guarantee this on small screens.
- **Font scaling**: The app respects the system font-scale setting (`fontScale`) and uses `sp` units for text. Layouts have been reviewed to avoid clipping at scales up to 200%.
- **Keyboard / switch access**: Focus order follows visual reading order. Custom compound controls expose an accessibility role and state.
- **Predictive back gesture**: The app opts in to the Android 13+ predictive-back API so screen-reader users get consistent back-navigation feedback.

### Website (where operated)

- Semantic HTML with correct heading hierarchy.
- `lang` attribute set on `<html>`.
- Skip-to-content link where applicable.
- Colour contrast checked against WCAG AA.
- Alt text on informative images; empty `alt=""` on decorative ones.
- Keyboard navigation across all interactive controls with visible focus indicators.
- Form controls (if any) with associated `<label>` elements.

## Known limitations

The maintainer is a single individual and comprehensive third-party accessibility audits have not yet been commissioned. The following are known or possible limitations. If you encounter one, please report it using the contact channel below.

- **Third-party audit**: A comprehensive third-party accessibility audit has not yet been commissioned. Assessment is currently maintainer-run using TalkBack, Android Accessibility Scanner, and CI lint rules.
- **Chart data exposure**: The Vico line chart's per-day data points are not individually reachable by accessibility services. A textual MIN / AVG / MAX / current-rate summary for the visible period is provided as an accessible alternative, and the chart itself announces a summary directing users to that readout. A per-day table or sonified view is not provided.
- **Localised content descriptions**: Some strings inherited from the upstream `sal0max/currencies` project may not yet have translated content descriptions in every one of the 20+ supported languages. Fallbacks display the English string.
- **Dynamic colour palettes**: On Android 12+ the system-generated dynamic colour scheme is honoured. Because that palette is derived at runtime from the user's wallpaper, its contrast ratios cannot be verified ahead of time; the fixed light / dark / pure-black themes remain the AA-verified baseline.
- **Custom on-screen keypad**: An in-app numeric keypad is used by default. On very small screens some keypad keys may fall below the 48 dp target-size guidance despite honouring `minWidth` / `minHeight` where present. Users who prefer the system keyboard (which may integrate better with certain accessibility services) can switch via **Settings → Keyboard**.
- **RTL layouts**: RTL is supported (Hebrew, Arabic) but edge cases in mixed LTR/RTL content — currency codes are Latin — are still being audited.

None of the above is a blocker to core currency-conversion functionality via TalkBack.

## Feedback and complaint mechanism

If you cannot use a feature of the app or the site because of an accessibility barrier, please contact the accessibility coordinator:

- **Email**: `26472689+EliorMachlev@users.noreply.github.com`
- **GitHub issue**: open an issue at github.com/EliorMachlev/CurrenciX/issues with the label `accessibility`.

Response target: **14 days** for an initial acknowledgement, **30 days** for a substantive response or fix plan.

If you are not satisfied with the response and you are located in a jurisdiction with an enforcement body, you may escalate:

- **Israel**: Commission for Equal Rights of Persons with Disabilities, Ministry of Justice (gov.il/he/departments/the_commission_for_equal_rights_of_persons_with_disabilities).
- **EU/EEA**: the enforcement body designated by your member state under the EAA (Art. 14).
- **United Kingdom**: the Equality and Human Rights Commission (equalityhumanrights.com).
- **United States**: U.S. Department of Justice, Civil Rights Division (ada.gov).

## Conformance assessment

Conformance is assessed by the maintainer using a combination of:

1. **Manual review** with TalkBack on a physical Android device across representative screens (home, chart, settings, backup, language picker, currency picker).
2. **Android Accessibility Scanner** for label / touch-target / contrast smoke checks.
3. **Static-analysis rules** during CI (lint accessibility rules enabled).
4. **Community feedback** via the GitHub issue tracker.

## Review cadence

This statement is reviewed at least annually and after any significant UI change. The **Last reviewed** date at the top of this document reflects the most recent review.

## Contact

`26472689+EliorMachlev@users.noreply.github.com`
