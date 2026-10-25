# UI

## One Activity, Navigation 3

`MainActivity` is the one main Activity (`ConvertTextActivity` and `WidgetConfigureActivity` stand alone). Every screen is a destination on one Compose back stack: no Fragments, no XML layouts, no ActionBar; the window is edge to edge. A new screen is a `Screen` key plus a route in `AppNavHost`, never a new Activity.

| Piece | Job |
|---|---|
| `Screen` | Sealed destinations. Arguments live on the key (`Timeline(from, to)`), so a screen can't open without them; each encodes to one line (`timeline:EUR:USD`) for saved state |
| `AppNavigator` | The back stack: `navigate` (pops back to a screen already there instead of stacking a copy), `replaceTop`, `pop`. The converter is always the bottom entry. Saved with the Activity, so rotation, `recreate()` and process death return to the same screen |
| `AppNavHost` | `NavDisplay` in a `SharedTransitionLayout`; each entry gets its own saved state and **its own `ViewModelStore`**, so a screen's ViewModel lives exactly as long as its entry |
| Routes | `ConverterRoute`, `TimelineRoute`, `CartRoute`, `SettingsRoute` (Settings and each `SettingsPage`), `FeesRoute`, `BackupRoute`: each wires its ViewModel, top bar and sheets |

The Activity keeps the splash, the XML theme, foldable posture, hardware-keyboard input and the Activity-scoped `MainViewModel` / `PreferenceViewModel`. `ConverterStatus` holds errors that arrive while another screen covers the converter until it's back. The cart's import / export pickers register on the Activity's `ActivityResultRegistry` with fixed keys, so a result outliving a recreation still arrives. Tests: `AppNavigatorTest`, `AppNavHostTest`, `MainActivitySmokeTest`, `TwoPaneSmokeTest`.

## Two panes

From 840 dp the converter stays on the left (40 % of the width, 360–480 dp) and the timeline or cart opens beside it. Each entry carries a `PaneRole`; `TwoPaneSceneStrategy` builds a two-pane scene when a detail sits on a list entry, keyed by the detail so switching details animates only that pane.

- In two panes the converter's shortcuts `replaceTop` the detail, so one back returns to the converter alone; the timeline beside it follows the converter's pair.
- The converter picks its row or column layout from the space it's given (`BoxWithConstraints`), not the orientation.
- In a column, the display is as tall as its content and the keypad takes the rest, shrinking its rows from 56 to 48 dp (`KeypadHeights`) before the display has to scroll.
- Currency pills don't fly between panes that are both on screen; `ConverterStatus` counts visible converters, since a layout change briefly composes two.

## Messages and prompts

- **One snackbar.** `AppSnackbar` is drawn once above every screen, reached through `LocalAppSnackbar` (a toast only outside the app shell). A new message replaces the current one.
- **Undo, not "are you sure".** Destructive actions act and offer Undo (a cart row returns to its index, Clear and Import restore the cart). Only deleting a *saved* cart confirms, as Undo can't reach it once the sheet closes. Copying shows no message on Android 13+, which confirms copies itself.
- **Every prompt is a sheet** on `LedgerPromptSheet` (content over Cancel / confirm), so all open, swipe away and go back alike: pickers, confirmations (`LedgerConfirmSheet`), explanations and typed input. The sheet makes room for the keyboard.
- **Text fields are state-based** (`TextFieldState`), so recomposition never moves the cursor; rules are `InputTransformation`s. Names stop at 50 characters (`NameInputTransformation`). Passwords use `OutlinedSecureTextField` (no copy or cut), aren't saved across configuration changes, and reach the backup code as a `CharArray` that's then cleared. A field without a visible label gives TalkBack its name as the hint (`fieldName`); an unusable entry says why under the field (`fieldMessage` / `invalidUnless`), not just with a red outline.
- **A switch row is one control** for TalkBack: the row carries the role and state (`switchState`), the inner `Switch` has no click of its own.

**Settings** is a list of categories, each its own screen (`SettingsTopic`, one `Screen.SettingsPage` each, saved as `settings:<id>`): Converter, Exchange rates, Look & Feel, Exchange rate chart, Receipt languages, Backup & Restore, Security & privacy, About. `SettingsPages.kt` lists each page's sections; their picker sheets are shared (`PreferenceDialogsHost`).

## Look

- **Colors.** The brand palette is paper and ink with a bill-green accent; **Wallpaper colors** (on by default) builds a tonal-spot palette from the wallpaper's main color (`WallpaperColors.kt`, `material-color-utilities`), not the system's dynamic schemes, which on some phones (MIUI / HyperOS) follow the theme rather than the wallpaper; those are only the fallback. The choice travels in `LocalDynamicColor` / `LocalWallpaperSeed`, so sheets' nested themes match. Pure black stays black.
- **Contrast.** Every brand role pair meets WCAG AA (`theme/Color.kt`): 4.5:1 for text, 3:1 for outlines and controls. Where green and brass are read as text on paper, darkened variants are used (`GreenOnPaper`, `BrassOnPaper`); the fee stamp takes its ink from the surface (`stampInk`).
- **One look.** Everything tappable or read off a block (pills, period and tip buttons, chips, statistics, tiles) sits on `ColorScheme.tile`; the typed amount's tile is brass. Type is Inter (Space Grotesk is the logo's), with `LabelFamily` and `AmountFamily` (tabular figures, so digits don't shift) in `theme/Type.kt`. Spacing comes from `theme/Spacing.kt`; files name their own spacing constants from it.
- **Top bars.** The converter's is small: the drawer button (hamburger ↔ arrow, driven by the drawer's offset), the wordmark, four shortcuts; the drawer slides in under it and back closes it. Other screens use `ScreenScaffold`, a medium bar collapsing to small on scroll (always small in short windows); the timeline keeps the small bar to give the chart height.
- **Icons** are Material Symbols Rounded (400, outlined, 24 dp) as vector drawables, generated by `art/material-symbols/generate.py` (add a row and rerun; no `material-icons-extended`). Feature icons follow the wallpaper primary when Wallpaper colors is on, moved toward the text color just enough for 3:1 (`readableOn`); controls always use the plain content color.
- **Choices of one** from a few options are connected toggle buttons (`ConnectedToggleGroup`); labels shrink to fit rather than being cut off at large font sizes.
- **Flags** render through `Currency.flagPainter()`, parsed once and cached app-wide. Never wrap them in `AndroidView` or rasterise them: the picker's 173 rows used to, at a cost in scroll smoothness.
- **Refresh rate.** On faster-than-60 Hz screens the app asks for its fastest mode (`preferFastestRefreshRate`), as some phones hold apps at 60 Hz otherwise; **Smooth motion** (on by default) hands it back to the system.
- **Taps.** Haptics come from `util/HapticUtils.kt` (skipped in battery saver); the click sound is Compose's own, turned off with **Click sounds** (`SoundEffectOnInteraction`). Both follow the settings live, sheets included.
- `UiTestTags` are stable tags for the `:baselineprofile` journeys, exposed as resource ids; keep them in sync with `baselineprofile/.../UiTags.kt`.

