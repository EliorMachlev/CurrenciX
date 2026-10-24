package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.util.formatCartAmount
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.ltrIsolate
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.LedgerSectionHeader
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.viewmodel.cart.TripSummary
import com.eliormachlev.currencix.viewmodel.cart.subtotalOf
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val ROW_MIN_HEIGHT_DP = 48

// An unnamed cart is listed by the start of its id.
private const val SHORT_ID_LENGTH = 8

// Between the parts of a saved cart's summary line.
private const val SUMMARY_SEPARATOR = " · "

private val SAVED_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** What the saved-carts list can ask for, each naming the cart or trip it's about. */
@Immutable
class SavedCartActions(
    val onPick: (SavedCart) -> Unit,
    val onRename: (SavedCart) -> Unit,
    val onDelete: (SavedCart) -> Unit,
    // Into a trip, or out of one (null).
    val onMoveToTrip: (SavedCart, String?) -> Unit = { _, _ -> },
    // A new trip, with this cart in it.
    val onNewTrip: (SavedCart) -> Unit = {},
    val onEditTrip: (Trip) -> Unit = {},
    // The trip's before-you-go checklist.
    val onTripChecklist: (Trip) -> Unit = {},
)

/**
 * The saved carts, grouped by trip: each trip's header (what it has cost so
 * far, against its budget) over its carts, then the carts in no trip.
 */
@Composable
fun SavedCartsList(
    items: List<SavedCart>,
    actions: SavedCartActions,
    trips: List<TripSummary> = emptyList(),
) {
    val tripIds = trips.mapTo(mutableSetOf()) { it.trip.id }
    val loose = items.filter { it.tripId !in tripIds }
    val row: @Composable (SavedCart) -> Unit = { cart -> SavedCartRow(cart, trips.map { it.trip }, actions) }
    // Trips under way or ahead first; those over go after them, in the order made.
    val today = remember { LocalDate.now() }
    val ordered = remember(trips, today) { trips.sortedBy { it.trip.hasEnded(today) } }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        ordered.forEach { summary ->
            item(key = "trip:${summary.trip.id}") {
                TripHeader(
                    summary,
                    today,
                    onEdit = { actions.onEditTrip(summary.trip) },
                    onChecklist = { actions.onTripChecklist(summary.trip) },
                )
            }
            items(items = items.filter { it.tripId == summary.trip.id }, key = { it.id }) { row(it) }
        }
        if (trips.isNotEmpty() && loose.isNotEmpty()) {
            item(
                key = OTHER_CARTS_KEY,
            ) { LedgerSectionHeader(stringResource(R.string.trip_other_carts)) }
        }
        items(items = loose, key = { it.id }) { row(it) }
    }
}

// Distinct from cart ids (UUIDs) and the "trip:" keys.
private const val OTHER_CARTS_KEY = "section:other"

// "Japan 2026     ☑ ✎" / "14 Nov – 21 Nov 2026" or "Ended" / "1,234.50 ₪ of 3,000.00 ₪" and a bar,
// or "1,234.50 ₪ spent" / "₪ 120 a day · budget ₪ 375 a day".
@Composable
private fun TripHeader(
    summary: TripSummary,
    today: LocalDate,
    onEdit: () -> Unit,
    onChecklist: () -> Unit,
) {
    val context = LocalContext.current
    val trip = summary.trip
    val spent = summary.spent ?: BigDecimal.ZERO
    // Amounts kept whole inside a right-to-left sentence.
    val spentText = ltrIsolate(context.formatCartAmount(spent, trip.currency))
    val budget = trip.budget
    val progress = budget?.takeIf { it.signum() > 0 }?.let { spent.divide(it, MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f) }
    val over = budget != null && spent > budget
    Column(Modifier.fillMaxWidth().padding(start = Spacing.Large, top = Spacing.Medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                trip.name.ifBlank { stringResource(R.string.trip_unnamed) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The checklist is for a trip with somewhere to go that isn't over.
            if (trip.destination != null && !trip.hasEnded(today)) {
                IconButton(onClick = rememberHapticOnClick(onChecklist)) {
                    Icon(painterResource(R.drawable.ic_checklist), contentDescription = stringResource(R.string.trip_checklist))
                }
            }
            IconButton(onClick = rememberHapticOnClick(onEdit)) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.trip_edit))
            }
        }
        TripWhen(trip, today)
        Text(
            if (budget == null) {
                stringResource(R.string.trip_spent, spentText)
            } else {
                stringResource(R.string.trip_spent_of, spentText, ltrIsolate(context.formatCartAmount(budget, trip.currency)))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TripPerDay(trip, summary.spent, today)
        progress?.let {
            LinearProgressIndicator(
                progress = { it },
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.XSmall, end = Spacing.Large),
            )
        }
    }
}

