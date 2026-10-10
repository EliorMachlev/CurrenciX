# Architecture

CurrenciX follows **MVVM** (Model-View-ViewModel) with a Repository layer, implemented in Kotlin with AndroidX Lifecycle components.

## Layer Overview

```
┌──────────────────────────────────────┐
│              View Layer              │
│  Activities · Custom Views · Dialogs │
└──────────────┬───────────────────────┘
               │ observes LiveData
┌──────────────▼───────────────────────┐
│           ViewModel Layer            │
│  MainViewModel · TimelineViewModel   │
│  PreferenceViewModel · CartViewModel │
└──────────────┬───────────────────────┘
               │ calls
┌──────────────▼───────────────────────┐
│          Repository Layer            │
│  ExchangeRatesRepository             │
│  ExchangeRatesService                │
└──────────────┬───────────────────────┘
               │ reads/writes
┌──────────────▼───────────────────────┐
│   Cache Layer (repository/cache/)    │
│  Memory (LRU) → Disk (JSON) → Network│
│  Fetcher · RetryPolicy · InFlightDedupe│
└──────────────┬───────────────────────┘
               │ on cache miss
┌──────────────▼───────────────────────┐
│      Data / Persistence Layer        │
│  ApiProvider (Retrofit or raw OkHttp)│
│  Database (DataStore Preferences)    │
└──────────────────────────────────────┘
```

## Source Layout

```
app/src/main/kotlin/com/eliormachlev/currencix/
├── CurrenciesApplication.kt   # Application subclass — prewarms provider DNS at startup
├── model/
│   ├── ApiProvider.kt          # Enum of 7 active providers + abstract Api interface
│   ├── Currency.kt             # 190+ ISO-4217 currencies with symbols & flags
│   ├── ExchangeRates.kt        # Snapshot of rates for a base currency
│   ├── Rate.kt                 # Single (currency, rate) pair
│   ├── Timeline.kt             # Historical rate series
│   ├── adapter/                # Moshi / XML adapters per provider
│   └── provider/               # HTTP implementations per provider (api/ holds the Retrofit interfaces)
├── repository/
│   ├── Database.kt             # Hands out the stores below; holds no logic of its own
│   ├── RateStore.kt … CartStore.kt  # One typed DataStore wrapper per concern (8 stores over 5 namespaces)
│   ├── ExchangeRatesRepository.kt
│   ├── ExchangeRatesService.kt # Singleton, coroutine-based fetch orchestration
│   ├── BackupManager.kt        # Encrypted export/import — see security.md
│   ├── RefreshState.kt         # In-memory "refresh running" state + debounced indicators
│   ├── cache/                  # Memory → disk → network rate cache (Store5-inspired, no Store5 dep)
│   └── persistence/             # PersistenceKey enum, DataStore delegates, PrefStore helper
├── view/
│   ├── main/                   # MainActivity (the only Activity) + the converter route
│   ├── navigation/             # Screen keys, AppNavigator back stack, AppNavHost (Navigation 3), screen motion, shared pills
│   ├── preference/             # Settings, Fees and Backup routes
│   ├── timeline/               # Chart route
│   ├── cart/                   # Bill-splitting calculator route
│   └── compose/                # Shared Compose foundation: theme (incl. Motion tokens), top bars (ScreenScaffold), CurrencyPill, UiTestTags, drag-reorder, dialogs, onboarding
├── viewmodel/
│   ├── main/MainViewModel.kt   # Conversion pipeline: pair, rates, fees → result. Exposes `input` (CalculatorInputState), which the keypad drives directly
│   ├── preference/
│   ├── timeline/
│   └── cart/                   # CartViewModel, CartMath, CartRatesCache
└── util/                       # Date, math, text, LiveData helpers, RetrofitProvider, HttpClientProvider

helpers/src/main/kotlin/de/salomax/helpers/
└── currencies/
    └── CurrencyFetcher.kt      # Generates localized currency-name XML resources
```

## Key Design Decisions

### Multiple API Providers via Enum + Abstract Interface

`ApiProvider` is an enum whose entries each implement `Api`, an abstract interface exposing `getRates()` and `getTimeline()`. Switching provider at runtime is a single DataStore write; no factory classes required.

### DataStore Preferences as the Persistence Layer

