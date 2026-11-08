# Architecture

CurrenciX is **MVVM** with a repository layer, in Kotlin: Compose screens in one Activity, state holders exposing `StateFlow` (plain classes kept with Compose's `retain`, no `ViewModel`), and DataStore for everything stored.

| Topic | What's in it |
|---|---|
| [Data](data.md) | DataStore settings and Protobuf files, the stores, the rate cache, refresh state |
| [Networking](networking.md) | The OkHttp client, Retrofit with kotlinx.serialization vs raw feeds, fallback and slow providers, background refresh, startup warm-up |
| [UI](ui.md) | Navigation, two panes, sheets and text fields, theme, motion, right-to-left, predictive back |
| [Features](features.md) | Converter, outside the app, cart and trips, receipt scanning, chart, rate alerts, problem reports |
| [Website](../website.md) | The GitHub Pages site |

## Layers

```
┌──────────────────────────────────────┐
│ View: MainActivity · Compose screens │
└──────────────┬───────────────────────┘
               │ collects StateFlow
┌──────────────▼───────────────────────┐
│ State holders: Main · Timeline ·     │
│ Cart · Backup                        │
└──────────────┬───────────────────────┘
               │ calls
┌──────────────▼───────────────────────┐
│ Repository: ExchangeRatesRepository, │
│ ExchangeRatesService                 │
└──────────────┬───────────────────────┘
               │ reads / writes
┌──────────────▼───────────────────────┐
│ Cache: memory (LRU) → disk → network │
└──────────────┬───────────────────────┘
               │ on a miss
┌──────────────▼───────────────────────┐
│ Data: ApiProvider (Retrofit / OkHttp)│
│ Database (DataStore: prefs + proto)  │
└──────────────────────────────────────┘
```

Konsist tests (`ArchitectureTest`) hold the View / ViewModel / Repository / Model boundaries, so a refactor can't blur them. The app has no `ViewModel`: a screen's state holder is a plain class that takes a `RetainedScope` (its coroutine scope, cancelled when the holder is retired) and delegates `RetainObserver` to it, built in the route with `retain { … }` (keys for what it is made for), and the tests keep a bare `viewModel()` or `ViewModelProvider(owner)`, which finds a constructor by reflection, out of the source.

## Source layout

```
app/src/main/kotlin/com/eliormachlev/currencix/
├── CurrenciesApplication.kt   # Warms provider DNS and the stores, installs the crash recorder
├── model/                     # ApiProvider (8 providers + the Api interface), Currency (173), rates, timelines
│   ├── adapter/               # Per provider: decoded responses → rates / timelines, and the XML parsers
│   └── provider/              # One class per provider; api/ holds the Retrofit interfaces and @Serializable responses
├── repository/                # Database and its stores, ExchangeRatesRepository, BackupManager, SavedProtos
│   ├── cache/                 # Memory → disk → network rate cache
│   ├── json/                  # The app's own JSON files: JsonTree, DuplicateFieldName
│   └── persistence/           # PersistenceKey, CachedStore / PrefStore, Pref, SavedDataFile
├── viewmodel/                 # state holders: main (conversion pipeline), preference (settings), timeline, cart, backup; util (RetainedScope, StateFlow helpers)
├── view/
│   ├── main/                  # MainActivity + the converter route
│   ├── navigation/            # Screen keys, AppNavigator, AppNavHost, screen motion
│   ├── compose/               # Theme, Motion, Spacing, top bars, sheets, shared components
│   ├── preference/ timeline/ cart/ alert/ lock/
│   ├── convert/               # ConvertTextActivity (text-selection menu)
│   ├── widget/ tile/          # Glance widget, Quick Settings tile
├── ocr/                       # Receipt reading (ReceiptReader, Tesseract language data)
├── worker/                    # Background rate refresh
├── alert/                     # Rate alert checks and notifications
├── problems/                  # Crash and freeze records
└── util/                      # HTTP client, dates, math, text, network status

helpers/   # CurrencyFetcher: generates the localized currency-name resources
ocr-engine/         # Tesseract, the receipt reader in both flavors
```

## Rules that hold everywhere

- **Dispatchers in one place.** `AppDispatchers` holds IO, Default and Main. Classes the app constructs take one (default `AppDispatchers.production`) so tests can pass their own; nothing else names `Dispatchers.IO` / `Default`, which detekt's `InjectDispatcher` enforces.
- **StateFlow for the UI.** State holders expose `StateFlow`; screens collect with `collectAsStateWithLifecycle`, so nothing is collected while the app is in the background. Settings arrive as `Pref.flow`, so a write reaches every collector without a re-read. A derived stream is `stateIn(scope, WhileSubscribed(5s), …)` (`stateInWhileSubscribed`), so its upstream stops shortly after the screen does; one read synchronously through `.value` (the share text, the currency picker), or one the holder acts on itself, is collected for the holder's life (`stateInEagerly`). Derived streams are built once (`by lazy` or a property), never per call, since a screen asks for them on every recomposition. Something to act on once, not a state (each refresh's error), is a `SharedFlow`, not a `StateFlow`, which would drop a repeat of the same value. Heavy readouts (the chart's statistics) run on Default, latest request wins (`mapLatest`).
- **Flavors and build types never branch in `main`.** Where a variant decides whether something exists, `main` asks a same-named file in each source set for a nullable hook (`httpInspector`, `httpLogger`, `debugPreferenceRows`, `rateApp`, `releaseNotesUrl()`, `plantConsoleLogging()`). `main` never reads `FLAVOR`, and reads `BuildConfig.DEBUG` only for the debug build's red × in the top-bar mark. `fdroid` has no Play Services and is a reproducible-build target; `play` may use Play APIs (see [build.md](../build.md)).
- **No dialogs, no toasts, no Fragments.** Every prompt is a sheet, every message the one snackbar, every screen a Compose destination (see [ui.md](ui.md)).