// Its dates, or "Ended" once they're past; nothing without dates.
@Composable
private fun TripWhen(
    trip: Trip,
    today: LocalDate,
) {
    val dates = TripDates(trip.start, trip.end).label(LocalConfiguration.current.locales[0]) ?: return
    Text(
        if (trip.hasEnded(today)) stringResource(R.string.trip_ended, dates) else dates,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// What it has cost a day so far, against the budget's share of each day when both are known.
@Composable
private fun TripPerDay(
    trip: Trip,
    spent: BigDecimal?,
    today: LocalDate,
) {
    val context = LocalContext.current
    val daysSoFar = trip.daysSoFar(today) ?: return
    val perDay = (spent ?: BigDecimal.ZERO).divide(BigDecimal.valueOf(daysSoFar), MathContext.DECIMAL64)
    val perDayText = ltrIsolate(context.formatCartAmount(perDay, trip.currency))
    val budgetPerDay =
        trip.budget?.let { budget -> trip.days?.let { budget.divide(BigDecimal.valueOf(it), MathContext.DECIMAL64) } }
    Text(
        if (budgetPerDay == null) {
            stringResource(R.string.trip_per_day, perDayText)
        } else {
            stringResource(R.string.trip_per_day_of, perDayText, ltrIsolate(context.formatCartAmount(budgetPerDay, trip.currency)))
        },
        style = MaterialTheme.typography.bodySmall,
        color =
            if (budgetPerDay != null &&
                perDay > budgetPerDay
            ) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    )
}

@Composable
private fun SavedCartRow(
    cart: SavedCart,
    trips: List<Trip>,
    actions: SavedCartActions,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ROW_MIN_HEIGHT_DP.dp)
                .hapticClickable(
                    onClickLabel = stringResource(id = R.string.a11y_action_load),
                    onClick = { actions.onPick(cart) },
                ).padding(horizontal = Spacing.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = Spacing.Small)) {
            Text(
                text = cart.name.ifBlank { cart.id.take(SHORT_ID_LENGTH) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = savedCartSummary(cart),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TripButton(cart = cart, trips = trips, actions = actions)
        IconButton(onClick = rememberHapticOnClick { actions.onRename(cart) }) {
            Icon(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = stringResource(id = R.string.cart_rename),
            )
        }
        IconButton(onClick = rememberHapticOnClick { actions.onDelete(cart) }) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(id = R.string.cart_delete_item),
            )
        }
    }
}

// 🧳: put the cart in a trip (the current one ticked), take it out, or start a new trip with it.
@Composable
private fun TripButton(
    cart: SavedCart,
    trips: List<Trip>,
    actions: SavedCartActions,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = rememberHapticOnClick { open = true }) {
            Icon(painterResource(R.drawable.ic_luggage), contentDescription = stringResource(R.string.trip_move))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val pick = { tripId: String? ->
                open = false
                actions.onMoveToTrip(cart, tripId)
            }
            trips.forEach { trip ->
                DropdownMenuItem(
                    text = { Text(trip.name.ifBlank { stringResource(R.string.trip_unnamed) }) },
                    leadingIcon = { if (trip.id == cart.tripId) Icon(painterResource(R.drawable.ic_check), contentDescription = null) },
                    onClick = { pick(trip.id) },
                )
            }
            if (cart.tripId != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.trip_none)) }, onClick = { pick(null) })
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.trip_new)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                onClick = {
                    open = false
                    actions.onNewTrip(cart)
                },
            )
        }
    }
}

// "4 items · 23.50 $ · 3 Oct 2026": what's in a saved cart, what it comes
// to in its own currency, and when it was saved.
@Composable
private fun savedCartSummary(cart: SavedCart): String {
    val context = LocalContext.current
    // In the app's language, which can differ from the phone's.
    val locale = LocalConfiguration.current.locales[0]
    val count = pluralStringResource(R.plurals.cart_saved_items, cart.items.size, cart.items.size)
    val total = remember(cart) { context.formatCartAmount(subtotalOf(cart), Currency.fromString(cart.currency)) }
    val date =
        remember(cart.createdAt, locale) {
            Instant
                .ofEpochMilli(cart.createdAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(SAVED_DATE_FORMAT.withLocale(locale))
        }
    return listOf(count, total, date).joinToString(SUMMARY_SEPARATOR)
}
