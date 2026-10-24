package com.eliormachlev.currencix.view.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.AbroadOffer
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.HomeCurrency
import com.eliormachlev.currencix.model.abroadOffer
import com.eliormachlev.currencix.model.compareCards
import com.eliormachlev.currencix.model.convert
import com.eliormachlev.currencix.model.isHome
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.networkCountry
import com.eliormachlev.currencix.view.alert.RateAlertSheet
import com.eliormachlev.currencix.view.alert.rememberSetRateAlert
import com.eliormachlev.currencix.view.compose.LocalAppSnackbar
import com.eliormachlev.currencix.view.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.view.compose.dialogs.LedgerConfirmSheet
import com.eliormachlev.currencix.view.compose.onboarding.OnboardingAnchor
import com.eliormachlev.currencix.view.compose.onboarding.ProvideOnboardingAnchors
import com.eliormachlev.currencix.view.compose.onboarding.Spotlight
import com.eliormachlev.currencix.view.compose.onboarding.SpotlightStep
import com.eliormachlev.currencix.view.compose.rememberDatabase
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.view.main.compose.AbroadSheet
import com.eliormachlev.currencix.view.main.compose.CompareCardsSheet
import com.eliormachlev.currencix.view.main.compose.ConverterBody
import com.eliormachlev.currencix.view.main.compose.ConverterTopBar
import com.eliormachlev.currencix.view.main.compose.ConverterTopBarActions
import com.eliormachlev.currencix.view.main.compose.DrawerAction
import com.eliormachlev.currencix.view.main.compose.DrawerControl
import com.eliormachlev.currencix.view.main.compose.HistoricalDatePickerSheet
import com.eliormachlev.currencix.view.main.compose.MainDisplay
import com.eliormachlev.currencix.view.main.compose.MainDisplayCallbacks
import com.eliormachlev.currencix.view.main.compose.MainKeypad
import com.eliormachlev.currencix.view.main.compose.MainKeypadCallbacks
import com.eliormachlev.currencix.view.main.compose.MainScreen
import com.eliormachlev.currencix.view.main.compose.QuickConversionsSheet
import com.eliormachlev.currencix.view.main.compose.RecentPairsRow
import com.eliormachlev.currencix.view.main.compose.keypadHeights
import com.eliormachlev.currencix.view.navigation.AppNavigator
import com.eliormachlev.currencix.view.navigation.LocalPaneRole
import com.eliormachlev.currencix.view.navigation.PaneRole
import com.eliormachlev.currencix.view.navigation.Screen
import com.eliormachlev.currencix.view.navigation.SettingsTopic
import com.eliormachlev.currencix.view.navigation.paneRole
import com.eliormachlev.currencix.view.preference.compose.ProviderPickerWithKeyPrompt
import com.eliormachlev.currencix.viewmodel.main.KeyAvailability
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal

// Which of the converter's sheets / dialogs is open. Saved, so an open sheet
// survives rotation.
private enum class ConverterOverlay { ProviderPicker, QuickConversions, HistoricalDatePicker, StatusExplanation, CompareCards }

private const val RECENT_PAIR_SETTLE_MILLIS = 2_000L
private val RECENT_PAIRS_MARGIN = Spacing.Large
private val RECENT_PAIRS_BOTTOM_GAP = Spacing.Small
private val RECENT_PAIRS_COMPACT_TOP = Spacing.Tiny

