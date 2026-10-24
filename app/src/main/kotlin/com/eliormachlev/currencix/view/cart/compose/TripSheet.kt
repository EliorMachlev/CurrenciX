package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Trip
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.parseDecimal
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.NameInputTransformation
import com.eliormachlev.currencix.view.compose.SupportingText
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPromptSheet
import com.eliormachlev.currencix.view.compose.fieldMessage
import com.eliormachlev.currencix.view.compose.invalidUnless
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.view.main.spinner.CurrencyPickerSheet
import com.eliormachlev.currencix.viewmodel.cart.TripDraft
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A new trip, or the edits to [initial]: its name, the home currency its
 * spending adds up in (one of [currencies]), an optional budget in it, and,
 * optionally, where it's to (the currency paid there, from the full list)
 * and when (a date range). Confirming hands [onSave] the [TripDraft].
 * Editing offers Delete too ([onDelete]).
 */
@Composable
fun TripSheet(
    initial: Trip?,
    currencies: List<Currency>,
    onSave: (TripDraft) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val name = rememberTextFieldState(initial?.name.orEmpty())
    val budgetField = rememberTextFieldState(initial?.budget?.toPlainString().orEmpty())
    var currency by rememberSaveable { mutableStateOf(initial?.currency ?: currencies.first()) }
    val plan = rememberTripPlan(initial)
    val budgetText = budgetField.text.toString()
    val budget = parseDecimal(budgetText)?.takeIf { it.signum() > 0 }
    val budgetError = invalidUnless(budgetText, budget, stringResource(R.string.error_positive_number))
    LedgerPromptSheet(
        title = stringResource(if (initial == null) R.string.trip_new else R.string.trip_edit),
        confirmLabel = stringResource(R.string.done),
        onConfirm = { onSave(plan.draft(name.text.toString().trim(), currency, budget)) },
        onDismiss = onDismiss,
        confirmEnabled = name.text.isNotBlank() && budgetError == null,
        leadingAction = onDelete?.let { delete -> { DeleteTripButton(delete) } },
    ) {
        OutlinedTextField(
            state = name,
            label = { Text(stringResource(R.string.trip_name)) },
            inputTransformation = NameInputTransformation,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.Medium))
        HomeCurrencyChips(currencies, selected = currency, onSelect = { currency = it })
        Spacer(Modifier.height(Spacing.Small))
        BudgetField(budgetField, currency, budgetError)
        Spacer(Modifier.height(Spacing.Small))
        TripPlanRows(plan)
        Spacer(Modifier.height(Spacing.Small))
        SupportingText(stringResource(R.string.trip_rates_note))
    }
    TripPlanPickers(plan, home = currency)
}

@Composable
private fun DeleteTripButton(onDelete: () -> Unit) {
    TextButton(onClick = rememberHapticOnClick(onDelete)) {
        Text(stringResource(R.string.trip_delete), color = MaterialTheme.colorScheme.error)
    }
}

// The home currencies to pick from, as chips.
@Composable
private fun HomeCurrencyChips(
    currencies: List<Currency>,
    selected: Currency,
    onSelect: (Currency) -> Unit,
) {
    Text(stringResource(R.string.trip_currency), style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        currencies.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(option.iso4217Alpha()) })
        }
    }
}

