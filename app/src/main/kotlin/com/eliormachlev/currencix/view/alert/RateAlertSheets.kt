package com.eliormachlev.currencix.view.alert

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.alert.ALERT_RATE_DECIMALS
import com.eliormachlev.currencix.alert.canNotify
import com.eliormachlev.currencix.model.AlertDirection
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.RateAlert
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.parseDecimal
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.util.roundForDisplay
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.compose.LocalAppSnackbar
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.PairChipContent
import com.eliormachlev.currencix.view.compose.SupportingText
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPromptSheet
import com.eliormachlev.currencix.view.compose.dialogs.PromptMessage
import com.eliormachlev.currencix.view.compose.theme.Spacing
import java.math.BigDecimal

/**
 * "Rate alert" for [pair]: the rate to hear about, seeded with [current]
 * (today's, or a day picked on the chart), and what it means ("…when 1 USD
 * is 3.5000 ILS or less"). Confirming hands [onSet] the alert; [current] is
 * also what decides above or below.
 */
@Composable
fun RateAlertSheet(
    pair: CurrencyPair,
    current: BigDecimal,
    onSet: (RateAlert) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val seed = remember(current) { current.roundForDisplay(ALERT_RATE_DECIMALS).toPlainString() }
    val field = rememberTextFieldState(seed)
    val threshold = parseDecimal(field.text.toString())?.takeIf { it.signum() > 0 }
    val format = { value: BigDecimal ->
        value.roundForDisplay(ALERT_RATE_DECIMALS).toHumanReadableNumber(context, decimalPlaces = ALERT_RATE_DECIMALS)
    }
    LedgerPromptSheet(
        title = stringResource(R.string.alert_sheet_title),
        confirmLabel = stringResource(R.string.alert_sheet_confirm),
        onConfirm = { threshold?.let { onSet(RateAlert.towards(pair, it, current)) } },
        onDismiss = onDismiss,
        confirmEnabled = threshold != null,
    ) {
        Ltr { Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tiny)) { PairChipContent(pair.from, pair.to) } }
        Spacer(Modifier.height(Spacing.Medium))
        OutlinedTextField(
            state = field,
            label = { Text(stringResource(R.string.alert_sheet_label, pair.from.iso4217Alpha(), pair.to.iso4217Alpha())) },
            supportingText = { Text(stringResource(R.string.alert_sheet_now, format(current))) },
            isError = field.text.isNotBlank() && threshold == null,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        threshold?.let {
            val direction = RateAlert.towards(pair, it, current).direction
            Spacer(Modifier.height(Spacing.Small))
            PromptMessage(
                stringResource(
                    if (direction == AlertDirection.ABOVE) R.string.alert_sheet_above else R.string.alert_sheet_below,
                    pair.from.iso4217Alpha(),
                    format(it),
                    pair.to.iso4217Alpha(),
                ),
            )
        }
        Spacer(Modifier.height(Spacing.Small))
        SupportingText(stringResource(R.string.alert_sheet_background))
    }
}

/**
 * Saves an alert the user just set: asks for notifications the first time
 * (Android 13+), turns background refresh on (alerts are checked when
 * rates arrive), and says on the snackbar whether the alert can reach them.
 */
@Composable
fun rememberSetRateAlert(): (RateAlert) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalAppSnackbar.current
    val db = remember(context) { Database(context) }
    val confirm = { granted: Boolean ->
        snackbar?.show(resources.getString(if (granted) R.string.alert_set else R.string.alert_notifications_off))
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> confirm(granted) }
    return remember(db, askPermission) {
        { alert ->
            db.alerts.add(alert)
            db.providers.autoRefresh.set(true)
            if (canNotify(context)) confirm(true) else askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** The alerts waiting to fire, each with a delete button; from Settings. */
@Composable
fun RateAlertsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { Database(context) }
    val alerts by db.alerts.alerts.flow
        .collectAsStateWithLifecycle(db.alerts.alerts.get())
    LedgerBottomSheet(title = stringResource(R.string.alerts_title), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.XXLarge, vertical = Spacing.Small)) {
            if (alerts.isEmpty()) {
                PromptMessage(stringResource(R.string.alerts_empty))
            }
            alerts.forEach { alert -> AlertRow(alert, onDelete = { db.alerts.remove(alert.id) }) }
        }
    }
}

// "🇺🇸 USD → 🇮🇱 ILS   3.5000 or less   🗑"
@Composable
private fun AlertRow(
    alert: RateAlert,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val threshold = alert.threshold.toHumanReadableNumber(context, decimalPlaces = ALERT_RATE_DECIMALS)
    val condition =
        stringResource(if (alert.direction == AlertDirection.ABOVE) R.string.alert_row_above else R.string.alert_row_below, threshold)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Ltr { Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tiny)) { PairChipContent(alert.pair.from, alert.pair.to) } }
        Text(
            condition,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.Medium),
        )
        IconButton(onClick = rememberHapticOnClick(onDelete)) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.alert_delete))
        }
    }
}
