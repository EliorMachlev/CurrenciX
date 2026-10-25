# Architecture

CurrenciX is **MVVM** with a repository layer, in Kotlin: Compose screens in one Activity, ViewModels exposing `LiveData`, and DataStore for everything stored.

| Topic | What's in it |
|---|---|
| [Data](data.md) | DataStore settings and Protobuf files, the stores, the rate cache, refresh state |
| [Networking](networking.md) | The OkHttp client, Retrofit vs raw feeds, fallback and slow providers, background refresh, startup warm-up |
| [UI](ui.md) | Navigation, two panes, sheets and text fields, theme, motion, right-to-left, predictive back |
| [Features](features.md) | Converter, outside the app, cart and trips, receipt scanning, chart, rate alerts, problem reports |
| [Website](../website.md) | The GitHub Pages site |

## Layers

```
┌──────────────────────────────────────┐
│ View: MainActivity · Compose screens │
└──────────────┬───────────────────────┘
               │ observes LiveData
┌──────────────▼───────────────────────┐
│ ViewModel: Main · Timeline ·         │
│ Preference · Cart                    │
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

Konsist tests (`ArchitectureTest`) hold the View / ViewModel / Repository / Model boundaries, so a refactor can't blur them.

## Source layout

```
app/src/main/kotlin/com/eliormachlev/currencix/
├── CurrenciesApplication.kt   # Warms provider DNS and the stores, installs the crash recorder
├── model/                     # ApiProvider (8 providers + the Api interface), Currency (173), rates, timelines
│   ├── adapter/               # Moshi / XML adapters per provider
│   └── provider/              # One class per provider; api/ holds the Retrofit interfaces
├── repository/                # Database and its stores, ExchangeRatesRepository, BackupManager, SavedProtos
│   ├── cache/                 # Memory → disk → network rate cache
│   └── persistence/           # PersistenceKey, CachedStore / PrefStore, Pref, SavedDataFile
├── viewmodel/                 # main (conversion pipeline), preference, timeline, cart
├── view/
│   ├── main/                  # MainActivity + the converter route
│   ├── navigation/            # Screen keys, AppNavigator, AppNavHost, screen motion
│   ├── compose/               # Theme, Motion, Spacing, top bars, sheets, shared components
│   ├── preference/ timeline/ cart/ alert/ lock/
│   ├── convert/               # ConvertTextActivity (text-selection menu)
│   ├── widget/ tile/          # Glance widget, Quick Settings tile
├── ocr/                       # Receipt reading (OcrEngine, ReceiptReader, Tesseract language data)
├── worker/                    # Background rate refresh
├── alert/                     # Rate alert checks and notifications
├── problems/                  # Crash and freeze records
└── util/                      # HTTP client, dates, math, text, LiveData helpers

helpers/   # CurrencyFetcher: generates the localized currency-name resources
ocr-engine/, ocr/   # Tesseract, and the Play app's on-demand feature module
```

## Rules that hold everywhere

- **Dispatchers in one place.** `AppDispatchers` holds IO, Default and Main. Classes the app constructs take one (default `AppDispatchers.production`) so tests can pass their own; nothing else names `Dispatchers.IO` / `Default`, which detekt's `InjectDispatcher` enforces.
- **LiveData for the UI.** ViewModels expose `LiveData`; screens observe with `observeAsState`. Settings arrive as `Pref.liveData` / `Pref.flow`, so a write reaches every observer without a re-read. Derived streams are built once (`by lazy`), never per call, since a screen asks for them on every recomposition. Heavy readouts (the chart's statistics) run on Default, latest request wins.
- **Flavors and build types never branch in `main`.** Where a variant decides whether something exists, `main` asks a same-named file in each source set for a nullable hook (`httpInspector`, `debugPreferenceRows`, `rateApp`, `releaseNotesUrl()`, `plantConsoleLogging()`). `main` never reads `FLAVOR`, and reads `BuildConfig.DEBUG` only for the debug build's red × in the top-bar mark. `fdroid` has no Play Services and is a reproducible-build target; `play` may use Play APIs (see [build.md](../build.md)).
- **No dialogs, no toasts, no Fragments.** Every prompt is a sheet, every message the one snackbar, every screen a Compose destination (see [ui.md](ui.md)).
