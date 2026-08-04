package de.salomax.currencies.view.main.spinner

import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate
import de.salomax.currencies.util.DECIMAL_PLACES_DEFAULT
import de.salomax.currencies.util.hasAppendedCurrencySymbol
import de.salomax.currencies.util.stripRtlMark
import de.salomax.currencies.util.toHumanReadableNumber
import java.math.BigDecimal
import java.math.MathContext

private const val FLAG_WIDTH_DP = 24
private const val FLAG_HEIGHT_DP = 17
private const val ROW_MIN_HEIGHT_DP = 56
private const val DRAG_ELEVATION_ALPHA = 0.85f
private const val API_HINT_ALPHA = 0.7f

internal data class CurrencyPickerConversion(
    val baseRate: Rate,
    val baseSum: BigDecimal,
    val decimalPlaces: Int = DECIMAL_PLACES_DEFAULT,
)

@Composable
internal fun SearchableCurrencyPicker(
    rates: List<Rate>,
    stars: List<Currency>,
    filterStarred: Boolean,
    conversion: CurrencyPickerConversion?,
    onRateClicked: (Rate) -> Unit,
    onStarClicked: (Rate) -> Unit,
    onToggleStarredFilter: () -> Unit,
    onStarredOrderChanged: (List<Currency>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val padH = dimensionResource(id = R.dimen.margin2x)
    val ctx = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            filterStarred = filterStarred,
            onToggleStarredFilter = onToggleStarredFilter,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = padH, vertical = dimensionResource(id = R.dimen.margin1x)),
        )
        val filtered =
            remember(rates, stars, filterStarred, query) {
                buildOrderedList(ctx, rates, stars, filterStarred, query)
            }
        val allowReorder = query.isEmpty() && !filterStarred
        // Stable SnapshotStateList across recompositions so LazyColumn keeps its
        // scroll/animation state when stars LiveData round-trips after a drag.
        // Rebuilding on every `filtered` change (previously via remember(filtered))
        // handed LazyColumn a new items collection and caused a visible refresh.
        val displayItems = remember { mutableStateListOf<Rate>() }
        LaunchedEffect(filtered) {
            if (displayItems != filtered) {
                displayItems.clear()
                displayItems.addAll(filtered)
            }
        }
        CurrencyList(
            items = displayItems,
            stars = stars,
            conversion = conversion,
            allowReorder = allowReorder,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onRateClicked = onRateClicked,
            onStarClicked = onStarClicked,
            onDragEnded = {
                if (allowReorder) {
                    onStarredOrderChanged(collectStarredOrder(displayItems, stars))
                }
            },
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    filterStarred: Boolean,
    onToggleStarredFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = null)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onToggleStarredFilter,
            modifier = Modifier.padding(start = dimensionResource(id = R.dimen.margin1x)),
        ) {
            Icon(
                imageVector = if (filterStarred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(id = R.string.tooltip_filter_starred),
                tint =
                    if (filterStarred) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun CurrencyList(
    items: SnapshotStateList<Rate>,
    stars: List<Currency>,
    conversion: CurrencyPickerConversion?,
    allowReorder: Boolean,
    modifier: Modifier = Modifier,
    onRateClicked: (Rate) -> Unit,
    onStarClicked: (Rate) -> Unit,
    onDragEnded: () -> Unit,
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_MIN_HEIGHT_DP.dp.toPx() }
    // Plain remember (not rememberLazyListState) so the scroll position is
    // scoped to the current composition — otherwise the saveable state carries
    // a prior dialog's scroll offset over and the list opens mid-scroll.
    val listState = remember { LazyListState() }

    // Don't compose the LazyColumn until we have real items. LazyListState
    // created against an empty list can end up in a stale position when items
    // arrive later; deferring creation guarantees the first layout uses the
    // final data and lands at index 0.
    if (items.isEmpty()) return

    // Contiguous starred prefix — drag target is clamped to this range so
    // the user can't shuffle a favorite into the unstarred section.
    val starredLastIndex =
        remember(items, stars) {
            var i = 0
            while (i < items.size && stars.contains(items[i].currency)) i++
            i - 1
        }

    LazyColumn(state = listState, modifier = modifier) {
        // Zero-height key anchor at index 0. LazyColumn preserves scroll by
        // pinning the first-visible item's key at the same y across item
        // reorders. Without an anchor, that first-visible key is often the
        // dragged row itself — so as its position changes, LazyList silently
        // scrolls to follow it (the "auto-scroll" the user saw). With this
        // anchor present at the top, LazyList tracks the anchor's stable key
        // and the reorder below it doesn't trigger any scroll drift.
        item(key = "top_anchor") { Spacer(Modifier.fillMaxWidth().padding(0.dp)) }
        itemsIndexed(items = items, key = { _, rate -> rate.currency.name }) { index, rate ->
            val isStarred = stars.contains(rate.currency)
            val isDragging = draggingIndex == index
            // Read the row's live index from inside the long-running pointerInput
            // block without restarting it. Keying pointerInput on `index` would
            // tear the gesture down every time a swap changes the row's position,
            // cancelling drags after a single step.
            val currentIndex by rememberUpdatedState(index)
            // Visual translation for the "make way" effect. The list itself
            // isn't mutated during drag — instead the dragged row follows the
            // finger and rows in the [source, target] range shift by one row
            // to open the gap. The single mutation happens on drag end, so
            // LazyColumn sees exactly one keyed reorder event (and the top
            // anchor above swallows any scroll compensation).
            val src = draggingIndex
            val tgt = targetIndex
            val translation =
                when {
                    isDragging -> dragOffsetY
                    src != null && tgt != null && src < tgt && index in (src + 1)..tgt -> -rowHeightPx
                    src != null && tgt != null && src > tgt && index in tgt until src -> rowHeightPx
                    else -> 0f
                }
            CurrencyRow(
                rate = rate,
                isStarred = isStarred,
                conversion = conversion,
                onClick = { onRateClicked(rate) },
                onStarClick = { onStarClicked(rate) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // animateItem handles reorder animations for changes
                        // that come from outside a drag (star toggles, list
                        // refresh). Skip it during active drag — we run our
                        // own translation and animateItem on top of that
                        // would double-move rows.
                        .then(if (draggingIndex != null) Modifier else Modifier.animateItem())
                        .graphicsLayer {
                            translationY = translation
                            if (isDragging) alpha = DRAG_ELEVATION_ALPHA
                        }.then(
                            if (allowReorder && isStarred) {
                                Modifier.pointerInput(allowReorder) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIndex = currentIndex
                                            targetIndex = currentIndex
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            val s = draggingIndex
                                            val t = targetIndex
                                            if (s != null &&
                                                t != null &&
                                                s != t &&
                                                s in items.indices &&
                                                t in items.indices
                                            ) {
                                                val moved = items.removeAt(s)
                                                items.add(t, moved)
                                            }
                                            draggingIndex = null
                                            targetIndex = null
                                            dragOffsetY = 0f
                                            onDragEnded()
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            targetIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val s = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                            val delta = kotlin.math.round(dragOffsetY / rowHeightPx).toInt()
                                            targetIndex = (s + delta).coerceIn(0, starredLastIndex)
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
            )
        }
        item(key = "api_hint") {
            ApiHintRow()
        }
    }
}

@Composable
private fun CurrencyRow(
    rate: Rate,
    isStarred: Boolean,
    conversion: CurrencyPickerConversion?,
    onClick: () -> Unit,
    onStarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    Row(
        modifier =
            modifier
                .heightIn(min = ROW_MIN_HEIGHT_DP.dp)
                .clickable(onClick = onClick)
                .padding(
                    horizontal = dimensionResource(id = R.dimen.margin2x),
                    vertical = dimensionResource(id = R.dimen.margin1x),
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CurrencyFlag(rate.currency)
        Spacer(Modifier.size(dimensionResource(id = R.dimen.margin2x)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rate.currency.iso4217Alpha(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = rate.currency.fullName(ctx),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (conversion != null) {
                Text(
                    text = buildConversionText(ctx, rate, conversion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onStarClick) {
            Icon(
                imageVector = if (isStarred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint =
                    if (isStarred) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Composable
private fun CurrencyFlag(currency: Currency) {
    AndroidView(
        factory = { ctx: Context ->
            ImageView(ctx).apply {
                adjustViewBounds = true
                contentDescription = null
            }
        },
        update = { iv -> iv.setImageDrawable(currency.flag(iv.context)) },
        modifier =
            Modifier
                .size(width = FLAG_WIDTH_DP.dp, height = FLAG_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(2.dp)),
    )
}

@Composable
private fun ApiHintRow() {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Text(
            text = stringResource(id = R.string.currency_dropdown_api_hint),
            style = MaterialTheme.typography.labelMedium,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = dimensionResource(id = R.dimen.margin2x),
                        vertical = dimensionResource(id = R.dimen.margin1x),
                    ).alpha(API_HINT_ALPHA),
        )
    }
}

private fun buildOrderedList(
    context: Context,
    rates: List<Rate>,
    stars: List<Currency>,
    filterStarred: Boolean,
    query: String,
): List<Rate> {
    val filtered =
        rates
            .filter { rate ->
                if (query.isNotEmpty()) {
                    rate.currency.fullName(context).contains(query, ignoreCase = true) ||
                        rate.currency.iso4217Alpha().contains(query, ignoreCase = true)
                } else {
                    true
                }
            }.filter { if (filterStarred) stars.contains(it.currency) else true }
    val starred = stars.mapNotNull { code -> filtered.find { it.currency == code } }
    val rest = filtered.filterNot { stars.contains(it.currency) }
    return starred + rest
}

private fun collectStarredOrder(
    items: List<Rate>,
    stars: List<Currency>,
): List<Currency> {
    val visible = items.map { it.currency }.filter { stars.contains(it) }
    val missing = stars.filterNot { visible.contains(it) }
    return visible + missing
}

private fun buildConversionText(
    context: Context,
    item: Rate,
    conversion: CurrencyPickerConversion,
): String {
    val sum = if (conversion.baseSum.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ONE else conversion.baseSum
    val sourceSymbol = conversion.baseRate.currency.symbol() ?: ""
    val source = sum.toHumanReadableNumber(context, decimalPlaces = conversion.decimalPlaces, trim = true)
    val destinationSymbol = item.currency.symbol() ?: ""
    val destination =
        sum
            .divide(conversion.baseRate.value, MathContext.DECIMAL128)
            .multiply(item.value)
            .toHumanReadableNumber(context, decimalPlaces = conversion.decimalPlaces, trim = true)
    val appended = hasAppendedCurrencySymbol(context)
    val left = formatAmount(source, sourceSymbol, appended)
    val right = formatAmount(destination, destinationSymbol, appended)
    return "$left = $right".stripRtlMark().trim()
}

private fun formatAmount(
    amount: String,
    symbol: String,
    appended: Boolean,
): String =
    when {
        symbol.isEmpty() -> amount
        appended -> "$amount $symbol"
        else -> "$symbol $amount"
    }
