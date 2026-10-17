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
import com.eliormachlev.currencix.ocr.OcrInstall
import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.ocr.Tessdata
import com.eliormachlev.currencix.ocr.TessdataDownloadWorker
import com.eliormachlev.currencix.ocr.installReceiptReader
import com.eliormachlev.currencix.ocr.isReceiptReaderReady
import com.eliormachlev.currencix.ocr.readReceipt
import com.eliormachlev.currencix.util.ReceiptLine
import com.eliormachlev.currencix.util.ReceiptParser
import com.eliormachlev.currencix.util.cacheFile
import com.eliormachlev.currencix.view.cart.compose.CartChoiceOption
import com.eliormachlev.currencix.view.cart.compose.CartChoiceRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Where the camera app writes the photo (see file_provider_paths.xml); deleted once read.
private const val RECEIPTS_DIR = "receipts"
private const val PHOTO_NAME = "receipt.jpg"

/** Getting the receipt reader onto the device, where the build doesn't carry it (Play). */
sealed interface ReceiptSetup {
    /** Asking whether to download it. */
    data object Offer : ReceiptSetup

    /** Downloading; [fraction] done when known. */
    data class Installing(
        val fraction: Float?,
    ) : ReceiptSetup
}

/** Where a receipt scan stands: reading the photo, or its lines found, for review. */
@Stable
class ReceiptScan internal constructor() {
    // Set by [rememberReceiptScan], where the photo launchers live.
    internal var takePhoto: () -> Unit = {}
    internal var choosePhoto: () -> Unit = {}
    internal var startSetup: () -> Unit = {}
    internal var cancelSetup: () -> Unit = {}

    // What to do once the reader is ready: show the photo choice.
    internal var afterSetup: () -> Unit = {}

    /** The reader's download, while it's being offered or running; null otherwise. */
    var setup by mutableStateOf<ReceiptSetup?>(null)
        internal set

    /** The download just failed (cleared once said, see [setupFailureShown]). */
    var setupFailed by mutableStateOf(false)
        internal set

    internal var isReady: () -> Boolean = { true }

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
                    CartChoiceOption(R.string.receipt_take_photo, R.string.receipt_take_photo_desc, onPick = takePhoto),
                    CartChoiceOption(R.string.receipt_choose_photo, R.string.receipt_choose_photo_desc, onPick = choosePhoto),
                ),
        )

    /** The review is done (items added, or dismissed). */
    fun clear() {
        lines = null
    }

    /**
     * Starts a scan: [then] (show the photo choice) right away when the
     * reader is on the device, otherwise once its download, offered first,
     * is done.
     */
    fun begin(then: () -> Unit) {
        if (isReady()) {
            then()
        } else {
            afterSetup = then
            setup = ReceiptSetup.Offer
        }
    }

    /** The offered download, accepted. */
    fun acceptSetup() = startSetup()

    /** The offer or the progress, closed: the download goes on, but nothing follows it here. */
    fun dismissSetup() {
        cancelSetup()
        setup = null
    }

    fun setupFailureShown() {
        setupFailed = false
    }
}

/**
 * Scanning a receipt into the cart: a photo from the system camera or the
 * photo picker (neither needs a permission), read on the device
 * ([readReceipt]) and split into items ([ReceiptParser]). The app
 * language's OCR data is fetched in the background the first time a scan
 * starts ([TessdataDownloadWorker]); until it's here, English is used. Where
 * the reader itself isn't on the device (the Play app's on-demand module),
 * the first scan offers to download it ([installReceiptReader]).
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
    ReaderSetup(scan)
    return scan
}

// Wires [scan]'s download of the reader: the store's confirmation dialog
// when it asks for one, progress into ReceiptSetup.Installing, and on to the
// photo choice once it's done.
@Composable
private fun ReaderSetup(scan: ReceiptScan) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    val job = remember { arrayOfNulls<Job>(1) }
    scan.isReady = { isReceiptReaderReady(context) }
    scan.cancelSetup = { job[0]?.cancel() }
    scan.startSetup = {
        scan.setup = ReceiptSetup.Installing(null)
        job[0] =
            scope.launch {
                installReceiptReader(context).collect { step ->
                    when (step) {
                        is OcrInstall.Downloading -> {
                            scan.setup = ReceiptSetup.Installing(step.fraction)
                        }

                        is OcrInstall.NeedsConfirmation -> {
                            step.confirm(confirm)
                        }

                        OcrInstall.Installed -> {
                            scan.setup = null
                            scan.afterSetup()
                        }

                        OcrInstall.Failed -> {
                            scan.setup = null
                            scan.setupFailed = true
                        }
                    }
                }
            }
    }
}
