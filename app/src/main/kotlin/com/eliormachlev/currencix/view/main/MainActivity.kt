package com.eliormachlev.currencix.view.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.NetworkStatusLiveData
import com.eliormachlev.currencix.util.SHARE_IMAGES_SUBDIR
import com.eliormachlev.currencix.util.buildShareChooser
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.filenameTimestampNow
import com.eliormachlev.currencix.util.fromHtmlLegacy
import com.eliormachlev.currencix.util.hapticTap
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.stripRtlMark
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.util.toPngBytes
import com.eliormachlev.currencix.view.BaseActivity
import com.eliormachlev.currencix.view.cart.CartActivity
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.onboarding.OnboardingAnchor
import com.eliormachlev.currencix.view.compose.onboarding.ProvideOnboardingAnchors
import com.eliormachlev.currencix.view.compose.onboarding.Spotlight
import com.eliormachlev.currencix.view.compose.onboarding.SpotlightStep
import com.eliormachlev.currencix.view.compose.theme.Wordmark
import com.eliormachlev.currencix.view.main.compose.BannerContent
import com.eliormachlev.currencix.view.main.compose.BannerKind
import com.eliormachlev.currencix.view.main.compose.DrawerAction
import com.eliormachlev.currencix.view.main.compose.HeroCaptureController
import com.eliormachlev.currencix.view.main.compose.HistoricalDatePickerSheet
import com.eliormachlev.currencix.view.main.compose.MainDisplay
import com.eliormachlev.currencix.view.main.compose.MainDisplayCallbacks
import com.eliormachlev.currencix.view.main.compose.MainKeypad
import com.eliormachlev.currencix.view.main.compose.MainKeypadCallbacks
import com.eliormachlev.currencix.view.main.compose.MainScreen
import com.eliormachlev.currencix.view.main.compose.QuickConversionsSheet
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.view.preference.compose.ProviderPickerDialog
import com.eliormachlev.currencix.view.timeline.TimelineActivity
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import com.eliormachlev.currencix.viewmodel.main.Operator
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Fee-percent precision on the share fee-stamp line — matches the on-screen
// FeeChip so shared text reads the same as the visible pill.
private const val FEE_PERCENT_DECIMAL_PLACES = 2

// Rate footer line ("1 USD = 3.028 ILS") — matches the on-screen hero footer
// precision (see FOOTER_RATE_DECIMAL_PLACES in MainDisplay.kt).
private const val SHARE_RATE_DECIMAL_PLACES = 4

// Fee-name joiner for the share fee stamp — matches the on-screen FeeChip.
private const val SHARE_FEE_NAME_SEPARATOR = ", "

// Hero-card snapshot chooser payload. MIME + extension pair kept together so
// the file name and Intent's `type` never drift out of sync.
private const val SHARE_IMAGE_MIME = "image/png"
private const val SHARE_IMAGE_EXT = ".png"

// Default date pattern used before the user-configured pattern LiveData emits.
private const val DEFAULT_DATE_PATTERN = "dd/MM/yy HH:mm"

private const val WORDMARK_TITLE_SP = 26f

// Matches Material's standard "medium container" motion duration — long
// enough to read as a morph, short enough to feel responsive on the tap.
private const val HAMBURGER_MORPH_MILLIS = 320

// Splash → wordmark hand-off overlap (#155). The platform splash icon
// fades out over this window while the Compose wordmark's × reveal
// (WORDMARK_REVEAL_MILLIS = 520ms) is already running — a small overlap
// hides the seam that would otherwise show if we waited for the icon to
// disappear before starting the reveal. 150ms lands roughly at the reveal's
// first-quarter frames, so the eye never catches a hard cut.
private const val SPLASH_EXIT_FADE_MILLIS = 150L

