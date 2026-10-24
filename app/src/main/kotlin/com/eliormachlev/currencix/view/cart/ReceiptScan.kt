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
import com.eliormachlev.currencix.ocr.OcrLanguages
import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.ocr.Tessdata
import com.eliormachlev.currencix.ocr.TessdataDownloadWorker
import com.eliormachlev.currencix.ocr.installReceiptReader
import com.eliormachlev.currencix.ocr.isRecognizerInstalled
import com.eliormachlev.currencix.ocr.readReceipt
import com.eliormachlev.currencix.util.ReceiptLine
import com.eliormachlev.currencix.util.ReceiptParser
import com.eliormachlev.currencix.util.cacheFile
import com.eliormachlev.currencix.view.cart.compose.CartChoiceOption
import com.eliormachlev.currencix.view.cart.compose.CartChoiceRequest
import com.eliormachlev.currencix.view.compose.rememberDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Where the camera app writes the photo (see file_provider_paths.xml); deleted once read.
private const val RECEIPTS_DIR = "receipts"
private const val PHOTO_NAME = "receipt.jpg"

/**
 * Downloads the scan asks the user about first. Nothing is downloaded for
 * reading receipts until they say yes to it by name, with its size.
 */
sealed interface ReceiptSetup {
    /**
     * What reading needs and isn't here: the text [reader] (the Play app's
     * module) and the [english] data, plus, as a separate choice left
     * unticked, the app's own [language] when it hasn't been asked about yet.
     */
    data class Offer(
        val reader: Boolean,
        val english: Boolean,
        val language: TessLanguage?,
    ) : ReceiptSetup

    /** Whether to also read receipts in [language], its data downloaded once; English only otherwise. */
    data class LanguageOffer(
        val language: TessLanguage,
    ) : ReceiptSetup

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

    // The app language's OCR data (null for English), and the user's say on downloading it.
    internal var local: TessLanguage? = null
    internal var isInstalled: (TessLanguage) -> Boolean = { true }
    internal var isRecognizerHere: () -> Boolean = { true }
    internal var answerFor: (TessLanguage) -> Boolean? = { null }
    internal var answer: (TessLanguage, Boolean) -> Unit = { _, _ -> }

    // What to do once the reader is ready: show the photo choice.
    internal var afterSetup: () -> Unit = {}

    /** The download being offered or running; null otherwise. */
    var setup by mutableStateOf<ReceiptSetup?>(null)
        internal set

    /** The download just failed (cleared once said, see [setupFailureShown]). */
    var setupFailed by mutableStateOf(false)
        internal set

    /** The photo is being read. */
    var reading by mutableStateOf(false)
        internal set

    /** The items found, waiting for review; null when there's nothing to review. */
    var lines by mutableStateOf<List<ReceiptLine>?>(null)
        internal set

    /** The app language's OCR data is still downloading, so only English was used. */
    var languagePending by mutableStateOf(false)
        internal set

    /** The app language the user said no to, so receipts were read in English: it can be offered again. */
    var languageDeclined by mutableStateOf<TessLanguage?>(null)
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
     * reader is here and there's nothing to ask, otherwise once the user has
     * answered (and what they agreed to that reading needs has downloaded).
     */
    fun begin(then: () -> Unit) {
        val english = !isInstalled(TessLanguage.ENGLISH)
        val reader = !isRecognizerHere()
        val ask = local?.takeIf { !isInstalled(it) && answerFor(it) == null }
        afterSetup = then
        setup =
            when {
                english || reader -> ReceiptSetup.Offer(reader = reader, english = english, language = ask)
                ask != null -> ReceiptSetup.LanguageOffer(ask)
                else -> null
            }
        if (setup == null) then()
    }

    /** The reader's download, accepted; [withLanguage]: the offered app language too. */
    fun acceptSetup(withLanguage: Boolean) {
        (setup as? ReceiptSetup.Offer)?.language?.let { answer(it, withLanguage) }
        answer(TessLanguage.ENGLISH, true)
        startSetup()
    }

    /** The offer or the progress, closed: a download goes on, but nothing follows it here. */
    fun dismissSetup() {
        cancelSetup()
        setup = null
    }

    /** The app language offered on its own, answered; the scan goes on either way, in English until it's here. */
    fun answerLanguage(download: Boolean) {
        (setup as? ReceiptSetup.LanguageOffer)?.language?.let { language ->
            answer(language, download)
            if (download) {
                languageDeclined = null
                languagePending = lines != null
            }
        }
        setup = null
        afterSetup()
    }

    /** Asks again about the language the user said no to (from the review); nothing follows the answer. */
    fun offerLanguageAgain() {
        val language = languageDeclined ?: return
        afterSetup = {}
        setup = ReceiptSetup.LanguageOffer(language)
    }

    fun setupFailureShown() {
        setupFailed = false
    }
}

/**
 * Scanning a receipt into the cart: a photo from the system camera or the
 * photo picker (neither needs a permission), read on the device
 * ([readReceipt]) and split into items ([ReceiptParser]). No language data
 * ships with the app: the first scan offers what reading needs (English, and
 * the Play app's text reader, [installReceiptReader]), and the app language's
 * data is a separate yes or no, asked once ([ReceiptSetup]) and remembered
 * ([com.eliormachlev.currencix.repository.ReceiptLanguageSettings]); more can be
 * added, kept for a while and deleted in Settings ([OcrLanguages]).
 * A language agreed to downloads in the background ([TessdataDownloadWorker]);
 * until it's here, English is used.
 */
@Composable
fun rememberReceiptScan(): ReceiptScan {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val languages = rememberDatabase().receiptLanguages
    val local = TessLanguage.forLocale(LocalConfiguration.current.locales[0])
    // Made when a photo is taken, not before: most cart visits never scan.
    val photo = remember(context) { lazy { cacheFile(context, RECEIPTS_DIR, PHOTO_NAME) } }
    val scan = remember { ReceiptScan() }
    val answerFor = { language: TessLanguage -> languages.answerFor(language.code) }
    scan.local = local
    scan.isInstalled = { Tessdata.isInstalled(context, it) }
    scan.isRecognizerHere = { isRecognizerInstalled(context) }
    scan.answerFor = answerFor
    scan.answer = { language, allowed ->
        languages.setAllowed(language.code, allowed)
        if (allowed && language != TessLanguage.ENGLISH) TessdataDownloadWorker.enqueue(context, language)
    }
    val read = { uri: Uri, deleteAfter: Boolean ->
        scan.reading = true
        scope.launch {
            // A language kept for a trip that has ended isn't read with any more.
            OcrLanguages.deleteExpired(context)
            val text = readReceipt(context, uri, Tessdata.languages(context, local))
            if (deleteAfter) photo.value.first.delete()
            val missing = local?.takeIf { !Tessdata.isInstalled(context, it) }
            scan.languagePending = missing != null && answerFor(missing) == true
            scan.languageDeclined = missing?.takeIf { answerFor(it) == false }
            scan.lines = ReceiptParser.parse(text)
            scan.reading = false
        }
    }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken -> if (taken) read(photo.value.second, true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { read(it, false) } }
    // A language agreed to before whose download hasn't finished (or whose
    // file is gone) carries on; one not agreed to is never fetched.
    val resumeLanguage = { local?.takeIf { answerFor(it) == true }?.let { TessdataDownloadWorker.enqueue(context, it) } }
    scan.takePhoto = {
        resumeLanguage()
        camera.launch(photo.value.second)
    }
    scan.choosePhoto = {
        resumeLanguage()
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