The app has no SQLite database. `SharedPreferences` has been fully migrated to **Jetpack DataStore Preferences**: all data (cached rates, starred currencies, user state, preferences, and the Cart's current + saved carts as JSON blobs) lives in 5 namespaced `DataStore<Preferences>` instances (`PersistenceKey`: `rates`, `timelines`, `last_state`, `starred_currencies`, `prefs`), managed by the stores in `repository/` and by `repository/persistence/`. File names deliberately match the old SharedPreferences basenames so that exported backups round-trip across the migration. `PrefStore.mappedLiveData { … }` / `mappedFlow { … }` bridge each namespace's `Flow<Preferences>` into `LiveData<T>` / `Flow<T>` for observers, and the stores' `…Blocking()` accessors use `DataStore.snapshot()` where a blocking read is unavoidable (e.g. `CurrenciesApplication`'s startup DNS prewarm).

`Database` is a handle on eight stores, each owning one concern, so no class grows past what one screen of code can hold:

| Property | Store | Holds |
|---|---|---|
| `rates` | `RateStore` | The latest rates, and each pair's cached timeline |
| `lastState` | `LastStateStore` | The converter's pair, the recent pairs, a pinned historical date |
| `stars` | `StarStore` | Starred currencies in the user's order; the starred-only filter |
| `providers` | `ProviderSettings` | Main and fallback provider, the Open Exchange Rates key, auto-refresh |
| `fees` | `FeeStore` | Saved fees and the active exchange / bank fee |
| `display` | `DisplaySettings` | Theme, decimals, date format, keypad, haptics, onboarding gate |
| `chart` | `ChartSettings` | What the timeline chart draws |
| `carts` | `CartStore` | The working cart and the saved ones |

Callers reach a value through its store: `Database(context).display.getTheme()`.

The one exception to "DataStore only": Cart JSON can be exported to / imported from an external file via the Storage Access Framework (`CartFileIo.kt`), but that's a one-off user-initiated file transfer, not an ongoing persistence layer.

### Three-tier rate cache: memory → disk → network

`repository/cache/` implements a Fetcher / SourceOfTruth / Converter triad inspired by Store5's design (no Store5 dependency — sized for this codebase, ~300–400 lines total). `RateCache.get(key)` walks: an in-memory LRU tier, then a disk JSON tier (`DiskJsonStore`, atomic writes), then the network on a miss — with `InFlightDedupe` collapsing concurrent requests for the same key and `RetryPolicy` backing off transient failures. This sits above the OkHttp-level HTTP cache (`HttpClientProvider`'s 5 MiB disk cache of raw response bytes) — different tiers, different jobs: OkHttp caches transport bytes, `RateCache` caches parsed `ExchangeRates` / `Timeline` domain objects.

### Networking: one OkHttp client; Retrofit for fixed-shape JSON, raw OkHttp for the rest

All providers share one `OkHttpClient` singleton (`HttpClientProvider`) with a disk cache and a per-provider `Cache-Control` rewrite interceptor. Which API sits on top of it is decided per endpoint, by the shape of the payload:

| Transport | Used for | Endpoints |
|---|---|---|
| **Retrofit** (`model/provider/api/*Api.kt`) | Fixed-shape JSON that a Moshi adapter decodes directly | Frankfurter, Open Exchange Rates, InforEuro, Bank of Canada, Bank of Israel `PublicApi` (latest rates) |
| **Raw OkHttp** (`HttpClientProvider.fetch`) | Feeds parsed by hand off the byte stream | Norges Bank (SDMX XML), Bank Rossii (XML), Bank of Israel SDMX-JSON (historical rates + timeline) |

The split is deliberate and permanent, not an unfinished migration. Retrofit's value is typed `@Path`/`@Query` binding plus converter-driven decoding. The raw-OkHttp feeds get the first but not the second: the XML ones go through SAX parsers, and Bank of Israel's SDMX-JSON is keyed by dimension-index tuples rather than a fixed schema, so `BankOfIsraelSdmxParser` walks it as a generic JSON tree. Wrapping those in Retrofit would only replace a query string.

Both transports share one error contract, so `ExchangeRatesRepository`'s error handling never needs to know which a provider uses. Non-2xx responses become `ApiHttpError(statusCode)`, network failures propagate as their original exception types, and a body that decodes to `null` becomes an `IOException("<provider>: empty JSON")`. On the Retrofit side this lives in two helpers in `ProviderUtils.kt`: `retrofitApi<T>()` binds an interface to the provider's `baseUrl` on the shared client, and `fetchRetrofit { }` unwraps the call. The interfaces return `Response<T>` rather than the bare body because Retrofit's suspend adapter would otherwise throw an opaque `KotlinNullPointerException` on a null body. Adapters that bail out on a non-object payload must consume it first (`JsonReader.skipIfNotObject()`), since Retrofit's Moshi converter rejects a document with input left over.

`ProviderRequestTest` pins the exact URL every provider sends and this error contract, on either transport, with no network. A provider that changes transport must not change what goes over the wire.

### Moshi + Custom Adapters for Diverse API Formats