/** The converter screen: top bar, drawer, hero card, keypad and their sheets. */
@Composable
internal fun ConverterRoute(
    host: ConverterHost,
    navigator: AppNavigator,
    foldingFeature: FoldingFeature?,
) {
    val viewModel = host.viewModel
    val isUpdating by viewModel.isRefreshing().collectAsStateWithLifecycle()
    val isExpandedKeypad by viewModel.isExpandedKeypadEnabled.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var overlay by rememberSaveable { mutableStateOf<ConverterOverlay?>(null) }
    val reveal = remember { host.takeWordmarkReveal() }
    ReportVisibleWhileComposed(host.status)
    val paneRole = LocalPaneRole.current
    val destinations = remember(viewModel, navigator, paneRole) { ConverterDestinations(viewModel, navigator, paneRole) }
    if (paneRole == PaneRole.List) TimelineFollowsPair(viewModel, navigator)
    val onLeaveViaDrawer = rememberDrawerClosedOnReturn(drawerState)
    // Every entry of the drawer and every shortcut of the top bar goes
    // through here, so the drawer is closed (or left closed) whichever of
    // the two opened something: with the drawer under the top bar, the
    // shortcuts stay in reach while it is open.
    val onAction: (DrawerAction) -> Unit = { action ->
        scope.launch { drawerState.close() }
        onLeaveViaDrawer(action)
        when (action) {
            DrawerAction.Refresh -> viewModel.forceUpdateExchangeRate()
            DrawerAction.Share -> scope.launch { host.share.share() }
            else -> destinations.open(action) { overlay = it }
        }
    }

    // Abroad: offer the local currency once per visit, when nothing else is open.
    AbroadOfferHost(viewModel, enabled = overlay == null)

    ProvideOnboardingAnchors {
        MainScreen(
            drawer = DrawerControl(drawerState, onAction),
            body = ConverterBody(isUpdating, viewModel::forceUpdateExchangeRate, foldingFeature, keypadHeights(isExpandedKeypad)),
            topBar = {
                ConverterTopBar(
                    drawerState = drawerState,
                    onToggleDrawer = { scope.launch { drawerState.toggle() } },
                    actions = topBarActions(onAction),
                    reveal = reveal,
                    isRefreshing = isUpdating,
                )
            },
            displayContent = {
                ConverterDisplay(
                    host = host,
                    onOpenFees = destinations::openFees,
                    onOpenProvider = { overlay = ConverterOverlay.ProviderPicker },
                    onExplainStatus = { overlay = ConverterOverlay.StatusExplanation },
                    onCompareCards = { overlay = ConverterOverlay.CompareCards },
                )
            },
            keypadContent = { ConverterKeypad(viewModel) },
        )
        OnboardingSpotlightHost()
    }
    ConverterOverlays(
        overlay = overlay,
        host = host,
        onOpenFees = destinations::openFees,
        onDismiss = { overlay = null },
        onOpenProvider = { overlay = ConverterOverlay.ProviderPicker },
    )
}

// The screens the converter opens, with the arguments they take from it.
// [paneRole] is the converter's pane in the two-pane layout (null otherwise).
private class ConverterDestinations(
    private val viewModel: MainViewModel,
    private val navigator: AppNavigator,
    private val paneRole: PaneRole?,
) {
    private fun openTimeline() {
        val from = viewModel.getBaseCurrency().value ?: return
        val to = viewModel.getDestinationCurrency().value ?: return
        openDetail(Screen.Timeline(from, to))
    }

    // A null side is fine here — CartViewModel.seedFromMain keeps the cart's
    // own pair or resolves just the missing side.
    private fun openCart() = openDetail(Screen.Cart(viewModel.getBaseCurrency().value, viewModel.getDestinationCurrency().value))

    // Beside the converter, a detail screen takes the detail pane's place
    // rather than stacking on it: back then returns to the converter alone.
    private fun openDetail(screen: Screen) {
        if (paneRole == PaneRole.List && navigator.current.paneRole == PaneRole.Detail) {
            navigator.replaceTop(screen)
        } else {
            navigator.navigate(screen)
        }
    }

    fun openFees() = navigator.navigate(Screen.Fees)

    private fun openSettings() = navigator.navigate(Screen.Settings)

    // Straight to Settings' About screen; back returns to the converter.
    private fun openAbout() = navigator.navigate(Screen.SettingsPage(SettingsTopic.ABOUT))

    /** A drawer entry that opens a screen or one of the converter's sheets ([showOverlay]). */
    fun open(
        action: DrawerAction,
        showOverlay: (ConverterOverlay) -> Unit,
    ) {
        when (action) {
            DrawerAction.Timeline -> openTimeline()
            DrawerAction.Cart -> openCart()
            DrawerAction.QuickConversions -> showOverlay(ConverterOverlay.QuickConversions)
            DrawerAction.DatePicker -> showOverlay(ConverterOverlay.HistoricalDatePicker)
            DrawerAction.ChangeApi -> showOverlay(ConverterOverlay.ProviderPicker)
            DrawerAction.Fees -> openFees()
            DrawerAction.Settings -> openSettings()
            DrawerAction.About -> openAbout()
            DrawerAction.Refresh, DrawerAction.Share -> Unit
        }
    }
}