@Composable
private fun BudgetField(
    state: TextFieldState,
    currency: Currency,
    error: String?,
) {
    OutlinedTextField(
        state = state,
        label = { Text(stringResource(R.string.trip_budget)) },
        suffix = { Text(currency.iso4217Alpha()) },
        isError = error != null,
        supportingText = fieldMessage(error),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Where and when a trip is, as it's being edited, and which of them is being picked. */
@Stable
private class TripPlan(
    destination: Currency?,
    dates: TripDates,
) {
    var destination by mutableStateOf(destination)
    var dates by mutableStateOf(dates)
    var picking by mutableStateOf<TripPick?>(null)

    fun draft(
        name: String,
        currency: Currency,
        budget: BigDecimal?,
    ) = TripDraft(name, currency, budget, destination?.takeIf { it != currency }, dates.start, dates.end)
}

// Kept across a rotation: the destination's code and the two days.
@Composable
private fun rememberTripPlan(initial: Trip?): TripPlan =
    rememberSaveable(
        saver =
            listSaver(
                save = { listOf(it.destination?.iso4217Alpha(), it.dates.start?.toEpochDay(), it.dates.end?.toEpochDay()) },
                restore = {
                    TripPlan(
                        (it[0] as String?)?.let(Currency::fromString),
                        TripDates((it[1] as Long?)?.let(LocalDate::ofEpochDay), (it[2] as Long?)?.let(LocalDate::ofEpochDay)),
                    )
                },
            ),
    ) { TripPlan(initial?.destination, TripDates(initial?.start, initial?.end)) }

// Where to and when, each optional, each with a way to clear it.
@Composable
private fun TripPlanRows(plan: TripPlan) {
    val context = LocalContext.current
    val optional = stringResource(R.string.trip_optional)
    TripPlanRow(
        label = stringResource(R.string.trip_destination),
        value = plan.destination?.let { "${it.iso4217Alpha()} · ${it.fullName(context)}" } ?: optional,
        onClick = { plan.picking = TripPick.Destination },
        onClear = plan.destination?.let { { plan.destination = null } },
    )
    TripPlanRow(
        label = stringResource(R.string.trip_dates),
        value = plan.dates.label(LocalConfiguration.current.locales[0]) ?: optional,
        onClick = { plan.picking = TripPick.Dates },
        onClear = plan.dates.start?.let { { plan.dates = TripDates() } },
    )
}

// The picker for whichever of them is being picked: any currency but [home], or the days.
@Composable
private fun TripPlanPickers(
    plan: TripPlan,
    home: Currency,
) {
    val close = { plan.picking = null }
    when (plan.picking) {
        TripPick.Destination -> {
            CurrencyPickerSheet(
                currentRate = null,
                currentSum = BigDecimal.ONE,
                disabledCurrency = home,
                selectedCurrency = plan.destination,
                onRateClicked = { rate ->
                    plan.destination = rate.currency
                    close()
                },
                onDismiss = close,
            )
        }

        TripPick.Dates -> {
            TripDatesSheet(
                initial = plan.dates,
                onPick = {
                    plan.dates = it
                    close()
                },
                onDismiss = close,
            )
        }

        null -> {}
    }
}

// Which of the trip's details is being picked.
private enum class TripPick { Destination, Dates }

/** A trip's first and last days, either unset. */
data class TripDates(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
) {
    // "14 Nov – 21 Nov 2026", or the first day alone; null when unset.
    fun label(locale: Locale): String? {
        val first = start ?: return null
        val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        return end?.let { "${format.format(first)} – ${format.format(it)}" } ?: format.format(first)
    }
}

// One of the trip's optional details: its name, the value or "Optional", and a way to clear it.
@Composable
private fun TripPlanRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .hapticClickable(onClick = onClick)
                .padding(vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        onClear?.let { clear ->
            IconButton(onClick = rememberHapticOnClick(clear)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.trip_clear, label))
            }
        }
    }
}

/** The trip's days, as a range (the last day may stay unset). */
@Composable
private fun TripDatesSheet(
    initial: TripDates,
    onPick: (TripDates) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = initial.start?.toUtcMillis(),
            initialSelectedEndDateMillis = initial.end?.toUtcMillis(),
            initialDisplayMode = DisplayMode.Picker,
        )
    LedgerPromptSheet(
        title = stringResource(R.string.trip_dates),
        confirmLabel = stringResource(R.string.done),
        onConfirm = { onPick(TripDates(state.selectedStartDateMillis?.toUtcDate(), state.selectedEndDateMillis?.toUtcDate())) },
        onDismiss = onDismiss,
        confirmEnabled = state.selectedStartDateMillis != null,
        scrollableBody = false,
    ) {
        DateRangePicker(
            state = state,
            title = null,
            headline = null,
            showModeToggle = false,
            modifier = Modifier.height(DATE_RANGE_HEIGHT),
        )
    }
}

// Tall enough for a few months of the range picker inside the sheet.
private val DATE_RANGE_HEIGHT = 460.dp

// The date pickers count days in UTC milliseconds.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
