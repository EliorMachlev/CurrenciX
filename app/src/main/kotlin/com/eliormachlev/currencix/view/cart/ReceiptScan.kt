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
import com.eliormachlev.currencix.ocr.OcrLanguages
import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.ocr.Tessdata
import com.eliormachlev.currencix.ocr.TessdataDownloadWorker
import com.eliormachlev.currencix.ocr.awaitReceiptLanguages
import com.eliormachlev.currencix.ocr.forCurrency
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
     * With no language on the phone, the [languages] to read in, best first,
     * each a choice left unticked (the trip's or the cart currency's, the
     * app's own, English), whatever was said before; one kept [until] its
     * trip ends when it's for one. Reading needs at least one ticked.
     */
    data class Offer(
        val languages: List<TessLanguage>,
        val until: Map<TessLanguage, Instant> = emptyMap(),
    ) : ReceiptSetup

    /** Whether to also read receipts in [language], its data downloaded once (kept [until] the trip ends); not now otherwise. */
    data class LanguageOffer(
        val language: TessLanguage,
        val until: Instant? = null,
    ) : ReceiptSetup

    /** Waiting for the languages agreed to, which reading has to have. */
    data object Installing : ReceiptSetup
}

/** Where a receipt scan stands: reading the photo, or its lines found, for review. */
@Stable
class ReceiptScan internal constructor() {
    // Set by [rememberReceiptScan], where the photo launchers live.
    internal var takePhoto: () -> Unit = {}
    internal var choosePhoto: () -> Unit = {}

    // Waits for the languages reading has to have.
    internal var startSetup: (Set<TessLanguage>) -> Unit = {}
    internal var cancelSetup: () -> Unit = {}

    // The languages worth reading this cart's receipts in, best first (the
    // trip's or the cart currency's, then the app's own), each kept until [untilFor].
    internal var candidates: List<TessLanguage> = emptyList()
    internal var untilFor: (TessLanguage) -> Instant? = { null }
    internal var isInstalled: (TessLanguage) -> Boolean = { true }
    internal var answerFor: (TessLanguage) -> Boolean? = { null }
    internal var answer: (TessLanguage, Boolean, Instant?) -> Unit = { _, _, _ -> }

    // What to do once reading is ready: show the photo choice.
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

    /** A language agreed to is still downloading, so only those here were used. */
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
     * Starts a scan: [then] (show the photo choice) right away when there's
     * a language here and nothing to ask, otherwise once the user has
     * answered (and what they agreed to that reading needs has downloaded).
     */
    fun begin(then: () -> Unit) {
        val needsLanguage = TessLanguage.entries.none(isInstalled)
        val ask = candidates.firstOrNull { !isInstalled(it) && answerFor(it) == null }
        afterSetup = then
        setup =
            when {
                needsLanguage -> offer()
                ask != null -> ReceiptSetup.LanguageOffer(ask, untilFor(ask))
                else -> null
            }
        if (setup == null) then()
    }

    // With no language here, every one worth reading in (English included) is a choice.
    private fun offer(): ReceiptSetup.Offer {
        val languages = (candidates + TessLanguage.ENGLISH).distinct()
        val until = languages.mapNotNull { language -> untilFor(language)?.let { language to it } }.toMap()
        return ReceiptSetup.Offer(languages = languages, until = until)
    }

    /** The download, accepted with the [chosen] languages; the others count as said no to. */
    fun acceptSetup(chosen: Set<TessLanguage>) {
        val offer = setup as? ReceiptSetup.Offer ?: return
        offer.languages.forEach { answer(it, it in chosen, offer.until[it]) }
        startSetup(chosen)
    }

    /** The offer or the progress, closed: a download goes on, but nothing follows it here. */
    fun dismissSetup() {
        cancelSetup()
        setup = null
    }

    /** A language offered on its own, answered; the scan goes on either way, in those here until it is. */
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
 * ships with the app and none is downloaded by default: the first scan
 * offers the languages to read in, none ticked: [cart]'s (its trip's destination's,
 * else its currency's, [forCurrency]), the app's own and English, at least
 * one needed. Later, a language worth reading in that wasn't asked about is
 * a separate yes or no, asked once ([ReceiptSetup]) and remembered
 * ([com.eliormachlev.currencix.repository.ReceiptLanguageSettings]); one for a
 * trip with an end date is kept until that day ends. More can be added, kept
 * for a while and deleted in Settings ([OcrLanguages]). A language agreed to
 * downloads in the background ([TessdataDownloadWorker]); until it's here,
 * the ones on the phone are used.
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
    scan.answerFor = answerFor
    scan.answer = { language, allowed, until ->
        if (allowed) OcrLanguages.download(context, language, until) else languages.setAllowed(language.code, allowed = false)
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
    LanguageSetup(scan)
    return scan
}

// Wires [scan]'s wait for the languages agreed to: ReceiptSetup.Installing
// while they download, then the photo choice, or the failure.
@Composable
private fun LanguageSetup(scan: ReceiptScan) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val job = remember { arrayOfNulls<Job>(1) }
    scan.cancelSetup = { job[0]?.cancel() }
    scan.startSetup = { awaited ->
        scan.setup = ReceiptSetup.Installing
        job[0] =
            scope.launch {
                val arrived = awaitReceiptLanguages(context, awaited)
                scan.setup = null
                if (arrived) scan.afterSetup() else scan.setupFailed = true
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
