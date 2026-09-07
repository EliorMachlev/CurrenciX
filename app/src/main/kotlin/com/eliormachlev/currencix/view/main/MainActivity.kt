package com.eliormachlev.currencix.view.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.DatePicker
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.sp
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
import com.eliormachlev.currencix.util.showWithHapticButtons
import com.eliormachlev.currencix.util.stripRtlMark
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.BaseActivity
import com.eliormachlev.currencix.view.cart.CartActivity
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.theme.Wordmark
import com.eliormachlev.currencix.view.main.compose.MainDisplay
import com.eliormachlev.currencix.view.main.compose.MainDisplayCallbacks
import com.eliormachlev.currencix.view.main.compose.MainKeypad
import com.eliormachlev.currencix.view.main.compose.MainKeypadCallbacks
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.view.preference.showProviderPickerDialog
import com.eliormachlev.currencix.view.timeline.TimelineActivity
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import com.eliormachlev.currencix.viewmodel.main.Operator
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val HISTORICAL_MIN_YEAR = 2010
private const val MAX_ERROR_TEXT_LINES = 20

// fee true-cost / percent formatting for the share-sheet extra
private const val FEE_PERCENT_DECIMAL_PLACES = 2
private const val AMOUNT_DECIMAL_PLACES = 2

// Default date pattern used before the user-configured pattern LiveData emits.
private const val DEFAULT_DATE_PATTERN = "dd/MM/yy HH:mm"

private const val WORDMARK_TITLE_SP = 26f

