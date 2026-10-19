package com.eliormachlev.currencix.view.main

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.util.ParsedPrice
import com.eliormachlev.currencix.util.PriceParser

// The label on what the app copies itself: never offered back as a paste.
private const val OWN_CLIP_LABEL = "CurrenciX"

// A copy older than this is probably not meant for the converter.
internal const val RECENT_COPY_MILLIS = 10 * 60 * 1000L

// Longer than any price someone would copy; a guard against pasting a page.
private const val MAX_CLIP_LENGTH = 200

// The clipboard, always through the application context (see rememberClipboardOffer).
internal fun clipboardOf(context: Context): ClipboardManager = context.applicationContext.getSystemService(ClipboardManager::class.java)

/** Copies [text] (an amount from the converter), marked as the app's own. */
internal fun copyToClipboard(
    context: Context,
    text: CharSequence,
) {
    val clipboard = clipboardOf(context)
    // The system confirms the copy itself (with a preview); a message of
    // ours on top of it would be noise.
    clipboard.setPrimaryClip(ClipData.newPlainText(OWN_CLIP_LABEL, text))
}

/** What the clipboard says about its clip, without reading it. */
internal data class ClipInfo(
    val isText: Boolean,
    val label: String?,
    val copiedAt: Long,
)

/** [ClipInfo] from Android's description; reading it shows no "pasted from clipboard" notice. */
internal fun ClipDescription.toClipInfo() =
    ClipInfo(
        isText = hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML),
        label = label?.toString(),
        copiedAt = timestamp,
    )

/**
 * Whether a clip is worth offering: text, copied in the last
 * [RECENT_COPY_MILLIS] of [now], and not the app's own.
 */
internal fun isPasteCandidate(
    clip: ClipInfo?,
    now: Long,
): Boolean = clip != null && clip.isText && clip.label != OWN_CLIP_LABEL && now - clip.copiedAt in 0..RECENT_COPY_MILLIS

/**
 * The pair to convert a pasted [price] with, from [current]: its currency
 * becomes the "from" side (swapping when it's the "to" side); without one,
 * or when it's already "from", the pair stays.
 */
internal fun pairForPaste(
    price: ParsedPrice,
    current: CurrencyPair,
): CurrencyPair {
    val currency = price.currency ?: return current
    return when (currency) {
        current.from -> current
        current.to -> CurrencyPair(current.to, current.from)
        else -> CurrencyPair(currency, current.to)
    }
}

/** The clipboard as the converter's paste chip sees it. */
internal class ClipboardOffer(
    /** Whether to show the chip. */
    val available: Boolean,
    /** Reads the clip (Android notes the paste) and finds a price in it; null without one. Hides the chip for this clip. */
    val take: (preferred: List<Currency>) -> ParsedPrice?,
)

/**
 * Watches the clipboard while the screen is resumed and on every new copy,
 * offering a recent clip once.
 */
@Composable
internal fun rememberClipboardOffer(): ClipboardOffer {
    val context = LocalContext.current
    // From the application, not the activity: a ClipboardManager keeps the
    // context it came from, and the system's clipboard service keeps the
    // manager (through the binder it registers for change listeners) long
    // after the listener is removed. Taken from the activity, that held each
    // destroyed MainActivity (LeakCanary: ClipboardManager$1 → mContext).
    val clipboard = remember(context) { clipboardOf(context) }
    var available by remember { mutableStateOf(false) }
    // The clip already offered and taken, by its copy time: not offered again.
    var taken by remember { mutableStateOf<Long?>(null) }
    val check = {
        val clip = clipboard.primaryClipDescription?.toClipInfo()
        available = isPasteCandidate(clip, System.currentTimeMillis()) && clip?.copiedAt != taken
    }
    LifecycleResumeEffect(clipboard) {
        check()
        onPauseOrDispose { }
    }
    DisposableEffect(clipboard) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener { check() }
        clipboard.addPrimaryClipChangedListener(listener)
        onDispose { clipboard.removePrimaryClipChangedListener(listener) }
    }
    return ClipboardOffer(available) { preferred ->
        taken = clipboard.primaryClipDescription?.timestamp
        available = false
        val text =
            clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.take(MAX_CLIP_LENGTH)
                ?.toString()
        text?.let { PriceParser.parse(it, preferred) }
    }
}