// The drawer moves on Material's own motion: DrawerState.open() / close()
// take no animation spec (the spec-taking animateTo is deprecated).
private suspend fun DrawerState.toggle() = if (isOpen) close() else open()

// The top bar's shortcuts are the drawer's first four entries.
private fun topBarActions(open: (DrawerAction) -> Unit) =
    ConverterTopBarActions(
        onTimeline = { open(DrawerAction.Timeline) },
        onCart = { open(DrawerAction.Cart) },
        onQuickConversions = { open(DrawerAction.QuickConversions) },
        onHistoricalRates = { open(DrawerAction.DatePicker) },
        onRefresh = { open(DrawerAction.Refresh) },
    )

// A drawer entry that opens another screen starts closing the drawer, but
// the converter leaves composition before that animation ends — and would
// come back with the drawer still open. Returns the hook to call with the
// chosen entry; on return, the drawer is snapped shut.
@Composable
private fun rememberDrawerClosedOnReturn(drawerState: DrawerState): (DrawerAction) -> Unit {
    var closeOnReturn by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (closeOnReturn) {
            drawerState.snapTo(DrawerValue.Closed)
            closeOnReturn = false
        }
    }
    return { action -> closeOnReturn = action.opensScreen }
}

// Beside the converter, the timeline charts whatever pair the converter
// shows: changing the pair swaps the detail pane to the new pair's chart.
@Composable
private fun TimelineFollowsPair(
    viewModel: MainViewModel,
    navigator: AppNavigator,
) {
    val base = viewModel.getBaseCurrency().observeAsState().value
    val dest = viewModel.getDestinationCurrency().observeAsState().value
    LaunchedEffect(base, dest) {
        val shown = navigator.current as? Screen.Timeline ?: return@LaunchedEffect
        if (base == null || dest == null || base == dest) return@LaunchedEffect
        if (shown.from != base || shown.to != dest) navigator.replaceTop(Screen.Timeline(base, dest))
    }
}

// The converter is on screen for as long as it's composed: a screen pushed
// over it leaves composition once its transition ends.
@Composable
private fun ReportVisibleWhileComposed(status: ConverterStatus) {
    DisposableEffect(status) {
        status.setConverterVisible(true)
        onDispose { status.setConverterVisible(false) }
    }
}

// The sheets / dialogs the converter opens on top of itself.
@Composable
private fun ConverterOverlays(
    overlay: ConverterOverlay?,
    host: ConverterHost,
    onOpenFees: () -> Unit,
    onDismiss: () -> Unit,
    onOpenProvider: () -> Unit,
) {
    val viewModel = host.viewModel
    when (overlay) {
        ConverterOverlay.ProviderPicker -> {
            val current by host.preferenceModel.apiProvider.collectAsStateWithLifecycle()
            ProviderPickerWithKeyPrompt(
                selected = current,
                viewModel = host.preferenceModel,
                onDismiss = onDismiss,
                onPicked = host.preferenceModel::setApiProvider,
            )
        }

        ConverterOverlay.QuickConversions -> {
            QuickConversionsSheet(
                viewModel = viewModel,
                status = host.status,
                onSwap = viewModel::swapCurrencies,
                onOpenFees = onOpenFees,
                onDismiss = onDismiss,
            )
        }

        ConverterOverlay.HistoricalDatePicker -> {
            HistoricalDatePickerSheet(
                initial = viewModel.getHistoricalDate(),
                onPick = viewModel::setHistoricalDate,
                onDismiss = onDismiss,
            )
        }

        ConverterOverlay.CompareCards -> {
            CompareCardsHost(viewModel, onDismiss)
        }

        ConverterOverlay.StatusExplanation -> {
            StatusExplanationSheet(host.status, onOpenProvider, onDismiss)
        }

        null -> {}
    }
}

