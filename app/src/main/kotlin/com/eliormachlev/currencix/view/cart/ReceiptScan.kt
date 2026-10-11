package com.eliormachlev.currencix.view.cart

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.ocr.Tessdata
import com.eliormachlev.currencix.ocr.TessdataDownloadWorker
import com.eliormachlev.currencix.ocr.readReceipt
import com.eliormachlev.currencix.util.ReceiptLine
import com.eliormachlev.currencix.util.ReceiptParser
import com.eliormachlev.currencix.util.cacheFile
import com.eliormachlev.currencix.view.cart.compose.CartChoiceOption
import com.eliormachlev.currencix.view.cart.compose.CartChoiceRequest
import kotlinx.coroutines.launch

// Where the camera app writes the photo (see file_provider_paths.xml); deleted once read.
private const val RECEIPTS_DIR = "receipts"
private const val PHOTO_NAME = "receipt.jpg"

/** Where a receipt scan stands: reading the photo, or its lines found, for review. */
@Stable
class ReceiptScan internal constructor() {
    // Set by [rememberReceiptScan], where the photo launchers live.
    internal var takePhoto: () -> Unit = {}
    internal var choosePhoto: () -> Unit = {}

    /** The photo is being read. */
    var reading by mutableStateOf(false)
        internal set

    /** The items found, waiting for review; null when there's nothing to review. */
    var lines by mutableStateOf<List<ReceiptLine>?>(null)
        internal set

    /** The app language's OCR data is still downloading, so only English was used. */
    var languagePending by mutableStateOf(false)
        internal set

    /** "Take a photo" / "Choose a photo", as a choice sheet. */
    fun sourceChoice() =
        CartChoiceRequest(
            titleRes = R.string.receipt_scan,
            options =
                listOf(
                    CartChoiceOption(R.string.receipt_take_photo, R.string.receipt_take_photo_desc, takePhoto),
                    CartChoiceOption(R.string.receipt_choose_photo, R.string.receipt_choose_photo_desc, choosePhoto),
                ),
        )

    /** The review is done (items added, or dismissed). */
    fun clear() {
        lines = null
    }
}

/**
 * Scanning a receipt into the cart: a photo from the system camera or the
 * photo picker (neither needs a permission), read on the device
 * ([readReceipt]) and split into items ([ReceiptParser]). The app
 * language's OCR data is fetched in the background the first time a scan
 * starts ([TessdataDownloadWorker]); until it's here, English is used.
 */
@Composable
fun rememberReceiptScan(): ReceiptScan {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val local = TessLanguage.forLocale(LocalConfiguration.current.locales[0])
    // Made when a photo is taken, not before: most cart visits never scan.
    val photo = remember(context) { lazy { cacheFile(context, RECEIPTS_DIR, PHOTO_NAME) } }
    val scan = remember { ReceiptScan() }
    val read = { uri: Uri, deleteAfter: Boolean ->
        scan.reading = true
        scope.launch {
            val text = readReceipt(context, uri, Tessdata.languages(context, local))
            if (deleteAfter) photo.value.first.delete()
            scan.languagePending = local != null && !Tessdata.isInstalled(context, local)
            scan.lines = ReceiptParser.parse(text)
            scan.reading = false
        }
    }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken -> if (taken) read(photo.value.second, true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { read(it, false) } }
    val fetchLanguage = { local?.let { TessdataDownloadWorker.enqueue(context, it) } }
    scan.takePhoto = {
        fetchLanguage()
        camera.launch(photo.value.second)
    }
    scan.choosePhoto = {
        fetchLanguage()
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    return scan
}
