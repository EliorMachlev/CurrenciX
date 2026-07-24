package de.salomax.currencies.util

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import de.salomax.currencies.R

// Alpha used for the secondary explainer line beneath each choice-row title —
// keeps the description visually subordinate to the title. Shared by the
// cart's clear-action picker and the fee-side preference dialog.
const val CHOICE_DESC_ALPHA = 0.7f

/**
 * Vertical LinearLayout with the standard dialog horizontal padding — the
 * common shape used for the "custom view" body of choice-picker and
 * fee-editor dialogs across cart and preferences.
 */
fun paddedDialogContainer(ctx: Context, topPadding: Int = 0): LinearLayout {
    val padH = ctx.resources.getDimensionPixelSize(R.dimen.margin3x)
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padH, topPadding, padH, 0)
    }
}

/**
 * Build a clickable "title + one-line explainer" row for a picker dialog.
 * Optional [leadingView] (e.g. a RadioButton) sits left of the text column;
 * text column expands to fill remaining width when present, otherwise takes
 * the full row.
 */
fun choiceExplainerRow(
    ctx: Context,
    title: CharSequence,
    description: CharSequence,
    leadingView: View? = null,
    onClick: () -> Unit,
): LinearLayout {
    val padV = ctx.resources.getDimensionPixelSize(R.dimen.margin2x)
    val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, padV, 0, padV)
        isClickable = true
        val ta = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        background = ta.getDrawable(0)
        ta.recycle()
        setOnClickListener { onClick() }
    }
    if (leadingView != null) row.addView(leadingView)
    val textCol = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(ctx).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        })
        addView(TextView(ctx).apply {
            text = description
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            alpha = CHOICE_DESC_ALPHA
        })
    }
    val textLp = if (leadingView != null)
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    else
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    row.addView(textCol, textLp)
    return row
}
