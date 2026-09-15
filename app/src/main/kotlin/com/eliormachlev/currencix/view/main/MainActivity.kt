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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.NetworkStatusLiveData
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.fromHtmlLegacy
import com.eliormachlev.currencix.util.hapticTap
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.ltrIsolate
import com.eliormachlev.currencix.util.stripRtlMark
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.BaseActivity
import com.eliormachlev.currencix.view.cart.CartActivity
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.theme.Wordmark
import com.eliormachlev.currencix.view.main.compose.BannerContent
import com.eliormachlev.currencix.view.main.compose.BannerKind
import com.eliormachlev.currencix.view.main.compose.DrawerAction
import com.eliormachlev.currencix.view.main.compose.MainDisplay
import com.eliormachlev.currencix.view.main.compose.MainDisplayCallbacks
import com.eliormachlev.currencix.view.main.compose.MainKeypad
import com.eliormachlev.currencix.view.main.compose.MainKeypadCallbacks
import com.eliormachlev.currencix.view.main.compose.MainScreen
import com.eliormachlev.currencix.view.main.compose.showHistoricalDatePickerDialog
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.view.preference.showProviderPickerDialog
import com.eliormachlev.currencix.view.timeline.TimelineActivity
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import com.eliormachlev.currencix.viewmodel.main.Operator
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// fee true-cost / percent formatting for the share-sheet extra
private const val FEE_PERCENT_DECIMAL_PLACES = 2
private const val AMOUNT_DECIMAL_PLACES = 2

// Default date pattern used before the user-configured pattern LiveData emits.
private const val DEFAULT_DATE_PATTERN = "dd/MM/yy HH:mm"

private const val WORDMARK_TITLE_SP = 26f

