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
│   ├── cache/                  # Memory → disk → network rate cache (Store5-inspired, no Store5 dep)
│   └── persistence/             # PersistenceKey enum, DataStore delegates, PrefStore helper
├── view/
│   ├── main/                   # Converter screen — full Compose (ComposeView built in code, no XML layout)
│   ├── preference/             # Settings — XML Activity shell hosts Fragments that each return a ComposeView
│   ├── timeline/               # Chart screen — full Compose
│   ├── cart/                   # Bill-splitting calculator — full Compose
│   ├── compose/                # Shared Compose foundation: theme, common components, drag-reorder, dialogs, onboarding
│   └── BaseActivity.kt
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

ViewModels expose `LiveData<T>` streams. Activities observe them without holding references to the ViewModel, ensuring lifecycle-safety and no memory leaks. Preference changes propagate automatically via `PrefStore.mappedLiveData { … }`, which bridges each DataStore namespace's `Flow<Preferences>` into a `LiveData<T>` so observers pick up writes without a manual re-read.

### Timeline chart auto-scales decimal places (ignores user preference)

The timeline screen's rate labels (min/max/avg/current/past) do **not** honor the global `decimal_places` preference from Settings. `TimelineViewModel.getDecimalPlaces()` derives the number of decimals from the visible data range: `(max − min).abs().getSignificantDecimalPlaces(3)`, capped at 7.

Why: the user preference (default 2) is tuned for the converter screen where amounts are typed by hand. On the chart, a low-volatility pair like EUR↔USD moves in the 3rd–4th decimal, so a fixed 2-decimal display would render the min/max/avg identical and the chart's whole point would be lost. Auto-scaling keeps enough precision to show variation regardless of the pair.

Trade-off: users who explicitly raise or lower `decimal_places` in Settings will see that setting silently overridden on the chart. Intentional, but surprising — recorded here so future work doesn't "fix" it without weighing the readability cost.

### Compose adoption: Main / Timeline / Cart are fully Compose; Preference is Compose-in-a-Fragment

The Compose migration is largely done, not partial. `MainActivity`, `TimelineActivity`, and `CartActivity` each call `setContentView(composeHost)` with a `ComposeView` built directly in code — there is no `activity_main.xml`, `activity_timeline.xml`, or `activity_cart.xml` any more. `PreferenceActivity` is the one holdout: it still inflates `activity_preference.xml` and hosts Fragments via a `FragmentTransaction` (standard Android navigation), but each Fragment's `onCreateView` returns a `ComposeView` with its content as Compose (`PreferenceScreen.kt`, `FeesScreen.kt`, `BackupScreen.kt`, etc.) rather than the legacy `PreferenceFragmentCompat` XML-driven screens. So in practice every screen's *content* is Compose; only Preference keeps a thin XML/Fragment shell around it.

Shared Compose foundation lives in `view/compose/`: `AppTheme.kt` (Material 3 theme, light/dark/OLED — the app dropped `com.google.android.material` in favor of Compose Material 3 + appcompat-only chrome), shared common components, drag-reorder, plus `dialogs/` and `onboarding/` subpackages.

### Timeline chart engine: Vico via Compose interop

The timeline chart is rendered by [Vico](https://github.com/patrykandpatrick/vico) (`com.patrykandpatrick.vico:compose`), hosted inside a `ComposeView` embedded in the otherwise View-based XML layout (`timeline_chart.xml`). `TimelineChart.kt` is a `@Composable` that observes the ViewModel's `LiveData` streams via `observeAsState()` (bridged by `androidx.compose.runtime:runtime-livedata`) and drives a `CartesianChartHost` backed by a `CartesianChartModelProducer`.

Why Vico over the previous engine (SparkView): SparkView is unmaintained and required a hand-rolled adapter, a manual dashed baseline `Paint`, and custom scrub handling. Vico ships all of that as first-class API (`HorizontalLine` decorations, `CartesianMarkerVisibilityListener`) and is actively developed. This was also the entry point that first introduced Jetpack Compose to what was then a View-only app; Compose has since become the primary UI toolkit (see above).

Behavior preserved: dashed reference line at the last value, scrub-to-past-date via marker-shown callback, theme-aware colors resolved through `MaterialColors.getColor` and passed into the composable.

### Graph options: user-tunable chart chrome

Four `LiveData<Boolean>` streams (backed by `PrefStore.mappedLiveData`) — grid, X-axis labels, Y-axis labels, and highlight-extremes — flow from `Database` through the `TimelineActivity` into `TimelineChart`. All default to `true` so first-run appearance is unchanged. Inside the composable each toggle swaps a Vico component for `null` (e.g. `guideline = if (showGrid) rememberAxisGuidelineComponent() else null`); Vico treats `null` as "don't draw," so no branching in the layer definitions is needed.

### Application subclass prewarms DNS for the selected provider

`CurrenciesApplication` is registered via `android:name=".CurrenciesApplication"` on the manifest's `<application>` tag. Its only responsibility today is to resolve the currently-selected `ApiProvider`'s host on a background daemon thread during `onCreate()`, so the first exchange-rate request doesn't pay for DNS.

The preference read (`Database(this).getApiProvider()`) and the `InetAddress.getAllByName(host)` call both run **inside** the background thread — DataStore's synchronous snapshot read and DNS resolution are both blocking I/O and neither belongs on the main thread during app startup. Failures (offline, DNS outage) are swallowed with `runCatching`; this is a best-effort warm-up, not a health check.

`ApiProvider.getHost()` (a narrow accessor over the enum's `private implementation.baseUrl`) exposes only the hostname to callers, so the `Application` never touches the full base URL.

### Background rate refresh via WorkManager

`androidx.work:work-runtime-ktx` schedules periodic background refresh of exchange rates (provider-aware TTL — see `worker/RateRefreshScheduler.kt`). Off by default; opt-in via Settings.

### Home-screen widget via Glance

`view/widget/CurrencyWidget.kt` is an `AppWidgetProvider` whose content is composed with **Glance** (`androidx.glance:glance-appwidget`) rather than hand-rolled `RemoteViews`. `widget_currency.xml` remains as the widget's preview/initial-layout resource required by the App Widget framework, but the live content is Glance composables.

### Predictive back gesture

Opted in via `android:enableOnBackInvokedCallback="true"` on the manifest's `<application>` tag. This is a global opt-in for the predictive back animation on Android 13+ (API 33). No per-screen `OnBackInvokedCallback` wiring is added — the app's existing back behavior is compatible with the default animated preview.

### Build Flavors: `play` vs `fdroid`

| Dimension | `fdroid` | `play` |
|---|---|---|
| Play Services | None | Allowed |
| Reproducibility | Yes | No |
| Distribution | F-Droid | Google Play |

Source sets under `app/src/fdroid/` and `app/src/play/` override or add flavor-specific code without touching the shared `main` source set.
