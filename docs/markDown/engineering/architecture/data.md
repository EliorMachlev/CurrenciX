# Data

There's no SQLite. Settings and small state are **Preferences DataStore**; what the user builds up (carts, trips, fees, rate alerts) is **Proto DataStore**.

## Settings: `Pref<T>`

Four preference namespaces (`PersistenceKey`: `rates`, `last_state`, `starred_currencies`, `prefs`), whose names are also the sections of an exported backup. Each setting is one `Pref<T>` declared in its store (`booleanPref(KEY, default = true)`, `keyedPref(key, decode, encode)`), so its key, default and stored shape live in one line:

- `get()` reads the in-memory snapshot (only where a blocking read is unavoidable, such as startup), `set()` / `update { }` write, `flow` / `liveData` observe. ViewModels turn one into state with `Pref.stateInWhileSubscribed(viewModelScope)`; a composable without a ViewModel uses `rememberDatabase()` and `collectAsStateWithLifecycle()`, which starts from the stored value so the first frame is right.
- A value stored under a key with another type than declared (a setting whose type changed between builds) reads as unset: the default applies and the next write replaces it, instead of failing to cast (`PrefTest`).

## Saved data: Protobuf files

The working cart, saved carts, trips, fees and rate alerts are trees of values, so they live in `carts.pb`, `fees.pb` and `alerts.pb` (`SavedDataFile`). The schema is `src/main/proto/currencix/saved.proto`; **Wire** generates the Kotlin at build time (no reflection).

- `SavedProtos.kt` maps messages to models. Every read, from these files, a JSON backup or a cart file, goes through the same checks (`SavedValues.kt`: quantities clamped, amounts above zero, unknown currencies or types skipped, missing ids replaced).
- Amounts are decimal strings in both formats, so nothing is lost to a float.
- A file that can't be read starts over empty (`ReplaceFileCorruptionHandler`) rather than failing every read.

## One store type behind both

Both kinds sit behind `CachedStore<S>` (`PrefStore` is the preferences flavour). Writes update the in-memory cache at once and reach disk through a serial queue; disk emissions are ignored while the store's own writes are pending, and the cache resyncs when the queue drains, so a read between two quick writes never sees the first undone (`PrefStoreTest`).

`Database` hands out twelve stores, one concern each:

| Property | Store | Holds |
|---|---|---|
| `rates` | `RateStore` | Latest rates, each pair's cached timeline |
| `lastState` | `LastStateStore` | The converter's pair, recent pairs, a pinned historical date, the country last offered its currency |
| `stars` | `StarStore` | Starred currencies in order; the starred-only filter |
| `providers` | `ProviderSettings` | Main and fallback provider, API keys (sealed, see [security.md](../security.md)), unreliable providers shown, auto-refresh and interval |
| `fees` | `FeeStore` | Saved fees and the active exchange / bank fee |
| `display` | `DisplaySettings` | Theme, wallpaper colors, decimals, date format, keypad, haptics, sounds, smooth motion, preview, onboarding |
| `privacy` | `PrivacySettings` | App lock, block screenshots, private requests |
| `chart` | `ChartSettings` | What the chart draws |
| `carts` | `CartStore` | Working cart, saved carts, trips |
| `alerts` | `RateAlertStore` | Rate alerts waiting to fire |
| `receiptLanguages` | `ReceiptLanguageSettings` | Receipt languages allowed / declined, and keep-until times |
| `quickAmounts` | `QuickAmountSettings` | The user's own quick-conversion amounts, per currency |

## Keeping reads cheap

- **Warm-up.** A store's first read waits on disk, so `CurrenciesApplication.warmUpStores` reads each one in the background at process start, before any screen asks.
- **Decode on change only.** Every write re-emits the whole store; an encoded value (a cart, the fees) is decoded again only when its own field changes (`LastResult` in `fieldPref` / `keyedPref`), so typing a price doesn't re-decode every saved cart. Cached rates are parsed once per new set, process-wide, on the refresh's thread (`RateStore.parsedRates`).
- **Timelines are files.** Each provider's history per pair is `filesDir/timelines/<providerId>_<BASE>_<SYMBOL>.json` (`TimelineFiles`): saving one pair rewrites one file (atomically, via `.tmp`), the last few stay parsed in memory, and it's under `filesDir` so the system never discards years of history to free space.
- **Bad data is dropped, not fatal.** A rate or history that won't parse is dropped and fetched again, and counted per store (`DroppedCacheEntries`), so a store that keeps dropping, a write bug, shows in the log.

The app's own JSON (backups, cart files, disk caches) goes through Moshi's streaming reader and writer (`repository/json/JsonTree.kt`), with lenient reads for hand-edited or older files. No `org.json`, no Moshi reflection: provider responses use KSP-generated or hand-written adapters.

The one file outside DataStore: a cart's export / import through the Storage Access Framework (`CartExporter`), a one-off transfer, not storage.

## Rate cache: memory → disk → network

`repository/cache/` is a Store5-style fetcher / source of truth (no Store5 dependency, about 900 lines). `RateCache.get(key)` tries an in-memory LRU, then a disk JSON store (`DiskJsonStore`, atomic writes), then the network; `InFlightDedupe` merges concurrent requests for one key and `RetryPolicy` backs off transient failures. It caches parsed `ExchangeRates` / `Timeline`; OkHttp's 5 MiB HTTP cache below it caches raw bytes.

**Not now: Room.** The rate data would move to Room only if rate-history queries, per-pair override hierarchies or cross-provider comparisons become real features; settings, stars and fees would stay in DataStore either way.

## Refresh state

"A refresh is running" is transient, so it's in memory (`RefreshState.kt`), not DataStore. The UI never shows the raw flag; it picks a view, so a cached refresh shows no shimmer and a pull spinner never blinks shut (`RefreshIndicatorTest`):

| View | Behavior | Used by |
|---|---|---|
| `inFlight` | Raw truth | No double refresh; the timeline's swap |
| `pullIndicator` | Immediate, held ≥ 400 ms | Pull-to-refresh, the drawer's Refresh |
| `passiveIndicator` | After 150 ms, held ≥ 400 ms | Digit shimmer, timeline progress |
