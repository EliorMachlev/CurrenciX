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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
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
        val allowReorder = query.isEmpty() && !filterStarred
        // Starred rates in the user-defined order, filtered by query. Held in
        // a SnapshotStateList so the drag gesture can mutate it in place on
        // drop without rebuilding the whole picker. Keyed on the inputs so the
        // list is populated synchronously on the first frame that has data —
        // an async LaunchedEffect fill would render an empty favorites section
        // first, and LazyList's key-anchored scroll would then hold the first
        // non-starred key at the top when favorites arrived on the next frame.
        val starredDisplay =
            remember(rates, stars, query) {
                mutableStateListOf<Rate>().apply {
                    addAll(buildStarredList(ctx, rates, stars, query))
                }
            }
        val nonStarredFiltered =
            remember(rates, stars, filterStarred, query) {
                if (filterStarred) emptyList() else buildNonStarredList(ctx, rates, stars, query)
            }
        CurrencyList(
            starredItems = starredDisplay,
            nonStarredItems = nonStarredFiltered,
            conversion = conversion,
            allowReorder = allowReorder,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onRateClicked = onRateClicked,
            onStarClicked = onStarClicked,
            onDragEnded = {
                if (allowReorder) {
                    onStarredOrderChanged(collectStarredOrder(starredDisplay, stars))
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
    val keyboard = LocalSoftwareKeyboardController.current
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
            modifier =
                Modifier
                    .weight(1f)
                    // AlertDialog-hosted ComposeView doesn't reliably raise
                    // the IME on focus by itself; explicitly ask the keyboard
                    // controller to show whenever the field gains focus.
                    .onFocusChanged { if (it.isFocused) keyboard?.show() },
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
private fun KeepAtTopOnFavoritesAppear(
    listState: LazyListState,
    starredCount: Int,
) {
    // The starred list identity changes on every rates/stars/query update
    // (remember(rates, stars, query) rebuilds it), so we can't key the effect
    // on the list itself without losing prev-count tracking across changes.
    // Wrap the count in rememberUpdatedState so snapshotFlow reads the fresh
    // value on each recomposition instead of a captured stale one.
    val currentCount by rememberUpdatedState(starredCount)
    LaunchedEffect(listState) {
        var prevCount = -1
        var prevAtTop = true
        snapshotFlow {
            currentCount to
                (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0)
        }.collect { (count, atTop) ->
            if (prevCount == 0 && count > 0 && prevAtTop) {
                listState.scrollToItem(0)
            }
            prevCount = count
            prevAtTop = atTop
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun CurrencyList(
    starredItems: SnapshotStateList<Rate>,
    nonStarredItems: List<Rate>,
    conversion: CurrencyPickerConversion?,
    allowReorder: Boolean,
    modifier: Modifier = Modifier,
    onRateClicked: (Rate) -> Unit,
    onStarClicked: (Rate) -> Unit,
    onDragEnded: () -> Unit,
) {
    // Plain remember (not rememberLazyListState) so the scroll position is
    // scoped to the current composition — otherwise the saveable state carries
    // a prior dialog's scroll offset over and the list opens mid-scroll.
    val listState = remember { LazyListState() }
    // When the favorites slot appears at index 0 (empty → non-empty), LazyList
    // key-preservation keeps the previously-first-visible non-starred key at
    // the viewport top, pushing the new favorites section above the fold. If
    // the user was already scrolled to the top, snap back to the top so the
    // freshly-added favorite is what they see.
    KeepAtTopOnFavoritesAppear(listState = listState, starredCount = starredItems.size)
    if (starredItems.isEmpty() && nonStarredItems.isEmpty()) return

    LazyColumn(state = listState, modifier = modifier) {
        // Favorites live in a single lazy slot as a non-lazy Column. That
        // sidesteps LazyList's key-anchored scroll preservation entirely for
        // the drag: the drag mutation happens inside the Column, and
        // LazyColumn just sees one item slot ("favorites") whose contents
        // recompose.
        if (starredItems.isNotEmpty()) {
            item(key = "favorites") {
                FavoritesSection(
                    items = starredItems,
                    conversion = conversion,
                    allowReorder = allowReorder,
                    onRateClicked = onRateClicked,
                    onStarClicked = onStarClicked,
                    onDragEnded = onDragEnded,
                )
            }
        }
        items(items = nonStarredItems, key = { it.currency.name }) { rate ->
            CurrencyRow(
                rate = rate,
                isStarred = false,
                conversion = conversion,
                onClick = { onRateClicked(rate) },
                onStarClick = { onStarClicked(rate) },
                modifier = Modifier.fillMaxWidth().animateItem(),
            )
        }
        item(key = "api_hint") {
            ApiHintRow()
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun FavoritesSection(
    items: SnapshotStateList<Rate>,
    conversion: CurrencyPickerConversion?,
    allowReorder: Boolean,
    onRateClicked: (Rate) -> Unit,
    onStarClicked: (Rate) -> Unit,
    onDragEnded: () -> Unit,
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_MIN_HEIGHT_DP.dp.toPx() }

    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, rate ->
            key(rate.currency.name) {
                val isDragging = draggingIndex == index
                val currentIndex by rememberUpdatedState(index)
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
                    isStarred = true,
                    conversion = conversion,
                    onClick = { onRateClicked(rate) },
                    onStarClick = { onStarClicked(rate) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = translation
                                if (isDragging) alpha = DRAG_ELEVATION_ALPHA
                            }.then(
                                if (allowReorder) {
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
                                                    Snapshot.withMutableSnapshot {
                                                        val moved = items.removeAt(s)
                                                        items.add(t, moved)
                                                    }
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
                                                targetIndex = (s + delta).coerceIn(0, items.lastIndex)
                                            },
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                )
            }
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
    // Row-level clickable used to wrap flag + text + star, so a rapid tap
    // that landed just outside the 48dp IconButton fell through to the row
    // and dismissed the dialog. Split the click regions: the flag+text area
    // is the dismiss-on-select target, the star's own IconButton is a
    // separate target that only toggles. Drag modifier still lives on the
    // outer Row via `modifier`.
    Row(
        modifier = modifier.heightIn(min = ROW_MIN_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
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
                    // Math reads left-to-right in every locale — force LTR so
                    // "1 $ = 3.70 ₪" isn't visually mirrored under RTL layout.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = buildConversionText(ctx, rate, conversion),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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

private fun matchesQuery(
    context: Context,
    rate: Rate,
    query: String,
): Boolean =
    query.isEmpty() ||
        rate.currency.fullName(context).contains(query, ignoreCase = true) ||
        rate.currency.iso4217Alpha().contains(query, ignoreCase = true)

private fun buildStarredList(
    context: Context,
    rates: List<Rate>,
    stars: List<Currency>,
    query: String,
): List<Rate> =
    stars
        .mapNotNull { code -> rates.find { it.currency == code } }
        .filter { matchesQuery(context, it, query) }

private fun buildNonStarredList(
    context: Context,
    rates: List<Rate>,
    stars: List<Currency>,
    query: String,
): List<Rate> =
    rates
        .filterNot { stars.contains(it.currency) }
        .filter { matchesQuery(context, it, query) }

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
