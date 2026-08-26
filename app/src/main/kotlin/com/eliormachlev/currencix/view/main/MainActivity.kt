package com.eliormachlev.currencix.view.main

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.DatePicker
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.BuildConfig
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.FeeSide
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.KEYBOARD_TYPE_BASIC
import com.eliormachlev.currencix.repository.KEYBOARD_TYPE_EXPANDED
import com.eliormachlev.currencix.repository.KEYBOARD_TYPE_SYSTEM_FULL
import com.eliormachlev.currencix.repository.isSystemKeyboardType
import com.eliormachlev.currencix.util.CalculatorKeyListener
import com.eliormachlev.currencix.util.NetworkStatusLiveData
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.fromHtmlLegacy
import com.eliormachlev.currencix.util.getDecimalSeparator
import com.eliormachlev.currencix.util.hapticTap
import com.eliormachlev.currencix.util.hideSoftInputFrom
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.ltrIsolate
import com.eliormachlev.currencix.util.paintParenCycle
import com.eliormachlev.currencix.util.rateSpinnerListener
import com.eliormachlev.currencix.util.setTextAndCursorToEnd
import com.eliormachlev.currencix.util.showSoftInputOn
import com.eliormachlev.currencix.util.stripRtlMark
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.util.toNumber
import com.eliormachlev.currencix.view.BaseActivity
import com.eliormachlev.currencix.view.cart.CartActivity
import com.eliormachlev.currencix.view.main.spinner.SearchableSpinner
import com.eliormachlev.currencix.view.preference.PreferenceActivity
import com.eliormachlev.currencix.view.preference.showProviderPickerDialog
import com.eliormachlev.currencix.view.timeline.TimelineActivity
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import com.eliormachlev.currencix.viewmodel.main.Operator
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val HISTORICAL_MIN_YEAR = 2010
private const val STALE_RATES_DAYS = 3L
private const val MAX_ERROR_TEXT_LINES = 20

// context-menu item ids for the from/to text views
private const val CTX_MENU_COPY_FROM = 0
private const val CTX_MENU_PASTE_FROM = 1
private const val CTX_MENU_COPY_TO = 2

// fee badge / true-cost formatting
private const val FEE_BADGE_DECIMAL_PLACES = 2
private const val AMOUNT_DECIMAL_PLACES = 2

