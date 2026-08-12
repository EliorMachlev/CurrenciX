package com.eliormachlev.currencix.view.timeline

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.stripTimePattern
import com.eliormachlev.currencix.view.BaseActivity
import com.eliormachlev.currencix.view.preference.GraphOptionsDialog
import com.eliormachlev.currencix.view.timeline.compose.TimelineScreen
import com.eliormachlev.currencix.viewmodel.timeline.TimelineViewModel
import com.google.android.material.color.MaterialColors
import java.time.format.DateTimeFormatter

class TimelineActivity : BaseActivity() {
    companion object {
        const val EXTRA_FROM = "ARG_FROM"
        const val EXTRA_TO = "ARG_TO"

        fun newIntent(
            context: Context,
            from: Currency,
            to: Currency,
        ): Intent =
            Intent(context, TimelineActivity::class.java)
                .putExtra(EXTRA_FROM, from)
                .putExtra(EXTRA_TO, to)
    }

    private lateinit var timelineModel: TimelineViewModel
    private var menuItemToggle: MenuItem? = null

    // Foldable state is hoisted into a mutableState so the composable re-lays out
    // whenever WindowInfoTracker emits a new posture.
    private val foldingFeatureState = mutableStateOf<FoldingFeature?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Wrap the Compose surface in an XML root with fitsSystemWindows="true"
        // so edge-to-edge (targetSdk 35+) doesn't draw the chart behind the
        // ActionBar / status bar. Mirrors the pattern used by every other
        // activity in this app.
        setContentView(R.layout.activity_timeline)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        val db = Database(this)
        val formatter =
            DateTimeFormatter.ofPattern(stripTimePattern(db.getDateFormatBlocking()))

        val currencyFrom = readCurrencyExtra(EXTRA_FROM, Currency.EUR)
        val currencyTo = readCurrencyExtra(EXTRA_TO, Currency.USD)

        timelineModel =
            ViewModelProvider(
                this,
                TimelineViewModel.Factory(application, currencyFrom, currencyTo),
            )[TimelineViewModel::class.java]

        // Keep the ActionBar title in sync with the ViewModel.
        timelineModel.getTitle().observe(this) { title = it }
        // Enable/disable the swap-currencies menu item based on refresh state / errors.
        timelineModel.isUpdating().observe(this) { isRefreshing ->
            menuItemToggle?.isEnabled = !isRefreshing
        }
        timelineModel.getError().observe(this) { err ->
            menuItemToggle?.isEnabled = err == null
        }

        observeFoldingFeature { feature -> foldingFeatureState.value = feature }

        val lineColor = Color(MaterialColors.getColor(this, R.attr.colorPrimary, 0))
        val axisColor =
            Color(MaterialColors.getColor(this, android.R.attr.textColorSecondary, 0))
        // colorOnSurface (text-on-background) keeps the scrub line visually
        // distinct from the green primary (max highlight), red min line, and
        // the blue/purple period-change verticals on every theme.
        val scrubLineColor = Color(MaterialColors.getColor(this, R.attr.colorOnSurface, 0))

        findViewById<ComposeView>(R.id.timeline_compose).setContent {
            val feature by remember { foldingFeatureState }
            TimelineScreen(
                model = timelineModel,
                formatter = formatter,
                foldingFeature = feature,
                chartContent = {
                    val entriesLive =
                        timelineModel.getRates().map { rates ->
                            rates?.entries?.map { entry -> entry.key to entry.value.value.toFloat() }
                        }
                    // Range extremes (scrub-independent) so the chart's min/max
                    // reference lines stay pinned to the visible period's
                    // absolute low/high while the finger drags.
                    val highlightMinLive = timelineModel.getRatesRangeMin()
                    val highlightMaxLive = timelineModel.getRatesRangeMax()
                    TimelineChart(
                        entriesLive = entriesLive,
                        showGridLive = db.isChartGridEnabled(),
                        showXAxisLive = db.isChartXAxisLabelEnabled(),
                        showYAxisLive = db.isChartYAxisLabelEnabled(),
                        highlightExtremesLive = db.isChartHighlightExtremesEnabled(),
                        highlightPeriodChangeLive = db.isChartHighlightPeriodChangeEnabled(),
                        dateFormatLive = db.getDateFormat(),
                        highlightMinLive = highlightMinLive,
                        highlightMaxLive = highlightMaxLive,
                        lineColor = lineColor,
                        baselineColor = axisColor,
                        axisColor = axisColor,
                        scrubLineColor = scrubLineColor,
                        onScrub = { date -> timelineModel.setPastDate(date) },
                    )
                },
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.timeline, menu)
        menuItemToggle = menu.findItem(R.id.toggle)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.toggle -> {
                timelineModel.toggleCurrencies()
                true
            }
            R.id.graph_options -> {
                GraphOptionsDialog().show(supportFragmentManager, null)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun readCurrencyExtra(
        key: String,
        default: Currency,
    ): Currency =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(key, Currency::class.java) ?: default
        } else {
            @Suppress("DEPRECATION")
            (intent.getSerializableExtra(key) as? Currency) ?: default
        }
}
