package com.eliormachlev.currencix.view.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.hintText
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState

/**
 * A row that toggles a setting, read by TalkBack as one switch: its title,
 * "switch", on or off, and "disabled" when it is. Put it on the row that
 * takes the tap, and give the row's Switch `onCheckedChange = null`, so the
 * switch only shows the state: otherwise TalkBack stops twice, once on the
 * row (no state) and once on the switch (no name).
 */
fun Modifier.switchState(
    checked: Boolean,
    enabled: Boolean = true,
): Modifier =
    semantics {
        role = Role.Switch
        toggleableState = ToggleableState(checked)
        if (!enabled) disabled()
    }

/**
 * The name TalkBack reads for a text field whose label is drawn outside it
 * (a heading above the field, or none at all): read as the field's hint,
 * after whatever is typed, so an empty field isn't just "edit box". Hide the
 * drawn label from TalkBack where there is one, so it isn't read twice.
 */
fun Modifier.fieldName(name: String): Modifier = semantics { hintText = name }
