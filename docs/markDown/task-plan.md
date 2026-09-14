# CurrenciX task plan

Ordering rule: **backend first, UI second.** Within UI: **main → settings → cart → chart.**

Critical path: 0 → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9.
Tasks inside a phase are parallelizable unless a `blocked-by` note says otherwise.

---

## Phase 0 — Foundations

Guardrails first. These catch regressions in everything that follows.

- **#153 detekt** — static analysis in CI. Enforce the CLAUDE.md code-shape defaults mechanically.
- **#157 Konsist** — architecture invariants as JUnit tests. Pins layer boundaries before rewrites start touching them.
- **#144 LeakCanary** — debug-only. Safety net for the migrations in Phases 1–3.
- **#156 JankStats** — debug-only. Establish a baseline frame-time signal *before* motion changes so regressions are structural, not visual.
- **#143 Chucker** — debug-only HTTP inspector. Needed for Phase 2's networking migration.
- **#154 Showkase** — component gallery. Land early so every composable added during redesign registers automatically.

## Phase 1 — Data / state layer

- **#150 DataStore migration** — namespace-by-namespace behind a `PersistenceKey` enum. Verify backup round-trip after each namespace. Kills `warmSharedPreferences()`. Inline a ~30-line `restartApp(context)` helper (`PackageManager.getLaunchIntentForPackage` + `exitProcess(0)`) and call it from the migration-complete hook and from `BackupManager.importArchive()` success — no ProcessPhoenix dep.
- **#149 Molecule (LiveData → StateFlow)** — order: BackupViewModel → PreferenceViewModel → FeeManagerViewModel → MainViewModel. Land early so all subsequent UI (Phases 4–8) writes StateFlow-native, not LiveData that needs re-migration.
- **#161 kotlinx.collections.immutable** *(rides with #149)* — expose `ImmutableList<T>` on the state side of every StateFlow that carries a list, so Compose stability inference skips recomposition on unchanged content. Land in the same pass so types don't need a second migration.

## Phase 2 — Networking layer

- **#158 Retrofit atop OkHttp** — one provider at a time. Chucker (#143) already available for debugging.
- **#163 OkHttp Cache audit** *(blocks #148 scoping)* — audit each provider's `Cache-Control` / `ETag` headers, wire `OkHttp.Cache` for cooperative providers, per-provider rewrite interceptor for the rest. Whatever HTTP handles shrinks #148's app-layer scope.

## Phase 3 — Data plumbing

- **#148 In-house rate cache** *(blocked-by: #150, #158, #163)* — Fetcher / SourceOfTruth / Converter shape kept clean, but implemented in ~300–400 lines instead of adopting Store5. Delivers per-provider TTL, retry-with-jitter, in-flight dedupe, memory + disk tiering, layered on top of whatever OkHttp Cache handles from #163. Boundaries kept clean so #164 (Room) can swap the SoT implementation without touching orchestration.
- **#151 WorkManager auto-refresh** *(blocked-by: #150, #148)* — provider-aware TTL + user override. Triggers `RateStore.fresh()` rather than raw provider calls. Default OFF at first launch; opt-in via onboarding (#147).

## Phase 4 — UI primitives

Load-bearing for every feature phase that follows. Land these before touching feature UI so features inherit ledger + bespoke-dialog treatment.

- **#159 List redesign (ledger)** — migrate `PreferenceRow`/`PreferenceSection` first; cascades to Preferences, Fees, Backup for free. Drawer + picker rows next.
- **#160 Dialog redesign** — `BasicAlertDialog` for confirm/password, `ModalBottomSheet` for data-provider picker. M3 primitives, no third-party dialog lib.

## Phase 5 — Main screen

- **#137 finish motion** — anything left from current session; verify against JankStats baseline.
- **#155 Splash → wordmark hand-off** — closes the black-frame gap between system launcher and first content.
- **#140 Hand-rolled shimmer** — `Modifier.shimmer()` on hero digit slot while `isUpdating`. No third-party dep.
- **#146 Haze** *(design-gated)* — prototype only; if the receipt vocabulary rejects frosted glass, close as won't-fix.
- **#147 Onboarding (Showcase Layout)** — spotlights anchor to final main-screen positions; do after visuals settle.
- **#145 Share-as-image via GraphicsLayer** — first-party `rememberGraphicsLayer()` → Bitmap → `ACTION_SEND image/png`. Do after main-screen visuals are final.

## Phase 6 — Settings / Fees / Backup

- **#139 Fees list `animateItem()`** — inherits row aesthetics from #159.
- **#142 Favorites drag-to-reorder** — Reorderable lib; runs inside the redesigned currency picker (ModalBottomSheet from #160).

## Phase 7 — Cart

- **#138 Cart: SwipeToDismissBox + `animateItem()`** — swipe on trailing edge.
- **#141 Cart: drag-to-reorder** — shares Reorderable dep with #142; drag handle on leading edge so swipe/drag don't conflict.

## Phase 8 — Chart / Timeline

No open tasks. Ledger row treatment (#159) applies to any list on timeline screen. Verify Vico charts still read correctly against redesigned dialog + list surroundings; open a task only if a mismatch surfaces.

## Phase 9 — Launch prep

- **#152 Baseline Profiles + ProfileInstaller** — must go last. Profiles measure the *final* code paths, not intermediate ones. Generate on CI, bake into APK.

## Parked (no phase — reopen on trigger)

- **#164 Room migration for rate namespace** — reopen when rate history/timeline queries, per-pair override hierarchies, or cross-provider comparison views become real features. Until then, DataStore + #148 in-house cache is the right shape. Room-only for the rate namespace; preferences/favorites/fees stay in DataStore.

---

## Task index (by ID)

| ID | Phase | Task |
|---|---|---|
| 137 | 5 | Motion: wordmark reveal, hero card, prefs stagger, hamburger |
| 138 | 7 | Cart: SwipeToDismissBox + animateItem() |
| 139 | 6 | Fees list: animateItem() on Specific Pair rows |
| 140 | 5 | Hand-rolled shimmer modifier on hero card while updating |
| 141 | 7 | Cart: drag-to-reorder via Reorderable |
| 142 | 6 | Currency favorites: drag-to-reorder via Reorderable |
| 143 | 0 | Chucker: in-app HTTP inspector |
| 144 | 0 | LeakCanary: memory leak detection |
| 145 | 5 | Share hero card as image (first-party GraphicsLayer) |
| 146 | 5 | Haze: frosted-glass drawer scrim / fee chip (design-gated) |
| 147 | 5 | Showcase Layout: first-run onboarding |
| 148 | 3 | In-house rate cache (Fetcher / SoT / Converter shape) |
| 149 | 1 | Molecule: LiveData → StateFlow migration |
| 150 | 1 | SharedPreferences → DataStore migration |
| 151 | 3 | Auto-refresh rates via WorkManager |
| 152 | 9 | Baseline Profiles + ProfileInstaller |
| 153 | 0 | detekt: static analysis |
| 154 | 0 | Showkase: browsable @Preview gallery |
| 155 | 5 | Splash screen: androidx.core.splashscreen |
| 156 | 0 | JankStats: local jank tracking |
| 157 | 0 | Konsist: architecture invariants as tests |
| 158 | 2 | Retrofit atop existing OkHttp |
| 159 | 4 | List redesign: ledger direction |
| 160 | 4 | Dialog redesign: BasicAlertDialog + ModalBottomSheet |
| 161 | 1 | kotlinx.collections.immutable: ImmutableList for Compose stability |
| 163 | 2 | OkHttp Cache audit + wire cooperative providers |
| 164 | — | Room migration for rate namespace (parked) |
