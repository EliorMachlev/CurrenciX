# Accessibility Statement

**Effective:** 2026-08-13
**Last reviewed:** 2026-10-25
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

The whole app is Jetpack Compose (Material 3), so accessibility comes from Compose semantics rather than View attributes.

- **Screen readers**: Every interactive control has a text label reachable by TalkBack; icon-only buttons (the top bars' shortcuts, swap, search, clear, delete, close) carry a translated `contentDescription`, and purely decorative icons carry none.
- **Switches**: A row with a switch is one TalkBack stop that reads its title, "switch" and on or off; a greyed-out row says it's disabled.
- **Named fields**: Every text field has a name TalkBack reads, including the ones without a visible label (the fee editor's name and percent, the API key, the language search, a cart item's name), and the fee editor's From / To buttons say which currency they hold.
- **Error messages**: An entry that can't be used (a rate alert, a tip, a budget) says why in words under the field ("Enter a number greater than 0"), which TalkBack reads with it, rather than only turning the field red.
- **One stop per row**: Rows that hold several pieces of text read as one TalkBack node: currency picker rows and chips, and Credits entries merge their parts (`mergeDescendants`), and rows whose parts would read badly in pieces say one sentence instead (`clearAndSetSemantics`), as the cart's prices do: "4.50 US dollar, quantity 3, about 16.42 Israeli new shekel".
- **Actions without gestures**: What a long-press or a drag does on a cart row (duplicate, move to top, delete, who an item is for) is also offered as TalkBack actions.
- **Section headings**: Section titles in settings lists, sheets and the Credits list are marked as headings, so TalkBack's heading navigation jumps between them.
- **Chart summary**: The exchange-rate chart is read as a sentence rather than a line: the change over the period with its first and last rates and dates, and the high and low (`timelineSummary`). The statistics tiles beside it (max, min, average, median, change) are ordinary text.
- **Colour and contrast**: Light, dark and pure-black themes are provided. Every role pair of their palette is checked against the WCAG AA minima (4.5:1 for normal text, 3:1 for large text and non-text UI) and passes: in the light theme, the brand green and brass are darkened where they're read as text or as a control, and the muted text, outlines, error reds, the fee stamp and the sheets' drag handle were adjusted to reach them. A cart row already in the basket is crossed out and only slightly faded, so it still reads at 4.5:1. With **Wallpaper colors** on, coloured feature icons are moved toward the text colour just far enough to keep 3:1 against the background (`readableOn`).
- **Non-colour cues**: Rate changes carry an explicit sign and a trend arrow, so direction never relies on red / green alone.
- **Touch targets**: Interactive elements meet Material's minimum interactive size of **48 dp × 48 dp**, matching WCAG 2.1's target-size guidance for mobile.
- **Font scaling**: Text uses `sp` and follows the system font scale. The screenshot tests render the densest screens at 200 % font size, and labels that would be cut off shrink to fit instead (the chart's period buttons fit whole at 200 %).
- **Keyboard / switch access**: Focus order follows the visual reading order; custom controls (toggle groups, the keypad, chips) expose their role and state. The converter also takes a hardware keyboard's digits and operators.
- **Haptics and sound**: Vibration and click sounds each have their own switch (Settings → Look & Feel).
- **Predictive back gesture**: The app opts in to the predictive-back API so back navigation behaves the same throughout.

### Website (where operated)

- Semantic HTML with one `<h1>` per page and an ordered heading hierarchy; tables have header cells and scopes.
- `lang` attribute set on `<html>`.
- A skip-to-content link on every page, and the current page marked in the navigation (`aria-current`).
- Light and dark themes following the system, with the app's palette: text at 4.5:1 or more, controls and outlines at 3:1.
- Decorative icons and the brand mark are hidden from screen readers; every picture of the app has a text alternative describing what it shows, and the working examples (the receipt, the keypad, the rows that open) are real form controls and buttons with names and states (`aria-pressed`, `aria-expanded`), with the receipt's totals read out as they change.
- Keyboard navigation across all links and buttons, with a visible focus ring.
- Layouts reflow down to a 320 px wide screen without sideways scrolling (wide tables scroll inside their own box), and the brand mark's one animation is off when the system asks for reduced motion.

## Known limitations

The maintainer is a single individual and comprehensive third-party accessibility audits have not yet been commissioned. The following are known or possible limitations. If you encounter one, please report it using the contact channel below.

- **Third-party audit**: A comprehensive third-party accessibility audit has not yet been commissioned. Assessment is currently maintainer-run using TalkBack, Android Accessibility Scanner, and CI lint rules.
- **Chart data exposure**: The Vico line chart's per-day data points are not individually reachable by accessibility services. The spoken summary and the statistics tiles cover the visible period; a per-day table or sonified view is not provided.
- **Changing values aren't announced**: When a rate refresh changes the converted amount, TalkBack doesn't announce it by itself (no live region); the new value is read when focus reaches it.
- **Localised content descriptions**: The app is in 32 languages (English plus 31 translations), and Android Lint fails the build on a string missing from any of them, so content descriptions are translated too; the quality of the translations inherited from the upstream `sal0max/currencies` project varies.
- **Wallpaper colour palettes**: **Wallpaper colors** (on by default) builds the palette from the user's wallpaper at runtime, so its contrast can't be verified for every wallpaper ahead of time; the sample palettes checked pass, and Material's tonal palettes keep text and surfaces several tones apart. Turning it off in Settings → Look & Feel gives the fixed light / dark / pure-black themes, whose every role pair is checked.
- **Custom on-screen keypad**: Amounts are typed on the app's own keypad; there is no option to use the system keyboard instead (a hardware keyboard works). Its rows shrink from 56 dp to 48 dp on short screens, so keys stay at the 48 dp target.
- **RTL layouts**: In Hebrew, Arabic and Farsi, Settings, sheets and dialogs are laid out right to left; numbers, the converter and the chart stay left to right so digits and math read the same in every language. Labels mixing a right-to-left language with Latin currency codes or provider names keep the language's reading order (see architecture.md, *Right-to-left languages*).

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
4. **Checks on the screenshot tests' renders**, run by the maintainer: every screen in English and Hebrew, light, dark and pure black, and at 200 % font size, checked for controls TalkBack can't name, text and icon contrast measured on the rendered pixels, and text cut off.
5. **Community feedback** via the GitHub issue tracker.

## Review cadence

This statement is reviewed at least annually and after any significant UI change. The **Last reviewed** date at the top of this document reflects the most recent review.

## Contact

`26472689+EliorMachlev@users.noreply.github.com`