Each exchange-rate API returns a different JSON (or XML) schema. Rather than normalising at the network layer, each provider ships its own Moshi adapter (or SAX parser for Norges Bank / Bank Rossii) that maps the raw response to the shared `ExchangeRates` / `Timeline` model.

### LiveData for Reactive UI

ViewModels expose `LiveData<T>` streams. Screens observe them (`observeAsState`) without holding references to the ViewModel, ensuring lifecycle-safety and no memory leaks. Preference changes propagate automatically via `PrefStore.mappedLiveData { … }`, which bridges each DataStore namespace's `Flow<Preferences>` into a `LiveData<T>` so observers pick up writes without a manual re-read.

### Refresh state: in memory, debounced for display

"A rate refresh is running" is transient UI state, so it lives in `repository/RefreshState.kt` in memory rather than in DataStore. The repository marks refreshes started and finished. The UI never shows the raw flag; it picks one of three views:

| View | Behavior | Used by |
|---|---|---|
| `inFlight` | Raw truth | Logic: no double refresh, Timeline's swap-menu enablement |
| `pullIndicator` | Immediate, held ≥ 400 ms | Pull-to-refresh spinner, drawer Refresh item |
| `passiveIndicator` | Only after 150 ms, then held ≥ 400 ms | Digit shimmer, Timeline progress bar |

The debounce is the `asRefreshIndicator` Flow operator, covered by `RefreshIndicatorTest`. A cached refresh therefore shows no shimmer at all, and a pull-to-refresh spinner never blinks shut.

### Timeline chart auto-scales decimal places (ignores user preference)

The timeline screen's rate labels (min/max/avg/current/past) do **not** honor the global `decimal_places` preference from Settings. `TimelineViewModel.getDecimalPlaces()` derives the number of decimals from the visible data range: `(max − min).abs().getSignificantDecimalPlaces(3)`, capped at 7.

Why: the user preference (default 2) is tuned for the converter screen where amounts are typed by hand. On the chart, a low-volatility pair like EUR↔USD moves in the 3rd–4th decimal, so a fixed 2-decimal display would render the min/max/avg identical and the chart's whole point would be lost. Auto-scaling keeps enough precision to show variation regardless of the pair.

Trade-off: users who explicitly raise or lower `decimal_places` in Settings will see that setting silently overridden on the chart. Intentional, but surprising — recorded here so future work doesn't "fix" it without weighing the readability cost.

### One Activity, Compose navigation (Navigation 3)

`MainActivity` is the app's only Activity. Every screen — converter, timeline, cart, settings, fees, backup — is a destination on one Compose back stack, so there are no Fragments, no XML layouts and no ActionBar (the theme is `Theme.AppCompat.DayNight.NoActionBar`, and the window is edge to edge).

| Piece | Where | Job |
|---|---|---|
| `Screen` | `view/navigation/Screen.kt` | Sealed interface of destinations. Arguments live on the key (`Timeline(from, to)`, `Cart(mainBase, mainDest)`), so a screen can't open without them. Each encodes to one line (`timeline:EUR:USD`) for saved state. |
| `AppNavigator` | `view/navigation/AppNavigator.kt` | Owns the back stack: `navigate` (pops back to a screen already on the stack instead of stacking a copy), `replaceTop` (swaps the top screen without adding history) and `pop`. The converter is always the bottom entry. Saved with the Activity's instance state, so rotation, a theme change (`recreate()`) and process death come back to the same screen. |
| `AppNavHost` | `view/navigation/AppNavHost.kt` | `NavDisplay` from Navigation 3 inside a `SharedTransitionLayout`. Decorators give each entry its own saved-state holder and **its own `ViewModelStore`**: a screen's ViewModel lives exactly as long as its entry, as it did with one Activity per screen. |
| `TwoPaneSceneStrategy` | `view/navigation/TwoPaneScene.kt` | On wide windows, shows a detail screen beside the converter instead of over it (below). |
| Routes | `ConverterRoute`, `TimelineRoute`, `CartRoute`, `SettingsRoute` / `FeesRoute` / `BackupRoute` | Each wires one screen: its ViewModel, top bar, sheets and dialogs. |

What the Activity keeps: the splash hand-off, the XML theme (pure black), foldable posture, hardware-keyboard input for the converter, and the Activity-scoped `MainViewModel` / `PreferenceViewModel`. `ConverterStatus` (banner + error messages) observes for the Activity's lifetime but only shows errors while the converter is on screen — an error that arrives while another screen covers it is held until the user comes back, as when the converter was its own paused Activity.

