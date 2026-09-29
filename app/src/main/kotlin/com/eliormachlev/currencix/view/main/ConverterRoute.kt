package com.eliormachlev.currencix.view.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.ParsedPrice
import com.eliormachlev.currencix.util.fromHtmlLegacy
import com.eliormachlev.currencix.view.compose.AppSnackbar
import com.eliormachlev.currencix.view.compose.LocalAppSnackbar
import com.eliormachlev.currencix.view.compose.onboarding.OnboardingAnchor
import com.eliormachlev.currencix.view.compose.onboarding.ProvideOnboardingAnchors
import com.eliormachlev.currencix.view.compose.onboarding.Spotlight
import com.eliormachlev.currencix.view.compose.onboarding.SpotlightStep
import com.eliormachlev.currencix.view.compose.showOrToast
import com.eliormachlev.currencix.view.compose.theme.Motion
import com.eliormachlev.currencix.view.convert.targetCurrency
import com.eliormachlev.currencix.view.main.compose.ConverterTopBar
import com.eliormachlev.currencix.view.main.compose.ConverterTopBarActions
import com.eliormachlev.currencix.view.main.compose.DrawerAction
import com.eliormachlev.currencix.view.main.compose.HistoricalDatePickerSheet
import com.eliormachlev.currencix.view.main.compose.MainDisplay
import com.eliormachlev.currencix.view.main.compose.MainDisplayCallbacks
import com.eliormachlev.currencix.view.main.compose.MainKeypad
import com.eliormachlev.currencix.view.main.compose.MainKeypadCallbacks
import com.eliormachlev.currencix.view.main.compose.MainScreen
import com.eliormachlev.currencix.view.main.compose.QuickConversionsSheet
import com.eliormachlev.currencix.view.main.compose.RecentPairsRow
import com.eliormachlev.currencix.view.navigation.AppNavigator
import com.eliormachlev.currencix.view.navigation.LocalPaneRole
import com.eliormachlev.currencix.view.navigation.PaneRole
import com.eliormachlev.currencix.view.navigation.Screen
import com.eliormachlev.currencix.view.navigation.paneRole
import com.eliormachlev.currencix.view.preference.compose.ProviderPickerDialog
import com.eliormachlev.currencix.view.scan.PriceScan
import com.eliormachlev.currencix.view.scan.ScannedPricesSheet
import com.eliormachlev.currencix.view.scan.rememberPriceScan
import com.eliormachlev.currencix.view.scan.textReader
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Which of the converter's sheets / dialogs is open. Saved, so an open sheet
// survives rotation.
private enum class ConverterOverlay { ProviderPicker, QuickConversions, HistoricalDatePicker }

private const val RECENT_PAIR_SETTLE_MILLIS = 2_000L
private val RECENT_PAIRS_MARGIN = 16.dp

