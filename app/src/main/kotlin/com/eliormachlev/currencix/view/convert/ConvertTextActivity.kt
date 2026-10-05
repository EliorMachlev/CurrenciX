package com.eliormachlev.currencix.view.convert

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.hasAppendedCurrencySymbol
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.util.withCurrencySymbol
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.CurrencyFlagImage
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.main.ConverterLaunch
import java.math.BigDecimal

// Selected text can be a whole page; a price is in the first few words.
private const val MAX_SELECTION_LENGTH = 200

// The rate line ("1 EUR ≈ 3.6851 ILS") is shown finer than amounts.
private const val RATE_DECIMALS = 4

private val FLAG_WIDTH = 28.dp
private val FLAG_HEIGHT = 20.dp
private val FLAG_CORNER = 3.dp
private val CONTENT_PADDING = 24.dp
private val GAP = 12.dp

/**
 * "Convert currency" in the text-selection menu of any app: converts the
 * price in the selected text with the cached rates, in a sheet over that
 * app — no network, so it's instant. Offers to copy the result or to open
 * the converter on that pair and amount.
 *
 * Translucent and in its own task (see the manifest), so the calling app
 * stays underneath and nothing lingers in Recents.
 */
class ConvertTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val text =
            intent
                .getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                ?.toString()
                .orEmpty()
                .take(MAX_SELECTION_LENGTH)
        val db = Database(this)
        val from = db.lastState.getLastBaseCurrencyBlocking() ?: Currency.USD
        val to = db.lastState.getLastDestinationCurrencyBlocking() ?: Currency.EUR
        val conversion = convertSelection(text, db.rates.getExchangeRatesBlocking(), from, to)
        val decimals = db.display.getDecimalPlacesBlocking()
        setContent {
            AppTheme(dynamicColor = db.display.isDynamicColorEnabledBlocking()) {
                ConvertTextSheet(
                    text = text,
                    conversion = conversion,
                    decimals = decimals,
                    onOpen = { intent -> openApp(intent) },
                    onDismiss = ::finish,
                )
            }
        }
    }

    private fun openApp(intent: Intent) {
        // From another app's task: the converter opens in its own.
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
internal fun ConvertTextSheet(
    text: String,
    conversion: SelectionConversion,
    decimals: Int,
    onOpen: (Intent) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    LedgerBottomSheet(title = stringResource(R.string.process_text_label), onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = CONTENT_PADDING, vertical = GAP),
            verticalArrangement = Arrangement.spacedBy(GAP),
        ) {
            when (conversion) {
                is SelectionConversion.Converted ->
                    ConvertedContent(conversion, decimals, onOpen = { onOpen(openIntent(context, conversion)) })
                SelectionConversion.NoAmount -> Message(stringResource(R.string.selection_no_amount, text.trim()))
                SelectionConversion.NoRates -> {
                    Message(stringResource(R.string.selection_no_rates, appName))
                    FilledTonalButton(onClick = { onOpen(ConverterLaunch.openConverter(context)) }) {
                        Text(stringResource(R.string.selection_open_app, appName))
                    }
                }
            }
        }
    }
}

private fun openIntent(
    context: Context,
    conversion: SelectionConversion.Converted,
): Intent = ConverterLaunch.convert(context, conversion.from, conversion.to, conversion.amount)

@Composable
private fun ConvertedContent(
    conversion: SelectionConversion.Converted,
    decimals: Int,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val source = remember(conversion) { money(context, conversion.amount, conversion.from, decimals) }
    val result = remember(conversion) { money(context, conversion.result, conversion.to, decimals) }
    val rateValue = remember(conversion) { conversion.rate.toHumanReadableNumber(context, RATE_DECIMALS, trim = true) }
    val rate = stringResource(R.string.info_conversion, "1", conversion.from.iso4217Alpha(), rateValue, conversion.to.iso4217Alpha())
    AmountLine(conversion.from, source, MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.onSurfaceVariant)
    AmountLine(conversion.to, result, MaterialTheme.typography.headlineMedium, MaterialTheme.colorScheme.onSurface)
    Text(rate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (conversion.assumedFrom) {
        Message(stringResource(R.string.selection_assumed_currency, conversion.from.iso4217Alpha()))
    }
    Spacer(Modifier.height(GAP / 2))
    Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
        OutlinedButton(onClick = rememberHapticOnClick { copy(context, result) }) {
            Text(stringResource(R.string.selection_copy))
        }
        FilledTonalButton(onClick = rememberHapticOnClick(onOpen)) {
            Text(stringResource(R.string.selection_open_app, appName))
        }
    }
}

@Composable
private fun AmountLine(
    currency: Currency,
    amount: String,
    style: TextStyle,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GAP)) {
        CurrencyFlagImage(
            currency = currency,
            modifier = Modifier.size(width = FLAG_WIDTH, height = FLAG_HEIGHT).clip(RoundedCornerShape(FLAG_CORNER)),
        )
        Text(amount, style = style, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// "₪ 184.20" / "184,20 €" — the symbol on the locale's side.
private fun money(
    context: Context,
    value: BigDecimal,
    currency: Currency,
    decimals: Int,
): String =
    withCurrencySymbol(
        value.toHumanReadableNumber(context, decimals),
        currency.symbolOrIso(),
        hasAppendedCurrencySymbol(context),
    )

private fun copy(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    // The system confirms the copy itself (with a preview), so no message here.
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
}