## Brand mark

One mark: two arrows swapping around a ×, green over brass. It's the launcher icon, the themed icon, the splash and the × of the top-bar wordmark. `art/brand/generate.py` draws it into `res/values/brand_mark.xml` (plus the SVGs, store icon and README GIFs), and every drawable reads the paths from there. It stays inside the adaptive icon's safe circle. Debug builds draw the × in red everywhere. The splash shows the version, drawn at build time by `GenerateSplashVersion` as a PNG (as a vector its glyphs would be far too long a path). The splash holds until the wordmark is drawn, then the mark fades while the wordmark's mark turns into place, once per cold start; the top-bar mark turns by half turns on launch, while rates refresh and when tapped (which refreshes), always resting green on top.

## Motion

All timing comes from `theme/Motion.kt`, so "make it snappier" is one file: short 120 ms (fades), medium 180 ms (entrances), long 600 ms (a half turn of the mark, a chart drawing in), a 20 ms stagger. Two springs, both stiffness 2500: `snappy` for taps, `settle` for panels. **Anything re-triggerable mid-flight uses a spring**, since an interrupted spring keeps its velocity while a restarted tween kinks. The drawer uses Material's own motion, as `DrawerState.open()` takes no spec.

- **Typing is never animated.** Result digits crossfade and pulse only when they change from outside the keypad (a refresh, a pick, a fee toggle); `DigitsChangeOrigin` tells which.
- **Animated values are read in the draw phase** (`graphicsLayer { }`, `Canvas`), so they redraw without recomposing.
- **Nothing animates forever.** The caret is a 500 ms on/off toggle, not a fade that would request a frame every vsync.
- Marked moments: swapping trades the two pills' places (`rememberPillTrade`); a rate that moves on refresh flashes green or red (`flashOnChange`); the cart total counts to its new value (`countTo`); a new rate alert's bell swings once.

Screen transitions (`ScreenMotion.kt`, 200 ms): open slides the new screen in 1/10 of the width and fades it in while the old one sinks to 97 %; back reverses it. `ScreenFrame` keeps overlapping screens opaque. Everything riding a transition finishes within it, so the shared currency pills (`sharedCurrencyPill`) move on its curve; two pills match only with the same currency on the same side.

## Right-to-left

Hebrew, Arabic and Farsi split the app in two:

- **Numbers stay left to right.** `AppTheme` locks LTR (and the manifest says `supportsRtl="false"`), so the converter, keypad, amounts, chart and the from → to pair never mirror; neither does the wordmark.
- **Currency symbols go where each language puts them** ("3.09 ₪" in Hebrew, "49,99 €" in German): every amount with its currency goes through `Context.money` / `withCurrencySymbol`, or asks `hasAppendedCurrencySymbol` when its parts are styled.
- **Chrome and prose follow the language.** Top bars, the drawer, settings, sheets, cart rows and the timeline's controls opt back in with `ReadingDirection` (or `ProseTheme`); anything inside whose left-to-right order carries meaning goes back in an `Ltr` island.
- **Popups set it inside their window,** since a window always resolves LTR under `supportsRtl="false"`: `LedgerBottomSheet` and the tour's `Spotlight` apply `ReadingDirection` in their content.
- **Mixed-script labels** ("היום · InforEuro") go through `String.inReadingOrder`, an RTL isolate.

`ReadingDirectionTest`, `PopupReadingDirectionTest` and `ConverterRtlTest` check it; `assertOnReadingStartSide` lets one test body serve both directions. The screenshot matrix runs each language's cells with the whole activity in that language and night mode, so sheets are fully localized and themed.

## Predictive back

Opted in (`enableOnBackInvokedCallback`), so the system's back-to-home animation plays. Inside the app nothing previews a back under the finger; letting go plays the ordinary close:

- **Screens:** `NavigationBackHandler` takes the gesture, and `NavDisplay` gets a separate event state that never sees the swipe, so it plays `popTransition` on release.
- **Drawer:** a plain `BackHandler` (`ModalDrawerSheet(drawerState)` shrinks under the finger).
- **Sheets:** `LedgerBottomSheet` turns off its own back handling (which also shrinks) and closes from a `BackHandler` in its content.

A screen intercepts back only while it must (the cart, with unsaved edits), so an unchanged cart keeps the predictive animation. `MainActivitySmokeTest` holds a swipe halfway on all three and checks nothing moved.
