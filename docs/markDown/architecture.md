# Architecture

CurrenciX follows **MVVM** (Model-View-ViewModel) with a Repository layer, implemented in Kotlin with AndroidX Lifecycle components.

## Layer Overview

```
┌──────────────────────────────────────┐
│              View Layer              │
│ MainActivity · Compose screens/sheets│
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
│  Database (DataStore: prefs + proto) │
└──────────────────────────────────────┘
```

## Source Layout

```
app/src/main/kotlin/com/eliormachlev/currencix/
├── CurrenciesApplication.kt   # Application subclass — prewarms provider DNS and the saved stores, installs the crash recorder
├── problems/ProblemReports.kt # Crashes and freezes kept on the phone, shared from About on request
├── model/
│   ├── ApiProvider.kt          # Enum of 8 active providers + the Api interface
│   ├── Currency.kt             # 173 ISO-4217 currencies with symbols & flags
│   ├── ExchangeRates.kt        # Snapshot of rates for a base currency
│   ├── Rate.kt                 # Single (currency, rate) pair
│   ├── Timeline.kt             # Historical rate series
│   ├── adapter/                # Moshi / XML adapters per provider
│   └── provider/               # HTTP implementations per provider (api/ holds the Retrofit interfaces)
├── repository/
│   ├── Database.kt             # Hands out the stores below; holds no logic of its own
│   ├── RateStore.kt … RateAlertStore.kt  # One typed DataStore wrapper per concern (10 stores over 4 preference namespaces and 3 Protobuf files, plus the old timelines one read once)
│   ├── ExchangeRatesRepository.kt
│   ├── ExchangeRatesService.kt # Singleton, coroutine-based fetch orchestration
│   ├── BackupManager.kt        # Encrypted export/import — see security.md
│   ├── SavedProtos.kt          # Carts, trips, fees and rate alerts ↔ their Protobuf messages (SavedValues.kt: the checks every read applies)
│   ├── json/JsonTree.kt        # The app's own JSON (backups, cart files, caches) over Moshi's streaming reader/writer
│   ├── TimelineFiles.kt        # Each provider's history per pair, one file apiece
│   ├── ProviderCooldown.kt     # Providers that failed lately, passed over for the fallback
│   ├── RefreshState.kt         # In-memory "refresh running" state + debounced indicators
│   ├── cache/                  # Memory → disk → network rate cache (Store5-inspired, no Store5 dep)
│   └── persistence/             # PersistenceKey enum, DataStore delegates, CachedStore / PrefStore, SavedDataFile (the Protobuf files)
├── view/
│   ├── main/                   # MainActivity (the main Activity) + the converter route
│   ├── convert/                # ConvertTextActivity: "Convert currency" in other apps' text-selection menu
│   ├── lock/                   # The optional app lock
│   ├── alert/                  # Rate alert UI
│   ├── tile/                   # Quick Settings tile
│   ├── widget/                 # Glance home-screen widget + its configure activity
│   ├── navigation/             # Screen keys, AppNavigator back stack, AppNavHost (Navigation 3), screen motion, shared pills
│   ├── preference/             # Settings, Fees and Backup routes
│   ├── timeline/               # Chart route
│   ├── cart/                   # Bill-splitting calculator route
│   ├── compose/                # Shared Compose foundation: theme (incl. Motion tokens), top bars (ScreenScaffold), CurrencyPill, UiTestTags, drag-reorder, dialogs, onboarding
│
├── alert/RateAlerts.kt         # Checks rate alerts after each update and posts the notifications
├── ocr/                        # Receipt reading: OcrEngine, ReceiptReader, Tesseract language data (TessLanguage, Tessdata)
├── worker/                     # Background rate refresh (RateRefreshWorker, RateRefreshScheduler)
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

### Multiple API Providers via Enum + Interface

`ApiProvider` is an enum whose entries each implement `Api`, an interface exposing `getRates()` and `getTimeline()`. Switching provider at runtime is a single DataStore write; no factory classes required.

### DataStore as the Persistence Layer

The app has no SQLite database. `SharedPreferences` has been fully migrated to **Jetpack DataStore Preferences**: settings and small state (cached rates, starred currencies, user state, preferences) live in namespaced `DataStore<Preferences>` instances (`PersistenceKey`: `rates`, `last_state`, `starred_currencies`, `prefs`; `timelines` is only read once, to move what's in it into per-pair files, see below), managed by the stores in `repository/` and by `repository/persistence/`. File names deliberately match the old SharedPreferences basenames so that exported backups round-trip across the migration. Each setting is one `Pref<T>` (`repository/persistence/Pref.kt`) declared in its store: `display.hapticFeedback`, `providers.apiProvider`, `fees.fees`, `lastState.recentPairs`, … A `Pref` reads with `get()` (the in-memory snapshot, where a blocking read is unavoidable, e.g. `CurrenciesApplication`'s startup DNS prewarm), writes with `set()` or a read-modify-write `update { }`, and is observed as `flow` / `liveData` (`PrefStore.mappedFlow` / `mappedLiveData` underneath), so a setting's key, default and stored shape live in one declaration (`booleanPref(KEY, default = true)`, `keyedPref(key, decode, encode)`). ViewModels turn one into UI state with `Pref.stateInWhileSubscribed(viewModelScope)`.

What the user saves (the working cart, the saved carts, the trips, the fees and the rate alerts) is a tree of values, not a setting, so it lives in three typed **Proto DataStore** files instead, `carts.pb`, `fees.pb` and `alerts.pb` (`persistence/SavedDataFile.kt`). Their schema is `app/src/main/proto/currencix/saved.proto`; **Wire** generates the Kotlin messages at build time (plain code, no reflection), and `WireSerializer` hands them to DataStore. `SavedProtos.kt` converts between the messages and the app's models, and every read, from these files or from a JSON backup or cart file, goes through the same checks (`SavedValues.kt`: quantities and splits clamped, amounts above zero, an unknown currency, fee type or alert direction skipped, a missing id replaced). Amounts are decimal strings in both formats, so nothing is lost to a float. A file that can't be read starts over empty (`ReplaceFileCorruptionHandler`) rather than failing every read. `CartStore`, `FeeStore` and `RateAlertStore` expose them as the same `Pref<T>`s as before (`fieldPref`, decoded again only when its field of the message changes).

Both kinds of file sit behind one `CachedStore<S>` (`persistence/CachedStore.kt`); `PrefStore` is its preferences flavour, with edits written as `MutablePreferences` changes. Writes update the in-memory cache at once and reach disk through a serial queue; while any of its own writes are still pending, the disk's emissions (each reflecting only the writes finished so far) are ignored, and the cache is resynced from disk when the queue drains, so a read between two quick writes never sees the first one undone (`PrefStoreTest`).

`Database` is a handle on ten stores, each owning one concern, so no class grows past what one screen of code can hold:

| Property | Store | Holds |
|---|---|---|
| `rates` | `RateStore` | The latest rates, and each pair's cached timeline |
| `lastState` | `LastStateStore` | The converter's pair, the recent pairs, a pinned historical date |
| `stars` | `StarStore` | Starred currencies in the user's order; the starred-only filter |
| `providers` | `ProviderSettings` | Main and fallback provider, each keyed provider's API key, whether pickers show unreliable providers, auto-refresh and its interval |
| `fees` | `FeeStore` | Saved fees and the active exchange / bank fee |
| `display` | `DisplaySettings` | Theme, wallpaper colors, decimals, date format, keypad (expanded, side on wide screens), haptics, click sounds, smooth motion, conversion preview, cart totals collapsed, onboarding gate |
| `privacy` | `PrivacySettings` | App lock, private requests |
| `chart` | `ChartSettings` | What the timeline chart draws |
| `carts` | `CartStore` | The working cart, the saved ones and their trips |
| `alerts` | `RateAlertStore` | Rate alerts waiting to fire |

Row summaries and prompt messages (`LedgerLabel`, `PromptMessage`) take a `CharSequence` and keep its links tappable (`rememberLinkedText`): a string resource's `<a href>` links and any web address written out in plain text open the browser, and its bold and italics stay. Callers reach a value through its store: `Database(context).display.getTheme()`. A composable that reads settings without a view model takes `rememberDatabase()` and follows a `Pref` with `pref.collectAsStateWithLifecycle()` (`view/compose/DatabaseState.kt`), which starts from the stored value so the first frame is already right.

Each provider's history for each pair (the chart, the widget's trend line) is a file of its own instead, `filesDir/timelines/<providerId>_<BASE>_<SYMBOL>.json` (`TimelineFiles`, behind `RateStore.getCachedTimeline` / `putCachedTimeline`): up to ten years of daily rates per pair used to share one preferences file, held whole in memory and rewritten on every save. Saving one pair now rewrites that file alone (atomically, through a `.tmp` rename), the last few pairs read stay parsed in memory, and what was in the old `timelines` file moves over on first use. It's under `filesDir`, not `cacheDir`, so the system doesn't throw away years of history to free space. The files are read off the main thread: the timeline's quick first paint from saved history happens inside the refresh job.

Saved data that can't be read back (a rate or a history that doesn't parse) is dropped and fetched again rather than crashing, and counted per store (`DroppedCacheEntries`, logged), so a store that keeps dropping entries, a sign of a write bug, shows in the log.

Each store's first read waits on the disk (`PrefStore.snapshot`), so `CurrenciesApplication` reads every store once in the background as the process starts (`warmUpStores`), moving the old history file over at the same time: by the time a screen asks, the read is done, instead of happening on the main thread mid-frame.

Every write to a store emits the whole store again, and each setting read from it maps that snapshot to its value. A value stored encoded (the cart, the saved carts, the fees and the rate alerts, each in its Protobuf field) is decoded only when its own stored field changes (`LastResult`, in `fieldPref` and `keyedPref`), so typing a price doesn't re-decode every saved cart on the main thread. The cached rates are parsed once per new set, process-wide, on the refresh's own background thread (`RateStore.parsedRates`), not by each screen and widget on each emission. `Currency.fromString` is a map lookup.

The one exception to "DataStore only": a cart can be exported to / imported from an external JSON file via the Storage Access Framework (`CartExporter.kt`), but that's a one-off user-initiated file transfer, not an ongoing persistence layer.

The app's own JSON (backups, cart files, the rate caches on disk, the chart history files) is read and written with Moshi's streaming `JsonReader` / `JsonWriter` through `repository/json/JsonTree.kt`: maps, lists, strings, numbers and booleans, with the lenient field reads (`optString`, `optInt`, …) a hand-edited or older file needs. There is no `org.json` and no Moshi reflection (`moshi-kotlin` / `kotlin-reflect` aren't in the app): provider responses go through KSP-generated or hand-written adapters.

### Three-tier rate cache: memory → disk → network

`repository/cache/` implements a Fetcher / SourceOfTruth / Converter triad inspired by Store5's design (no Store5 dependency — sized for this codebase, ~300–400 lines total). `RateCache.get(key)` walks: an in-memory LRU tier, then a disk JSON tier (`DiskJsonStore`, atomic writes), then the network on a miss — with `InFlightDedupe` collapsing concurrent requests for the same key and `RetryPolicy` backing off transient failures. This sits above the OkHttp-level HTTP cache (`HttpClientProvider`'s 5 MiB disk cache of raw response bytes) — different tiers, different jobs: OkHttp caches transport bytes, `RateCache` caches parsed `ExchangeRates` / `Timeline` domain objects.

### Networking: one OkHttp client; Retrofit for fixed-shape JSON, raw OkHttp for the rest

All providers share one `OkHttpClient` singleton (`HttpClientProvider`) with a disk cache and a per-provider `Cache-Control` rewrite interceptor. Which API sits on top of it is decided per endpoint, by the shape of the payload:

| Transport | Used for | Endpoints |
|---|---|---|
| **Retrofit** (`model/provider/api/*Api.kt`) | Fixed-shape JSON that a Moshi adapter decodes directly | Frankfurter, Open Exchange Rates, AllRatesToday, InforEuro, Bank of Canada, Bank of Israel `PublicApi` (latest rates) |
| **Raw OkHttp** (`HttpClientProvider.fetch`) | Feeds parsed by hand off the byte stream | Norges Bank (SDMX XML), Bank Rossii (XML), Bank of Israel SDMX-JSON (historical rates + timeline) |

The split is deliberate and permanent, not an unfinished migration. Retrofit's value is typed `@Path`/`@Query` binding plus converter-driven decoding. The raw-OkHttp feeds get the first but not the second: the XML ones go through SAX parsers, and Bank of Israel's SDMX-JSON is keyed by dimension-index tuples rather than a fixed schema, so `BankOfIsraelSdmxParser` walks it as a generic JSON tree. Wrapping those in Retrofit would only replace a query string.

Both transports share one error contract, so `ExchangeRatesRepository`'s error handling never needs to know which a provider uses. Non-2xx responses become `ApiHttpError(statusCode)`, network failures propagate as their original exception types, and a body that decodes to `null` becomes an `IOException("<provider>: empty JSON")`. On the Retrofit side this lives in two helpers in `ProviderUtils.kt`: `retrofitApi<T>()` binds an interface to the provider's `baseUrl` on the shared client, and `fetchRetrofit { }` unwraps the call. The interfaces return `Response<T>` rather than the bare body because Retrofit's suspend adapter would otherwise throw an opaque `KotlinNullPointerException` on a null body. Adapters that bail out on a non-object payload must consume it first (`JsonReader.skipIfNotObject()`), since Retrofit's Moshi converter rejects a document with input left over.

`ProviderRequestTest` pins the exact URL every provider sends and this error contract, on either transport, with no network. A provider that changes transport must not change what goes over the wire.

### Moshi + Custom Adapters for Diverse API Formats

Each exchange-rate API returns a different JSON (or XML) schema. Rather than normalising at the network layer, each provider ships its own Moshi adapter (or SAX parser for Norges Bank / Bank Rossii) that maps the raw response to the shared `ExchangeRates` / `Timeline` model.

### Coroutine dispatchers in one place

`AppDispatchers` (`util/AppDispatchers.kt`) holds the IO and Default dispatchers, and the main one (looked up on first use, as a plain JVM has none). Classes the app constructs take one, defaulting to `AppDispatchers.production` (`ExchangeRatesRepository`, `DiskJsonStore`; `HttpClientProvider.fetch` and `retrofitCall` take the IO dispatcher as a parameter), so a test can hand in its own; the ones Android instantiates (the Application, `MainActivity`, the widget receiver) and `PrefStore`'s process-wide scope use `production` directly. Nothing names `Dispatchers.IO` / `Default` elsewhere, which detekt's `InjectDispatcher` enforces.

### LiveData for Reactive UI

ViewModels expose `LiveData<T>` streams. Screens observe them (`observeAsState`) without holding references to the ViewModel, ensuring lifecycle-safety and no memory leaks. Preference changes propagate automatically via `Pref.liveData` / `Pref.flow` (`PrefStore.mappedLiveData { … }` underneath), which bridge each DataStore namespace's `Flow<Preferences>` into a `LiveData<T>` so observers pick up writes without a manual re-read.

A ViewModel's derived streams are made once (`by lazy`), not per call: a screen asks for them as it composes, and a new LiveData per recomposition redid its work every time. The chart's readouts (the change, average, median, minimum, maximum) are worked out on the Default dispatcher, latest request wins, since scrubbing the chart asks for them every frame (`TimelineViewModel.rangeLiveData`).

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

`MainActivity` is the app's one main Activity (the others stand alone: `ConvertTextActivity` over another app, `WidgetConfigureActivity` for a widget). Every screen — converter, timeline, cart, settings, fees, backup — is a destination on one Compose back stack, so there are no Fragments, no XML layouts and no ActionBar (the theme is `Theme.AppCompat.DayNight.NoActionBar`, and the window is edge to edge).

| Piece | Where | Job |
|---|---|---|
| `Screen` | `view/navigation/Screen.kt` | Sealed interface of destinations. Arguments live on the key (`Timeline(from, to)`, `Cart(mainBase, mainDest)`), so a screen can't open without them. Each encodes to one line (`timeline:EUR:USD`) for saved state. |
| `AppNavigator` | `view/navigation/AppNavigator.kt` | Owns the back stack: `navigate` (pops back to a screen already on the stack instead of stacking a copy), `replaceTop` (swaps the top screen without adding history) and `pop`. The converter is always the bottom entry. Saved with the Activity's instance state, so rotation, a theme change (`recreate()`) and process death come back to the same screen. |
| `AppNavHost` | `view/navigation/AppNavHost.kt` | `NavDisplay` from Navigation 3 inside a `SharedTransitionLayout`. Decorators give each entry its own saved-state holder and **its own `ViewModelStore`**: a screen's ViewModel lives exactly as long as its entry, as it did with one Activity per screen. |
| `TwoPaneSceneStrategy` | `view/navigation/TwoPaneScene.kt` | On wide windows, shows a detail screen beside the converter instead of over it (below). |
| Routes | `ConverterRoute`, `TimelineRoute`, `CartRoute`, `SettingsRoute` / `FeesRoute` / `BackupRoute` | Each wires one screen: its ViewModel, top bar and sheets. `SettingsRoute` draws both Settings (`Screen.Settings`) and each of its category screens (`Screen.SettingsPage`). |

What the Activity keeps: the splash hand-off, the XML theme (pure black), foldable posture, hardware-keyboard input for the converter, and the Activity-scoped `MainViewModel` / `PreferenceViewModel`. `ConverterStatus` (banner + error messages) observes for the Activity's lifetime but only shows errors while the converter is on screen — an error that arrives while another screen covers it is held until the user comes back, as when the converter was its own paused Activity.

Things that used to lean on Activity plumbing now live in Compose: the cart's JSON import/export pickers register on the Activity's `ActivityResultRegistry` with fixed keys (so a result that outlives a recreation still reaches the cart), backup uses `rememberLauncherForActivityResult`, and the cart's unsaved-changes prompt is a `BackHandler` enabled only while there are unsaved edits — so an unchanged cart keeps the predictive back animation.

`AppNavigatorTest` covers the key encoding and stack rules; `AppNavHostTest` covers push/pop, system back, per-screen ViewModel clearing and recreation; `MainActivitySmokeTest` walks the real routes and `TwoPaneSmokeTest` the tablet layout (Robolectric).

### Two panes on tablets and unfolded foldables

From Material's *expanded* width (840 dp) the converter stays on the left and the timeline or the cart opens beside it. Each `NavEntry` carries a `PaneRole` in its metadata (`Converter` → `List`; `Timeline`, `Cart` → `Detail`; settings and its pages, fees and backup have none and still cover the window). `TwoPaneSceneStrategy` builds a `TwoPaneScene` when the top entry is a detail and a list entry sits below it; otherwise `SinglePaneSceneStrategy` shows one screen. The converter pane is 40 % of the width, kept between 360 and 480 dp.

- The scene is keyed by the detail entry, so switching detail screens runs the normal open/back transitions on the detail pane while `NavDisplay` keeps the converter in place as a shared entry.
- In two-pane, the converter's shortcuts **replace** the detail screen (`replaceTop`) rather than stacking, so one back always returns to the converter alone. The timeline beside it follows the converter's pair (`TimelineFollowsPair`).
- The converter picks its row (hero | keypad) or column layout from the space it's given (`BoxWithConstraints`), not from the screen orientation, so it stays a column in its pane on a landscape tablet.
- In the column layout the display is as tall as its content, and the keypad takes what's left: its rows are 56 dp when there's room and shrink to 48 dp (`KeypadHeights`) before the display has to scroll. That keeps the recent pairs under the hero card on screen on phones where a full-size keypad would cover them.
- The keypad (`MainKeypad`, shared by the converter and the cart) is three columns of rounded tiles beside an operator rail: C (clear, on the error tone), the brackets as one tile split in two (each half types its own; ")" is dimmed while there's nothing to close), % and delete (a long press clears), over the digits, 0, the decimal point; the rail is one brass strip (`secondaryContainer`) of ÷ × − + with a two-row green = at its foot (`CalculatorInputState.evaluate`: the calculation's result becomes the amount, to carry on from). Keys are in Inter, like the rest of the app; while pressed a tile gives a little and its corners round from 16 to 28 dp (`Motion.snappy`). Five rows; the expanded keypad (Settings → Converter) adds a row of 00 / 0 / 000.
- Keys that would do nothing are dimmed and don't respond, from `CalculatorInputState.keys` (`KeyAvailability`): "." once the number has one (or after ")" or %), % until there's a number to apply it to, ")" while there's nothing to close, = while there's no calculation. Each kind of key has its own vibration: digits a light tap (`KeyboardTap`), operators, brackets, % and delete firmer (`VirtualKey`), = a confirm (`Confirm`), and C or a long press on delete the heaviest (`LongPress`).
- On a screen 600 dp wide or more (an unfolded foldable, a tablet) the keypad keeps to one side at 420 dp, in reach of one thumb, with a button in the space beside it, an arrow pointing to the other side, that moves it across (`DisplaySettings.keypadOnLeft`, right by default).
- Currency pills don't fly between screens on a two-pane window (`LocalTwoPaneWindow`): the converter and the cart are on screen together, so there's nothing to fly between.
- Moving between the single and two-pane layouts briefly composes the converter in both scenes, so `ConverterStatus` counts visible converters instead of keeping one flag.

### Messages: one snackbar, with Undo

Toasts are gone. `AppSnackbar` (`view/compose/AppSnackbar.kt`) is owned by `MainActivity` and drawn once above every screen, clear of the navigation bar and keyboard. Screens reach it through `LocalAppSnackbar`; outside the app shell (previews, screenshot tests) it is null and `showOrToast` falls back to a toast. A new message replaces the one showing rather than queuing.

The cart lists its items as receipt lines (`CartItemRow`): the name, editable in place, and the price at the end (tap it for the keypad); under them the calculation when the price is worked out ("3.25 + 0.50", wrapping when long) and the price in the destination currency, fee-free (`RowConversion`; the totals card adds fees). The rows of a section (pinned, other items) share one card (`RowPosition`: rounded ends, hairlines between), the row being edited is tinted, and an "Add item" row follows the last one. An empty cart says what it's for, that a price can be a sum, and offers "Add first item" (`CartEmptyState`). In the totals card the conversion fee reads in the muted color of the other extras, not red, and a budget shows as a bar too (red once over).

A row has a quantity (1–99, `CartItem.quantity`): the stepper on the row being edited sets it, the price counts it (`evaluateItem`), and the calculation line shows it ("4.50 × 3"). The keypad's **Next** opens the next row's price in the order shown, and past the last row a new row's (`CartHost.nextPrice`). Long-press a row (or use TalkBack's actions) for Duplicate, Move to top (of its section) and Delete. A long name wraps up to three lines; a pasted line break becomes a space (`WrappingNameInputTransformation`). TalkBack reads a row's price in one go, with currency names: "4.50 US dollar, quantity 3, about 16.42 Israeli new shekel". **Shopping list** (the menu; saved with the cart, `SavedCart.shoppingMode`) swaps each drag handle for a tick: a ticked row is crossed off and dimmed, and the totals card adds **In basket**, the ticked items' share of the total (`basketShareOf`, so tip and fees count in proportion). The totals card collapses to the currency row and the total with the chevron by the total (`DisplaySettings.cartTotalsCollapsed`, kept across carts), and says **Rates from <date>** once the rates are older than a long weekend explains (`ratesAreOld`, more than 4 days). The saved-carts list shows each cart's item count, total and date. Share offers an **Image** as well: the PDF's receipt drawn into a PNG as long as the cart (`toPngBytes`, same drawing code as `toPdfBytes`).

Destructive actions don't ask first; they act and offer **Undo**: deleting a cart row (swipe or button) restores it at the same index (`CartRowEdits.restore`), Clear and Import restore the previous cart. Only deleting a *saved* cart, which Undo can't reach once you've left the sheet, still confirms. Removing a recent pair asks first and then offers Undo as well, since a long-press can land on the wrong chip (`LastStateStore.removeRecentPair` returns the list as it was, `restoreRecentPairs` puts it back after any pair used since). Copying an amount shows no message on Android 13+, where the system already confirms copies.

### Prompts: every one is a sheet

The app has no dialogs: every prompt is a bottom sheet built on `LedgerPromptSheet` (`view/compose/dialogs/LedgerPromptSheet.kt`), the prompt's content over a Cancel / confirm row, so they all open, swipe away and go back the same way. That covers the pickers; every "are you sure?" (`LedgerConfirmSheet`: deleting a saved cart, importing a backup over the current settings, removing from history) and "here's what happened" (the fallback provider's explanation); and the prompts that take typed input: a cart's name (`CartNameInputSheet`), a keyed provider's API key (`TextEntrySheet`), the backup passwords (`LedgerPasswordSheet` and the export prompt), a fee (`FeeEditorSheet`, its Delete on the far side of the row), and the timeline's custom date range. The sheet makes room for the keyboard itself.

Every text field is state-based (`TextFieldState`, Compose 1.13): the field owns its text, so a recomposition never moves the cursor, and input rules are an `InputTransformation` (the fee percent field normalizes the decimal separator and trims what's past its limits there). Passwords use `OutlinedSecureTextField`, which blocks copy and cut; they aren't saved across configuration changes and reach the backup code as a `CharArray` copied straight from the field, which is then cleared. Names (a cart, a cart item, a fee) stop at 50 characters (`NameInputTransformation` in `view/compose/TextInput.kt`, which cuts a longer paste to fit). The cart item's name wraps onto more lines, so its field also turns a pasted line break into a space (`WrappingNameInputTransformation`). A field with no visible label of its own (the cart item's name, the fee editor's name and percent, a sheet's single entry field, the language search) gives TalkBack its name as the field's hint (`fieldName`, in `view/compose/AccessibilitySemantics.kt`). An entry that can't be used says why under the field (`fieldMessage` / `invalidUnless`, `view/compose/FieldMessage.kt`: "Enter a number greater than 0", "Enter a percentage above 0, up to 100"), which TalkBack reads with it, not only by turning the outline red.

A row with a switch (settings, the fee sheets, graph options, the historical-date toggle) is one control for TalkBack: the row carries the switch's role and state (`switchState`), and the `Switch` inside has no click of its own (`onCheckedChange = null`), so it reads once, "title, switch, on". A greyed-out row without an action says it's disabled too (`LedgerRow`).

### Settings

Settings is a list of categories, each a screen of its own like the fee manager, with a chevron on every row that opens one (`NavigationRow`): **Converter** (fees, keypad, decimal places, conversion preview), **Exchange rates** (provider, fallback, key, auto-refresh and how often, rate alerts, and **About the providers** under it), **Look & Feel** (display; sound & touch), **Exchange rate chart** (the chart's switches, the same as its options sheet), **Receipt languages** (see Receipt scanning), **Backup & Restore**, **Security & privacy** (app lock; private requests, see security.md), and **About this app** (version, changelog, credits under it, disclaimer). The pages are `SettingsTopic`s, a topic with a `parent` opening from that topic's screen; each is one `Screen.SettingsPage` on the back stack, saved as `settings:<id>` (plain `settings` stays Settings itself, so a stack saved before still restores). `SettingsPages.kt` lists each page's sections; the picker sheets they open are shared (`PreferenceDialogsHost`). The converter's drawer has **About** too, straight to that screen.

### Receipt scanning

**Scan receipt** (the cart's menu) takes a photo with the system camera app (`TakePicture`, into `cache/receipts/` through the `FileProvider`) or picks one (`PickVisualMedia`); neither needs a permission, so the app still has none for the camera. The photo is decoded at most 2400 px on its long side, turned upright from its EXIF orientation, and read on the device by Tesseract (`readReceipt`, `ocr/ReceiptReader.kt`) in single-column mode, off the main thread. `ReceiptParser` turns the text into items: a line ending in a price (or starting with one, as a right-to-left receipt read as English does) is an item named by the rest of the line; a quantity line with no words takes the name above it; totals, tax, payment, change and discount lines are dropped by their usual words in the app's languages. The review sheet lists them all ticked; the ticked ones are added as rows (`CartRowEdits.add`).

Where the recognizer lives depends on the build (`OcrEngine`, one `ocrEngine` per flavor). The F-Droid app carries it: `:ocr-engine` (Tesseract4Android behind `TesseractRecognizer`) is an `fdroidImplementation` dependency. The Play app doesn't: the recognizer and its ~7 MB of native code per CPU type are the on-demand feature module `:ocr`, so people who never scan don't download them. The first scan offers the download (`ReceiptSetupOfferSheet`, below), then `installReceiptReader` runs Play Feature Delivery's install (`SplitInstallManager`, with Play's own confirmation dialog when it asks for one on mobile data) and downloads English like any other language, showing progress (`ReceiptSetupProgressSheet`); once both are there, the photo choice follows. The recognizer is then loaded by name without a restart (`SplitCompat.install`, `SplitInstallHelper.loadLibrary` for Tesseract's four native libraries, then `TesseractRecognizer` by reflection, since the base app can't link against a module it may not have). Closing the progress lets the install carry on; a failure says so in a snackbar. The feature module is declared, and built, only with an app bundle: AGP can't make the per-CPU APKs with a feature module, and only Play installs features.

Languages: none ships with the app, and none is downloaded without the user's say. The first scan in either app offers what reading needs (`ReceiptSetupOfferSheet`), each item named with its size (`TessLanguage.bytes`, in the system's size format): the Play app's text reader and English data; the app language's data (`TessLanguage.forLocale`), when it hasn't been asked about, is a separate tick there, left unticked. With the reader and English here, an app language not yet asked about gets its own question (`ReceiptLanguageOfferSheet`: Download or English only, closing it meaning no). Each answer is remembered (`PrivacySettings.receiptLanguagesAllowed` / `receiptLanguagesDeclined`), so a language is asked about once; after a no, the review sheet offers it again ("Read Hebrew too"). Beyond the app's own languages, more can be added for reading receipts abroad (Korean, Japanese, Thai and 50 others; 85 in all, `TessLanguage`), in **Settings → Receipt languages** (`ReceiptLanguagesScreen`, `ReceiptLanguagesViewModel`): the languages on the phone or downloading, each with its size and how long it's kept; a tap changes that, the bin deletes it (`OcrLanguages.delete`: the file and any download, the app language then counted as said no to, English asked for again with the next scan), and **Add a language** offers a searchable list (by its name in the app's language, in English or in its own), then asks to download the one picked, naming its size, kept for good or **until a date and time** the user picks (`KeepLanguageSheet`, `KeepUntilSheet`; a trip's end). Those times are kept in `ReceiptLanguageSettings.keepUntil`; `TessdataExpiryWorker` runs once at the soonest of them and deletes what's due (`OcrLanguages.deleteExpired`, also run before every scan and when the screen opens, so a late run never leaves an ended trip's language in use), then schedules the next. Receipts are read in the app's language first, then the others added, then English (`Tessdata.languages`, "heb+kor+eng"). A language agreed to downloads in the background like the rate refresh (`TessdataDownloadWorker`: WorkManager, a network constraint, backoff, the shared OkHttp client without its call timeout and cache), from tessdata_fast's 4.1.0 tag, and kept only when its SHA-256 matches (`Tessdata.install`). Until it's there, scans read English only and the review says names will read better once it's done; after, they read "heb+eng".

### Cart pins and drag order

Pinned rows sit above the rest. Storage keeps the user's own order, and the pinned-first order is only applied on screen (`CartItemsList`), so unpinning a row returns it to its old place. Pinning or unpinning re-sorts the list at once.

Once something is pinned, the list shows two sections, **Pinned** and **Other items**, under the same brass headings as Settings (`LedgerSectionHeader`); with nothing pinned it's one plain list. Each section reorders on its own. A row never swaps with one from the other section (`moveByKey`, matched by key because the headings sit between rows), so to move a row across, pin or unpin it. On drop, `CartRowEdits.commitDrag` moves only that row in storage (`afterDrop`), just before the next row of its section on screen, and every other row keeps its slot.

### Converter conveniences

- **Fallback provider**: when the rates on screen came from the fallback provider, the status pill says so ("Bank of Israel unavailable • Using Frankfurter.app"); tapping it opens a sheet explaining that the next refresh tries the main provider first again, with a button to change provider (`BannerContent.explanation`).
- **Rate age**: the footer timestamp reads "5 min ago" / "Yesterday" while recent (within 24 h for providers with a time, 7 days for date-only ones) and ticks every minute; long-press shows the full date and time, tap still opens the provider picker.
- **Percent fields** (`PercentTextField`: the fee editor's percent, the cart's tip): laid out left to right in every language, the number from the left and "%" at the field's right edge, where Hebrew would otherwise put it on the left. The fee list's "0.5 %" is isolated left to right for the same reason (`feeSummaryWithInactive`).
- **The pair's pills** (`CurrencyPairRow`): every place that shows the converting pair shows it the same way, two currency pills (flag, code, chevron; a tap picks that side's currency) with the round swap button between them (`SwapFab`: half a turn per tap, a long-press for the screen's own use, labelled for TalkBack). The converter's hero card, the cart's footer, the rate chart (under its bar; its pills pick the chart's currencies, `TimelineViewModel.setCurrencies`, and swap waits out a refresh or an error) and Quick conversions use it. It lays out left to right in every language, "from" on the left, even inside a screen or sheet that follows Hebrew's direction. Its pills are shared elements, so opening the cart or the chart from the converter flies them across; Quick conversions turns that off (`sharedElement = false`), as it sits over the converter's own pills.
- **Recent pairs**: a pair that stays on screen for 2 s is recorded (`RecentPairs`, newest first, at most 6, stored as `USD:ILS,EUR:USD` in `last_state`). A pair and its swap count as one. Chips under the hero card (not with the expanded keypad, whose extra row leaves no room for them; the gap above where they'd start stays, so the keypad starts there and never touches the card; pairs are still recorded) switch to the others in one tap (each `🇸🇪 SEK → 🇮🇱 ILS`, the row laid out left to right in every language, also where it sits in a sheet that follows Hebrew's direction, such as Quick conversions; the long-press menu follows the language), through `MainViewModel.setCurrencyPair`, which writes both sides at once (the swap button uses it too). A long-press on a chip asks whether to remove the pair from the history (`RemoveFromHistorySheet`, with the chip's flags); after removing, the snackbar offers Undo. While the card shows a status pill (offline, unreachable, the fallback, a past date) the chips are compact (26 dp instead of 36, with less margin), giving back the height the pill takes so they stay clear of the keypad.
- **First-run pair**: into the currency of the phone's region (its system setting, not the app's language), from USD, or from EUR where USD is home; without a region the app knows, USD → EUR (`HomeCurrency`). It's the stored pair's default, so it applies until the user picks one.
- **Compare cards**: with two or more bank / card fees, a "Compare cards" button sits beside the fee stamp. Its sheet lists each card priced for the amount on screen, with the pair's own fees and the exchange fee in force, cheapest first, "Cheapest" and "+8.17 ₪ more" beside them; the card in use is ticked, and tapping another makes it the one in force (`compareCards`, `CompareCardsSheet`).
- **Paste amount**: while the clipboard holds text copied in the last 10 minutes by another app, a "Paste amount" chip leads the recent pairs. Only the clip's description is read until the chip is tapped (so Android shows no "pasted from clipboard" notice for merely opening the app); the tap reads it, `PriceParser` finds the price, its currency becomes the "from" side (swapping when it's the "to" side, `pairForPaste`), and the amount is typed in. A clip is offered once, and the app labels its own copies so they're never offered back (`ClipboardAmount.kt`). The clipboard is always taken from the application context (`clipboardOf`): a `ClipboardManager` keeps the context it came from, and the system keeps the manager (through the binder it registers for change listeners) after the listener is gone, so one taken from the activity held every destroyed `MainActivity`.
- **Currency picker**: while the list is unfiltered, the recent pairs' currencies show as chips above it (minus the one on this side and the one it can't be). A long-press on one asks whether to remove it; since the chips come from the recent pairs, that removes every recent pair using the currency. Search matches the code, the name, and **the countries that use the currency** ("Japan" → JPY, "Germany" → EUR), in the app's language and in English (`CurrencyCountries`, built once per language from the platform's locale data).

### Outside the app: text selection, shortcuts, widget

- **Convert currency** in any app's text-selection menu (`ConvertTextActivity`, `ACTION_PROCESS_TEXT`): a sheet over that app converting the selected price with the cached rates — instant, no network. `PriceParser` finds the amount and currency ("€49.99", "49,99 €", "USD 1,234.56"); a shared symbol ("$", "kr") prefers the user's own currencies, codes count only in capitals, and letter symbols only as whole words. The price converts into the converter's destination, or back into its base when it's already in the destination (`targetCurrency`). Copy, or open the converter on that pair and amount.
- **Launcher shortcuts** (`AppShortcuts`): the top three recent pairs and the cart, kept in step with the recent pairs. Dynamic, since a static shortcut needs a fixed package name and the debug build's differs.
- **Quick conversions** (`QuickConversionsSheet`, from the drawer): the converter's pills and swap button at the top (a pill opens the converter's own currency picker, `ConverterCurrencyPicker`, over the sheet), then the pair at round amounts (1 to 1,000), plus the amount typed in the converter as a tinted row in its place among them (and 2,000 when that makes an odd number, so the tiles pair up), as tiles, two to a line (`QuickConversionsTiles`): each the amount in its currency ("$1"), then, with a fee in force, the result and the fee ("₪3.09 +1%") over the big total with the fee (the converter's True Cost, result × fee stack), as the converter's receipt has them; without a fee the result is the big number. The pills head them, so the tiles need no header; the totals shrink to fit a tile when long; the results round to the user's decimal places (Settings → Converter), as the converter's result does. Each tile keeps its full line, `ILS ₪ 1 = USD $ 0.32   +1% = USD $ 0.33`, for copying, sharing and TalkBack. A tap on a tile puts its amount into the converter and closes the sheet; a long-press copies its line. The recent pairs sit on top as chips to switch pairs without closing; under the rows, where the rates come from ("Based on <provider>, <date>") and, in the error color, the converter's banner when there is one (offline, provider unreachable, the fallback in use, a past date). Share sends the pills, the tiles and that footer as an image (`captureInto` on the table, drawn on the sheet's surface) with the same as text (`shareImageOrText`, which the converter's share uses too).
- **Quick Settings tile** (`RateTileService`): the converter's pair and its rate from the cached rates ("USD → ILS", "3.6512"); a tap fetches fresh rates (a one-off `RateRefreshWorker` run, `RateRefreshScheduler.refreshNow`), and the worker asks the tile to redraw once they're stored; a long-press opens the app, through MainActivity's `QS_TILE_PREFERENCES` filter. The same on every Android version.
- Both go through `ConverterLaunch`, the app's intents for "the converter on this pair / amount" and "the cart". MainActivity is exported, so every extra is validated and anything unreadable is dropped.
- **Widget**: each widget follows the converter's pair by default or keeps a pair of its own (`WidgetConfigureActivity`, opened from the widget's reconfigure action), stored in the widget's Glance state. That activity isn't exported: a launcher opens it through `AppWidgetHost`, where the system starts it on the launcher's behalf, and since configuration is optional a launcher that can't do that still gets a working widget. From 260 dp wide it draws a 30-day trend line from the cached timeline (green rising, red falling). Two round buttons at its end: **×1 / ×10 / ×100 / ×1K** steps the amount it converts (`WidgetAmounts`, kept per widget in its Glance state), and **⇄** swaps its pair, which from then on is the widget's own (the converter's pair is untouched); both run as Glance `ActionCallback`s. Tapping the rate opens the converter on its pair and amount. On Android 15+ the widget picker shows the widget itself, with the converter's pair and cached rate, instead of the static `previewLayout`: `CurrencyGlanceWidget.providePreview` draws it and `publishWidgetPreviews` (started by the Application) republishes it after the rates or the pair change, at most once an hour since the system rate-limits these updates.

### Cart extras: tip / tax, split, budget

A cart carries `CartExtras` (saved with it; older carts load without). **Tip / tax** is a percentage on top of the items, applied before fees since a card's FX fee is charged on everything paid; **split** shows each person's share of the total; **budget** (destination currency) shows what's left, or how far over in red. Edited in one sheet from the cart's menu or by tapping those footer rows, and included in the shared text. Above the total, the footer's breakdown shows each addition the same way, its rate by the label and the amount it adds with a plus: "Tip / tax (10%) +5.00 $", "Conversion fee (1.00%) +1.20 ₪" (`cart_tip_row`, `cart_fee_row`).

**Split by person** (same sheet): add who's paying (`CartExtras.people`, up to 12), then pick who each item is for from its long-press menu (**For…**, `CartItem.personId`; TalkBack gets "For Dana" actions). Each person pays their own items plus an equal part of the shared ones (no one's, or someone since removed), as a share of the subtotal, so tip and fees count in proportion (`personSharesOf`). The totals card lists each person's share under the total in place of the even split, and the shared text adds a line each. Removing someone returns their items to shared (`CartViewModel.setExtras`).

**Trips**: in the saved-carts sheet, carts can be grouped into trips (`Trip`: name, home currency, optional budget; `CartStore.trips`, `SavedCart.tripId`). Each trip's header shows what its carts come to in the home currency, at today's rates and with the card fees in force for paying in each cart's currency from home (`summarizeTrips`), against the budget with a bar; carts in no trip follow under "Other carts". A cart's 🧳 button moves it into a trip, out of one, or into a new one (`TripSheet`); a trip header's ✎ edits or deletes it (its carts stay). The saved cart's trip is authoritative: saving the working cart over it keeps the trip (`CartStore.saveCart`). Trip operations live in `CartTrips` (`viewModel.trips`).

### Timeline states

The statistics are a 2×2 grid (max/min, average/median) of tonal tiles, with "—" until data arrives, and the change since the start of the range as a signed percentage with a trend arrow ("−7.55 %", a true minus sign). Scrubbing the chart shows a bubble with the date and rate over the point; once the finger lifts, a **Convert at <date>** chip under the chart opens the converter on the pair at that day's rates (the historical-date mode), going away with a new pair or period. The chips sit in a strip of their own under the chart card (`ChartDayActions`), never over the line or the bubble, one chip tall even before a touch (when it says the chart can be touched) so the chart doesn't resize, and outside the card, so a shared chart doesn't carry them. The top bar keeps swap as an icon, and puts the rate alert, share and graph options under ⋮, leaving the pair its room in the title. TalkBack hears the chart as a sentence instead of a line: "Up 2.37%, from 0.9153 euro on 13/09/25 to 0.9370 euro on 13/09/26, high … low …" (`timelineSummary`). When the provider can't deliver a timeline, the chart card explains why and offers **Try again** and **Change provider** (the provider picker, which retries on pick) instead of an empty chart. The bar's title shows the requested pair right away, not only once data has loaded.

Periods: week, month, year, **5 years**, and **Custom**, all buttons in one row (Custom opens Material's date-range picker, back to `TIMELINE_MAX_YEARS` = 10 years, and tapped again reopens it to change the dates; the chosen dates show as the first and last rates' dates above). The view model filters one cached window per pair and asks the repository for older history only when a span starts before what it has fetched (`fetchCovering`); the repository then fetches only what its cached window lacks, the tail and any missing head a year at a time, showing each piece as it lands (`timelineFetchRanges`, see api-providers.md), and keeps cached history up to 10 years instead of trimming it to the last year. A span with no rates once loading is done (a weekend, or dates before the provider's history) shows a "No rates for these dates" notice in place of the chart (`isRangeEmpty`), and every statistic handles the empty set rather than assuming a first and last rate. **Share chart** (top bar) sends the chart card as a PNG, captioned with the pair, the dates and the source: the card draws through a `LayerCapture`, the same snapshot mechanism the converter's share uses for the hero card.

### Colors: paper and ink, or Material You

Settings → Look & Feel has **Wallpaper colors**, on by default, which swaps the brand palette (paper and ink, bill-green accent) for a palette built from the home screen wallpaper's main colour (`WallpaperColors.kt`: `WallpaperManager`'s colours, Material's tonal-spot palette from `material-color-utilities`, as Android builds its own), kept up to date as the wallpaper changes. Not the system's `dynamicLight/DarkColorScheme`: on some phones (MIUI / HyperOS) those follow the phone's theme, not the wallpaper; they're the fallback only when the wallpaper has no colour to give (some live wallpapers). The seed travels in `LocalWallpaperSeed`, so nested themes don't ask the wallpaper again. `MainActivity` passes the preference to `AppTheme`, which provides it through `LocalDynamicColor`, so the nested `AppTheme` inside sheets keeps the same palette. Pure black keeps its black background either way. One-off brand accents that aren't scheme colors (the fee stamp, status pills) stay as they are.

Every role pair of the brand palette meets WCAG AA (`theme/Color.kt`): 4.5:1 for text on each surface it's drawn on, 3:1 for outlines and controls. In light, bill green and brass are too pale to read as text on paper, so where they are read (buttons, links, focused fields, headings, the "=" key and the pills they fill) the scheme uses darkened ones, `GreenOnPaper` and `BrassOnPaper`, with white on them; the brand tones stay in the mark (`BrandGreenOnPaper`). The muted text, the outlines and the error reds of both themes are set the same way. The fee stamp takes its ink from the surface (`stampInk`), dark red on paper and a light one on ink. A tile tinted brass (the typed amount in Quick conversions) draws its secondary lines in its own text color (`onSecondaryContainer`), not the muted gray.

### One look on every screen

The screens share one set of tones and one typeface, so the converter, its sheets, the chart and the cart read as one app. Everything a person taps or reads off a block (the currency pills, the period and tip buttons, the recent-pair chips, the chart's statistics, the quick-conversion tiles) sits on the same tone, `ColorScheme.tile` (`surfaceContainerHighest`), which stands out on the white cards and sheets as well as on the paper page; a tile that stands for the typed amount is tinted brass instead. Type is Inter throughout (Space Grotesk is the logo's alone), with the two roles beyond Material's scale named once in `theme/Type.kt`: `LabelFamily` for section titles, a field's name, the fee stamp and the sum being worked out, and `AmountFamily` for amounts, which also use `TABULAR_FIGURES` so digits line up and don't shift as they change. A screen that needs a label or an amount takes these rather than a typeface of its own.

### Top bars: Material 3, per screen

Each screen draws its own Compose top bar. The converter's (`ConverterTopBar`) is a small bar: the drawer button (the hamburger ↔ arrow morph, drawn from `DrawerArrowDrawable` and driven by the drawer's offset), the Currenci× wordmark, and four shortcut icons. The drawer slides in under it, so the arrow that closes it stays in sight and in reach; the shortcuts stay usable too and, like the drawer's entries, close it on the way. Back (button or gesture) closes an open drawer instead of leaving the app. Every other screen uses `ScreenScaffold`, by default with a **medium** bar (large title under the actions) that collapses into the small one as content scrolls under it, taking the raised surface tone once collapsed; short windows (a phone in landscape) always get the small bar. The timeline is the exception: it keeps the small bar (titled "Exchange rate chart") so the chart gets the height, with its pair under the bar as the converter shows it; the cart's bar has Save as an icon of its own (`CartBarActions`), and its overflow menu (`TopBarOverflowMenu`), holding Save as and the rest, has icons, with Clear set apart in the error color.

Shared Compose foundation lives in `view/compose/`: `AppTheme.kt` (Material 3 theme, light/dark/OLED, optional wallpaper colors — the app dropped `com.google.android.material` in favor of Compose Material 3 + appcompat-only chrome), `theme/Motion.kt` (motion tokens, below), `theme/Spacing.kt` (the spacing scale every padding and gap takes its value from; files name their own spacing constants, `ROW_GAP = Spacing.Small`, and keep sizes, radii and strokes local), `FlagSize` + `CurrencyFlag` in `ComposeCommon.kt` (the three sizes flags are drawn at), `SettingsList` (every settings screen's inset list), `SheetListEnd` (the breather after a picker sheet's last row), shared common components, drag-reorder, plus `dialogs/` and `onboarding/` subpackages.

**Refresh rate.** On a screen faster than 60 Hz, the app asks for its fastest mode at the current resolution while `MainActivity` is up (`Window.preferFastestRefreshRate`, `util/RefreshRate.kt`), since some phones' automatic modes (Xiaomi's among them) hold an app at 60 Hz unless it asks. It's asked again on every start, as a fold can change the modes on offer; battery saver and heat limits still cap it. Settings → Look & Feel → **Smooth motion** (`DisplaySettings.highRefreshRate`, on by default, shown only on such screens) hands the choice back to the system.

Taps give two kinds of feedback, each with its own switch in Settings. Haptics come from the app's tap helpers (`util/HapticUtils.kt`), skipped in battery saver. The click sound is Compose's own: since 1.13 a clickable plays the system click when the phone's touch sounds are on, and `AppTheme` turns that off with `SoundEffectOnInteraction` when the user switches off **Click sounds**. Each activity's root `AppTheme(display)` follows both settings live, and a sheet's nested `AppTheme` inherits them (`LocalClickSounds`, like `LocalDynamicColor`).

A choice of one from a few short options is a row of Material 3 Expressive connected toggle buttons (`ConnectedToggleGroup`, `view/compose/ConnectedToggleGroup.kt`): the timeline's periods (Custom among them, opening the date picker instead of picking at once) and the cart's quick tip rates. The checked button keeps its shape (Material's default would round it fully) and takes the accent color; labels shrink to fit rather than being cut off at large font sizes: down to 10 sp at the default size, divided by the font scale, so at 200 % "Custom" and "5 years" still fit whole. The caller decides what tapping the checked one does: a period stays picked, a tip rate clears.

Every UI icon is a Material Symbols **Rounded** glyph (weight 400, outlined, 24 dp) stored as a vector drawable `res/drawable/ic_*.xml` and drawn with `painterResource`, so the app has one icon style. The drawables are generated by `art/material-symbols/generate.py` from the `@material-symbols/svg-400` package; to add an icon, add a row to its table and rerun it (the app doesn't depend on `material-icons-extended`). The converter's morphing hamburger (`DrawerArrowDrawable`) is tuned to the same Symbols geometry: 1.5 dp round-capped bars.

Feature icons (`featureIconColor`) are the top bars' shortcuts (`TopBarAction`), the drawer's entries, and every settings and ledger row with an icon. With **Wallpaper colors** on (`LocalDynamicColor`), their line takes the theme's primary, with no tile behind it, so they follow the wallpaper in light, dark and OLED; where that primary is too pale to read against the background it's moved toward the text color just far enough to reach WCAG's 3:1 for icons (`readableOn`). With it off they stay in the plain icon colors of paper and ink. Controls are always in the plain content color: back, ⋮ and its menu, the hamburger, and inline actions (add, remove, close, edit, delete, check, chevrons).

### Brand mark

The app has one mark: two arrows swapping around a "×", the green one over the brass one. It is the launcher icon, the themed (monochrome) icon, the splash screen, and the × of the converter's top-bar wordmark ("Currenci" + the mark, `Wordmark` / `BrandMark`). `art/brand/generate.py` draws it and writes its paths to `res/values/brand_mark.xml`, plus the SVGs in `art/brand/`, the store icon, and the README's animated mark (`currencix-mark-light.gif` / `-dark.gif`: the splash animation in the splash's colors, on GitHub's light and dark page backgrounds); every drawable reads the paths from there:

- `ic_launcher_foreground` (on `ic_launcher_background`, ink) and `ic_launcher_monochrome`, in `mipmap/ic_launcher`. The mark stays inside the adaptive icon's 66 dp safe circle, so no launcher mask crops it. Debug builds draw the mark's × in red (`debugMarkCross`) everywhere it appears, to tell them apart: the launcher icon and splash through `src/debug` colour overrides (`values` and `values-night`), the top bar through `BuildConfig.DEBUG` in `BrandMark`.
- `brand_mark`, the splash icon, in the `brandMark*` colors: darkened green and brass on paper, the brand tones on ink (`values-night`), over `splashBackground`, the app's own background. `brand_mark_animated` keeps turning its arrows half a turn at a time (`animator/brand_mark_spin`), so green and brass swap places, until the splash fades. Under it the splash shows the app's version (`splash_version`): the splash only takes a drawable there, so the `GenerateSplashVersion` task in `app/build.gradle.kts` draws "v<versionName>" in Space Grotesk as an xxxhdpi PNG, light and dark (`drawable-night`), for each variant at build time (as a vector, its glyphs would be far too long a path) (a debug build's version carries its commit).

The website (`docs/*.html`, GitHub Pages) uses the same mark, palette and type: paper and ink following the system's light or dark mode, Space Grotesk and Inter served from `docs/fonts/`, and the app's icon style, Material Symbols Rounded, as one sprite (`docs/icons.svg`) generated with the favicon by `art/website/generate.py`. Its pages show working pieces of the app rather than describing them: the converter's receipt (a price, an example rate and a card fee), the converter with a keypad that calculates, and the cart and the providers as boards of rows to open (`docs/site.js`, which sends nothing anywhere). Its pictures are crops of the app's own screenshot tests (`docs/img/`, from the Roborazzi output). Nothing on it loads from another site. `FontFilesTest` checks that every bundled font, the app's and the site's, is a font file.

The splash holds until Compose has drawn the wordmark; then the mark fades (`MainActivity.fadeOutSplashIcon`) while the wordmark's mark turns into place (`WordmarkReveal`), once per cold start. In the top bar the mark is drawn in Compose from the same paths (`BrandMark`) and moves by half turns (`MarkTurns`, `Motion.turn`): into place on launch, for as long as rates refresh, and when tapped, which refreshes the rates; it always comes to rest green on top, as on the icon.

Every currency flag renders through `Currency.flagPainter()` (`painterResource` over `Currency.flagRes`), which parses each vector flag once, caches it app-wide, and draws it crisply at any size. Don't wrap flags in an `AndroidView`/`ImageView` or rasterise the `Drawable` to a bitmap. The currency picker's 173 rows used to do the former, and it cost scroll smoothness.

`UiTestTags` are stable semantics tags on the elements the `:baselineprofile` journeys drive (keypad keys, pills, drawer rows, picker list, onboarding Skip). They're exposed as resource ids via `testTagsAsResourceId` on `AppNavHost` (every screen) and on the popups that have their own window. They carry no visual meaning. Keep them in sync with `baselineprofile/.../UiTags.kt`.

### Motion: shared tokens, springs for anything interruptible

All UI timing comes from `view/compose/theme/Motion.kt`, so "make it snappier" is a one-file change. Durations sit on the Material 3 scale: short 120 ms (value fades), medium 180 ms (entrances, onboarding steps), long 600 ms for a half turn of the logo's arrows (launch, refresh) and for a chart's line drawing in, and a 20 ms stagger — kept on the quick side of Material's ranges. There are two springs, both stiffer than Material's medium (2500): `snappy` for tap-driven motion (the swap button's flip) and `settle` for panels (the Cart keypad). Anything the user can re-trigger mid-flight uses a spring, because an interrupted spring keeps its velocity while a restarted tween visibly kinks. The navigation drawer is the one panel not on `settle`: it moves on Material's own drawer motion, because `DrawerState.open()` / `close()` take no animation spec (the spec-taking `animateTo` is deprecated). Content pacing (the loading shimmer, the rate pill's auto-scroll) isn't a transition and keeps its own constants.

Moments the motion marks:

- **Swap.** The two currency pills (converter and cart) trade places: each is drawn at the other's spot from the first frame and slides home on `settle` (`rememberPillTrade` in `CurrencyPill.kt`, a layer translation, mirrored right-to-left). The timeline's title rolls over to the reversed pair (`SwappingTitle`); any other change of pair just fades.
- **Chart.** A new pair or period draws the line in from the start edge over 600 ms (`Motion.draw`, see the timeline section).
- **Refresh.** When an update moves the converter's rate for the same pair, its line turns green (up) or red (down) and fades back over 1.4 s (`flashOnChange`); a new pair or the first value doesn't flash.
- **Cart total.** The total counts to its new value over 450 ms when an item is added or a price changes (`countTo`), and jumps when the currency changes.
- **Rate alerts.** A recent pair with an alert waiting carries a small bell (TalkBack adds "Rate alert set"); the bell swings once, from its top, when the alert is new.

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

The timeline chart is rendered by [Vico](https://github.com/patrykandpatrick/vico) (`com.patrykandpatrick.vico:compose`), drawn straight into the timeline screen's Compose tree (`TimelineRoute` → `TimelineChart`). `TimelineChart.kt` is a `@Composable` that observes the ViewModel's `LiveData` streams via `observeAsState()` (bridged by `androidx.compose.runtime:runtime-livedata`) and drives a `CartesianChartHost` backed by a `CartesianChartModelProducer`.

The chart draws at most two points per pair of pixels (`thinForWidth`: each bucket's low and high, in order), so the 5-year view's ~1,800 days are drawn, hit-tested and scrubbed as a few hundred points with every peak and trough kept; the chart is measured before it's first drawn so it's never handed the whole series. When a different stretch of history arrives (a new pair or period) the chart draws itself in from the start edge (`drawIn`, `Motion.draw`), a clip on its layer, so it costs no recomposition.

Why Vico over the previous engine (SparkView): SparkView is unmaintained and required a hand-rolled adapter, a manual dashed baseline `Paint`, and custom scrub handling. Vico ships all of that as first-class API (`HorizontalLine` decorations, `CartesianMarkerVisibilityListener`) and is actively developed. This was also the entry point that first introduced Jetpack Compose to what was then a View-only app; the whole UI is Compose now (see above).

Behavior preserved: dashed reference line at the last value, scrub-to-past-date via marker-shown callback, theme-aware colors taken from the Compose `MaterialTheme.colorScheme` and passed into the composable.

### Graph options: user-tunable chart chrome

The chart's `Pref<Boolean>` settings — grid, X-axis labels, Y-axis labels, highlight-extremes and highlight-period-change — flow as `liveData` from `Database.chart` (`ChartSettings`) through `TimelineRoute` into `TimelineChart`. All default to `true` so first-run appearance is unchanged. Inside the composable each toggle swaps a Vico component for `null` (e.g. `guideline = if (showGrid) rememberAxisGuidelineComponent() else null`); Vico treats `null` as "don't draw," so no branching in the layer definitions is needed.

### Application subclass prewarms DNS for the selected provider

`CurrenciesApplication` is registered via `android:name=".CurrenciesApplication"` on the manifest's `<application>` tag. Its first job is to resolve the currently-selected `ApiProvider`'s host on a background daemon thread during `onCreate()`, so the first exchange-rate request doesn't pay for DNS.

The preference read (`Database(this).providers.apiProvider.get()`) and the lookup through the shared client's own resolver (`HttpClientProvider.client(null).dns.lookup(host)`, so the answer lands in the cache its requests use: `AndroidDns` on Android 17+, for Encrypted Client Hello, the system resolver before) both run **inside** the background thread — DataStore's synchronous snapshot read and DNS resolution are both blocking I/O and neither belongs on the main thread during app startup. Failures (offline, DNS outage) are swallowed with `runCatching`; this is a best-effort warm-up, not a health check.

`ApiProvider.getHost()` (a narrow accessor over the enum's `private implementation.baseUrl`) exposes only the hostname to callers, so the `Application` never touches the full base URL.

It also warms up the saved stores in the background (see the DataStore section), installs the crash recorder (`ProblemReports`, below), and, in debug builds, StrictMode (`installStrictMode`, `src/debug`; a no-op in release): disk and network work on the main thread, and leaked closeables, receivers and activities, are logged (`StrictMode` tag, in logcat and the on-device log file), not crashed on.

### Crashes and freezes, kept on the phone

The app sends nothing about itself anywhere. Instead, **About this app → Report a problem** shows whether it crashed or froze lately and, when it has, shares the details through whatever app the user picks (`ProblemReports`, `problems/`): the app's version, the Android version and phone model, then each problem. Crashes in the app's own code are written down as they happen by an uncaught-exception handler that then hands the crash on as before (`filesDir/problems/crash_<time>.txt`, the latest five kept); freezes ("app not responding") and crashes in native code come from Android's own record of why the app's process ended (`ApplicationExitInfo`). The row is dimmed while there's nothing to share.

### Slow or failing providers

The latest rates come from the main provider and the fallback at once (`ExchangeRatesRepository.fetchRates`); the main one's are used when it answers. Two things keep a struggling main provider from holding the screen. After it fails, it's passed over for the fallback for ten minutes, or until it answers again (`ProviderCooldown`, in memory). And on a refresh someone is waiting on, if it hasn't answered in 10 s (`MAIN_PROVIDER_PATIENCE`) the fallback's answer is used, the main provider's request carrying on in the rate cache's scope for next time. The background refresh, which nobody waits on, gives the main provider the network's full time limits.

### Background rate refresh via WorkManager

`androidx.work:work-runtime-ktx` schedules background refresh of exchange rates: just after each publication for a provider that publishes at a known time (`PublicationSchedule`), or every `RefreshInterval` the user picks (an hour by default) for one that updates many times a day — see `worker/RateRefreshScheduler.kt`. Off by default; opt-in via Settings.

### Rate alerts

"Tell me when 1 USD is 3.50 ILS or less": a `RateAlert` (pair, threshold, `AlertDirection`) set from the timeline (**Rate alert** in the top bar's ⋮ menu at today's rate, or **Alert at <rate>** beside **Convert at** for a touched day) or from a recent pair's long-press menu (**Rate alert** / **Remove from history**). The direction comes from where the rate stood when it was set (`RateAlert.towards`). Alerts live in `RateAlertStore` (JSON in the backed-up `prefs`) and are listed, with delete buttons, under Settings → Exchange rates → Rate alerts. After every rate update, from the app or the background worker, `ExchangeRatesRepository.store` saves the rates and calls `checkRateAlerts`: each alert reached is taken out of the store in one edit (`takeReached`, so it fires once even if two checks overlap) and posted as a notification that opens the converter on its pair. On Android 17 and later the rate in its title is colored with the system's notification colors (`Notification.createSemanticStyleAnnotation`): green ("safe") when it rose to the alert, red ("danger") when it fell to it; earlier versions show it plain. Nothing is checked for a pinned past date, or while notifications aren't allowed (the alerts wait). Setting an alert asks for `POST_NOTIFICATIONS` the first time and turns background refresh on, since that's what keeps rates arriving (`rememberSetRateAlert`).

### Home-screen widget via Glance

`view/widget/CurrencyWidget.kt` is a `GlanceAppWidgetReceiver` whose content is composed with **Glance** (`androidx.glance:glance-appwidget`) rather than hand-rolled `RemoteViews`. `widget_currency.xml` remains as the widget's preview/initial-layout resource required by the App Widget framework, but the live content is Glance composables. Per-widget pairs and the trend line: see *Outside the app* above.

### Right-to-left languages

Hebrew, Arabic and Farsi split the app in two:

- **Currency symbols go where the language puts them.** "$ 49.99" in English, "49,99 €" in German, "3.09 ₪" in Hebrew, as each language's currency format has it (`hasAppendedCurrencySymbol`, asked once per language). Every amount written out with its currency goes through `Context.money` / `withCurrencySymbol` (`util/TextUtils.kt`), or asks `hasAppendedCurrencySymbol` when its parts are styled (the converter's result, the timeline's rates): quick conversions, the cart's totals and its budget field, shared text, the currency picker and the text-conversion screen alike.
- **Numbers stay left to right.** `AppTheme` locks the layout direction to LTR (the manifest declares `supportsRtl="false"` for the same reason), so the converter, keypad, hero card, cart amounts and chart never mirror: digits, math and the from → to pair read left to right in every language. The Currenci× wordmark is a logo and never mirrors either.
- **The converter's chrome follows the language.** `MainScreen` lays out the top bar and the navigation drawer with `ReadingDirection`: in Hebrew the hamburger sits on the right with the shortcuts on the left, the drawer slides in from the right, and the hamburger's arrow points right, the way the drawer closes. The converter body under them goes back in an `Ltr` island (`ConverterRtlTest`). The timeline does the same: its top bar and its statistics tiles and period controls follow the language, while the chart, the past / current row in step with its time axis, and the from → to pair in the title stay left to right. The cart too: its top bar, item rows (drag handle on the right, pin on the left), labels and add button follow the language, while the currency pair, every amount and price, and the keypad stay left to right.
- **Prose follows the language.** Settings and its screens and the bottom sheets opt back in with `ReadingDirection` (or `ProseTheme`, `AppTheme` plus `ReadingDirection`): text right-aligned, switches and icons on the left, buttons mirrored. Anything inside them whose left-to-right order carries meaning goes back in an `Ltr` island: a fee's currency pair, the widget's from / swap / to row, the amounts and the rate line in the convert-selection sheet.
- **Popups set it inside their window.** Because of `supportsRtl="false"`, a sheet's or popup's window always resolves left to right, whatever the composition around it asked for. So `LedgerBottomSheet` (and with it every prompt) and the first-run tour's card (`Spotlight`, a popup) apply `ReadingDirection` inside their content.
- **Mixed-script labels keep their order.** Inside the LTR layout, a right-to-left label that names something in Latin script ("היום · InforEuro", "שקל חדש (ILS ₪)") goes through `String.inReadingOrder(context)`, an RTL isolate, so its parts read in the language's order.

`ReadingDirectionTest` checks a sheet, a confirm sheet and a password sheet in Hebrew and English with the whole activity in that language, as on a phone; `PopupReadingDirectionTest` covers the tour card and the convert-selection sheet. The assertions are in `util/ReadingSide.kt` (test sources): `assertOnReadingStartSide` checks that a node sits where the test's language starts reading (right in Hebrew, left in English), so one test body serves both directions.

The screenshot matrix runs each language's cells with the whole activity in that language and night mode (`ScreenshotRule` switches Robolectric's qualifiers, `iw-night` say, which recreates the activity, and sets the content on the new one), so sheets, whose windows take their resources from the activity, are fully Hebrew in the Hebrew cells, and dark in the dark ones: surfaces that open their own `AppTheme` (sheets, the cart list, the chart) read light or dark from the configuration.

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
| Reproducible-build target | Yes | No |
| Distribution | F-Droid | Google Play |

Source sets under `app/src/fdroid/` and `app/src/play/` override or add flavor-specific code without touching the shared `main` source set.

Where a build type or flavor decides whether something exists at all, `main` asks for it through a same-named file in each source set that hands back a nullable hook: `httpInspector` (Chucker in debug, `null` in release), `debugPreferenceRows` (the debug-only Settings rows), `rateApp` (the store rating, Play only) and `releaseNotesUrl()` / `plantConsoleLogging()`. `main` never branches on `BuildConfig.DEBUG` or `FLAVOR`, so no variant compiles a branch it can't take.
