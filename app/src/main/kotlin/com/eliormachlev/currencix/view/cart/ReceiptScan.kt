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
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.ocr.OcrInstall
import com.eliormachlev.currencix.ocr.OcrLanguages
import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.ocr.Tessdata
import com.eliormachlev.currencix.ocr.TessdataDownloadWorker
import com.eliormachlev.currencix.ocr.forCurrency
import com.eliormachlev.currencix.ocr.installReceiptReader
import com.eliormachlev.currencix.ocr.isRecognizerInstalled
import com.eliormachlev.currencix.ocr.readReceipt
import com.eliormachlev.currencix.util.ReceiptLine
import com.eliormachlev.currencix.util.ReceiptParser
import com.eliormachlev.currencix.util.cacheFile
import com.eliormachlev.currencix.view.cart.compose.CartChoiceOption
import com.eliormachlev.currencix.view.cart.compose.CartChoiceRequest
import com.eliormachlev.currencix.view.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.view.compose.rememberDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

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
     * unticked, a [language] not asked about yet (the trip's or the cart
     * currency's, else the app's own), kept [until] the trip ends when known.
     */
    data class Offer(
        val reader: Boolean,
        val english: Boolean,
        val language: TessLanguage?,
        val until: Instant? = null,
    ) : ReceiptSetup

    /** Whether to also read receipts in [language], its data downloaded once (kept [until] the trip ends); English only otherwise. */
    data class LanguageOffer(
        val language: TessLanguage,
        val until: Instant? = null,
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

    // The languages worth reading this cart's receipts in besides English, best first
    // (the trip's or the cart currency's, then the app's own), each kept until [untilFor].
    internal var candidates: List<TessLanguage> = emptyList()
    internal var untilFor: (TessLanguage) -> Instant? = { null }
    internal var isInstalled: (TessLanguage) -> Boolean = { true }
    internal var isRecognizerHere: () -> Boolean = { true }
    internal var answerFor: (TessLanguage) -> Boolean? = { null }
    internal var answer: (TessLanguage, Boolean, Instant?) -> Unit = { _, _, _ -> }

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

    /** A language agreed to is still downloading, so only English (and those here) was used. */
    var languagePending by mutableStateOf(false)
        internal set

    /** A language worth reading in that the user said no to, so it wasn't used: it can be offered again. */
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
        val ask = candidates.firstOrNull { !isInstalled(it) && answerFor(it) == null }
        afterSetup = then
        setup =
            when {
                english || reader -> ReceiptSetup.Offer(reader = reader, english = english, language = ask, until = ask?.let(untilFor))
                ask != null -> ReceiptSetup.LanguageOffer(ask, untilFor(ask))
                else -> null
            }
        if (setup == null) then()
    }

    /** The reader's download, accepted; [withLanguage]: the offered app language too. */
    fun acceptSetup(withLanguage: Boolean) {
        (setup as? ReceiptSetup.Offer)?.let { offer -> offer.language?.let { answer(it, withLanguage, offer.until) } }
        answer(TessLanguage.ENGLISH, true, null)
        startSetup()
    }

    /** The offer or the progress, closed: a download goes on, but nothing follows it here. */
    fun dismissSetup() {
        cancelSetup()
        setup = null
    }

    /** A language offered on its own, answered; the scan goes on either way, in English until it's here. */
    fun answerLanguage(download: Boolean) {
        (setup as? ReceiptSetup.LanguageOffer)?.let { offer ->
            answer(offer.language, download, offer.until)
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
        setup = ReceiptSetup.LanguageOffer(language, untilFor(language))
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
 * the Play app's text reader, [installReceiptReader]). The language worth
 * reading [cart]'s receipts in (its trip's destination's, else its
 * currency's, [forCurrency]; else the app's own) is a separate yes or no,
 * asked once ([ReceiptSetup]) and remembered
 * ([com.eliormachlev.currencix.repository.ReceiptLanguageSettings]); one for a
 * trip with an end date is kept until that day ends. More can be added, kept
 * for a while and deleted in Settings ([OcrLanguages]). A language agreed to
 * downloads in the background ([TessdataDownloadWorker]); until it's here,
 * English is used.
 */
@Composable
fun rememberReceiptScan(cart: SavedCart?): ReceiptScan {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = rememberDatabase()
    val languages = db.receiptLanguages
    val trips by db.carts.trips.collectAsStateWithLifecycle()
    val local = TessLanguage.forLocale(LocalConfiguration.current.locales[0])
    val trip = tripOf(cart, trips, LocalDate.now())
    val suggested = (trip?.destination ?: cart?.currency?.let(Currency::fromString))?.let(TessLanguage::forCurrency)
    val candidates = listOfNotNull(suggested, local).distinct()
    // Made when a photo is taken, not before: most cart visits never scan.
    val photo = remember(context) { lazy { cacheFile(context, RECEIPTS_DIR, PHOTO_NAME) } }
    val scan = remember { ReceiptScan() }
    val answerFor = { language: TessLanguage -> languages.answerFor(language.code) }
    scan.candidates = candidates
    scan.untilFor = { language -> trip?.takeIf { language == suggested }?.endsAt() }
    scan.isInstalled = { Tessdata.isInstalled(context, it) }
    scan.isRecognizerHere = { isRecognizerInstalled(context) }
    scan.answerFor = answerFor
    scan.answer = { language, allowed, until ->
        when {
            !allowed || language == TessLanguage.ENGLISH -> languages.setAllowed(language.code, allowed)
            else -> OcrLanguages.download(context, language, until)
        }
    }
    val read = { uri: Uri, deleteAfter: Boolean ->
        scan.reading = true
        scope.launch {
            // A language kept for a trip that has ended isn't read with any more.
            OcrLanguages.deleteExpired(context)
            val text = readReceipt(context, uri, Tessdata.languages(context, local))
            if (deleteAfter) photo.value.first.delete()
            val missing = candidates.filterNot { Tessdata.isInstalled(context, it) }
            scan.languagePending = missing.any { answerFor(it) == true }
            scan.languageDeclined = missing.firstOrNull { answerFor(it) == false }
            scan.lines = ReceiptParser.parse(text)
            scan.reading = false
        }
    }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken -> if (taken) read(photo.value.second, true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { read(it, false) } }
    // A language agreed to before whose download hasn't finished (or whose
    // file is gone) carries on; one not agreed to is never fetched.
    val resumeLanguages = { candidates.filter { answerFor(it) == true }.forEach { TessdataDownloadWorker.enqueue(context, it) } }
    scan.takePhoto = {
        resumeLanguages()
        camera.launch(photo.value.second)
    }
    scan.choosePhoto = {
        resumeLanguages()
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

/**
 * The trip [cart] is for: the one it's saved in, else one under way or ahead
 * whose destination is the cart's currency (shopping there before saving).
 */
internal fun tripOf(
    cart: SavedCart?,
    trips: List<Trip>,
    today: LocalDate,
): Trip? {
    cart ?: return null
    val currency = Currency.fromString(cart.currency)
    return trips.firstOrNull { it.id == cart.tripId }
        ?: trips.firstOrNull { it.destination != null && it.destination == currency && !it.hasEnded(today) }
}
