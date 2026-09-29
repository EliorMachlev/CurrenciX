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
│   ├── Database.kt             # Typed DataStore Preferences wrapper (5 namespaces)
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
│   ├── main/MainViewModel.kt   # 778 lines — core conversion + calculator logic
│   ├── preference/
│   ├── timeline/
│   └── cart/                   # CartViewModel, CartMath, CartRatesCache
└── util/                       # Date, math, text, LiveData helpers, RetrofitProvider, HttpClientProvider

helpers/src/main/kotlin/de/salomax/helpers/
├── changelog/
│   ├── FastlaneToResource.kt   # Fastlane changelogs → Android XML resources
│   └── ResourceToFastlane.kt   # Reverse: Android XML → Fastlane format
└── currencies/
    └── CurrencyFetcher.kt      # Generates localized currency-name XML resources
```

## Key Design Decisions

### Multiple API Providers via Enum + Abstract Interface

`ApiProvider` is an enum whose entries each implement `Api`, an abstract interface exposing `getRates()` and `getTimeline()`. Switching provider at runtime is a single DataStore write; no factory classes required.

### DataStore Preferences as the Persistence Layer

The app has no SQLite database. `SharedPreferences` has been fully migrated to **Jetpack DataStore Preferences**: all data (cached rates, starred currencies, user state, preferences, and the Cart's current + saved carts as JSON blobs) lives in 5 namespaced `DataStore<Preferences>` instances (`PersistenceKey`: `rates`, `timelines`, `last_state`, `starred_currencies`, `prefs`), managed by `Database.kt` and `repository/persistence/`. File names deliberately match the old SharedPreferences basenames so that exported backups round-trip across the migration. `PrefStore.mappedLiveData { … }` / `mappedFlow { … }` bridge each namespace's `Flow<Preferences>` into `LiveData<T>` / `Flow<T>` for observers, and `Database.getXxx()`-style synchronous accessors use `DataStore.snapshot()` where a blocking read is unavoidable (e.g. `CurrenciesApplication`'s startup DNS prewarm).

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
- Currency pills don't fly between screens on a two-pane window (`LocalTwoPaneWindow`): the converter and the cart are on screen together, so there's nothing to fly between.
- Moving between the single and two-pane layouts briefly composes the converter in both scenes, so `ConverterStatus` counts visible converters instead of keeping one flag.

### Messages: one snackbar, with Undo

Toasts are gone. `AppSnackbar` (`view/compose/AppSnackbar.kt`) is owned by `MainActivity` and drawn once above every screen, clear of the navigation bar and keyboard. Screens reach it through `LocalAppSnackbar`; outside the app shell (previews, screenshot tests) it is null and `showOrToast` falls back to a toast. A new message replaces the one showing rather than queuing.

Destructive actions don't ask first; they act and offer **Undo**: deleting a cart row (swipe or button) restores it at the same index (`CartViewModel.restoreItem`), Clear and Import restore the previous cart. Only deleting a *saved* cart, which Undo can't reach once you've left the sheet, still confirms. Copying an amount shows no message on Android 13+, where the system already confirms copies.

### Converter conveniences

- **Rate age**: the footer timestamp reads "5 min ago" / "Yesterday" while recent (within 24 h for providers with a time, 7 days for date-only ones) and ticks every minute; long-press shows the full date and time, tap still opens the provider picker.
- **Recent pairs**: a pair that stays on screen for 2 s is recorded (`RecentPairs`, newest first, at most 6, stored as `USD:ILS,EUR:USD` in `last_state`). A pair and its swap count as one. Chips under the hero card switch to the others in one tap, through `MainViewModel.setCurrencyPair`, which writes both sides at once (the swap button uses it too).
- **Currency picker**: while the list is unfiltered, the recent pairs' currencies show as chips above it (minus the one on this side and the one it can't be). Search matches the code, the name, and **the countries that use the currency** ("Japan" → JPY, "Germany" → EUR), in the app's language and in English (`CurrencyCountries`, built once per language from the platform's locale data).

### Timeline states

The statistics are a 2×2 grid (max/min, average/median) of tonal tiles, with "—" until data arrives, and the change since the start of the range as a signed percentage with a trend arrow ("−7.55 %", a true minus sign). Scrubbing the chart shows a bubble with the date and rate over the point. When the provider can't deliver a timeline, the chart card explains why and offers **Try again** and **Change provider** (the provider picker, which retries on pick) instead of an empty chart. The bar's title shows the requested pair right away, not only once data has loaded.

### Colors: paper and ink, or Material You

The brand palette (paper and ink, bill-green accent) is the default. On Android 12+ Settings → Appearance has **Wallpaper colors**, which swaps in `dynamicLight/DarkColorScheme`. `MainActivity` passes the preference to `AppTheme`, which provides it through `LocalDynamicColor`, so the nested `AppTheme` inside sheets and dialogs keeps the same palette. Pure black keeps its black background either way. One-off brand accents that aren't scheme colors (the fee stamp, status pills) stay as they are.

### Top bars: Material 3, per screen

Each screen draws its own Compose top bar. The converter's (`ConverterTopBar`) is a small bar: the drawer button (the hamburger ↔ arrow morph, drawn from `DrawerArrowDrawable` and driven by the drawer's offset), the Currenci× wordmark, and four shortcut icons. The drawer opens over it. Every other screen uses `ScreenScaffold`, by default with a **medium** bar (large title under the actions) that collapses into the small one as content scrolls under it, taking the raised surface tone once collapsed; short windows (a phone in landscape) always get the small bar. The timeline is the exception: it keeps the small bar so the chart gets the height, with the pair beside the back arrow as flag, symbol and code on each side of an arrow (`🇺🇸 $ USD → 🇮🇱 ₪ ILS`; screen readers hear the localized "USD to ILS"); the cart's overflow menu (`TopBarOverflowMenu`) has icons, with Clear set apart in the error color.

Shared Compose foundation lives in `view/compose/`: `AppTheme.kt` (Material 3 theme, light/dark/OLED, optional wallpaper colors — the app dropped `com.google.android.material` in favor of Compose Material 3 + appcompat-only chrome), `theme/Motion.kt` (motion tokens, below), shared common components, drag-reorder, plus `dialogs/` and `onboarding/` subpackages.

Every UI icon is a Material Symbols **Rounded** glyph (weight 400, outlined, 24 dp) stored as a vector drawable `res/drawable/ic_*.xml` and drawn with `painterResource`, so the app has one icon style. The drawables are generated by `art/material-symbols/generate.py` from the `@material-symbols/svg-400` package; to add an icon, add a row to its table and rerun it (the app doesn't depend on `material-icons-extended`). The converter's morphing hamburger (`DrawerArrowDrawable`) is tuned to the same Symbols geometry: 1.5 dp round-capped bars.

Every currency flag renders through `Currency.flagPainter()` (`painterResource` over `Currency.flagRes`), which parses each vector flag once, caches it app-wide, and draws it crisply at any size. Don't wrap flags in an `AndroidView`/`ImageView` or rasterise the `Drawable` to a bitmap. The currency picker's 190+ rows used to do the former, and it cost scroll smoothness.

`UiTestTags` are stable semantics tags on the elements the `:baselineprofile` journeys drive (keypad keys, pills, drawer rows, picker list, onboarding Skip). They're exposed as resource ids via `testTagsAsResourceId` on `AppNavHost` (every screen) and on the popups that have their own window. They carry no visual meaning. Keep them in sync with `baselineprofile/.../UiTags.kt`.

### Motion: shared tokens, springs for anything interruptible

All UI timing comes from `view/compose/theme/Motion.kt`, so "make it snappier" is a one-file change. Durations sit on the Material 3 scale: short 120 ms (value fades), medium 180 ms (entrances, onboarding steps), long 320 ms (the launch wordmark only), and a 20 ms stagger — kept on the quick side of Material's ranges. There are two springs, both stiffer than Material's medium (2500): `snappy` for tap-driven motion (the swap button's flip) and `settle` for panels (the Cart keypad, and the drawer when the hamburger opens or closes it). Anything the user can re-trigger mid-flight uses a spring, because an interrupted spring keeps its velocity while a restarted tween visibly kinks. Content pacing (the loading shimmer, the rate pill's auto-scroll) isn't a transition and keeps its own constants.

Rules the main screen follows:

- **Typing is never animated.** The result digits crossfade and pulse only when they change from *outside* the keypad (a rate refresh, currency pick or fee toggle). `DigitsChangeOrigin` tells the two apart by whether the typed input changed since the last result change.
- **Animated values are read in the draw phase** (`graphicsLayer { }`, `Canvas`), so they redraw without recomposing. The top bar's hamburger is driven from the drawer's actual offset through `snapshotFlow`, so it tracks a finger dragging the drawer.
- **Nothing animates forever.** The input caret is a 500 ms on/off toggle, solid while typing. A continuous fade would request a frame on every vsync for as long as the screen is open.

Screen-to-screen transitions are Compose `ContentTransform`s in `view/navigation/ScreenMotion.kt`, handed to `NavDisplay`:

| Transition | New / returning screen | Screen leaving or covered |
|---|---|---|
| Open (`pushTransition`) | Slides in 1/10 of the width from the right, fades in over 120 ms | Sinks back to 97 % scale |
| Back (`popTransition`) | Rises from 97 % back to full size | Slides 1/10 right and fades out |
| Predictive back (`predictivePopTransition`) | Rises from 97 % as the finger moves | Shrinks to 90 %, drifts toward the swipe edge, rounds its corners (28 dp), fades only at the very end |

Open and back take 200 ms (a little longer than medium, since a whole screen travels further than an element). The predictive transition is seeked by the gesture: its 220 ms timeline maps onto the swipe, so it's linear, and letting go plays the remainder. The rounded corners come from `ScreenFrame` in `AppNavHost`, which also paints the window background behind every screen so screens stay opaque while they overlap; the radius follows the screen transition and is read only when drawing. Everything riding on a screen transition must finish within it — `AnimatedContent` keeps both screens up until the slowest animation ends — so the corner snaps when it has nothing to round, and the shared pills move on the transition's own 200 ms curve rather than the default spring.

The converter's and the cart's currency pills (`CurrencyPill`) are shared elements (`sharedCurrencyPillModifier`): opening the cart flies each pill into the matching cart pill and back again on return, predictive back included. Two pills match only when they show the same currency on the same side, so a cart that keeps its own pair doesn't pull the converter's pills across the screen. On two-pane windows they don't fly (see *Two panes* above).

### Timeline chart engine: Vico via Compose interop

The timeline chart is rendered by [Vico](https://github.com/patrykandpatrick/vico) (`com.patrykandpatrick.vico:compose`), hosted inside a `ComposeView` embedded in the otherwise View-based XML layout (`timeline_chart.xml`). `TimelineChart.kt` is a `@Composable` that observes the ViewModel's `LiveData` streams via `observeAsState()` (bridged by `androidx.compose.runtime:runtime-livedata`) and drives a `CartesianChartHost` backed by a `CartesianChartModelProducer`.

Why Vico over the previous engine (SparkView): SparkView is unmaintained and required a hand-rolled adapter, a manual dashed baseline `Paint`, and custom scrub handling. Vico ships all of that as first-class API (`HorizontalLine` decorations, `CartesianMarkerVisibilityListener`) and is actively developed. This was also the entry point that first introduced Jetpack Compose to what was then a View-only app; Compose has since become the primary UI toolkit (see above).

Behavior preserved: dashed reference line at the last value, scrub-to-past-date via marker-shown callback, theme-aware colors resolved through `MaterialColors.getColor` and passed into the composable.

### Graph options: user-tunable chart chrome

Four `LiveData<Boolean>` streams (backed by `PrefStore.mappedLiveData`) — grid, X-axis labels, Y-axis labels, and highlight-extremes — flow from `Database` through `TimelineRoute` into `TimelineChart`. All default to `true` so first-run appearance is unchanged. Inside the composable each toggle swaps a Vico component for `null` (e.g. `guideline = if (showGrid) rememberAxisGuidelineComponent() else null`); Vico treats `null` as "don't draw," so no branching in the layer definitions is needed.

### Application subclass prewarms DNS for the selected provider

`CurrenciesApplication` is registered via `android:name=".CurrenciesApplication"` on the manifest's `<application>` tag. Its only responsibility today is to resolve the currently-selected `ApiProvider`'s host on a background daemon thread during `onCreate()`, so the first exchange-rate request doesn't pay for DNS.

The preference read (`Database(this).getApiProvider()`) and the `InetAddress.getAllByName(host)` call both run **inside** the background thread — DataStore's synchronous snapshot read and DNS resolution are both blocking I/O and neither belongs on the main thread during app startup. Failures (offline, DNS outage) are swallowed with `runCatching`; this is a best-effort warm-up, not a health check.

`ApiProvider.getHost()` (a narrow accessor over the enum's `private implementation.baseUrl`) exposes only the hostname to callers, so the `Application` never touches the full base URL.

### Background rate refresh via WorkManager

`androidx.work:work-runtime-ktx` schedules periodic background refresh of exchange rates (provider-aware TTL — see `worker/RateRefreshScheduler.kt`). Off by default; opt-in via Settings.

### Home-screen widget via Glance

`view/widget/CurrencyWidget.kt` is an `AppWidgetProvider` whose content is composed with **Glance** (`androidx.glance:glance-appwidget`) rather than hand-rolled `RemoteViews`. `widget_currency.xml` remains as the widget's preview/initial-layout resource required by the App Widget framework, but the live content is Glance composables.

### Predictive back gesture

Opted in via `android:enableOnBackInvokedCallback="true"` on the manifest's `<application>` tag (Android 13+). Inside the app, `NavDisplay` handles the gesture itself and scrubs `predictivePopTransition` (above) with the swipe, shared pills included. On the converter, back leaves the app with the system's back-to-home animation. A screen that must intercept back only does so while it needs to (the cart, while it has unsaved edits), so every other back keeps the animated preview.

### Build Flavors: `play` vs `fdroid`

| Dimension | `fdroid` | `play` |
|---|---|---|
| Play Services | None | Allowed |
| Reproducibility | Yes | No |
| Distribution | F-Droid | Google Play |

Source sets under `app/src/fdroid/` and `app/src/play/` override or add flavor-specific code without touching the shared `main` source set.