class MainActivity : BaseActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var preferenceModel: PreferenceViewModel

    private var hapticEnabled = false

    // Cached date pattern so the frequently-fired `observeExchangeRates`
    // handler doesn't hit SharedPreferences on the main thread on every rate
    // emission. Kept in sync via the `getDateFormat()` LiveData below.
    private var dateFormatPattern: String = "dd/MM/yy HH:mm"

    private lateinit var refreshIndicator: LinearProgressIndicator
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var menuItemRefresh: MenuItem? = null

    private lateinit var offlineBanner: MaterialCardView
    private lateinit var offlineBannerText: TextView
    private var isOnline: Boolean = true
    private var latestRatesDate: LocalDate? = null
    private var latestRatesTime: LocalTime? = null

    private lateinit var tvCalculations: EditText
    private lateinit var tvFrom: EditText
    private lateinit var tvTo: TextView

    // Guards the expression-preview observer from stomping on characters the
    // user is actively typing on the system keyboard: our own setText() call
    // for seeding / mode-swap would otherwise re-fire the TextWatcher and
    // replay it, garbling the input.
    private var muteCalculationsWriteback = false

    // True while `KEYBOARD_TYPE_SYSTEM` is selected — the calculations
    // EditText is the source of truth for the typed expression, so the
    // formatted-preview observer skips it entirely (even for updates that
    // originate elsewhere: currency swap, rate refresh, mid-entry reformat).
    private var isSystemKeyboardMode = false
    private var lastKeyboardTypeApplied: Int? = null
    private lateinit var spinnerFrom: SearchableSpinner
    private lateinit var spinnerTo: SearchableSpinner
    private lateinit var tvInfoConversion: TextView
    private lateinit var tvInfoDate: TextView
    private lateinit var tvTrueCost: TextView
    private lateinit var tvOriginalValue: TextView
    private lateinit var tvFeeBadge: TextView
    private lateinit var btnFeeSide: AppCompatImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // general layout
        setContentView(R.layout.activity_main)
        title = buildWordmarkTitle()

        // model
        this.viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        this.preferenceModel = ViewModelProvider(this)[PreferenceViewModel::class.java]

        // views
        this.refreshIndicator = findViewById(R.id.refreshIndicator)
        this.swipeRefresh = findViewById(R.id.swipeRefresh)
        this.tvCalculations = findViewById(R.id.textCalculations)
        this.tvFrom = findViewById(R.id.textFrom)
        this.tvTo = findViewById(R.id.textTo)
        this.spinnerFrom = findViewById(R.id.spinnerFrom)
        this.spinnerTo = findViewById(R.id.spinnerTo)
        this.tvInfoConversion = findViewById(R.id.textInfoConversion)
        this.tvInfoDate = findViewById(R.id.textInfoDate)
        this.tvTrueCost = findViewById(R.id.textTrueCost)
        this.tvOriginalValue = findViewById(R.id.textOriginalValue)
        this.tvFeeBadge = findViewById(R.id.textFeeBadge)
        this.btnFeeSide = findViewById(R.id.btn_fee_side)
        this.offlineBanner = findViewById(R.id.offlineBanner)
        this.offlineBannerText = findViewById(R.id.offlineBannerText)

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
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

    // "True cost" (ORIGINAL side) or "Original value" (CONVERTED side) line —
    // mirrors the small annotation shown under the result when a fee applies.
    private fun buildShareFeeExtra(
        base: Currency,
        dest: Currency,
    ): String? {
        viewModel.getTrueCost().value?.let {
            return buildFeeAmountLine(R.string.fee_true_cost_prefix, it, base)
        }
        viewModel.getOriginalValue().value?.let {
            return buildFeeAmountLine(R.string.fee_original_value_prefix, it, dest)
        }
        return null
    }

    private fun buildShareFooter(rates: ExchangeRates?): String? {
        if (rates == null) return null
        val providerName = rates.provider?.getName() ?: return null
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
            .create()
            .show()
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?,
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        when (v.id) {
            R.id.textFrom -> {
                menu.add(0, CTX_MENU_COPY_FROM, 0, android.R.string.copy)
                val paste = menu.add(0, CTX_MENU_PASTE_FROM, 0, android.R.string.paste)
                // only show "paste" when applicable
                paste.isVisible = clipboardHasNumber()
            }
            R.id.textTo -> {
                menu.add(0, CTX_MENU_COPY_TO, 0, android.R.string.copy)
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            CTX_MENU_COPY_FROM -> copyToClipboard(findViewById<TextView>(R.id.textFrom).text.toString())
            CTX_MENU_PASTE_FROM -> {
                clipboardNumber()?.let { viewModel.paste(it) }
                // Preview observer is muted in system mode, so mirror the
                // fresh expression into the EditText ourselves.
                if (isSystemKeyboardMode) setCalculationsTextMuted(viewModel.currentTypedExpression())
            }
            CTX_MENU_COPY_TO -> copyToClipboard(findViewById<TextView>(R.id.textTo).text.toString())
        }
        return true
    }

    private fun clipboardManager(): ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun clipboardHasNumber(): Boolean {
        val clipboard = clipboardManager()
        return clipboard.hasPrimaryClip() &&
            clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true &&
            clipboardNumber() != null
    }

    private fun clipboardNumber(): Number? =
        clipboardManager()
            .primaryClip
            ?.getItemAt(0)
            ?.text
            ?.toNumber()

    private fun setListeners() {
        // long click on delete
        arrayOf<View>(findViewById(R.id.keypad), findViewById(R.id.keypad_extended)).forEach {
            it.findViewById<AppCompatImageButton>(R.id.btn_delete).setOnLongClickListener {
                viewModel.clear()
                true
            }
        }

        // long click on input "from"
        registerForContextMenu(tvFrom)
        // long click on input "to"
        registerForContextMenu(tvTo)

        // spinners: listen for changes
        spinnerFrom.onItemSelectedListener = rateSpinnerListener(viewModel::setBaseCurrency)
        spinnerTo.onItemSelectedListener = rateSpinnerListener(viewModel::setDestinationCurrency)

        // swipe to refresh
        swipeRefresh.setOnRefreshListener {
            // update
            viewModel.forceUpdateExchangeRate()
            swipeRefresh.isRefreshing = false
        }

        // fee-side toggle
        btnFeeSide.setOnClickListener {
            haptic(it)
            val current = viewModel.getFeeSide().value ?: FeeSide.ORIGINAL
            viewModel.setFeeSide(current.toggled())
        }
        btnFeeSide.setOnLongClickListener { openFeesSettings(it) }

        // long-press on the main swap arrow also opens the Fees settings,
        // mirroring the swap arrow inside the quick-conversions dialog.
        findViewById<View>(R.id.btn_toggle).setOnLongClickListener { openFeesSettings(it) }
    }

    private fun openFeesSettings(source: View): Boolean {
        haptic(source)
        startActivity(PreferenceActivity.feesIntent(this))
        return true
    }

    private fun copyToClipboard(copyText: String) {
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
            // Re-render the rates footer so a pattern change from Settings
            // takes effect immediately without waiting for the next refresh.
            observeExchangeRates(viewModel.getExchangeRates().value)
        }
        viewModel.ratesInformationFooter.observe(this) { tvInfoConversion.text = it }
        viewModel.getExchangeRates().observe(this) { observeExchangeRates(it) }
        viewModel.getError().observe(this) { showErrorSnackbar(it) }
        viewModel.isUpdating().observe(this) { isRefreshing ->
            refreshIndicator.visibility = if (isRefreshing) View.VISIBLE else View.GONE
            swipeRefresh.isEnabled = isRefreshing.not()
            menuItemRefresh?.isEnabled = isRefreshing.not()
        }
        viewModel.getCurrentBaseValueFormatted().observe(this) { formatted ->
            tvFrom.setTextAndCursorToEnd(formatted ?: "")
        }
        viewModel.getResultFormatted().observe(this) { tvTo.text = it }
        viewModel.getCalculationInputFormatted().observe(this) { formatted ->
            // In SYSTEM-keyboard mode `tvCalculations` is the EditText the
            // user is actively typing into; the pretty-formatted preview
            // would clobber the raw text and jump the cursor. Skip the
            // writeback entirely — including reformats from a currency swap
            // or rate refresh mid-entry.
            if (isSystemKeyboardMode || muteCalculationsWriteback) return@observe
            setCalculationsTextMuted(formatted ?: "")
        }
        viewModel.getBaseCurrency().observe(this) { observeBaseCurrency(it) }
        viewModel.getDestinationCurrency().observe(this) { observeDestinationCurrency(it) }
        viewModel.getCurrentBaseValueAsNumber().observe(this) { spinnerTo.setCurrentSum(it) }
        viewModel.getResultAsNumber().observe(this) { spinnerFrom.setCurrentSum(it) }
        viewModel.keyboardType.observe(this) { observeKeyboardType(it) }
        viewModel.nextParen().observe(this) { next ->
            findViewById<AppCompatButton>(R.id.btn_parens)?.paintParenCycle(next)
        }
        viewModel.isHapticFeedbackEnabled.observe(this) { hapticEnabled = it }
        viewModel.getFeeSide().observe(this) { observeFeeSide(it) }
        viewModel.getTrueCost().observe(this) { observeTrueCost(it) }
        viewModel.getOriginalValue().observe(this) { observeOriginalValue(it) }
        viewModel.getTotalStack().observe(this) { observeTotalStack(it) }
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

    private fun observeFeeSide(side: FeeSide?) {
        val effective = side ?: FeeSide.ORIGINAL
        btnFeeSide.setImageResource(
            if (effective == FeeSide.CONVERTED) {
                R.drawable.ic_fee_side_converted
            } else {
                R.drawable.ic_fee_side_original
            },
        )
        // TalkBack ignores src changes on ImageButton; without a stateDescription
        // the button always reads "Fee side" and users can't tell whether they
        // just flipped to "original" or "converted". ViewCompat is used because
        // View.setStateDescription is API 30+ and minSdk is 26.
        ViewCompat.setStateDescription(
            btnFeeSide,
            getString(
                if (effective == FeeSide.CONVERTED) {
                    R.string.fee_side_converted
                } else {
                    R.string.fee_side_original
                },
            ),
        )
    }

    private fun observeTotalStack(stack: BigDecimal?) {
        val effective = stack ?: BigDecimal.ONE
        if (effective.isNeutralFeeStack()) {
            tvFeeBadge.visibility = View.GONE
            btnFeeSide.visibility = View.GONE
            return
        }
        btnFeeSide.visibility = View.VISIBLE
        tvFeeBadge.text =
            effective.feePercentDelta(FEE_BADGE_DECIMAL_PLACES).toHumanReadableNumber(
                this,
                showPositiveSign = true,
                suffix = "%",
                trim = true,
            )
        tvFeeBadge.visibility = View.VISIBLE
    }

    private fun observeTrueCost(value: BigDecimal?) {
        renderFeeAmount(tvTrueCost, R.string.fee_true_cost_prefix, value, viewModel.getBaseCurrency().value)
    }

    private fun observeOriginalValue(value: BigDecimal?) {
        renderFeeAmount(tvOriginalValue, R.string.fee_original_value_prefix, value, viewModel.getDestinationCurrency().value)
    }

    private fun renderFeeAmount(
        target: TextView,
        prefixRes: Int,
        value: BigDecimal?,
        currency: Currency?,
    ) {
        if (value == null) {
            target.visibility = View.GONE
            return
        }
        target.text = buildFeeAmountLine(prefixRes, value, currency)
        target.visibility = View.VISIBLE
    }

    // "<prefix><amount> <ISO>" with the amount+ISO isolated LTR so a
    // right-aligned prefix in an RTL locale doesn't flip the number/code
    // pair. Shared by the on-screen fee annotations and the share sheet.
    private fun buildFeeAmountLine(
        prefixRes: Int,
        value: BigDecimal,
        currency: Currency?,
    ): String {
        val amount = value.toHumanReadableNumber(this, decimalPlaces = AMOUNT_DECIMAL_PLACES)
        return getString(prefixRes) + ltrIsolate("$amount ${currency?.iso4217Alpha().orEmpty()}")
    }

    private fun observeExchangeRates(rates: ExchangeRates?) {
        latestRatesDate = rates?.date
        latestRatesTime = rates?.time
        renderOfflineBanner()
        rates?.let {
            val date = it.date
            val dateString = formatRatesTimestamp(date, it.time)
            val providerString = it.provider?.getName()
            tvInfoDate.text =
                if (dateString != null && providerString != null) {
                    getString(
                        if (viewModel.getHistoricalDate() != null) {
                            R.string.info_date_historical
                        } else {
                            R.string.info_date_latest
                        },
                        dateString,
                        providerString,
                    ).fromHtmlLegacy()
                } else {
                    null
                }
            val isStaleOrHistorical =
                date?.isBefore(LocalDate.now().minusDays(STALE_RATES_DAYS)) == true ||
                    viewModel.getHistoricalDate() != null
            val infoColor =
                if (isStaleOrHistorical) {
                    MaterialColors.getColor(this, R.attr.colorError, null)
                } else {
                    getTextColorSecondary()
                }
            listOf(tvInfoDate, tvInfoConversion).forEach { tv -> tv.setTextColor(infoColor) }
            findViewById<ImageView>(R.id.iconHistorical).visibility =
                if (viewModel.getHistoricalDate() != null) View.VISIBLE else View.GONE
        }
        spinnerFrom.setRates(rates?.rates)
        spinnerTo.setRates(rates?.rates)
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

    private fun observeBaseCurrency(currency: Currency?) {
        spinnerFrom.setSelection(currency)
        // Whatever's picked on the base side must not be pickable on the
        // destination side — grey it out in the "to" picker.
        spinnerTo.setDisabledCurrency(currency)
        currency ?: return
        findRateFor(currency)?.let { spinnerTo.setCurrentRate(Rate(currency, it)) }
    }

    private fun observeDestinationCurrency(currency: Currency?) {
        spinnerTo.setSelection(currency)
        spinnerFrom.setDisabledCurrency(currency)
        currency ?: return
        findRateFor(currency)?.let { spinnerFrom.setCurrentRate(Rate(currency, it)) }
    }

    // Look up the current-cache rate value for [currency]; null when rates
    // haven't loaded yet or the currency isn't in the response.
    private fun findRateFor(currency: Currency): BigDecimal? =
        viewModel
            .getExchangeRates()
            .value
            ?.rates
            ?.find { it.currency == currency }
            ?.value

    private fun observeKeyboardType(type: Int) {
        val keypadRegular = findViewById<View>(R.id.keypad)
        val keypadExtended = findViewById<View>(R.id.keypad_extended)
        keypadRegular.visibility = if (type == KEYBOARD_TYPE_BASIC) View.VISIBLE else View.GONE
        keypadExtended.visibility = if (type == KEYBOARD_TYPE_EXPANDED) View.VISIBLE else View.GONE
        val separator = getDecimalSeparator(this)
        keypadExtended.findViewById<TextView>(R.id.btn_decimal).text = separator
        keypadRegular.findViewById<TextView>(R.id.btn_decimal).text = separator
        configureCalculationsEditText(type)
    }

    // Runtime flags mirror the XML defaults so mode swaps are reversible.
    // Compare by the exact type (not just `wantsSystem`) so a switch between
    // the numpad and full-text sub-variants refreshes the KeyListener + IME
    // class instead of no-oping.
    private fun configureCalculationsEditText(type: Int) {
        if (lastKeyboardTypeApplied == type) return
        val wantsSystem = type.isSystemKeyboardType()
        isSystemKeyboardMode = wantsSystem
        lastKeyboardTypeApplied = type
        // Detach first so seed-setText below doesn't accidentally clear the
        // state via a stale watcher call.
        tvCalculations.removeTextChangedListener(systemKeyboardWatcher)
        if (wantsSystem) {
            tvCalculations.isFocusable = true
            tvCalculations.isFocusableInTouchMode = true
            tvCalculations.isCursorVisible = true
            tvCalculations.showSoftInputOnFocus = true
            tvCalculations.keyListener = keyListenerFor(type)
            // Seed with the current typed expression (display glyphs → ASCII)
            // so switching mode mid-entry doesn't lose the user's work.
            setCalculationsTextMuted(viewModel.currentTypedExpression())
            tvCalculations.addTextChangedListener(systemKeyboardWatcher)
            showSystemImeOnField()
        } else {
            tvCalculations.isCursorVisible = false
            tvCalculations.showSoftInputOnFocus = false
            tvCalculations.isFocusable = false
            tvCalculations.isFocusableInTouchMode = false
            // Also clears inputType to TYPE_NULL — no separate reset needed.
            tvCalculations.keyListener = null
            hideSystemIme()
            // Restore the pretty formatted preview now that the writeback
            // observer is authoritative again.
            setCalculationsTextMuted(viewModel.getCalculationInputFormatted().value ?: "")
        }
    }

    private fun keyListenerFor(type: Int) =
        if (type == KEYBOARD_TYPE_SYSTEM_FULL) {
            CalculatorKeyListener.FULL_TEXT
        } else {
            CalculatorKeyListener.NUMPAD
        }

    private var cachedSystemKeyboardIcon: Drawable? = null

    private fun systemKeyboardIcon(): Drawable =
        cachedSystemKeyboardIcon ?: getDrawable(R.drawable.ic_keyboard_extended)!!.also {
            cachedSystemKeyboardIcon = it
        }

    // Show the keyboard glyph as a start-compound drawable while system-IME
    // mode is active and the field is empty, so the user has a visible tap
    // target instead of a bare caret. Cleared as soon as text appears (or on
    // mode-swap back to the custom keypads).
    private fun updateSystemKeyboardIcon() {
        val icon =
            if (isSystemKeyboardMode && tvCalculations.text.isNullOrEmpty()) {
                systemKeyboardIcon()
            } else {
                null
            }
        tvCalculations.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    }

    private fun showSystemImeOnField() {
        tvCalculations.setSelection(tvCalculations.text?.length ?: 0)
        showSoftInputOn(tvCalculations)
    }

    // Programmatic setText that suppresses the system-IME watcher re-entry —
    // used for the pretty-preview observer, paste, mode swaps, and the
    // watcher's own reconcile. `alreadyMuted` preserves the outer mute when
    // called from inside afterTextChanged.
    private fun setCalculationsTextMuted(text: CharSequence) {
        val alreadyMuted = muteCalculationsWriteback
        muteCalculationsWriteback = true
        try {
            tvCalculations.setTextAndCursorToEnd(text)
            updateSystemKeyboardIcon()
        } finally {
            if (!alreadyMuted) muteCalculationsWriteback = false
        }
    }

    private fun hideSystemIme() = hideSoftInputFrom(tvCalculations)

    // On every EditText mutation while system-IME mode is active, rebuild
    // the calculator state from the new text — same char-routing the hardware
    // keyboard uses. Simpler than diffing insert vs. delete positions and
    // correct for pastes / mid-string edits too.
    private val systemKeyboardWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (muteCalculationsWriteback) return
                val text = s?.toString().orEmpty()
                muteCalculationsWriteback = true
                try {
                    viewModel.clear()
                    text.forEach { handleCharKey(it) }
                    // Reconcile the visible text with the calculator's canonical
                    // form so shortcuts round-trip: e.g. `()` after a number
                    // becomes `*(`, matching what the custom keypads produce.
                    setCalculationsTextMuted(viewModel.currentTypedExpression())
                } finally {
                    muteCalculationsWriteback = false
                }
            }
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
     * swap currencies
     */
    fun toggleEvent(
        @Suppress("UNUSED_PARAMETER") view: View?,
    ) {
        val from = spinnerFrom.selectedItemPosition
        val to = spinnerTo.selectedItemPosition
        spinnerFrom.setSelection(to)
        spinnerTo.setSelection(from)
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

    private fun buildWordmarkTitle(): CharSequence {
        val text = getString(R.string.app_name)
        val sizePx =
            TypedValue
                .applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    WORDMARK_TITLE_SP,
                    resources.displayMetrics,
                ).toInt()
        return SpannableString(text).apply {
            setSpan(AbsoluteSizeSpan(sizePx), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(TypefaceSpan(WORDMARK_TITLE_FONT_FAMILY), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (BuildConfig.DEBUG) {
                setSpan(
                    ForegroundColorSpan(getColor(android.R.color.holo_red_light)),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }
}

private const val WORDMARK_TITLE_SP = 26f

// sans-serif-black is Android's heaviest built-in font family (weight 900).
// Combined with StyleSpan(BOLD), it gives the wordmark a distinct logo weight
// without shipping a custom font asset.
private const val WORDMARK_TITLE_FONT_FAMILY = "sans-serif-black"