// Isolated composable so per-frame progress reads only recompose this
// (empty) node — hoisting the read into MainScreen's setContent forced
// the whole tree to recompose per frame during the morph, showing as
// visible chop on the drawer/main content. The `val current = progress`
// line matters: it forces a snapshot read *during composition*, so the
// State subscription is established and the composable actually
// recomposes each frame while animateFloatAsState is running. Reading
// `progress` only inside SideEffect's lambda would defer the read to
// after composition (no subscription → no recomposition → no morph).
@Composable
private fun DrawerArrowSync(
    drawerState: DrawerState,
    drawable: DrawerArrowDrawable,
) {
    val progress by animateFloatAsState(
        targetValue = if (drawerState.targetValue == DrawerValue.Open) 1f else 0f,
        animationSpec = tween(durationMillis = HAMBURGER_MORPH_MILLIS),
        label = "hamburgerMorph",
    )
    val current = progress
    SideEffect { drawable.progress = current }
}

class MainActivity : BaseActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var preferenceModel: PreferenceViewModel

    // Cached date pattern shared with the share-sheet and offline-banner
    // formatters. Compose reads the same pattern via observeAsState so its
    // rate-footer stays in sync without needing a push from here.
    private var dateFormatPattern: String = DEFAULT_DATE_PATTERN

    // ActionBar hamburger → compose drawer bridge. The drawer state lives
    // inside composition (via rememberDrawerState) so its animation anchors
    // are always current; composition assigns a toggle lambda into this field
    // once it's ready, and the ActionBar home item invokes it. Nullable so we
    // no-op if the click somehow races the first composition.
    private var toggleDrawer: (() -> Unit)? = null

    // Composition-scoped opener for the Compose provider picker (LedgerBottomSheet).
    // Set by a DisposableEffect inside setContent so any non-compose caller
    // (drawer tap, rate-footer provider link) can trigger the sheet without
    // owning its own state. Nullable so an early tap before first composition
    // is a safe no-op.
    private var openProviderPicker: (() -> Unit)? = null

    // Same pattern as [openProviderPicker] for the quick-conversions sheet —
    // set by a DisposableEffect in MainRoot so menu/drawer taps can open the
    // Compose-native sheet without owning its own state.
    private var openQuickConversions: (() -> Unit)? = null

    // Same pattern for the historical-rates date picker sheet. Menu tap and
    // drawer tap both go through this so the sheet's state stays inside
    // MainRoot rather than requiring a Context/AlertDialog.
    private var openHistoricalDatePicker: (() -> Unit)? = null

    // Morphing hamburger ↔ arrow indicator hosted on the ActionBar. The
    // ActionBar customView slot only takes a Drawable, so we own it here and
    // let composition push a 0..1 progress from the drawer state each frame.
    private val drawerArrow: DrawerArrowDrawable by lazy { createDrawerArrow() }

    // Bridges the Compose hero card's GraphicsLayer to the share intent. The
    // hero card assigns its capture lambda on first composition; we invoke it
    // from lifecycleScope when the Share drawer item fires.
    private val heroCaptureController = HeroCaptureController()

    // State bridges Compose reads via observeAsState / mutableStateOf.
    private val foldingFeatureState = mutableStateOf<FoldingFeature?>(null)
    private val bannerState = mutableStateOf<BannerContent?>(null)
    private var isOnline: Boolean = true
    private var latestRatesDate: LocalDate? = null
    private var latestRatesTime: LocalTime? = null
    private var historicalDate: LocalDate? = null

    // True when the most recent refresh attempt failed (5xx, timeout, DNS,
    // etc.) while the device was online. Cleared once a new rates payload
    // arrives — a successful update is the definitive "provider is back".
    private var lastRefreshFailed: Boolean = false

    // Splash-screen keep-on-screen gate (#155). Flipped to true by the
    // wordmark's onFirstFrame callback so the platform splash holds until
    // Compose is pixel-ready to run its reveal, then releases into the
    // exit animation. Plain Boolean — the platform polls it from a pre-draw
    // listener on the main thread, and the wordmark writes it from a
    // LaunchedEffect (also main), so no snapshot state or volatility is
    // needed.
    private var firstContentReady: Boolean = false

    // True only for the cold-start onCreate — recreations from config change
    // or process-death restore skip the wordmark reveal so the affordance
    // isn't repeated every rotation. Set once in onCreate and read from
    // installComposeWordmarkTitle().
    private var isColdStart: Boolean = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() must run before super.onCreate() per the
        // androidx docs — it swaps the launcher-splash theme (AppTheme.Splash)
        // for postSplashScreenTheme (AppTheme) and installs the exit-animation
        // listener. Keep the splash on-screen until Compose's first frame is
        // ready, then hand off to the in-Compose wordmark reveal.
        //
        // Gate the animated reveal on savedInstanceState == null so config
        // change / process death restore don't re-run the reveal — cold start
        // is the only path that deserves the affordance.
        isColdStart = savedInstanceState == null
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !firstContentReady }
        if (isColdStart) {
            splashScreen.setOnExitAnimationListener(::fadeOutSplashIcon)
        } else {
            // No splash on warm restart — release the gate immediately so
            // the keep-on-screen check never sees a stale `false`.
            firstContentReady = true
        }

        super.onCreate(savedInstanceState)

        // model
        this.viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        this.preferenceModel = ViewModelProvider(this)[PreferenceViewModel::class.java]

        // Compose owns the entire screen tree — no XML layout involved.
        val composeHost =
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { AppTheme { MainRoot() } }
            }
        setContentView(composeHost)

        installComposeWordmarkTitle()
        installHamburger()

        // heavy lifting
        observe()

        // foldable devices
        observeFoldingFeature { feature -> foldingFeatureState.value = feature }
    }

    // Compose root — hoisted out of onCreate to keep the lifecycle method
    // small (detekt LongMethod). Holds the drawer state, the picker-overlay
    // flag, and the DisposableEffect that wires activity callbacks
    // (toggleDrawer / openProviderPicker) into the compose tree.
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainRoot() {
        val banner by bannerState
        val foldingFeature by foldingFeatureState
        val isUpdating by viewModel.isUpdating().collectAsStateWithLifecycle()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var providerPickerVisible by remember { mutableStateOf(false) }
        var quickConversionsVisible by remember { mutableStateOf(false) }
        var historicalDatePickerVisible by remember { mutableStateOf(false) }
        DisposableEffect(drawerState, scope) {
            toggleDrawer = {
                scope.launch {
                    if (drawerState.isOpen) drawerState.close() else drawerState.open()
                }
            }
            openProviderPicker = { providerPickerVisible = true }
            openQuickConversions = { quickConversionsVisible = true }
            openHistoricalDatePicker = { historicalDatePickerVisible = true }
            onDispose {
                toggleDrawer = null
                openProviderPicker = null
                openQuickConversions = null
                openHistoricalDatePicker = null
            }
        }
        DrawerArrowSync(drawerState = drawerState, drawable = drawerArrow)
        ProvideOnboardingAnchors {
            MainScreen(
                drawerState = drawerState,
                isRefreshing = isUpdating,
                onRefresh = viewModel::forceUpdateExchangeRate,
                isRefreshDrawerEnabled = !isUpdating,
                onDrawerItem = { action -> onDrawerAction(action) { scope.launch { drawerState.close() } } },
                foldingFeature = foldingFeature,
                displayContent = { MainDisplayContent(banner) },
                keypadContent = { MainKeypadContent() },
            )
            OnboardingSpotlightHost()
        }
        MainRootOverlays(
            providerPickerVisible = providerPickerVisible,
            dismissProviderPicker = { providerPickerVisible = false },
            quickConversionsVisible = quickConversionsVisible,
            dismissQuickConversions = { quickConversionsVisible = false },
            historicalDatePickerVisible = historicalDatePickerVisible,
            dismissHistoricalDatePicker = { historicalDatePickerVisible = false },
        )
    }

    // Composition-scoped overlay host — the three sheets/dialogs MainRoot
    // opens on top of MainScreen. Extracted so MainRoot itself stays under
    // the detekt LongMethod threshold and the overlay wiring reads on its own.
    @Composable
    @Suppress("LongParameterList")
    private fun MainRootOverlays(
        providerPickerVisible: Boolean,
        dismissProviderPicker: () -> Unit,
        quickConversionsVisible: Boolean,
        dismissQuickConversions: () -> Unit,
        historicalDatePickerVisible: Boolean,
        dismissHistoricalDatePicker: () -> Unit,
    ) {
        if (providerPickerVisible) {
            val current by preferenceModel.apiProvider.collectAsStateWithLifecycle()
            ProviderPickerDialog(
                selected = current,
                onDismiss = dismissProviderPicker,
                onPicked = { provider -> preferenceModel.setApiProvider(provider) },
            )
        }
        if (quickConversionsVisible) {
            QuickConversionsSheet(
                viewModel = viewModel,
                onSwap = { toggleEvent(null) },
                onOpenFees = ::openFeesSettings,
                onDismiss = dismissQuickConversions,
            )
        }
        if (historicalDatePickerVisible) {
            HistoricalDatePickerSheet(
                initial = viewModel.getHistoricalDate(),
                onPick = viewModel::setHistoricalDate,
                onDismiss = dismissHistoricalDatePicker,
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            hapticTap()
            toggleDrawer?.invoke()
            return true
        }
        hapticTap()
        return when (item.itemId) {
            R.id.timeline -> openTimelineActivity()
            R.id.quick_conversions -> {
                openQuickConversions?.invoke()
                true
            }
            R.id.date_picker -> {
                openHistoricalDatePicker?.invoke()
                true
            }
            R.id.cart -> {
                startActivity(Intent(this, CartActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Every drawer entry defers to a single handler; [dismiss] closes the
    // ModalNavigationDrawer via its composition-scoped state (passed in from
    // setContent so we don't touch the drawer state from outside compose).
    private fun onDrawerAction(
        action: DrawerAction,
        dismiss: () -> Unit,
    ) {
        hapticTap()
        dismiss()
        when (action) {
            DrawerAction.Timeline -> openTimelineActivity()
            DrawerAction.Cart -> startActivity(Intent(this, CartActivity::class.java))
            DrawerAction.QuickConversions -> openQuickConversions?.invoke()
            DrawerAction.DatePicker -> openHistoricalDatePicker?.invoke()
            DrawerAction.Refresh -> viewModel.forceUpdateExchangeRate()
            DrawerAction.Share -> shareCurrentConversion()
            DrawerAction.ChangeApi -> showApiProviderPicker()
            DrawerAction.Fees -> startActivity(PreferenceActivity.feesIntent(this))
            DrawerAction.Settings -> startActivity(Intent(this, PreferenceActivity::class.java))
        }
    }

    // Swap the default up-arrow indicator for a morphing hamburger — the
    // Compose ModalNavigationDrawer has no built-in ActionBar toggle, so we
    // drive the click by hand from onOptionsItemSelected(android.R.id.home)
    // and the drawable's progress from the drawer state (see setContent).
    private fun installHamburger() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
            setHomeAsUpIndicator(drawerArrow)
        }
    }

    // Tinted to match the ActionBar icons (colorControlNormal). We resolve the
    // attribute against the current theme rather than hardcoding — the value
    // differs across light/dark/OLED.
    private fun createDrawerArrow(): DrawerArrowDrawable {
        val tv = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorControlNormal, tv, true)
        val tint =
            if (tv.resourceId != 0) {
                ContextCompat.getColor(this, tv.resourceId)
            } else {
                tv.data
            }
        return DrawerArrowDrawable(this).apply {
            color = tint
            progress = 0f
        }
    }

    private fun showApiProviderPicker() {
        openProviderPicker?.invoke()
    }

    private fun shareCurrentConversion() {
        val text = buildShareText() ?: return
        // Drawer just started closing when this fires; wait one frame so the
        // closing animation doesn't leak into the snapshot. If the hero card
        // hasn't registered yet (activity backgrounded, first composition
        // still running), fall back to a text-only share so the tap is never
        // a no-op. AndroidUiDispatcher.Main provides the MonotonicFrameClock
        // that withFrameNanos requires — lifecycleScope's Dispatchers.Main
        // does not.
        lifecycleScope.launch(AndroidUiDispatcher.Main) {
            withFrameNanos { }
            val bitmap = heroCaptureController.capture()
            if (bitmap == null) {
                Timber.w("shareCurrentConversion: hero capture returned null, falling back to text share")
            }
            val chooser =
                if (bitmap != null) {
                    buildShareChooser(
                        context = this@MainActivity,
                        subdir = SHARE_IMAGES_SUBDIR,
                        filename = "currencix-${filenameTimestampNow()}$SHARE_IMAGE_EXT",
                        mimeType = SHARE_IMAGE_MIME,
                        bytes = bitmap.toPngBytes(),
                        extraText = text,
                    )
                } else {
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        },
                        null,
                    )
                }
            startActivity(chooser)
        }
    }

    // Assemble the share sheet's EXTRA_TEXT payload:
    //   $50 USD = ₪151.4 ILS
    //   +1% MAX = ₪152.91 ILS       (only when a fee stack is active)
    //
    //   -- Based on Bank of Israel, 18/09/26, $1 USD = ₪3.028 ILS
    // Composed from the on-screen values so it honors the currently typed
    // amount and the active fee stack (matching what the user sees).
    private fun buildShareText(): String? {
        val base = viewModel.getBaseCurrency().value ?: return null
        val dest = viewModel.getDestinationCurrency().value ?: return null
        val rates = viewModel.getExchangeRates().value ?: return null
        val rateList = rates.rates ?: return null
        if (rateList.none { it.currency == base } || rateList.none { it.currency == dest }) return null
        val places = viewModel.getDecimalPlaces().value
        val amount = viewModel.getCurrentBaseValueAsNumber().value ?: BigDecimal.ZERO
        val result = viewModel.getResultAsNumber().value ?: BigDecimal.ZERO
        val main =
            buildShareConversionLine(
                base = base,
                dest = dest,
                baseAmount = amount.toHumanReadableNumber(this, trim = true, decimalPlaces = places),
                destAmount = result.toHumanReadableNumber(this, trim = true, decimalPlaces = places),
            )
        val feeLine = buildShareFeeLine(dest, places)
        val footer = buildShareFooter(base, dest, rates) ?: return null
        return buildString {
            append(main)
            if (feeLine != null) {
                append('\n')
                append(feeLine)
            }
            append("\n\n-- ")
            append(footer)
        }
    }

    // "<baseSymbol><baseAmount> <baseIso> = <destSymbol><destAmount> <destIso>"
    // — pulled into a helper so the fee line, main line, and the rate stamp in
    // the footer all share one template (and thus one localization string).
    private fun buildShareConversionLine(
        base: Currency,
        dest: Currency,
        baseAmount: String,
        destAmount: String,
    ): String =
        getString(
            R.string.share_conversion_line,
            base.symbolOrIso(),
            baseAmount,
            base.iso4217Alpha(),
            dest.symbolOrIso(),
            destAmount,
            dest.iso4217Alpha(),
        )

    // Fee stamp line beneath the main conversion. Mirrors the on-screen
    // FeeChip: "<+pct> <NAMES> = <destSymbol><trueCost> <destIso>". Skipped
    // when no fee stack is active, or when the destination true-cost is not
    // yet computed.
    private fun buildShareFeeLine(
        dest: Currency,
        places: Int,
    ): String? {
        val stack = viewModel.getFeeStack().value ?: return null
        if (stack.isNeutralFeeStack()) return null
        val trueCost = viewModel.getResultWithFeesAsNumber().value ?: return null
        val percent =
            stack
                .feePercentDelta(FEE_PERCENT_DECIMAL_PLACES)
                .toHumanReadableNumber(this, showPositiveSign = true, suffix = "%", trim = true)
        val names =
            viewModel
                .getActiveFees()
                .value
                .orEmpty()
                .mapNotNull { it.name.trim().takeIf(String::isNotEmpty) }
                .joinToString(SHARE_FEE_NAME_SEPARATOR)
                .uppercase()
        val stamp = if (names.isEmpty()) percent else "$percent $names"
        return getString(
            R.string.share_fee_line,
            stamp,
            dest.symbolOrIso(),
            trueCost.toHumanReadableNumber(this, trim = true, decimalPlaces = places),
            dest.iso4217Alpha(),
        )
    }

    // "Based on <provider>, <date>, <$1 base = <sym><rate> <destIso>>".
    // The rate stamp reuses [buildShareConversionLine] so its format matches
    // the main line exactly (symbol prefixes, spacing, ISO tail). The rate
    // tail is appended in Kotlin rather than added as a %3$s placeholder on
    // `share_footer` so the 30+ existing translations don't need to grow an
    // extra argument slot (lint's StringFormatMatches would otherwise reject
    // the arg-count mismatch).
    private fun buildShareFooter(
        base: Currency,
        dest: Currency,
        rates: ExchangeRates,
    ): String? {
        val providerName = rates.provider?.getName(this) ?: return null
        val dateString = formatRatesTimestamp(rates.date, rates.time) ?: return null
        val rateList = rates.rates ?: return null
        val baseValue = rateList.firstOrNull { it.currency == base }?.value ?: return null
        val destValue = rateList.firstOrNull { it.currency == dest }?.value ?: return null
        val perOne = destValue.divide(baseValue, MathContext.DECIMAL128)
        val rateLine =
            buildShareConversionLine(
                base = base,
                dest = dest,
                baseAmount = "1",
                destAmount = perOne.toHumanReadableNumber(this, trim = true, decimalPlaces = SHARE_RATE_DECIMAL_PLACES),
            )
        return "${getString(R.string.share_footer, providerName, dateString)}, $rateLine"
    }

    // Combine [date] and optional [time] into a single formatted string using
    // the user's configured pattern. When [time] is null the time portion is
    // stripped from the pattern first so users on "date-only" don't see a
    // trailing "00:00". RTL marks injected by some locale formatters are
    // stripped so a right-side timestamp stays flush with the label.
    private fun formatRatesTimestamp(
        date: LocalDate?,
        time: LocalTime?,
    ): String? {
        if (date == null) return null
        val pattern = if (time != null) dateFormatPattern else stripTimePattern(dateFormatPattern)
        val temporal = if (time != null) date.atTime(time) else date
        return DateTimeFormatter.ofPattern(pattern).format(temporal).stripRtlMark()
    }

    private fun openTimelineActivity(): Boolean {
        val from = viewModel.getBaseCurrency().value ?: return false
        val to = viewModel.getDestinationCurrency().value ?: return false
        startActivity(TimelineActivity.newIntent(this, from, to))
        return true
    }

    private fun clipboardManager(): ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun openFeesSettings() {
        startActivity(PreferenceActivity.feesIntent(this))
    }

    private fun copyToClipboard(copyText: CharSequence) {
        clipboardManager().setPrimaryClip(ClipData.newPlainText(null, copyText))
        val message = getString(R.string.copied_to_clipboard, copyText).fromHtmlLegacy()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun observe() {
        Database(this).getDateFormat().observe(this) { pattern ->
            dateFormatPattern = pattern
            recomputeBanner()
        }
        viewModel.getExchangeRates().observe(this) { rates ->
            latestRatesDate = rates?.date
            latestRatesTime = rates?.time
            // Fresh payload means the provider is reachable again.
            if (rates != null) lastRefreshFailed = false
            recomputeBanner()
        }
        viewModel.getHistoricalLiveDate().observe(this) { date ->
            historicalDate = date
            recomputeBanner()
        }
        viewModel.getError().observe(this) { message ->
            showErrorSnackbar(message)
            // Only treat as "provider unreachable" when the device itself
            // has connectivity — otherwise the OFFLINE banner already tells
            // the story.
            if (!message.isNullOrEmpty() && isOnline) {
                lastRefreshFailed = true
                recomputeBanner()
            }
        }
        NetworkStatusLiveData(this).observe(this) { online ->
            isOnline = online
            recomputeBanner()
        }
    }

    // Ranking: Offline > Unreachable > Historical. Each condition subsumes
    // the "rates aren't fresh" signal of the next, so the most actionable
    // signal wins the pill.
    private fun recomputeBanner() {
        bannerState.value =
            when {
                !isOnline -> {
                    val date = latestRatesDate
                    val text =
                        if (date != null) {
                            getString(R.string.offline_banner_with_date, formatRatesTimestamp(date, latestRatesTime).orEmpty())
                        } else {
                            getString(R.string.offline_banner_no_data)
                        }
                    BannerContent(BannerKind.Offline, text)
                }
                lastRefreshFailed -> {
                    val date = latestRatesDate
                    val text =
                        if (date != null) {
                            getString(
                                R.string.unreachable_banner_with_date,
                                formatRatesTimestamp(date, latestRatesTime).orEmpty(),
                            )
                        } else {
                            getString(R.string.unreachable_banner_no_data)
                        }
                    BannerContent(BannerKind.Unreachable, text)
                }
                historicalDate != null -> {
                    val text = getString(R.string.historical_banner, formatRatesTimestamp(historicalDate, null).orEmpty())
                    BannerContent(BannerKind.Historical, text)
                }
                else -> null
            }
    }

    private fun showErrorSnackbar(message: String?) {
        message ?: return
        Toast.makeText(this, message.fromHtmlLegacy(), Toast.LENGTH_LONG).show()
    }

    // capture hardware keyboard input
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        // IMPORTANT: can't work with simple keyCodes here, as depending on the keyboard
        // configuration, wrong values will be returned (e.g. KEYCODE_8 instead of KEYCODE_PLUS).
        val key = event?.keyCharacterMap?.get(keyCode, event.metaState)?.let { Char(it) }
        return handleCharKey(key) || handleControlKey(keyCode)
    }

    private fun handleCharKey(key: Char?): Boolean {
        key ?: return false
        Operator.fromHardware(key)?.let {
            it.apply(viewModel)
            return true
        }
        when {
            key.isDigit() -> viewModel.addNumber(key.toString())
            key == '.' || key == ',' -> viewModel.addDecimal()
            key == '(' -> viewModel.openParen()
            key == ')' -> viewModel.closeParen()
            key == '%' -> viewModel.addPercent()
            else -> return false
        }
        return true
    }

    // Hardware-keyboard input path: KEYCODE_BACK from a physical keyboard is
    // not the same as the gesture-back the GestureBackNavigation lint flags,
    // so route it through onBackPressedDispatcher explicitly.
    @Suppress("GestureBackNavigation")
    private fun handleControlKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> viewModel.delete()
            KeyEvent.KEYCODE_BACK -> super.onBackPressedDispatcher.onBackPressed()
            else -> return false
        }
        return true
    }

    /*
     * swap currencies — invoked by QuickConversionsSheet and by the pure-
     * Compose swap FAB (via callbacks). The Compose FAB calls the underlying
     * viewModel setters directly and does not route through here.
     */
    fun toggleEvent(
        @Suppress("UNUSED_PARAMETER") view: View?,
    ) {
        val from = viewModel.getBaseCurrency().value ?: return
        val to = viewModel.getDestinationCurrency().value ?: return
        if (from == to) return
        viewModel.setBaseCurrency(to)
        viewModel.setDestinationCurrency(from)
    }

    // Hero display composable — inlined so it composes inside the MainScreen
    // tree instead of being hosted on a standalone ComposeView.
    @androidx.compose.runtime.Composable
    private fun MainDisplayContent(banner: BannerContent?) {
        val callbacks =
            MainDisplayCallbacks(
                onCopy = ::copyToClipboard,
                onOpenFees = ::openFeesSettings,
                onOpenProvider = ::showApiProviderPicker,
                onSwapLongPress = ::openFeesSettings,
            )
        val pattern by Database(this).getDateFormat().observeAsState(DEFAULT_DATE_PATTERN)
        MainDisplay(
            viewModel = viewModel,
            callbacks = callbacks,
            dateFormatPattern = pattern,
            banner = banner,
            captureController = heroCaptureController,
        )
    }

    // First-run onboarding overlay (#147). Reads the persisted `hasSeenOnboarding`
    // gate straight from Database (Compose scope owns the flow subscription) so
    // the flip on Skip/Finish is written *and* recomposes this host in one
    // step. `remember(this)` keys Database on the Activity so recreations get
    // a fresh DataStore handle without leaking the previous one.
    @androidx.compose.runtime.Composable
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

    // Compose keypad. System-IME variants surface no on-screen keypad so we
    // collapse them to BASIC for the fallback layout while the IME provides
    // the actual input path.
    @androidx.compose.runtime.Composable
    private fun MainKeypadContent() {
        val callbacks =
            MainKeypadCallbacks(
                onDigit = viewModel::addNumber,
                onDecimal = viewModel::addDecimal,
                onOperator = { op -> op.apply(viewModel) },
                onPercent = viewModel::addPercent,
                onParens = viewModel::applyNextParen,
                onDelete = viewModel::delete,
                onDeleteLong = viewModel::clear,
            )
        val kbType by viewModel.keyboardType.collectAsStateWithLifecycle()
        val nextParen by viewModel.nextParen().observeAsState('(')
        val effective = if (kbType.isSystem) KeyboardType.BASIC else kbType
        MainKeypad(
            keyboardType = effective,
            nextParen = nextParen,
            callbacks = callbacks,
        )
    }

    // Swap the AppCompat ActionBar title for a ComposeView that renders the
    // Wordmark composable ("Currenci×" in Space Grotesk — the final glyph is
    // the multiplication sign, a semantic pun on conversion). Uses the
    // ActionBar customView slot so the action items (chart, cart, timeline,
    // overflow) still lay out normally on the trailing edge.
    private fun installComposeWordmarkTitle() {
        val bar = supportActionBar ?: return
        bar.setDisplayShowTitleEnabled(false)
        bar.setDisplayShowCustomEnabled(true)
        // Local snapshots so the setContent lambda captures the cold-start
        // decision made once in onCreate rather than reading it lazily later.
        val startReveal = isColdStart
        bar.customView =
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    AppTheme {
                        Wordmark(
                            fontSize = WORDMARK_TITLE_SP.sp,
                            startReveal = startReveal,
                            onFirstFrame = { firstContentReady = true },
                        )
                    }
                }
            }
    }

    // Splash exit-animation listener. Fades the platform splash icon over
    // SPLASH_EXIT_FADE_MILLIS while the Compose wordmark's own × reveal
    // (already started via LaunchedEffect on first frame — see Wordmark.kt)
    // runs underneath. The overlap hides the seam that would otherwise
    // appear if we waited for the icon to disappear before starting the
    // reveal. Removes the SplashScreenView at the end so subsequent frames
    // aren't overdrawn by the leftover splash surface.
    //
    // We fade only iconView (not the whole SplashScreenView) so the paper
    // background stays solid underneath until removal — otherwise the
    // Compose paper background would briefly show through a semi-transparent
    // splash surface and read as a flash.
    private fun fadeOutSplashIcon(provider: SplashScreenViewProvider) {
        // Some OEM ROMs (observed on MIUI) return a null iconView from
        // SplashScreenViewProvider.ViewImpl31 — nothing to fade in that case,
        // just remove the splash surface directly so we don't NPE.
        val icon = runCatching { provider.iconView }.getOrNull()
        if (icon == null) {
            provider.remove()
            return
        }
        icon
            .animate()
            .alpha(0f)
            .setDuration(SPLASH_EXIT_FADE_MILLIS)
            .withEndAction { provider.remove() }
            .start()
    }
}