class MainActivity : BaseActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var preferenceModel: PreferenceViewModel

    // Cached date pattern shared with the share-sheet and offline-banner
    // formatters. Compose reads the same pattern via observeAsState so its
    // rate-footer stays in sync without needing a push from here.
    private var dateFormatPattern: String = DEFAULT_DATE_PATTERN

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var drawerItemRefresh: MenuItem? = null

    private lateinit var offlineBanner: MaterialCardView
    private lateinit var offlineBannerText: TextView
    private var isOnline: Boolean = true
    private var latestRatesDate: LocalDate? = null
    private var latestRatesTime: LocalTime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // general layout
        setContentView(R.layout.activity_main)
        installComposeWordmarkTitle()

        // model
        this.viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        this.preferenceModel = ViewModelProvider(this)[PreferenceViewModel::class.java]

        // views owned directly by activity_main.xml
        this.swipeRefresh = findViewById(R.id.swipeRefresh)
        this.offlineBanner = findViewById(R.id.offlineBanner)
        this.offlineBannerText = findViewById(R.id.offlineBannerText)
        this.drawerLayout = findViewById(R.id.main_drawer_layout)
        this.navigationView = findViewById(R.id.main_navigation_view)

        // hero card (pills + amount hero + amount to + rate footer)
        installMainDisplay()

        // compose-based keypad below the hero card. Owns its own row heights
        // so growing the hero card never expands / clips the keys, and vice
        // versa.
        installKeypad()

        // hamburger drawer — leading edge holds every menu action (with icons);
        // the trailing toolbar still shows the four highest-frequency shortcuts.
        installNavigationDrawer()

        // swipe-to-refresh: color scheme (not accessible in xml)
        swipeRefresh.setColorSchemeColors(MaterialColors.getColor(this, R.attr.colorOnPrimary, null))
        swipeRefresh.setProgressBackgroundColorSchemeColor(MaterialColors.getColor(this, R.attr.colorPrimary, null))

        // listeners & stuff
        setListeners()

        // heavy lifting
        observe()

        // foldable devices
        prepareFoldableLayoutChanges()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) return true
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

    // Every drawer entry defers to the same handler; refresh alone needs a
    // gate against re-triggering while an update is already in flight.
    private fun onDrawerItemSelected(item: MenuItem): Boolean {
        hapticTap()
        when (item.itemId) {
            R.id.nav_timeline -> openTimelineActivity()
            R.id.nav_cart -> startActivity(Intent(this, CartActivity::class.java))
            R.id.nav_quick_conversions -> openQuickConversionsDialog()
            R.id.nav_date_picker -> openHistoricalDatePicker()
            R.id.nav_refresh -> viewModel.forceUpdateExchangeRate()
            R.id.nav_share -> shareCurrentConversion()
            R.id.nav_change_api -> showApiProviderPicker()
            R.id.nav_fees -> startActivity(PreferenceActivity.feesIntent(this))
            R.id.nav_settings -> startActivity(Intent(this, PreferenceActivity::class.java))
            else -> return false
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun installNavigationDrawer() {
        drawerToggle =
            ActionBarDrawerToggle(
                this,
                drawerLayout,
                R.string.a11y_open_navigation_drawer,
                R.string.a11y_close_navigation_drawer,
            )
        drawerLayout.addDrawerListener(drawerToggle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        drawerToggle.syncState()
        navigationView.setNavigationItemSelectedListener(::onDrawerItemSelected)
        drawerItemRefresh = navigationView.menu.findItem(R.id.nav_refresh)

        // Route the system back gesture to close the drawer only while it's
        // open; disabled otherwise so back falls through to the default
        // dispatcher (finish activity).
        val closeDrawerCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
        onBackPressedDispatcher.addCallback(this, closeDrawerCallback)
        drawerLayout.addDrawerListener(
            object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerOpened(drawerView: View) {
                    closeDrawerCallback.isEnabled = true
                }

                override fun onDrawerClosed(drawerView: View) {
                    closeDrawerCallback.isEnabled = false
                }
            },
        )
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (::drawerToggle.isInitialized) drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::drawerToggle.isInitialized) drawerToggle.onConfigurationChanged(newConfig)
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
        val places = viewModel.getDecimalPlaces().value ?: AMOUNT_DECIMAL_PLACES
        val main =
            getString(
                R.string.info_conversion,
                amount.toHumanReadableNumber(this, trim = true, decimalPlaces = places),
                base.iso4217Alpha(),
                result.toHumanReadableNumber(this, trim = true, decimalPlaces = places),
                dest.iso4217Alpha(),
            )
        val extra = buildShareFeeExtra(base, dest)
        return if (extra != null) "$main\n$extra" else main
    }

    // Small annotation line(s) shown under the shared result. Order matches
    // the on-screen layout: ORIGINAL side surfaces fee then cost-with-fee;
    // CONVERTED side surfaces value-before-fee then reduction-fee.
    private fun buildShareFeeExtra(
        base: Currency,
        dest: Currency,
    ): String? {
        val stacks = viewModel.getSideStacks().value

        fun line(
            prefixRes: Int,
            value: BigDecimal?,
            currency: Currency,
            stack: BigDecimal?,
        ): String? = value?.let { buildFeeAmountLine(prefixRes, it, currency, stack) }
        return listOfNotNull(
            line(R.string.fee_true_cost_prefix, viewModel.getOriginalFeeAmount().value, base, stacks?.original),
            line(R.string.fee_cost_with_fee_prefix, viewModel.getTrueCost().value, base, null),
            line(R.string.fee_value_before_fee_prefix, viewModel.getOriginalValue().value, dest, null),
            line(R.string.fee_original_value_prefix, viewModel.getConvertedFeeAmount().value, dest, stacks?.converted),
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
        val startDate =
            Calendar
                .getInstance(TimeZone.getTimeZone("UTC"))
                .apply { this.set(HISTORICAL_MIN_YEAR, Calendar.JANUARY, 1) }
                .timeInMillis
        val layout = layoutInflater.inflate(R.layout.main_dialog_historical_rates, null)
        val toggle: SwitchMaterial = layout.findViewById(R.id.toggle)
        val datePicker: DatePicker = layout.findViewById(R.id.date_picker)
        val border: View = layout.findViewById(R.id.border)
        val historicalDate = viewModel.getHistoricalDate()

        fun showDatePicker(show: Boolean) {
            datePicker.visibility = if (show) View.VISIBLE else View.GONE
            border.visibility = if (show) View.VISIBLE else View.GONE
        }
        showDatePicker(historicalDate != null)
        datePicker.apply {
            minDate = startDate
            maxDate = Calendar.getInstance().timeInMillis
            firstDayOfWeek = Calendar.getInstance().firstDayOfWeek
            historicalDate?.let { updateDate(it.year, it.monthValue - 1, it.dayOfMonth) }
        }
        toggle.apply {
            setOnCheckedChangeListener { _, enabled -> showDatePicker(enabled) }
            isChecked = historicalDate != null
        }
        AlertDialog
            .Builder(this)
            .setTitle(R.string.historical_rates_dialog_title)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.setHistoricalDate(
                    if (toggle.isChecked) {
                        LocalDate.of(
                            datePicker.year,
                            datePicker.month + 1,
                            datePicker.dayOfMonth,
                        )
                    } else {
                        null
                    },
                )
            }.setNegativeButton(android.R.string.cancel, null)
            .showWithHapticButtons()
    }

    private fun clipboardManager(): ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun setListeners() {
        // swipe to refresh
        swipeRefresh.setOnRefreshListener {
            viewModel.forceUpdateExchangeRate()
            swipeRefresh.isRefreshing = false
        }
    }

    private fun openFeesSettings() {
        startActivity(PreferenceActivity.feesIntent(this))
    }

    private fun copyToClipboard(copyText: CharSequence) {
        clipboardManager().setPrimaryClip(ClipData.newPlainText(null, copyText))
        val message = getString(R.string.copied_to_clipboard, copyText).fromHtmlLegacy()
        snackbar(message)
            .setBackgroundTint(MaterialColors.getColor(this, R.attr.colorPrimary, null))
            .setTextColor(MaterialColors.getColor(this, R.attr.colorOnPrimary, null))
            .show()
    }

    private fun observe() {
        Database(this).getDateFormat().observe(this) { pattern ->
            dateFormatPattern = pattern
            renderOfflineBanner()
        }
        viewModel.getExchangeRates().observe(this) { rates ->
            latestRatesDate = rates?.date
            latestRatesTime = rates?.time
            renderOfflineBanner()
        }
        viewModel.getError().observe(this) { showErrorSnackbar(it) }
        viewModel.isUpdating().observe(this) { isRefreshing ->
            swipeRefresh.isEnabled = isRefreshing.not()
            drawerItemRefresh?.isEnabled = isRefreshing.not()
        }
        NetworkStatusLiveData(this).observe(this) { online ->
            isOnline = online
            renderOfflineBanner()
        }
    }

    private fun renderOfflineBanner() {
        if (isOnline) {
            offlineBanner.visibility = View.GONE
            return
        }
        val date = latestRatesDate
        offlineBannerText.text =
            if (date != null) {
                getString(R.string.offline_banner_with_date, formatRatesTimestamp(date, latestRatesTime).orEmpty())
            } else {
                getString(R.string.offline_banner_no_data)
            }
        offlineBanner.visibility = View.VISIBLE
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
        snackbar(
            message.fromHtmlLegacy(),
            Snackbar.LENGTH_INDEFINITE,
        ).setBackgroundTint(MaterialColors.getColor(this, R.attr.colorError, null))
            .setTextColor(MaterialColors.getColor(this, R.attr.colorOnError, null))
            .setActionTextColor(MaterialColors.getColor(this, R.attr.colorOnError, null))
            .setAction(android.R.string.ok) { }
            .setTextMaxLines(MAX_ERROR_TEXT_LINES)
            .show()
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

    private fun prepareFoldableLayoutChanges() {
        observeFoldingFeature { feature ->
            val root = findViewById<LinearLayout>(R.id.main_root)
            root.orientation =
                when {
                    feature.state == FoldingFeature.State.FLAT -> flatOrientation()
                    feature.orientation == FoldingFeature.Orientation.VERTICAL -> LinearLayout.HORIZONTAL
                    else -> LinearLayout.VERTICAL
                }
        }
    }

    private fun flatOrientation(): Int {
        val cfg = resources.configuration
        return if (cfg.screenHeightDp >= cfg.screenWidthDp) {
            LinearLayout.VERTICAL
        } else {
            LinearLayout.HORIZONTAL
        }
    }

    private fun installMainDisplay() {
        val callbacks =
            MainDisplayCallbacks(
                onCopy = ::copyToClipboard,
                onOpenFees = ::openFeesSettings,
                onOpenProvider = ::showApiProviderPicker,
                onSwapLongPress = ::openFeesSettings,
            )
        findViewById<ComposeView>(R.id.mainDisplayHost).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    val pattern by Database(context).getDateFormat().observeAsState(DEFAULT_DATE_PATTERN)
                    MainDisplay(
                        viewModel = viewModel,
                        fragmentManager = supportFragmentManager,
                        callbacks = callbacks,
                        dateFormatPattern = pattern,
                    )
                }
            }
        }
    }

    // Wire the Compose keypad. Callbacks talk to the viewModel directly — the
    // haptic tap is applied inside MainKeypad's hapticCombinedClickable, so we
    // do NOT re-fire haptics here. System-IME variants surface no on-screen
    // keypad, so we collapse them to BASIC for the fallback layout while the
    // IME provides the actual input path.
    private fun installKeypad() {
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
        findViewById<ComposeView>(R.id.keypadHost).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    val kbType by viewModel.keyboardType.observeAsState(KeyboardType.DEFAULT)
                    val nextParen by viewModel.nextParen().observeAsState('(')
                    val effective = if (kbType.isSystem) KeyboardType.BASIC else kbType
                    MainKeypad(
                        keyboardType = effective,
                        nextParen = nextParen,
                        callbacks = callbacks,
                    )
                }
            }
        }
    }

    // Swap the AppCompat ActionBar title for a ComposeView that renders the
    // Wordmark composable ("Currenci" in Inter SemiBold + a leaning italic X
    // in Instrument Serif). Uses the ActionBar customView slot so the action
    // items (chart, cart, timeline, overflow) still lay out normally on the
    // trailing edge.
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
