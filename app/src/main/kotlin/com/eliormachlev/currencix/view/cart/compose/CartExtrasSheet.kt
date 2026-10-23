package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartExtras
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Person
import com.eliormachlev.currencix.util.isCurrencySymbolOnRight
import com.eliormachlev.currencix.util.newId
import com.eliormachlev.currencix.util.parseDecimal
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.ConnectedToggleGroup
import com.eliormachlev.currencix.view.compose.NameInputTransformation
import com.eliormachlev.currencix.view.compose.PercentTextField
import com.eliormachlev.currencix.view.compose.SupportingText
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.theme.Spacing
import java.math.BigDecimal

private val CONTENT_PADDING = Spacing.Large
private val GAP = Spacing.Large
private val TIP_FIELD_GAP = Spacing.Small
private val SPLIT_COUNT_WIDTH = 40.dp

// One-tap tip / tax rates; any other goes in the field.
private val QUICK_TIPS = listOf("10", "15", "20")

// Tip / tax as a percentage, 0 < tip ≤ 100.
private val MAX_TIP = BigDecimal(100)

/**
 * Edits a cart's [CartExtras]: tip / tax percentage, how the total splits
 * (evenly between a number of people, or by person: each pays for their
 * own items and a part of the shared ones), and a budget in the destination
 * currency. Empty fields mean "none"; Done saves them all at once.
 */
@Composable
fun CartExtrasSheet(
    initial: CartExtras,
    destination: Currency?,
    onDone: (CartExtras) -> Unit,
    onDismiss: () -> Unit,
) {
    val tipState = rememberTextFieldState(initial.tipPercent?.toPlainString().orEmpty())
    var split by rememberSaveable { mutableIntStateOf(initial.splitWays) }
    val budgetState = rememberTextFieldState(initial.budget?.toPlainString().orEmpty())
    val people = remember { initial.people.toMutableStateList() }
    val tip = tipState.text.toString()
    val budget = budgetState.text.toString()
    val tipValue = remember(tip) { parseDecimal(tip)?.takeIf { it.signum() > 0 && it <= MAX_TIP } }
    val budgetValue = remember(budget) { parseDecimal(budget)?.takeIf { it.signum() > 0 } }
    val context = LocalContext.current
    val symbolAfter = remember(context) { isCurrencySymbolOnRight(context) }
    LedgerBottomSheet(title = stringResource(R.string.cart_menu_extras), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(CONTENT_PADDING), verticalArrangement = Arrangement.spacedBy(GAP)) {
            TipField(tipState, isError = tip.isNotBlank() && tipValue == null)
            // Split by person replaces the even split.
            if (people.isEmpty()) SplitStepper(split, onChange = { split = it })
            PeopleEditor(people)
            OutlinedTextField(
                state = budgetState,
                label = { Text(stringResource(R.string.cart_budget_label)) },
                // The currency on the side the language puts it, as on every amount.
                prefix = destination?.takeUnless { symbolAfter }?.let { { Text(it.symbolOrIso() + " ") } },
                suffix = destination?.takeIf { symbolAfter }?.let { { Text(" " + it.symbolOrIso()) } },
                isError = budget.isNotBlank() && budgetValue == null,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(
                onClick = rememberHapticOnClick { onDone(CartExtras(tipValue, split, budgetValue, people.toList())) },
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.done)) }
        }
    }
}

@Composable
private fun TipField(
    state: TextFieldState,
    isError: Boolean,
) {
    val value = state.text.toString()
    Column(verticalArrangement = Arrangement.spacedBy(TIP_FIELD_GAP)) {
        PercentTextField(state = state, label = stringResource(R.string.cart_tip_label), isError = isError)
        // A quick rate fills the field; tapping it again clears it.
        ConnectedToggleGroup(
            options = QUICK_TIPS,
            selected = value.takeIf { it in QUICK_TIPS },
            label = { "$it%" },
            onToggle = { percent, checked -> state.setTextAndPlaceCursorAtEnd(if (checked) percent else "") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// "Split between   (−)  3  (+)"
@Composable
private fun SplitStepper(
    ways: Int,
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.cart_split_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = rememberHapticOnClick { onChange(ways - 1) }, enabled = ways > 1) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.cart_split_decrease))
        }
        Text(
            ways.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = SPLIT_COUNT_WIDTH),
        )
        FilledTonalIconButton(onClick = rememberHapticOnClick { onChange(ways + 1) }, enabled = ways < CartExtras.MAX_SPLIT) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.cart_split_increase))
        }
    }
}

// "Split by person": a chip per person (× removes them) and a field to add
// one. Items are then assigned from each row's long-press menu.
@Composable
private fun PeopleEditor(people: SnapshotStateList<Person>) {
    val field = rememberTextFieldState()
    val add = {
        val name = field.text.toString().trim()
        if (name.isNotEmpty() && people.size < CartExtras.MAX_PEOPLE) {
            people += Person(newId(), name)
            field.clearText()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(TIP_FIELD_GAP)) {
        Text(stringResource(R.string.cart_split_by_person), style = MaterialTheme.typography.bodyLarge)
        if (people.isEmpty()) SupportingText(stringResource(R.string.cart_split_by_person_hint))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TIP_FIELD_GAP)) {
            people.forEach { person ->
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text(person.name) },
                    trailingIcon = {
                        IconButton(
                            onClick = rememberHapticOnClick { people.remove(person) },
                            modifier = Modifier.size(PERSON_REMOVE_SIZE),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.cart_person_remove, person.name),
                            )
                        }
                    },
                )
            }
        }
        if (people.size < CartExtras.MAX_PEOPLE) {
            OutlinedTextField(
                state = field,
                label = { Text(stringResource(R.string.cart_person_add)) },
                inputTransformation = NameInputTransformation,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onKeyboardAction = { add() },
                trailingIcon = {
                    IconButton(onClick = rememberHapticOnClick(add), enabled = field.text.isNotBlank()) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.cart_person_add))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val PERSON_REMOVE_SIZE = 24.dp
