package com.eliormachlev.currencix.view.scan

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.ParsedPrice
import com.eliormachlev.currencix.util.PriceParser
import com.eliormachlev.currencix.util.SCANS_SUBDIR
import com.eliormachlev.currencix.util.cacheFile
import com.eliormachlev.currencix.util.hasAppendedCurrencySymbol
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.util.withCurrencySymbol
import com.eliormachlev.currencix.view.compose.CurrencyFlagImage
import com.eliormachlev.currencix.view.compose.LedgerRow
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// One photo at a time: each scan overwrites the last.
private const val SCAN_FILENAME = "price.jpg"

private val FLAG_WIDTH = 24.dp
private val FLAG_HEIGHT = 17.dp
private val FLAG_CORNER = 2.dp
private val ROW_GAP = 12.dp

/** Starts a camera price scan ([rememberPriceScan]). */
@Stable
class PriceScan internal constructor(
    private val onStart: () -> Unit,
) {
    fun start() = onStart()
}

/**
 * The camera price scan: takes a photo with the camera app (no camera
 * permission of our own), reads its text ([textReader]) and hands back every
 * price found ([PriceParser.parseAll]) — or calls [onMessage] with why there's
 * none. The photo is deleted once read. Only call where [textReader] exists.
 */
@Composable
fun rememberPriceScan(
    preferred: List<Currency>,
    onPrices: (List<ParsedPrice>) -> Unit,
    onMessage: (String) -> Unit,
): PriceScan {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPreferred by rememberUpdatedState(preferred)
    val currentOnPrices by rememberUpdatedState(onPrices)
    val currentOnMessage by rememberUpdatedState(onMessage)
    val noPrices = stringResource(R.string.scan_no_prices)
    val noCamera = stringResource(R.string.scan_no_camera)
    // Survives the camera app taking over (and our process being killed meanwhile).
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            val file = photoPath?.let(::File) ?: return@rememberLauncherForActivityResult
            if (!taken) return@rememberLauncherForActivityResult
            scope.launch {
                val text = textReader?.read(context, file.toUri())?.getOrNull()
                withContext(Dispatchers.IO) { file.delete() }
                val prices = text?.let { PriceParser.parseAll(it, currentPreferred) }.orEmpty()
                if (prices.isEmpty()) currentOnMessage(noPrices) else currentOnPrices(prices)
            }
        }
    return remember(launcher, noCamera) {
        PriceScan {
            val (file, uri) = cacheFile(context, SCANS_SUBDIR, SCAN_FILENAME)
            photoPath = file.path
            try {
                launcher.launch(uri)
            } catch (_: ActivityNotFoundException) {
                currentOnMessage(noCamera)
            }
        }
    }
}

/** The prices a scan found; tapping one takes it into the converter. */
@Composable
fun ScannedPricesSheet(
    prices: List<ParsedPrice>,
    fallbackCurrency: Currency?,
    onPick: (ParsedPrice) -> Unit,
    onDismiss: () -> Unit,
) {
    LedgerBottomSheet(title = stringResource(R.string.scan_prices_title), onDismiss = onDismiss) {
        prices.forEachIndexed { index, price ->
            LedgerRow(
                onClick = {
                    onPick(price)
                    onDismiss()
                },
                showDivider = index != prices.lastIndex,
                label = { PriceLabel(price, price.currency ?: fallbackCurrency) },
            )
        }
    }
}

@Composable
private fun PriceLabel(
    price: ParsedPrice,
    currency: Currency?,
) {
    val context = LocalContext.current
    val text =
        remember(price, currency) {
            withCurrencySymbol(
                // As written on the tag: "3.50" keeps its zero.
                price.amount.toHumanReadableNumber(context, price.amount.scale().coerceAtLeast(0), trim = false),
                currency?.symbolOrIso().orEmpty(),
                hasAppendedCurrencySymbol(context),
            )
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        currency?.let {
            CurrencyFlagImage(it, Modifier.size(width = FLAG_WIDTH, height = FLAG_HEIGHT).clip(RoundedCornerShape(FLAG_CORNER)))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