// What the status pill's tap opens: the status explained, with a way to
// change the provider. Closes itself once the status no longer needs
// explaining (the main provider answered again).
@Composable
private fun StatusExplanationSheet(
    status: ConverterStatus,
    onOpenProvider: () -> Unit,
    onDismiss: () -> Unit,
) {
    val explanation = status.banner.value?.explanation
    if (explanation == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    LedgerConfirmSheet(
        title = stringResource(R.string.fallback_info_title),
        message = explanation,
        confirmLabel = stringResource(R.string.change_provider),
        cancelLabel = stringResource(android.R.string.ok),
        onConfirm = onOpenProvider,
        onDismiss = onDismiss,
    )
}

// The hero card, with its copy / fees / provider / status shortcuts.
@Composable
private fun ConverterDisplay(
    host: ConverterHost,
    onOpenFees: () -> Unit,
    onOpenProvider: () -> Unit,
    onExplainStatus: () -> Unit,
    onCompareCards: () -> Unit,
) {
    val context = LocalContext.current
    val banner by host.status.banner
    val database = rememberDatabase()
    val pattern by database.display.dateFormat.liveData
        .observeAsState(DEFAULT_DATE_PATTERN)
    val fees by database.fees.fees.collectAsStateWithLifecycle()
    // "Compare cards" only with two or more cards to compare.
    val canCompare = fees.count { it is Fee.GlobalBank && it.isActive } >= 2
    MainDisplay(
        viewModel = host.viewModel,
        callbacks =
            MainDisplayCallbacks(
                onCopy = { text -> copyToClipboard(context, text) },
                onOpenFees = onOpenFees,
                onOpenProvider = onOpenProvider,
                onSwapLongPress = onOpenFees,
                onExplainBanner = onExplainStatus,
                onCompareCards = onCompareCards.takeIf { canCompare },
            ),
        dateFormatPattern = pattern,
        banner = banner,
        captureController = host.share.heroCapture,
    )
    // Recorded whether or not the chips are shown.
    val current = rememberRecordedPair(host.viewModel, database)
    // The expanded keypad's extra row leaves no room for the chips: they'd
    // run under its top row. Without them the gap above where they'd start
    // stays, so the keypad reaches only that far and never touches the
    // card. The status pill makes the card taller: the chips give that
    // height back, so they stay clear of the keypad.
    val expandedKeypad by host.viewModel.isExpandedKeypadEnabled.collectAsStateWithLifecycle()
    val compact = banner != null
    if (expandedKeypad) {
        Spacer(Modifier.height(recentPairsTop(compact)))
    } else {
        ConverterRecentPairs(host.viewModel, database, current, compact)
    }
}

// The gap between the card and the chips (or, with the expanded keypad,
// the keypad, which starts where the chips would).
private fun recentPairsTop(compact: Boolean): Dp = if (compact) RECENT_PAIRS_COMPACT_TOP else RECENT_PAIRS_MARGIN

// "Which card is cheaper" for the amount on screen; picking a card makes it
// the one in force.
@Composable
private fun CompareCardsHost(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val db = rememberDatabase()
    val fees by db.fees.fees.collectAsStateWithLifecycle()
    val activeExchange by db.fees.activeExchangeId.collectAsStateWithLifecycle()
    val activeCard by db.fees.activeBankId.collectAsStateWithLifecycle()
    val base by viewModel.getBaseCurrency().observeAsState()
    val dest by viewModel.getDestinationCurrency().observeAsState()
    val amount by viewModel.getResultAsNumber().observeAsState()
    val costs = remember(fees, base, dest, activeExchange, amount) { compareCards(fees, base, dest, activeExchange, amount) }
    CompareCardsSheet(
        costs = costs,
        currency = dest,
        activeCardId = activeCard,
        onPick = { id ->
            db.fees.activeBankId.set(id)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

// The pair on screen, recorded as used once it has stayed on screen for
// RECENT_PAIR_SETTLE_MILLIS, so stepping through the picker doesn't fill
// the recent pairs with pass-throughs.
@Composable
private fun rememberRecordedPair(
    viewModel: MainViewModel,
    database: Database,
): CurrencyPair? {
    val base = viewModel.getBaseCurrency().observeAsState().value
    val dest = viewModel.getDestinationCurrency().observeAsState().value
    val current = if (base != null && dest != null && base != dest) CurrencyPair(base, dest) else null
    LaunchedEffect(current) {
        current ?: return@LaunchedEffect
        delay(RECENT_PAIR_SETTLE_MILLIS)
        database.lastState.addRecentPair(current)
    }
    return current
}

// One-tap chips for the pairs used before the one on screen ([current]).
@Composable
private fun ConverterRecentPairs(
    viewModel: MainViewModel,
    database: Database,
    current: CurrencyPair?,
    compact: Boolean,
) {
    val recents by database.lastState.recentPairs.flow
        .collectAsStateWithLifecycle(emptyList())
    val others = remember(recents, current) { recents.filterNot { it.isSameCurrencies(current) }.toImmutableList() }
    val paste = rememberPasteAmount(viewModel, current)
    var alertFor by remember { mutableStateOf<CurrencyPair?>(null) }
    val alerts by database.alerts.alerts.collectAsStateWithLifecycle()
    val alerted = remember(alerts) { alerts.mapTo(mutableSetOf()) { it.pair }.toImmutableSet() }
    RecentPairsRow(
        pairs = others,
        onPaste = paste,
        onAlert = { alertFor = it },
        alerted = alerted,
        onPick = viewModel::setCurrencyPair,
        onRemove = rememberRemoveWithUndo(database),
        contentPadding = PaddingValues(horizontal = RECENT_PAIRS_MARGIN),
        compact = compact,
        // The bottom gap keeps the chips clear of the keypad's top row.
        // Compact, the chips and their margins together give back the status
        // pill's height (its line and the gap under it).
        modifier =
            Modifier.padding(
                top = recentPairsTop(compact),
                bottom = if (compact) 0.dp else RECENT_PAIRS_BOTTOM_GAP,
            ),
    )
    alertFor?.let { pair -> PairAlertSheet(viewModel, pair, onDismiss = { alertFor = null }) }
}

// The rate alert sheet for a recent pair, at today's rate; nothing to set
// before the pair has a rate.
@Composable
private fun PairAlertSheet(
    viewModel: MainViewModel,
    pair: CurrencyPair,
    onDismiss: () -> Unit,
) {
    val rates by viewModel.getExchangeRates().observeAsState()
    val current = rates?.convert(BigDecimal.ONE, pair.from, pair.to)
    val setAlert = rememberSetRateAlert()
    if (current == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    RateAlertSheet(
        pair = pair,
        current = current,
        onSet = { alert ->
            setAlert(alert)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

// The paste chip's action while the clipboard holds a recent copy: the
// price found in it typed in (its currency on the "from" side), or a
// message when there's none. Null while there's nothing to offer.
@Composable
private fun rememberPasteAmount(
    viewModel: MainViewModel,
    current: CurrencyPair?,
): (() -> Unit)? {
    val offer = rememberClipboardOffer()
    val snackbar = LocalAppSnackbar.current
    val noAmount = stringResource(R.string.paste_no_amount)
    if (!offer.available) return null
    return {
        val price = offer.take(listOfNotNull(current?.from, current?.to))
        if (price == null) {
            snackbar?.show(noAmount)
        } else {
            current?.let { viewModel.setCurrencyPair(pairForPaste(price, it)) }
            viewModel.input.setAmount(price.amount)
        }
    }
}

// Forgets a recent pair, with an Undo on the snackbar: a long-press can land
// on the wrong chip, and the prompt before it is easy to confirm by habit.
@Composable
private fun rememberRemoveWithUndo(database: Database): (CurrencyPair) -> Unit {
    val snackbar = LocalAppSnackbar.current
    val resources = LocalResources.current
    return remember(database, snackbar, resources) {
        { pair ->
            val before = database.lastState.removeRecentPair(pair)
            snackbar?.showWithUndo(
                message = resources.getString(R.string.recent_removed_pair, pair.from.iso4217Alpha(), pair.to.iso4217Alpha()),
                actionLabel = resources.getString(R.string.undo),
                onUndo = { database.lastState.restoreRecentPairs(before) },
            )
        }
    }
}

// The in-app keypad. The picker only offers in-app variants, so the
// preference feeds straight into MainKeypad.
@Composable
private fun ConverterKeypad(viewModel: MainViewModel) {
    val isExpanded by viewModel.isExpandedKeypadEnabled.collectAsStateWithLifecycle()
    val input = viewModel.input
    val keys by input.keys.observeAsState(KeyAvailability())
    MainKeypad(
        isExpandedKeypad = isExpanded,
        keys = keys,
        callbacks =
            remember(input) {
                MainKeypadCallbacks(
                    onDigit = input::addNumber,
                    onDecimal = input::addDecimal,
                    onOperator = { op -> input.addOperator(op.display) },
                    onPercent = input::addPercent,
                    onOpenParen = input::addOpenParen,
                    onCloseParen = input::addCloseParen,
                    onDelete = input::delete,
                    onClear = input::clear,
                    onEquals = input::evaluate,
                )
            },
    )
}

// First-run onboarding overlay (#147). Reads the persisted `hasSeenOnboarding`
// gate straight from Database (Compose scope owns the flow subscription) so
// the flip on Skip/Finish is written *and* recomposes this host in one step.
@Composable
private fun OnboardingSpotlightHost() {
    val db = rememberDatabase()
    val hasSeen by db.display.hasSeenOnboarding.collectAsStateWithLifecycle()
    if (hasSeen) return
    val steps =
        listOf(
            SpotlightStep(
                anchor = OnboardingAnchor.FeeStamp,
                title = stringResource(R.string.onboarding_fee_title),
                body = stringResource(R.string.onboarding_fee_body),
            ),
            SpotlightStep(
                anchor = OnboardingAnchor.SwapFab,
                title = stringResource(R.string.onboarding_swap_title),
                body = stringResource(R.string.onboarding_swap_body),
            ),
            SpotlightStep(
                anchor = OnboardingAnchor.Hamburger,
                title = stringResource(R.string.onboarding_autorefresh_title),
                body = stringResource(R.string.onboarding_autorefresh_body),
                actionLabel = stringResource(R.string.onboarding_autorefresh_enable),
                onAction = { db.providers.autoRefresh.set(true) },
            ),
        )
    Spotlight(
        steps = steps,
        skipLabel = stringResource(R.string.onboarding_skip),
        nextLabel = stringResource(R.string.onboarding_next),
        finishLabel = stringResource(R.string.onboarding_finish),
        onDismiss = { db.display.hasSeenOnboarding.set(true) },
    )
}

// Checks where the phone is each time the converter comes back to the front:
// abroad, it offers the local currency → home's (once per visit); back home,
// it forgets the last visit so the next one is asked about.
@Composable
private fun AbroadOfferHost(
    viewModel: MainViewModel,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val db = rememberDatabase()
    var offer by remember { mutableStateOf<AbroadOffer?>(null) }
    LifecycleResumeEffect(viewModel) {
        val network = networkCountry(context)
        val home = HomeCurrency.systemRegion()
        val asked = db.lastState.abroadAskedCountry
        if (isHome(network, home)) asked.set(null)
        val pair =
            viewModel.getBaseCurrency().value?.let { from ->
                viewModel.getDestinationCurrency().value?.let { CurrencyPair(from, it) }
            }
        offer =
            abroadOffer(network, home, pair, asked.get(), HomeCurrency::currencyOf)
                .takeIf { db.display.hasSeenOnboarding.get() }
        onPauseOrDispose {}
    }
    val shown = offer?.takeIf { enabled } ?: return
    val answered = {
        db.lastState.abroadAskedCountry.set(shown.country)
        offer = null
    }
    AbroadSheet(
        offer = shown,
        onSwitch = {
            viewModel.setCurrencyPair(CurrencyPair(shown.local, shown.home))
            viewModel.forceUpdateExchangeRate()
            answered()
        },
        onDismiss = answered,
    )
}
