package com.eliormachlev.currencix.util

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import com.eliormachlev.currencix.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// Alpha used for the secondary explainer line beneath each choice-row title —
// keeps the description visually subordinate to the title. Shared by the
// cart's clear-action picker and the fee-side preference dialog.
const val CHOICE_DESC_ALPHA = 0.7f

/**
 * Apply the platform ripple background used by every clickable row inside
 * our dialog bodies and preference-picker sheets. Extracted so every row —
 * choice rows, add rows, trailing icon buttons — reads from the same source.
 */
fun View.applySelectableRowBackground() {
    val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
    background = ta.getDrawable(0)
    ta.recycle()
}

/**
 * Vertical LinearLayout with the standard dialog horizontal padding — the
 * common shape used for the "custom view" body of choice-picker and
 * fee-editor dialogs across cart and preferences.
 */
fun paddedDialogContainer(
    ctx: Context,
    topPadding: Int = 0,
): LinearLayout {
    val padH = ctx.resources.getDimensionPixelSize(R.dimen.margin3x)
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padH, topPadding, padH, 0)
    }
}

/**
 * Build a clickable "title + one-line explainer" row for a picker dialog.
 * Optional [leadingView] (e.g. a RadioButton) sits left of the text column
 * and optional [trailingView] (e.g. an edit icon with its own click listener)
 * sits right. The text column expands to fill remaining width when either
 * side view is present, otherwise takes the full row.
 */
fun choiceExplainerRow(
    ctx: Context,
    title: CharSequence,
    description: CharSequence,
    leadingView: View? = null,
    trailingView: View? = null,
    clickActionLabel: CharSequence? = null,
    onClick: () -> Unit,
): LinearLayout {
    val padV = ctx.resources.getDimensionPixelSize(R.dimen.margin2x)
    val row =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, padV, 0, padV)
            isClickable = true
            applySelectableRowBackground()
            setOnClickListener { onClick() }
            if (clickActionLabel != null) {
                ViewCompat.replaceAccessibilityAction(
                    this,
                    AccessibilityActionCompat.ACTION_CLICK,
                    clickActionLabel,
                    null,
                )
            }
        }
    if (leadingView != null) row.addView(leadingView)
    val textCol =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(ctx).apply {
                    text = title
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                },
            )
            addView(
                TextView(ctx).apply {
                    text = description
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    alpha = CHOICE_DESC_ALPHA
                },
            )
        }
    val hasSideView = leadingView != null || trailingView != null
    val textLp =
        if (hasSideView) {
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        } else {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    row.addView(textCol, textLp)
    if (trailingView != null) row.addView(trailingView)
    return row
}

/**
 * One entry in a [showChoiceExplainerDialog] — a title plus a one-line
 * explainer of what picking it does.
 */
data class ChoiceOption(
    val title: CharSequence,
    val description: CharSequence,
)

/**
 * Two-line summary suitable for a Preference row that mirrors a
 * [showChoiceExplainerDialog] entry — keeps the row and the picker option
 * visually and textually aligned.
 */
fun ChoiceOption.asPreferenceSummary(): CharSequence = "$title\n$description"

/**
 * Radio-list picker dialog where every option carries a short explainer under
 * its title. Shared by the fee-side preference and the keyboard-style
 * preference so both single-choice pickers with descriptive options render
 * and behave identically.
 */
fun showChoiceExplainerDialog(
    ctx: Context,
    @StringRes titleRes: Int,
    options: List<ChoiceOption>,
    selectedIndex: Int,
    onPicked: (Int) -> Unit,
) {
    val container = paddedDialogContainer(ctx)
    val radios = mutableListOf<RadioButton>()
    val dialogHolder = arrayOfNulls<AlertDialog>(1)

    options.forEachIndexed { index, option ->
        val radio =
            RadioButton(ctx).apply {
                isChecked = index == selectedIndex
                isClickable = false
            }
        radios += radio
        container.addView(
            choiceExplainerRow(
                ctx = ctx,
                title = option.title,
                description = option.description,
                leadingView = radio,
            ) {
                radios.forEachIndexed { i, r -> r.isChecked = i == index }
                onPicked(index)
                dialogHolder[0]?.dismiss()
            },
        )
    }

    dialogHolder[0] =
        MaterialAlertDialogBuilder(ctx)
            .setTitle(titleRes)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
}