/** The converter screen: top bar, drawer, hero card, keypad and their sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConverterRoute(
    host: ConverterHost,
    navigator: AppNavigator,
    foldingFeature: FoldingFeature?,
) {
    val viewModel = host.viewModel
    val isUpdating by viewModel.isRefreshing().collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var overlay by rememberSaveable { mutableStateOf<ConverterOverlay?>(null) }
    val startReveal = remember { host.takeWordmarkReveal() }
    ReportVisibleWhileComposed(host.status)
    val paneRole = LocalPaneRole.current
    val destinations = remember(viewModel, navigator, paneRole) { ConverterDestinations(viewModel, navigator, paneRole) }
    if (paneRole == PaneRole.List) TimelineFollowsPair(viewModel, navigator)
    val onLeaveViaDrawer = rememberDrawerClosedOnReturn(drawerState)
    // Constant per build: only the play flavor can read a photo's text.
    val scan = if (textReader != null) converterPriceScan(viewModel) else null
    val onDrawerItem: (DrawerAction) -> Unit = { action ->
        scope.launch { drawerState.slideTo(DrawerValue.Closed) }
        onLeaveViaDrawer(action)
        when (action) {
            DrawerAction.Refresh -> viewModel.forceUpdateExchangeRate()
            DrawerAction.Share -> scope.launch { host.share.share() }
            DrawerAction.ScanPrice -> scan?.start()
            else -> destinations.open(action) { overlay = it }
        }
    }

    ProvideOnboardingAnchors {
        MainScreen(
            drawerState = drawerState,
            topBar = {
                ConverterTopBar(
                    drawerState = drawerState,
                    onToggleDrawer = { scope.launch { drawerState.toggle() } },
                    actions = destinations.topBarActions { overlay = it },
                    startReveal = startReveal,
                    onFirstFrame = host.onWordmarkFirstFrame,
                )
            },
            isRefreshing = isUpdating,
            onRefresh = viewModel::forceUpdateExchangeRate,
            isRefreshDrawerEnabled = !isUpdating,
            onDrawerItem = onDrawerItem,
            foldingFeature = foldingFeature,
            displayContent = {
                ConverterDisplay(
                    host = host,
                    onOpenFees = destinations::openFees,
                    onOpenProvider = { overlay = ConverterOverlay.ProviderPicker },
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
    )
}

// The screens the converter opens, with the arguments they take from it.
// [paneRole] is the converter's pane in the two-pane layout (null otherwise).
private class ConverterDestinations(
    private val viewModel: MainViewModel,
    private val navigator: AppNavigator,
    private val paneRole: PaneRole?,
) {
    fun openTimeline() {
        val from = viewModel.getBaseCurrency().value ?: return
        val to = viewModel.getDestinationCurrency().value ?: return
        openDetail(Screen.Timeline(from, to))
    }

    // A null side is fine here — CartViewModel.seedFromMain keeps the cart's
    // own pair or resolves just the missing side.
    fun openCart() = openDetail(Screen.Cart(viewModel.getBaseCurrency().value, viewModel.getDestinationCurrency().value))

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

    fun openSettings() = navigator.navigate(Screen.Settings)

    /** The top bar's shortcuts: two screens and two sheets ([showOverlay]). */
    fun topBarActions(showOverlay: (ConverterOverlay) -> Unit) =
        ConverterTopBarActions(
            onTimeline = ::openTimeline,
            onCart = ::openCart,
            onQuickConversions = { showOverlay(ConverterOverlay.QuickConversions) },
            onHistoricalRates = { showOverlay(ConverterOverlay.HistoricalDatePicker) },
        )

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
            DrawerAction.Refresh, DrawerAction.Share, DrawerAction.ScanPrice -> Unit
        }
    }
}

// Opens / closes the drawer on the app's panel spring (Motion.settle) —
// quicker than Material's own drawer motion, which open() / close() use.
private suspend fun DrawerState.slideTo(value: DrawerValue) = animateTo(value, Motion.settle())

private suspend fun DrawerState.toggle() = slideTo(if (isOpen) DrawerValue.Closed else DrawerValue.Open)

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

// The camera price scan: one price found goes straight into the converter;
// several are offered in a sheet; none says so in the snackbar.
@Composable
private fun converterPriceScan(viewModel: MainViewModel): PriceScan {
    val snackbar = LocalAppSnackbar.current
    val context = LocalContext.current
    val base = viewModel.getBaseCurrency().observeAsState().value
    val dest = viewModel.getDestinationCurrency().observeAsState().value
    var found by remember { mutableStateOf<List<ParsedPrice>?>(null) }
    val scan =
        rememberPriceScan(
            preferred = listOfNotNull(base, dest),
            onPrices = { prices -> if (prices.size == 1) takeScannedPrice(viewModel, prices.single()) else found = prices },
            onMessage = { snackbar.showOrToast(context, it) },
        )
    found?.let { prices ->
        ScannedPricesSheet(
            prices = prices,
            fallbackCurrency = base,
            onPick = { takeScannedPrice(viewModel, it) },
            onDismiss = { found = null },
        )
    }
    return scan
}

// The price's currency becomes the base (converting into the usual target,
// see targetCurrency) and its amount is typed in. A price with no currency
// keeps the pair and just sets the amount.
private fun takeScannedPrice(
    viewModel: MainViewModel,
    price: ParsedPrice,
) {
    val base = viewModel.getBaseCurrency().value
    val dest = viewModel.getDestinationCurrency().value
    val from = price.currency
    if (from != null && base != null && dest != null) {
        viewModel.setCurrencyPair(CurrencyPair(from, targetCurrency(from, base, dest)))
    }
    viewModel.setAmount(price.amount)
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
) {
    val viewModel = host.viewModel
    when (overlay) {
        ConverterOverlay.ProviderPicker -> {
            val current by host.preferenceModel.apiProvider.collectAsStateWithLifecycle()
            ProviderPickerDialog(
                selected = current,
                onDismiss = onDismiss,
                onPicked = host.preferenceModel::setApiProvider,
            )
        }
        ConverterOverlay.QuickConversions ->
            QuickConversionsSheet(
                viewModel = viewModel,
                onSwap = viewModel::swapCurrencies,
                onOpenFees = onOpenFees,
                onDismiss = onDismiss,
            )
        ConverterOverlay.HistoricalDatePicker ->
            HistoricalDatePickerSheet(
                initial = viewModel.getHistoricalDate(),
                onPick = viewModel::setHistoricalDate,
                onDismiss = onDismiss,
            )
        null -> Unit
    }
}