Things that used to lean on Activity plumbing now live in Compose: the cart's JSON import/export pickers register on the Activity's `ActivityResultRegistry` with fixed keys (so a result that outlives a recreation still reaches the cart), backup uses `rememberLauncherForActivityResult`, and the cart's unsaved-changes prompt is a `BackHandler` enabled only while there are unsaved edits — so an unchanged cart keeps the predictive back animation.

`AppNavigatorTest` covers the key encoding and stack rules; `AppNavHostTest` covers push/pop, system back, per-screen ViewModel clearing and recreation; `MainActivitySmokeTest` walks the real routes and `TwoPaneSmokeTest` the tablet layout (Robolectric).

### Two panes on tablets and unfolded foldables

From Material's *expanded* width (840 dp) the converter stays on the left and the timeline or the cart opens beside it. Each `NavEntry` carries a `PaneRole` in its metadata (`Converter` → `List`; `Timeline`, `Cart` → `Detail`; settings, fees and backup have none and still cover the window). `TwoPaneSceneStrategy` builds a `TwoPaneScene` when the top entry is a detail and a list entry sits below it; otherwise `SinglePaneSceneStrategy` shows one screen. The converter pane is 40 % of the width, kept between 360 and 480 dp.

- The scene is keyed by the detail entry, so switching detail screens runs the normal open/back transitions on the detail pane while `NavDisplay` keeps the converter in place as a shared entry.
- In two-pane, the converter's shortcuts **replace** the detail screen (`replaceTop`) rather than stacking, so one back always returns to the converter alone. The timeline beside it follows the converter's pair (`TimelineFollowsPair`).
- The converter picks its row (hero | keypad) or column layout from the space it's given (`BoxWithConstraints`), not from the screen orientation, so it stays a column in its pane on a landscape tablet.
- In the column layout the display is as tall as its content, and the keypad takes what's left: its rows are 56 dp when there's room and shrink to 48 dp (`KeypadHeights`) before the display has to scroll. That keeps the recent pairs under the hero card on screen on phones where a full-size keypad would cover them.
- Currency pills don't fly between screens on a two-pane window (`LocalTwoPaneWindow`): the converter and the cart are on screen together, so there's nothing to fly between.
- Moving between the single and two-pane layouts briefly composes the converter in both scenes, so `ConverterStatus` counts visible converters instead of keeping one flag.

### Messages: one snackbar, with Undo

Toasts are gone. `AppSnackbar` (`view/compose/AppSnackbar.kt`) is owned by `MainActivity` and drawn once above every screen, clear of the navigation bar and keyboard. Screens reach it through `LocalAppSnackbar`; outside the app shell (previews, screenshot tests) it is null and `showOrToast` falls back to a toast. A new message replaces the one showing rather than queuing.

Destructive actions don't ask first; they act and offer **Undo**: deleting a cart row (swipe or button) restores it at the same index (`CartViewModel.restoreItem`), Clear and Import restore the previous cart. Only deleting a *saved* cart, which Undo can't reach once you've left the sheet, still confirms. Removing a recent pair asks first and then offers Undo as well, since a long-press can land on the wrong chip (`LastStateStore.removeRecentPair` returns the list as it was, `restoreRecentPairs` puts it back after any pair used since). Copying an amount shows no message on Android 13+, where the system already confirms copies.

### Prompts: sheets for choices, dialogs for typing

A prompt that only asks for a choice is a bottom sheet: every picker, and every "are you sure?" (`LedgerConfirmSheet`: deleting a saved cart, importing a backup over the current settings, removing from history) or "here's what happened" (the fallback provider's explanation). They open, swipe away and go back the same way. Dialogs are kept for prompts that take typed input: a cart's name, a backup password, a fee.

### Cart pins and drag order

Pinned rows sit above the rest. Storage keeps the user's own order, and the pinned-first order is only applied on screen (`CartItemsList`), so unpinning a row returns it to its old place. Pinning or unpinning re-sorts the list at once.

Once something is pinned, the list shows two sections, **Pinned** and **Other items**, under the same brass headings as Settings (`LedgerSectionHeader`); with nothing pinned it's one plain list. Each section reorders on its own. A row never swaps with one from the other section (`moveByKey`, matched by key because the headings sit between rows), so to move a row across, pin or unpin it. On drop, `CartViewModel.commitDrag` moves only that row in storage (`afterDrop`), just before the next row of its section on screen, and every other row keeps its slot.

### Converter conveniences

- **Fallback provider**: when the rates on screen came from the fallback provider, the status pill says so ("Bank of Israel unavailable • Using Frankfurter.app"); tapping it opens a sheet explaining that the next refresh tries the main provider first again, with a button to change provider (`BannerContent.explanation`).
- **Rate age**: the footer timestamp reads "5 min ago" / "Yesterday" while recent (within 24 h for providers with a time, 7 days for date-only ones) and ticks every minute; long-press shows the full date and time, tap still opens the provider picker.
- **Recent pairs**: a pair that stays on screen for 2 s is recorded (`RecentPairs`, newest first, at most 6, stored as `USD:ILS,EUR:USD` in `last_state`). A pair and its swap count as one. Chips under the hero card switch to the others in one tap, through `MainViewModel.setCurrencyPair`, which writes both sides at once (the swap button uses it too). A long-press on a chip asks whether to remove the pair from the history (`RemoveFromHistorySheet`, with the chip's flags); after removing, the snackbar offers Undo.
- **Currency picker**: while the list is unfiltered, the recent pairs' currencies show as chips above it (minus the one on this side and the one it can't be). A long-press on one asks whether to remove it; since the chips come from the recent pairs, that removes every recent pair using the currency. Search matches the code, the name, and **the countries that use the currency** ("Japan" → JPY, "Germany" → EUR), in the app's language and in English (`CurrencyCountries`, built once per language from the platform's locale data).