// Matches Material's standard "medium container" motion duration — long
// enough to read as a morph, short enough to feel responsive on the tap.
private const val HAMBURGER_MORPH_MILLIS = 320

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

    // Morphing hamburger ↔ arrow indicator hosted on the ActionBar. The
    // ActionBar customView slot only takes a Drawable, so we own it here and
    // let composition push a 0..1 progress from the drawer state each frame.
    private val drawerArrow: DrawerArrowDrawable by lazy { createDrawerArrow() }

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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // model
        this.viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        this.preferenceModel = ViewModelProvider(this)[PreferenceViewModel::class.java]

        // Compose owns the entire screen tree — no XML layout involved.
        val composeHost =
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    AppTheme {
                        val banner by bannerState
                        val foldingFeature by foldingFeatureState
                        val isUpdating by viewModel.isUpdating().collectAsStateWithLifecycle()
                        val drawerState = rememberDrawerState(DrawerValue.Closed)
                        val scope = rememberCoroutineScope()
                        DisposableEffect(drawerState, scope) {
                            toggleDrawer = {
                                scope.launch {
                                    if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                }
                            }
                            onDispose { toggleDrawer = null }
                        }
                        DrawerArrowSync(drawerState = drawerState, drawable = drawerArrow)
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
                    }
                }
            }
        setContentView(composeHost)

        installComposeWordmarkTitle()
        installHamburger()

        // heavy lifting
        observe()

        // foldable devices
        observeFoldingFeature { feature -> foldingFeatureState.value = feature }
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
                openQuickConversionsDialog()
                true
            }
            R.id.date_picker -> {
                openHistoricalDatePicker()
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
            DrawerAction.QuickConversions -> openQuickConversionsDialog()
            DrawerAction.DatePicker -> openHistoricalDatePicker()
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
        showProviderPickerDialog(
            context = this,
            current = Database(this).getApiProvider(),
        ) { provider -> preferenceModel.setApiProvider(provider) }
    }

    private fun shareCurrentConversion() {
        val conversion = buildShareConversion() ?: return
        val footer = buildShareFooter(viewModel.getExchangeRates().value)
        val text = if (footer != null) "$conversion\n-- $footer" else conversion
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        startActivity(Intent.createChooser(intent, null))
    }

    // Compose the shared conversion line from the on-screen values so it
    // honors the currently typed amount and the active fee stack (matching
    // what the user sees), rather than the "1 base ≈ result" info footer
    // which is always unit-scaled and fee-free.
    private fun buildShareConversion(): String? {
        val base = viewModel.getBaseCurrency().value ?: return null
        val dest = viewModel.getDestinationCurrency().value ?: return null
        val rates = viewModel.getExchangeRates().value?.rates ?: return null
        if (rates.none { it.currency == base } || rates.none { it.currency == dest }) return null
        val amount = viewModel.getCurrentBaseValueAsNumber().value ?: BigDecimal.ZERO
        val result = viewModel.getResultAsNumber().value ?: BigDecimal.ZERO
        val places = viewModel.getDecimalPlaces().value
        val main =
            getString(
                R.string.info_conversion,
                amount.toHumanReadableNumber(this, trim = true, decimalPlaces = places),
                base.iso4217Alpha(),
                result.toHumanReadableNumber(this, trim = true, decimalPlaces = places),
                dest.iso4217Alpha(),
            )
        val extra = buildShareFeeExtra(base)
        return if (extra != null) "$main\n$extra" else main
    }

    // Small annotation line(s) shown under the shared result: fee amount
    // then cost-with-fee, both on the input side.
    private fun buildShareFeeExtra(base: Currency): String? {
        val stack = viewModel.getFeeStack().value

        fun line(
            prefixRes: Int,
            value: BigDecimal?,
            currency: Currency,
            stackForLine: BigDecimal?,
        ): String? = value?.let { buildFeeAmountLine(prefixRes, it, currency, stackForLine) }
        return listOfNotNull(
            line(R.string.fee_true_cost_prefix, viewModel.getFeeAmount().value, base, stack),
            line(R.string.fee_cost_with_fee_prefix, viewModel.getTrueCost().value, base, null),
        ).takeIf { it.isNotEmpty() }
            ?.joinToString("\n")
    }

    private fun buildShareFooter(rates: ExchangeRates?): String? {
        if (rates == null) return null
        val providerName = rates.provider?.getName(this) ?: return null
        val dateString = formatRatesTimestamp(rates.date, rates.time) ?: return null
        return getString(R.string.share_footer, providerName, dateString)
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

    private fun openQuickConversionsDialog() {
        QuickConversionsDialog().show(supportFragmentManager, null)
    }

    private fun openTimelineActivity(): Boolean {
        val from = viewModel.getBaseCurrency().value ?: return false
        val to = viewModel.getDestinationCurrency().value ?: return false
        startActivity(TimelineActivity.newIntent(this, from, to))
        return true
    }

    private fun openHistoricalDatePicker() {
        showHistoricalDatePickerDialog(
            context = this,
            initial = viewModel.getHistoricalDate(),
            onPick = viewModel::setHistoricalDate,
        )
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

    // "<prefix><amount> <ISO> (<sign><pct>%)" with the amount+ISO isolated LTR
    // so a right-aligned prefix in an RTL locale doesn't flip the number/code
    // pair. The percent tail is omitted when the [stack] is trivial (no fee on
    // this side) or unknown. Used by the share sheet's extra fee lines.
    private fun buildFeeAmountLine(
        prefixRes: Int,
        value: BigDecimal,
        currency: Currency?,
        stack: BigDecimal?,
    ): String {
        val amount = value.toHumanReadableNumber(this, decimalPlaces = AMOUNT_DECIMAL_PLACES)
        val marker = currency?.symbolOrIso().orEmpty()
        val amountWithMarker = if (marker.isEmpty()) amount else "$amount $marker"
        val line = getString(prefixRes) + ltrIsolate(amountWithMarker)
        if (stack == null || stack.isNeutralFeeStack()) return line
        val percent =
            stack
                .feePercentDelta(FEE_PERCENT_DECIMAL_PLACES)
                .toHumanReadableNumber(this, showPositiveSign = true, suffix = "%", trim = true)
        return "$line ${ltrIsolate("($percent)")}"
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
     * swap currencies — invoked by QuickConversionsDialog and by the pure-
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
            fragmentManager = supportFragmentManager,
            callbacks = callbacks,
            dateFormatPattern = pattern,
            banner = banner,
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
        bar.customView =
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    AppTheme {
                        Wordmark(fontSize = WORDMARK_TITLE_SP.sp)
                    }
                }
            }
    }
}
