package com.eliormachlev.currencix.view.cart

import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.choiceExplainerRow
import com.eliormachlev.currencix.util.paddedDialogContainer
import com.eliormachlev.currencix.util.showWithHapticButtons

/**
 * Shared "title + one-line explainer per option" picker used by every
 * destructive/branch-y cart flow (Share, Clear). Mirrors the preference-screen
 * fee-side dialog so users get the same shape of guidance.
 */
data class CartChoice(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val onPick: () -> Unit,
)

fun AppCompatActivity.showCartChoiceExplainerDialog(
    @StringRes titleRes: Int,
    choices: List<CartChoice>,
) {
    val padV = resources.getDimensionPixelSize(R.dimen.margin2x)
    val container = paddedDialogContainer(this, topPadding = padV)
    val dialogHolder = arrayOfNulls<AlertDialog>(1)
    choices.forEach { choice ->
        container.addView(
            choiceExplainerRow(
                ctx = this,
                title = getString(choice.title),
                description = getString(choice.description),
            ) {
                choice.onPick()
                dialogHolder[0]?.dismiss()
            },
        )
    }
    dialogHolder[0] =
        AlertDialog
            .Builder(this)
            .setTitle(titleRes)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .showWithHapticButtons()
}