### Outside the app: text selection, shortcuts, widget

- **Convert currency** in any app's text-selection menu (`ConvertTextActivity`, `ACTION_PROCESS_TEXT`): a sheet over that app converting the selected price with the cached rates — instant, no network. `PriceParser` finds the amount and currency ("€49.99", "49,99 €", "USD 1,234.56"); a shared symbol ("$", "kr") prefers the user's own currencies, codes count only in capitals, and letter symbols only as whole words. The price converts into the converter's destination, or back into its base when it's already in the destination (`targetCurrency`). Copy, or open the converter on that pair and amount.
- **Launcher shortcuts** (`AppShortcuts`): the top three recent pairs and the cart, kept in step with the recent pairs. Dynamic, since a static shortcut needs a fixed package name and the debug build's differs.
- Both go through `ConverterLaunch`, the app's intents for "the converter on this pair / amount" and "the cart". MainActivity is exported, so every extra is validated and anything unreadable is dropped.
- **Widget**: each widget follows the converter's pair by default or keeps a pair of its own (`WidgetConfigureActivity`, opened from the widget's reconfigure action), stored in the widget's Glance state. That activity isn't exported: a launcher opens it through `AppWidgetHost`, where the system starts it on the launcher's behalf, and since configuration is optional a launcher that can't do that still gets a working widget. From 260 dp wide it draws a 30-day trend line from the cached timeline (green rising, red falling). Tapping opens the converter on its pair.

### Cart extras: tip / tax, split, budget

A cart carries `CartExtras` (saved with it; older carts load without). **Tip / tax** is a percentage on top of the items, applied before fees since a card's FX fee is charged on everything paid; **split** shows each person's share of the total; **budget** (destination currency) shows what's left, or how far over in red. Edited in one sheet from the cart's menu or by tapping those footer rows, and included in the shared text.

### Timeline states

The statistics are a 2×2 grid (max/min, average/median) of tonal tiles, with "—" until data arrives, and the change since the start of the range as a signed percentage with a trend arrow ("−7.55 %", a true minus sign). Scrubbing the chart shows a bubble with the date and rate over the point. When the provider can't deliver a timeline, the chart card explains why and offers **Try again** and **Change provider** (the provider picker, which retries on pick) instead of an empty chart. The bar's title shows the requested pair right away, not only once data has loaded.