// The hero card, with its copy / fees / provider shortcuts.
@Composable
private fun ConverterDisplay(
    host: ConverterHost,
    onOpenFees: () -> Unit,
    onOpenProvider: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    val banner by host.status.banner
    val database = remember(context) { Database(context) }
    val pattern by database.getDateFormat().observeAsState(DEFAULT_DATE_PATTERN)
    MainDisplay(
        viewModel = host.viewModel,
        callbacks =
            MainDisplayCallbacks(
                onCopy = { text -> copyToClipboard(context, text, snackbar) },
                onOpenFees = onOpenFees,
                onOpenProvider = onOpenProvider,
                onSwapLongPress = onOpenFees,
            ),
        dateFormatPattern = pattern,
        banner = banner,
        captureController = host.share.heroCapture,
    )
    ConverterRecentPairs(host.viewModel, database)
}

// One-tap chips for the pairs used before the one on screen. A pair counts
// as "used" once it has stayed on screen for RECENT_PAIR_SETTLE_MILLIS, so
// stepping through the picker doesn't fill the row with pass-throughs.
@Composable
private fun ConverterRecentPairs(
    viewModel: MainViewModel,
    database: Database,
) {
    val base = viewModel.getBaseCurrency().observeAsState().value
    val dest = viewModel.getDestinationCurrency().observeAsState().value
    val recents by database.getRecentPairsFlow().collectAsStateWithLifecycle(emptyList())
    val current = if (base != null && dest != null && base != dest) CurrencyPair(base, dest) else null
    LaunchedEffect(current) {
        current ?: return@LaunchedEffect
        delay(RECENT_PAIR_SETTLE_MILLIS)
        database.addRecentPair(current)
    }
    val others = remember(recents, current) { recents.filterNot { it.isSameCurrencies(current) }.toImmutableList() }
    RecentPairsRow(
        pairs = others,
        onPick = viewModel::setCurrencyPair,
        contentPadding = PaddingValues(horizontal = RECENT_PAIRS_MARGIN),
        modifier = Modifier.padding(top = RECENT_PAIRS_MARGIN),
    )
}

// The in-app keypad. The picker only offers in-app variants, so the
// preference feeds straight into MainKeypad.
@Composable
private fun ConverterKeypad(viewModel: MainViewModel) {
    val isExpanded by viewModel.isExpandedKeypadEnabled.collectAsStateWithLifecycle()
    val nextParen by viewModel.nextParen().observeAsState('(')
    MainKeypad(
        isExpandedKeypad = isExpanded,
        nextParen = nextParen,
        callbacks =
            MainKeypadCallbacks(
                onDigit = viewModel::addNumber,
                onDecimal = viewModel::addDecimal,
                onOperator = { op -> op.apply(viewModel) },
                onPercent = viewModel::addPercent,
                onParens = viewModel::applyNextParen,
                onDelete = viewModel::delete,
                onDeleteLong = viewModel::clear,
            ),
    )
}

// First-run onboarding overlay (#147). Reads the persisted `hasSeenOnboarding`
// gate straight from Database (Compose scope owns the flow subscription) so
// the flip on Skip/Finish is written *and* recomposes this host in one step.
@Composable
private fun OnboardingSpotlightHost() {
    val context = LocalContext.current
    val db = remember(context) { Database(context) }
    val hasSeen by db.getHasSeenOnboardingFlow().collectAsStateWithLifecycle(
        initialValue = db.getHasSeenOnboardingBlocking(),
    )
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
                onAction = { db.setAutoRefreshEnabled(true) },
            ),
        )
    Spotlight(
        steps = steps,
        skipLabel = stringResource(R.string.onboarding_skip),
        nextLabel = stringResource(R.string.onboarding_next),
        finishLabel = stringResource(R.string.onboarding_finish),
        onDismiss = { db.setHasSeenOnboarding(true) },
    )
}

private fun copyToClipboard(
    context: Context,
    text: CharSequence,
    snackbar: AppSnackbar?,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
    // Android 13+ confirms copies itself (with a preview); a second message
    // on top of the system's would be noise.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        snackbar.showOrToast(context, context.getString(R.string.copied_to_clipboard, text).fromHtmlLegacy())
    }
}
