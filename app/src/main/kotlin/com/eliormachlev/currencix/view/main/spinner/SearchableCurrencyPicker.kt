package com.eliormachlev.currencix.view.main.spinner

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.util.DECIMAL_PLACES_DEFAULT
import com.eliormachlev.currencix.util.DISABLED_ROW_ALPHA
import com.eliormachlev.currencix.util.hasAppendedCurrencySymbol
import com.eliormachlev.currencix.util.normalizeForSearch
import com.eliormachlev.currencix.util.stripRtlMark
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.compose.CurrencyFlagImage
import com.eliormachlev.currencix.view.compose.FavoriteToggleIcon
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.dragReorderGraphics
import com.eliormachlev.currencix.view.compose.dragReorderHandle
import com.eliormachlev.currencix.view.compose.rememberDragReorderState
import java.math.BigDecimal
import java.math.MathContext

private const val FLAG_WIDTH_DP = 24
private const val FLAG_HEIGHT_DP = 17
private const val FLAG_CORNER_RADIUS_DP = 2
private const val ROW_MIN_HEIGHT_DP = 56
private const val API_HINT_ALPHA = 0.7f

internal data class CurrencyPickerConversion(
    val baseRate: Rate,
    val baseSum: BigDecimal,
    val decimalPlaces: Int = DECIMAL_PLACES_DEFAULT,
)

@Composable
@Suppress("LongParameterList")
internal fun SearchableCurrencyPicker(
    rates: List<Rate>,
    stars: List<Currency>,
    filterStarred: Boolean,
    conversion: CurrencyPickerConversion?,
    disabledCurrency: Currency?,
    onRateClicked: (Rate) -> Unit,
    onStarClicked: (Rate) -> Unit,
    onToggleStarredFilter: () -> Unit,
    onStarredOrderChanged: (List<Currency>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val padH = dimensionResource(id = R.dimen.margin2x)
    val ctx = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
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
            disabledCurrency = disabledCurrency,
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
            placeholder = { Text(text = stringResource(id = R.string.a11y_search_currencies)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(id = R.string.a11y_search_currencies),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = stringResource(id = R.string.a11y_clear_search),
                        )
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
    disabledCurrency: Currency?,
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
                    disabledCurrency = disabledCurrency,
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
                isDisabled = rate.currency == disabledCurrency,
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
    disabledCurrency: Currency?,
    onRateClicked: (Rate) -> Unit,
    onStarClicked: (Rate) -> Unit,
    onDragEnded: () -> Unit,
) {
    val drag = rememberDragReorderState()
    val rowHeightPx = with(LocalDensity.current) { ROW_MIN_HEIGHT_DP.dp.toPx() }

    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, rate ->
            key(rate.currency.name) {
                CurrencyRow(
                    rate = rate,
                    isStarred = true,
                    conversion = conversion,
                    isDisabled = rate.currency == disabledCurrency,
                    onClick = { onRateClicked(rate) },
                    onStarClick = { onStarClicked(rate) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .dragReorderGraphics(drag, index, rowHeightPx)
                            .then(
                                if (allowReorder) {
                                    Modifier.dragReorderHandle(
                                        state = drag,
                                        index = index,
                                        key = rate.currency.name,
                                        rowHeightPx = rowHeightPx,
                                        itemCount = { items.size },
                                        onCommit = { from, to ->
                                            Snapshot.withMutableSnapshot {
                                                val moved = items.removeAt(from)
                                                items.add(to, moved)
                                            }
                                            onDragEnded()
                                        },
                                    )
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
@Suppress("LongParameterList")
private fun CurrencyRow(
    rate: Rate,
    isStarred: Boolean,
    conversion: CurrencyPickerConversion?,
    isDisabled: Boolean,
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
    // outer Row via `modifier`. When [isDisabled] the flag+text region is
    // greyed and unclickable (typically because the same currency is already
    // selected on the opposite side of the pair), but the star toggle stays
    // interactive — favoriting is independent of picker selection.
    Row(
        modifier = modifier.heightIn(min = ROW_MIN_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(enabled = !isDisabled, onClick = onClick)
                    .alpha(if (isDisabled) DISABLED_ROW_ALPHA else 1f)
                    .padding(
                        horizontal = dimensionResource(id = R.dimen.margin2x),
                        vertical = dimensionResource(id = R.dimen.margin1x),
                    )
                    // TalkBack would otherwise announce ISO code, full name, and
                    // preview conversion as three separate focus stops, forcing
                    // the user to swipe three times per row. Merge into one node
                    // so the whole row speaks as a single item.
                    .semantics(mergeDescendants = true) {},
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
                    Ltr {
                        Text(
                            text = buildConversionText(ctx, rate, conversion),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        FavoriteToggleIcon(
            active = isStarred,
            contentDescription = null,
            onClick = onStarClick,
        )
    }
}

@Composable
private fun CurrencyFlag(currency: Currency) {
    CurrencyFlagImage(
        currency = currency,
        modifier =
            Modifier
                .size(width = FLAG_WIDTH_DP.dp, height = FLAG_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(FLAG_CORNER_RADIUS_DP.dp)),
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

// [normalizedQuery] must already be [normalizeForSearch]-ed by the caller —
// filter passes iterate rates and call this once per row, so re-normalizing
// the query per row would be pure waste.
private fun matchesQuery(
    context: Context,
    rate: Rate,
    normalizedQuery: String,
): Boolean =
    normalizedQuery.isEmpty() ||
        rate.currency
            .fullName(context)
            .normalizeForSearch()
            .contains(normalizedQuery) ||
        rate.currency
            .iso4217Alpha()
            .normalizeForSearch()
            .contains(normalizedQuery)

private fun buildStarredList(
    context: Context,
    rates: List<Rate>,
    stars: List<Currency>,
    query: String,
): List<Rate> {
    val normalizedQuery = query.normalizeForSearch()
    return stars
        .mapNotNull { code -> rates.find { it.currency == code } }
        .filter { matchesQuery(context, it, normalizedQuery) }
}

private fun buildNonStarredList(
    context: Context,
    rates: List<Rate>,
    stars: List<Currency>,
    query: String,
): List<Rate> {
    val normalizedQuery = query.normalizeForSearch()
    return rates
        .filterNot { stars.contains(it.currency) }
        .filter { matchesQuery(context, it, normalizedQuery) }
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