Periods: week, month, year, **5 years**, and a **custom range** (Material's date-range picker, back to `TIMELINE_MAX_YEARS` = 10 years). The view model filters one cached window per pair and asks the repository for older history only when a span starts before what it has fetched (`fetchCovering`); the repository then fetches just the missing tail when the cache already reaches back far enough, or the whole span when it doesn't (`timelineFetchStart`), and keeps cached history up to 10 years instead of trimming it to the last year. A span with no rates once loading is done (a weekend, or dates before the provider's history) shows a "No rates for these dates" notice in place of the chart (`isRangeEmpty`), and every statistic handles the empty set rather than assuming a first and last rate. **Share chart** (top bar) sends the chart card as a PNG, captioned with the pair, the dates and the source: the card draws through a `LayerCapture`, the same snapshot mechanism the converter's share uses for the hero card.

### Colors: paper and ink, or Material You

The brand palette (paper and ink, bill-green accent) is the default. Settings → Appearance has **Wallpaper colors**, which swaps in `dynamicLight/DarkColorScheme`. `MainActivity` passes the preference to `AppTheme`, which provides it through `LocalDynamicColor`, so the nested `AppTheme` inside sheets and dialogs keeps the same palette. Pure black keeps its black background either way. One-off brand accents that aren't scheme colors (the fee stamp, status pills) stay as they are.

### Top bars: Material 3, per screen

Each screen draws its own Compose top bar. The converter's (`ConverterTopBar`) is a small bar: the drawer button (the hamburger ↔ arrow morph, drawn from `DrawerArrowDrawable` and driven by the drawer's offset), the Currenci× wordmark, and four shortcut icons. The drawer slides in under it, so the arrow that closes it stays in sight and in reach; the shortcuts stay usable too and, like the drawer's entries, close it on the way. Back (button or gesture) closes an open drawer instead of leaving the app. Every other screen uses `ScreenScaffold`, by default with a **medium** bar (large title under the actions) that collapses into the small one as content scrolls under it, taking the raised surface tone once collapsed; short windows (a phone in landscape) always get the small bar. The timeline is the exception: it keeps the small bar so the chart gets the height, with the pair beside the back arrow as flag, symbol and code on each side of an arrow (`🇺🇸 $ USD → 🇮🇱 ₪ ILS`; screen readers hear the localized "USD to ILS"); the cart's overflow menu (`TopBarOverflowMenu`) has icons, with Clear set apart in the error color.

Shared Compose foundation lives in `view/compose/`: `AppTheme.kt` (Material 3 theme, light/dark/OLED, optional wallpaper colors — the app dropped `com.google.android.material` in favor of Compose Material 3 + appcompat-only chrome), `theme/Motion.kt` (motion tokens, below), shared common components, drag-reorder, plus `dialogs/` and `onboarding/` subpackages.

Every UI icon is a Material Symbols **Rounded** glyph (weight 400, outlined, 24 dp) stored as a vector drawable `res/drawable/ic_*.xml` and drawn with `painterResource`, so the app has one icon style. The drawables are generated by `art/material-symbols/generate.py` from the `@material-symbols/svg-400` package; to add an icon, add a row to its table and rerun it (the app doesn't depend on `material-icons-extended`). The converter's morphing hamburger (`DrawerArrowDrawable`) is tuned to the same Symbols geometry: 1.5 dp round-capped bars.

Every currency flag renders through `Currency.flagPainter()` (`painterResource` over `Currency.flagRes`), which parses each vector flag once, caches it app-wide, and draws it crisply at any size. Don't wrap flags in an `AndroidView`/`ImageView` or rasterise the `Drawable` to a bitmap. The currency picker's 190+ rows used to do the former, and it cost scroll smoothness.

`UiTestTags` are stable semantics tags on the elements the `:baselineprofile` journeys drive (keypad keys, pills, drawer rows, picker list, onboarding Skip). They're exposed as resource ids via `testTagsAsResourceId` on `AppNavHost` (every screen) and on the popups that have their own window. They carry no visual meaning. Keep them in sync with `baselineprofile/.../UiTags.kt`.

### Motion: shared tokens, springs for anything interruptible

All UI timing comes from `view/compose/theme/Motion.kt`, so "make it snappier" is a one-file change. Durations sit on the Material 3 scale: short 120 ms (value fades), medium 180 ms (entrances, onboarding steps), long 320 ms (the launch wordmark only), and a 20 ms stagger — kept on the quick side of Material's ranges. There are two springs, both stiffer than Material's medium (2500): `snappy` for tap-driven motion (the swap button's flip) and `settle` for panels (the Cart keypad). Anything the user can re-trigger mid-flight uses a spring, because an interrupted spring keeps its velocity while a restarted tween visibly kinks. The navigation drawer is the one panel not on `settle`: it moves on Material's own drawer motion, because `DrawerState.open()` / `close()` take no animation spec (the spec-taking `animateTo` is deprecated). Content pacing (the loading shimmer, the rate pill's auto-scroll) isn't a transition and keeps its own constants.

Rules the main screen follows:

- **Typing is never animated.** The result digits crossfade and pulse only when they change from *outside* the keypad (a rate refresh, currency pick or fee toggle). `DigitsChangeOrigin` tells the two apart by whether the typed input changed since the last result change.
- **Animated values are read in the draw phase** (`graphicsLayer { }`, `Canvas`), so they redraw without recomposing. The top bar's hamburger is driven from the drawer's actual offset through `snapshotFlow`, so it tracks a finger dragging the drawer.
- **Nothing animates forever.** The input caret is a 500 ms on/off toggle, solid while typing. A continuous fade would request a frame on every vsync for as long as the screen is open.

Screen-to-screen transitions are Compose `ContentTransform`s in `view/navigation/ScreenMotion.kt`, handed to `NavDisplay`:

| Transition | New / returning screen | Screen leaving or covered |
|---|---|---|
| Open (`pushTransition`) | Slides in 1/10 of the width from the right, fades in over 120 ms | Sinks back to 97 % scale |
| Back (`popTransition`) | Rises from 97 % back to full size | Slides 1/10 right and fades out |

Open and back take 200 ms (a little longer than medium, since a whole screen travels further than an element). A back gesture plays the same back transition once it's let go; nothing moves while the finger is down (see *Predictive back gesture* below). `ScreenFrame` in `AppNavHost` paints the window background behind every screen so screens stay opaque while they overlap. Everything riding on a screen transition must finish within it — `AnimatedContent` keeps both screens up until the slowest animation ends — so the shared pills move on the transition's own 200 ms curve rather than the default spring.

The converter's and the cart's currency pills (`CurrencyPill`) are shared elements (`sharedCurrencyPillModifier`): opening the cart flies each pill into the matching cart pill and back again on return. Two pills match only when they show the same currency on the same side, so a cart that keeps its own pair doesn't pull the converter's pills across the screen. On two-pane windows they don't fly (see *Two panes* above).

### Timeline chart engine: Vico via Compose interop

The timeline chart is rendered by [Vico](https://github.com/patrykandpatrick/vico) (`com.patrykandpatrick.vico:compose`), hosted inside a `ComposeView` embedded in the otherwise View-based XML layout (`timeline_chart.xml`). `TimelineChart.kt` is a `@Composable` that observes the ViewModel's `LiveData` streams via `observeAsState()` (bridged by `androidx.compose.runtime:runtime-livedata`) and drives a `CartesianChartHost` backed by a `CartesianChartModelProducer`.

Why Vico over the previous engine (SparkView): SparkView is unmaintained and required a hand-rolled adapter, a manual dashed baseline `Paint`, and custom scrub handling. Vico ships all of that as first-class API (`HorizontalLine` decorations, `CartesianMarkerVisibilityListener`) and is actively developed. This was also the entry point that first introduced Jetpack Compose to what was then a View-only app; Compose has since become the primary UI toolkit (see above).

Behavior preserved: dashed reference line at the last value, scrub-to-past-date via marker-shown callback, theme-aware colors resolved through `MaterialColors.getColor` and passed into the composable.

### Graph options: user-tunable chart chrome

Four `LiveData<Boolean>` streams (backed by `PrefStore.mappedLiveData`) — grid, X-axis labels, Y-axis labels, and highlight-extremes — flow from `Database.chart` (`ChartSettings`) through `TimelineRoute` into `TimelineChart`. All default to `true` so first-run appearance is unchanged. Inside the composable each toggle swaps a Vico component for `null` (e.g. `guideline = if (showGrid) rememberAxisGuidelineComponent() else null`); Vico treats `null` as "don't draw," so no branching in the layer definitions is needed.

### Application subclass prewarms DNS for the selected provider

`CurrenciesApplication` is registered via `android:name=".CurrenciesApplication"` on the manifest's `<application>` tag. Its only responsibility today is to resolve the currently-selected `ApiProvider`'s host on a background daemon thread during `onCreate()`, so the first exchange-rate request doesn't pay for DNS.

The preference read (`Database(this).providers.getApiProvider()`) and the `InetAddress.getAllByName(host)` call both run **inside** the background thread — DataStore's synchronous snapshot read and DNS resolution are both blocking I/O and neither belongs on the main thread during app startup. Failures (offline, DNS outage) are swallowed with `runCatching`; this is a best-effort warm-up, not a health check.

`ApiProvider.getHost()` (a narrow accessor over the enum's `private implementation.baseUrl`) exposes only the hostname to callers, so the `Application` never touches the full base URL.

### Background rate refresh via WorkManager

`androidx.work:work-runtime-ktx` schedules periodic background refresh of exchange rates (provider-aware TTL — see `worker/RateRefreshScheduler.kt`). Off by default; opt-in via Settings.

### Home-screen widget via Glance

`view/widget/CurrencyWidget.kt` is an `AppWidgetProvider` whose content is composed with **Glance** (`androidx.glance:glance-appwidget`) rather than hand-rolled `RemoteViews`. `widget_currency.xml` remains as the widget's preview/initial-layout resource required by the App Widget framework, but the live content is Glance composables. Per-widget pairs and the trend line: see *Outside the app* above.

### Right-to-left languages

Hebrew, Arabic and Farsi split the app in two:

- **Numbers stay left to right.** `AppTheme` locks the layout direction to LTR (the manifest declares `supportsRtl="false"` for the same reason), so the converter, keypad, hero card, cart amounts and chart never mirror: digits, math and the from → to pair read left to right in every language. The Currenci× wordmark is a logo and never mirrors either.
- **The converter's chrome follows the language.** `MainScreen` lays out the top bar and the navigation drawer with `ReadingDirection`: in Hebrew the hamburger sits on the right with the shortcuts on the left, the drawer slides in from the right, and the hamburger's arrow points right, the way the drawer closes. The converter body under them goes back in an `Ltr` island (`ConverterRtlTest`). The timeline does the same: its top bar and its statistics tiles and period controls follow the language, while the chart, the past / current row in step with its time axis, and the from → to pair in the title stay left to right. The cart too: its top bar, item rows (drag handle on the right, pin on the left), labels and add button follow the language, while the currency pair, every amount and price, and the keypad stay left to right.
- **Prose follows the language.** Settings and its screens, the bottom sheets and the dialogs opt back in with `ReadingDirection` (or `ProseTheme`, `AppTheme` plus `ReadingDirection`): text right-aligned, switches and icons on the left, buttons mirrored. Anything inside them whose left-to-right order carries meaning goes back in an `Ltr` island: a fee's currency pair, the widget's from / swap / to row, the amounts and the rate line in the convert-selection sheet.
- **Popups set it inside their window.** Because of `supportsRtl="false"`, a dialog's or sheet's window always resolves left to right, whatever the composition around it asked for. So `LedgerBottomSheet`, `LedgerDialogFrame` and the first-run tour's card (`Spotlight`, a popup) apply `ReadingDirection` inside their content, and `ProseAlertDialog` / `ProseDatePickerDialog` stand in for Material's `AlertDialog` / `DatePickerDialog` (same look), whose own layout can't be reached from their slots.
- **Mixed-script labels keep their order.** Inside the LTR layout, a right-to-left label that names something in Latin script ("היום · InforEuro", "שקל חדש (ILS ₪)") goes through `String.inReadingOrder(context)`, an RTL isolate, so its parts read in the language's order.

`ReadingDirectionTest` checks a sheet, a confirm sheet, a ledger dialog and a Material-style dialog in Hebrew and English with the whole activity in that language, as on a phone; `PopupReadingDirectionTest` covers the tour card and the convert-selection sheet. The assertions are in `util/ReadingSide.kt` (test sources): `assertOnReadingStartSide` checks that a node sits where the test's language starts reading (right in Hebrew, left in English), so one test body serves both directions.

The screenshot matrix runs each language's cells with the whole activity in that language (`ScreenshotRule` switches Robolectric's qualifiers, which recreates the activity, and sets the content on the new one), so sheets and dialogs, whose windows take their resources from the activity, are fully Hebrew in the Hebrew cells.

### Predictive back gesture

Opted in via `android:enableOnBackInvokedCallback="true"` on the manifest's `<application>` tag, so back runs on the predictive back callbacks (and the system's back-to-home animation plays when back leaves the app from the converter). Inside the app nothing previews a back while the finger is down; letting go plays the ordinary close:

- **Screens**: `AppNavHost` gives `NavDisplay` the lower-level `SceneState` API. `NavigationBackHandler` takes the gesture with its own state, and `NavDisplay` gets a separate `rememberNavigationEventState` that never sees a swipe, so it never seeks a predictive transition and plays `popTransition` on release.
- **Drawer**: a plain `BackHandler` closes it. `ModalDrawerSheet(drawerState)` is not used, because it shrinks the sheet under the finger.
- **Bottom sheets**: `LedgerBottomSheet` turns off the sheet's own back handling (`shouldDismissOnBackPress = false`), which also shrinks the sheet, and closes the sheet from a `BackHandler` in its content instead. That handler sits in the sheet's window, on the sheet dialog's dispatcher.

`MainActivitySmokeTest` holds a back swipe halfway on each of the three and checks that nothing has moved. A screen that must intercept back only does so while it needs to (the cart, while it has unsaved edits).

### Build Flavors: `play` vs `fdroid`

| Dimension | `fdroid` | `play` |
|---|---|---|
| Play Services | None | Allowed |
| Reproducibility | Yes | No |
| Distribution | F-Droid | Google Play |

Source sets under `app/src/fdroid/` and `app/src/play/` override or add flavor-specific code without touching the shared `main` source set.

Where a build type or flavor decides whether something exists at all, `main` asks for it through a same-named file in each source set that hands back a nullable hook: `httpInspector` (Chucker in debug, `null` in release), `debugPreferenceRows` (the debug-only Settings rows), `rateApp` (the store rating, Play only) and `releaseNotesUrl()` / `plantConsoleLogging()`. `main` never branches on `BuildConfig.DEBUG` or `FLAVOR`, so no variant compiles a branch it can't take.
