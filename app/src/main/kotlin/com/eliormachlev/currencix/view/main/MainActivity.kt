package com.eliormachlev.currencix.view.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.sp
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
import com.eliormachlev.currencix.util.getDecimalSeparator
import com.eliormachlev.currencix.util.hapticTap
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.ltrIsolate
import com.eliormachlev.currencix.util.paintParenCycle
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
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.view.preference.showProviderPickerDialog
import com.eliormachlev.currencix.view.timeline.TimelineActivity
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import com.eliormachlev.currencix.viewmodel.main.Operator
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
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

    private var hapticEnabled = false

    // Cached date pattern shared with the share-sheet and offline-banner
    // formatters. Compose reads the same pattern via observeAsState so its
    // rate-footer stays in sync without needing a push from here.
    private var dateFormatPattern: String = DEFAULT_DATE_PATTERN

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var menuItemRefresh: MenuItem? = null

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

        // hero card (pills + amount hero + amount to + rate footer)
        installMainDisplay()

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
        this.menuItemRefresh = menu.findItem(R.id.refresh)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        hapticTap()
        return when (item.itemId) {
            R.id.settings -> {
                startActivity(Intent(this, PreferenceActivity::class.java))
                true
            }
            R.id.fees -> {
                startActivity(PreferenceActivity.feesIntent(this))
                true
            }
            R.id.change_api -> {
                showApiProviderPicker()
                true
            }
            R.id.refresh -> {
                viewModel.forceUpdateExchangeRate()
                true
            }
            R.id.share -> {
                shareCurrentConversion()
                true
            }
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
        // long click on delete
        arrayOf<View>(findViewById(R.id.keypad), findViewById(R.id.keypad_extended)).forEach {
            it.findViewById<AppCompatImageButton>(R.id.btn_delete).setOnLongClickListener {
                it.hapticTap(hapticEnabled)
                viewModel.clear()
                true
            }
        }

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
            menuItemRefresh?.isEnabled = isRefreshing.not()
        }
        viewModel.keyboardType.observe(this) { observeKeyboardType(it) }
        viewModel.nextParen().observe(this) { next ->
            findViewById<AppCompatButton>(R.id.btn_parens)?.paintParenCycle(next)
        }
        viewModel.isHapticFeedbackEnabled.observe(this) { hapticEnabled = it }
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

    // System-keyboard modes (SYSTEM_NUMPAD, SYSTEM_FULL) fall back to the
    // BASIC on-screen keypad until Phase 2b's follow-up wires an in-Compose
    // EditText — otherwise a user with a system-mode preference would see
    // both keypads hidden and have no way to type.
    private fun observeKeyboardType(type: KeyboardType) {
        val effective = if (type.isSystem) KeyboardType.BASIC else type
        val keypadRegular = findViewById<View>(R.id.keypad)
        val keypadExtended = findViewById<View>(R.id.keypad_extended)
        keypadRegular.visibility = if (effective == KeyboardType.BASIC) View.VISIBLE else View.GONE
        keypadExtended.visibility = if (effective == KeyboardType.EXPANDED) View.VISIBLE else View.GONE
        val separator = getDecimalSeparator(this)
        keypadExtended.findViewById<TextView>(R.id.btn_decimal).text = separator
        keypadRegular.findViewById<TextView>(R.id.btn_decimal).text = separator
    }

    private fun haptic(view: View) = view.hapticTap(hapticEnabled)

    /*
     * keyboard: number input
     */
    fun numberEvent(view: View) {
        haptic(view)
        viewModel.addNumber((view as AppCompatButton).text.toString())
    }

    /*
     * keyboard: add decimal point
     */
    fun decimalEvent(view: View) {
        haptic(view)
        viewModel.addDecimal()
    }

    /*
     * keyboard: delete
     */
    fun deleteEvent(view: View) {
        haptic(view)
        viewModel.delete()
    }

    /*
     * keyboard: percentage
     */
    fun percentEvent(view: View) {
        haptic(view)
        viewModel.addPercent()
    }

    /*
     * keyboard: do some calculations
     */
    fun calculationEvent(view: View) {
        haptic(view)
        Operator
            .fromDisplay((view as AppCompatButton).text.toString())
            ?.apply
            ?.invoke(viewModel)
    }

    /*
     * keyboard: parentheses (cycle-toggle between `(` and `)`)
     */
    fun parensEvent(view: View) {
        haptic(view)
        viewModel.applyNextParen()
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
