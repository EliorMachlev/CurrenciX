package com.eliormachlev.currencix.view.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.CurrencyPill
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.main.spinner.CurrencyPickerSheet
import com.eliormachlev.currencix.view.navigation.PillSide
import com.eliormachlev.currencix.view.preference.compose.SwitchRow
import kotlinx.coroutines.launch
import java.math.BigDecimal

private val CONTENT_PADDING = 16.dp
private val GAP = 12.dp

/**
 * Picks a widget's pair: follow the converter's (the default), or a pair of
 * its own. Offered by the launcher when a widget is placed (before Android 12)
 * and from its reconfigure action (Android 12+, where placing skips it).
 *
 * The result is OK from the start, so backing out keeps the widget with the
 * default rather than removing it.
 */
class WidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == null || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        enableEdgeToEdge()
        val db = Database(this)
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigureActivity).getGlanceIdBy(appWidgetId)
            val state: Preferences = getAppWidgetState(this@WidgetConfigureActivity, PreferencesGlanceStateDefinition, glanceId)
            val initial = WidgetChoice.from(state, db)
            setContent {
                AppTheme(dynamicColor = db.isDynamicColorEnabledBlocking()) {
                    WidgetConfigureSheet(
                        initial = initial,
                        onDone = { choice ->
                            lifecycleScope.launch {
                                updateAppWidgetState(this@WidgetConfigureActivity, glanceId) { choice.writeTo(it) }
                                CurrencyGlanceWidget.update(this@WidgetConfigureActivity, glanceId)
                                finish()
                            }
                        },
                        onDismiss = ::finish,
                    )
                }
            }
        }
    }
}

// What the sheet edits: follow the converter, or [pair].
private data class WidgetChoice(
    val followConverter: Boolean,
    val pair: CurrencyPair,
) {
    fun writeTo(state: MutablePreferences) {
        state[WidgetPairState.FOLLOW_CONVERTER] = followConverter
        state[WidgetPairState.FROM] = pair.from.iso4217Alpha()
        state[WidgetPairState.TO] = pair.to.iso4217Alpha()
    }

    companion object {
        fun from(
            state: Preferences,
            db: Database,
        ) = WidgetChoice(
            followConverter = state[WidgetPairState.FOLLOW_CONVERTER] != false,
            pair = WidgetPairState.resolve(state, db) ?: CurrencyPair(Currency.USD, Currency.EUR),
        )
    }
}

@Composable
private fun WidgetConfigureSheet(
    initial: WidgetChoice,
    onDone: (WidgetChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<PillSide?>(null) }
    LedgerBottomSheet(title = stringResource(R.string.widget_configure_title), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(CONTENT_PADDING), verticalArrangement = Arrangement.spacedBy(GAP)) {
            SwitchRow(
                title = stringResource(R.string.widget_follow_converter_title),
                summary = stringResource(R.string.widget_follow_converter_summary),
                checked = choice.followConverter,
                onCheckedChange = { choice = choice.copy(followConverter = it) },
            )
            if (!choice.followConverter) {
                PairRow(
                    pair = choice.pair,
                    onPick = { picking = it },
                    onSwap = { choice = choice.copy(pair = CurrencyPair(choice.pair.to, choice.pair.from)) },
                )
            }
            FilledTonalButton(onClick = { onDone(choice) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.done))
            }
        }
    }
    picking?.let { side ->
        CurrencyPickerSheet(
            currentRate = null,
            currentSum = BigDecimal.ONE,
            disabledCurrency = if (side == PillSide.FROM) choice.pair.to else choice.pair.from,
            onRateClicked = { rate ->
                val pair = choice.pair
                choice = choice.copy(pair = if (side == PillSide.FROM) pair.copy(from = rate.currency) else pair.copy(to = rate.currency))
            },
            onDismiss = { picking = null },
            selectedCurrency = if (side == PillSide.FROM) choice.pair.from else choice.pair.to,
        )
    }
}

@Composable
private fun PairRow(
    pair: CurrencyPair,
    onPick: (PillSide) -> Unit,
    onSwap: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GAP / 2)) {
        CurrencyPill(pair.from, PillSide.FROM, onClick = { onPick(PillSide.FROM) }, modifier = Modifier.weight(1f))
        IconButton(onClick = onSwap) {
            Icon(painterResource(R.drawable.ic_swap_horiz), contentDescription = stringResource(R.string.desc_toggle_currencies))
        }
        CurrencyPill(pair.to, PillSide.TO, onClick = { onPick(PillSide.TO) }, modifier = Modifier.weight(1f))
    }
}
