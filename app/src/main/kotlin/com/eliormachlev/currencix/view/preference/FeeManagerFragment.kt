package com.eliormachlev.currencix.view.preference

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeSide
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.CHOICE_DESC_ALPHA
import com.eliormachlev.currencix.util.ChoiceOption
import com.eliormachlev.currencix.util.applySelectableRowBackground
import com.eliormachlev.currencix.util.choiceExplainerRow
import com.eliormachlev.currencix.util.dpToPx
import com.eliormachlev.currencix.util.paddedDialogContainer
import com.eliormachlev.currencix.util.resolveThemeColor
import com.eliormachlev.currencix.util.showChoiceExplainerDialog
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.main.spinner.SearchableSpinnerDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal
import java.util.UUID

// Preference keys for UI-only rows. The leading __ marks them as ignored by
// the settings back-up/restore pipeline.
private const val PREF_KEY_GLOBAL_EXCHANGE = "__global_exchange"
private const val PREF_KEY_GLOBAL_BANK = "__global_bank"

// Sign-toggle button geometry (dp) and text size (sp).
private const val SIGN_TOGGLE_BUTTON_HEIGHT_DP = 56f
private const val SIGN_TOGGLE_BUTTON_WIDTH_DP = 80f
private const val SIGN_TOGGLE_TEXT_SIZE_SP = 20f

// Flag glyph height for inline flag spans (sp so it scales with body text).
private const val FLAG_INLINE_HEIGHT_SP = 14f

// Trailing/leading icon buttons in picker rows. 48dp total with 12dp padding
// yields a 24dp visible glyph — matches the Material3 IconButton dimensions
// used by the saved-carts list so all in-app row-affordance icons look alike.
private const val ROW_ICON_BUTTON_SIZE_DP = 48f
private const val ROW_ICON_PADDING_DP = 12f

// Minimum tap-target for the leading radio in the fee picker. Matches the
// Material accessibility guidance (WCAG 2.5.5) so the radio can be tapped
// independently of the row's edit affordance without missing.
private const val RADIO_MIN_TOUCH_DP = 48f

// Arrows used in the "specific pair" summary.
private const val ARROW_ONE_WAY = "\u2192"
private const val ARROW_BOTH_WAYS = "\u2194"

// Summary parts separator, e.g. "USD → EUR · Inactive".
private const val SUMMARY_SEPARATOR = "  ·  "

class FeeManagerFragment : PreferenceFragmentCompat() {
    private lateinit var db: Database

    private lateinit var globalExchangePref: Preference
    private lateinit var globalBankPref: Preference
    private lateinit var categoryPair: PreferenceCategory

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        db = Database(requireContext())
        val ctx = preferenceManager.context
        val screen: PreferenceScreen = preferenceManager.createPreferenceScreen(ctx)

        globalExchangePref =
            buildGlobalSelectorPreference(ctx, PREF_KEY_GLOBAL_EXCHANGE, R.string.fee_section_global_exchange)
        globalBankPref =
            buildGlobalSelectorPreference(ctx, PREF_KEY_GLOBAL_BANK, R.string.fee_section_global_bank)
        // Wrap each selector in its own category so the section header is
        // visible on the top-level screen; without it users can't tell the
        // exchange row from the bank/card row without opening the dialog.
        addCategory(screen, ctx, R.string.fee_section_global_exchange).addPreference(globalExchangePref)
        addCategory(screen, ctx, R.string.fee_section_global_bank).addPreference(globalBankPref)

        categoryPair = addCategory(screen, ctx, R.string.fee_section_specific_pair)

        preferenceScreen = screen

        db.getFees().observe(this) { fees -> renderFees(fees) }
    }

    private fun addCategory(
        screen: PreferenceScreen,
        ctx: Context,
        titleRes: Int,
    ): PreferenceCategory =
        PreferenceCategory(ctx)
            .apply {
                title = getString(titleRes)
                isIconSpaceReserved = false
            }.also(screen::addPreference)

    // Extra clearance below the EditText so its text-selection handle doesn't
    // drop onto the sign toggle.
    private fun addSignToggleWithTopMargin(
        container: LinearLayout,
        signToggle: View,
    ) {
        container.addView(
            signToggle,
            LinearLayout
                .LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = resources.getDimensionPixelSize(R.dimen.margin4x)
                },
        )
    }

    private fun feeSideOption(side: FeeSide): ChoiceOption =
        when (side) {
            FeeSide.CONVERTED ->
                ChoiceOption(
                    getString(R.string.fee_side_converted),
                    getString(R.string.fee_side_summary_converted),
                )
            else ->
                ChoiceOption(
                    getString(R.string.fee_side_original),
                    getString(R.string.fee_side_summary_original),
                )
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.fee_manager_title)
    }

    private fun buildGlobalSelectorPreference(
        ctx: Context,
        key: String,
        titleRes: Int,
    ): Preference =
        Preference(ctx).apply {
            this.key = key
            title = getString(titleRes)
            isIconSpaceReserved = false
        }

    private fun renderFees(fees: List<Fee>) {
        renderGlobalCategory(
            pref = globalExchangePref,
            entries = fees.filterIsInstance<Fee.GlobalExchange>(),
            sectionTitleRes = R.string.fee_section_global_exchange,
            getActiveId = db::getActiveExchangeIdBlocking,
            setActiveId = db::setActiveExchangeId,
            create = { d -> Fee.GlobalExchange(UUID.randomUUID().toString(), d.name, d.percent, d.isMarkup, d.isActive, d.feeSide) },
            update = { existing, d ->
                existing.copy(name = d.name, percent = d.percent, isMarkup = d.isMarkup, isActive = d.isActive, feeSide = d.feeSide)
            },
        )
        renderGlobalCategory(
            pref = globalBankPref,
            entries = fees.filterIsInstance<Fee.GlobalBank>(),
            sectionTitleRes = R.string.fee_section_global_bank,
            getActiveId = db::getActiveBankIdBlocking,
            setActiveId = db::setActiveBankId,
            create = { d -> Fee.GlobalBank(UUID.randomUUID().toString(), d.name, d.percent, d.isMarkup, d.isActive, d.feeSide) },
            update = { existing, d ->
                existing.copy(name = d.name, percent = d.percent, isMarkup = d.isMarkup, isActive = d.isActive, feeSide = d.feeSide)
            },
        )
        populate(
            category = categoryPair,
            entries = fees.filterIsInstance<Fee.SpecificPair>(),
            summaryFor = { fee ->
                buildPairSummary(fee.from, fee.to, fee.bothWays, requireContext())
            },
            onAdd = {
                showSpecificPairDialog(existing = null) { db.addFee(it) }
            },
            onEdit = { fee ->
                showSpecificPairDialog(
                    existing = fee,
                    onDelete = { db.deleteFee(fee.id) },
                ) { updated ->
                    db.updateFee(updated.copy(id = fee.id))
                }
            },
        )
    }

    /**
     * Wire one global-fee category (exchange or bank/card) into its selector
     * row. Callers supply the category-typed [create]/[update] factories so
     * the sealed-class copy stays type-safe without a `when` on [Fee].
     */
    private fun <T : Fee> renderGlobalCategory(
        pref: Preference,
        entries: List<T>,
        sectionTitleRes: Int,
        getActiveId: () -> String?,
        setActiveId: (String) -> Unit,
        create: (FeeDraft) -> T,
        update: (existing: T, FeeDraft) -> T,
    ) {
        renderGlobalSelector(
            pref = pref,
            entries = entries,
            activeId = getActiveId(),
            sectionTitleRes = sectionTitleRes,
            onPicked = setActiveId,
            onAdd = {
                showGlobalFeeDialog(existing = null) { draft ->
                    val fee = create(draft)
                    db.addFee(fee)
                    if (getActiveId() == null) setActiveId(fee.id)
                }
            },
            onEdit = { fee ->
                showGlobalFeeDialog(
                    existing = fee,
                    onDelete = { db.deleteFee(fee.id) },
                ) { draft -> db.updateFee(update(fee, draft)) }
            },
        )
    }

    private fun <T : Fee> renderGlobalSelector(
        pref: Preference,
        entries: List<T>,
        activeId: String?,
        sectionTitleRes: Int,
        onPicked: (String) -> Unit,
        onAdd: () -> Unit,
        onEdit: (T) -> Unit,
    ) {
        val sectionTitle = getString(sectionTitleRes)
        val active = entries.filter { it.isActive }
        val effective = active.firstOrNull { it.id == activeId } ?: active.firstOrNull()

        if (effective == null) {
            // Section title already appears as the category header above the
            // row; use the empty-state message as the row title to avoid
            // repeating it.
            pref.title = getString(R.string.fee_empty)
            pref.summary = null
        } else {
            pref.title = displayNameOf(effective)
            pref.summary = formatFeeDescription(effective)
        }
        pref.setOnPreferenceClickListener {
            showGlobalPickerDialog(
                entries = entries,
                effectiveId = effective?.id,
                title = sectionTitle,
                onPicked = { picked ->
                    onPicked(picked)
                    // Re-render immediately; underlying LiveData will also fire but this
                    // avoids a visible lag while the picker dialog dismisses.
                    renderGlobalSelector(pref, entries, picked, sectionTitleRes, onPicked, onAdd, onEdit)
                },
                onAdd = onAdd,
                onEdit = onEdit,
            )
            true
        }
    }

    private fun <T : Fee> showGlobalPickerDialog(
        entries: List<T>,
        effectiveId: String?,
        title: String,
        onPicked: (String) -> Unit,
        onAdd: () -> Unit,
        onEdit: (T) -> Unit,
    ) {
        val ctx = requireContext()
        val container = paddedDialogContainer(ctx)
        val dialogHolder = arrayOfNulls<AlertDialog>(1)
        val radios = mutableListOf<RadioButton>()
        // Opaque muted colour for the inactive-state cue. Paired with the
        // existing "Inactive" text marker so state is not conveyed by colour
        // alone (WCAG 1.4.1); using a theme-resolved colour keeps the AA
        // contrast target on light/dark/OLED.
        val mutedColor = ctx.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val editActionLabel = getString(R.string.fee_edit_percent)

        entries.forEachIndexed { index, fee ->
            val radio =
                buildPickerRadio(ctx, checked = fee.id == effectiveId) {
                    radios.forEachIndexed { i, r -> r.isChecked = i == index }
                    onPicked(fee.id)
                    dialogHolder[0]?.dismiss()
                }
            radios += radio
            container.addView(
                choiceExplainerRow(
                    ctx = ctx,
                    title = displayNameOf(fee),
                    description = formatFeeDescription(fee),
                    leadingView = radio,
                    textColor = if (fee.isActive) null else mutedColor,
                    clickActionLabel = editActionLabel,
                ) {
                    dialogHolder[0]?.dismiss()
                    onEdit(fee)
                },
            )
        }

        container.addView(
            buildAddRow(ctx) {
                dialogHolder[0]?.dismiss()
                onAdd()
            },
        )

        dialogHolder[0] =
            MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun buildPickerRadio(
        ctx: Context,
        checked: Boolean,
        onClick: () -> Unit,
    ): RadioButton {
        val minTouchPx = RADIO_MIN_TOUCH_DP.dpToPx().toInt()
        return RadioButton(ctx).apply {
            isChecked = checked
            isClickable = true
            isFocusable = true
            minWidth = minTouchPx
            minHeight = minTouchPx
            setOnClickListener { onClick() }
        }
    }

    private fun buildAddRow(
        ctx: Context,
        onClick: () -> Unit,
    ): View {
        val padV = ctx.resources.getDimensionPixelSize(R.dimen.margin2x)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, padV, 0, padV)
            isClickable = true
            applySelectableRowBackground()
            setOnClickListener { onClick() }
            addView(buildRowIcon(ctx, R.drawable.ic_add, null, onClick = null))
            addView(
                TextView(ctx).apply {
                    text = getString(R.string.fee_add)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
    }

    private fun buildRowIcon(
        ctx: Context,
        iconRes: Int,
        contentDesc: String?,
        onClick: (() -> Unit)?,
    ): ImageView {
        val sizePx = ROW_ICON_BUTTON_SIZE_DP.dpToPx().toInt()
        val padPx = ROW_ICON_PADDING_DP.dpToPx().toInt()
        return ImageView(ctx).apply {
            setImageResource(iconRes)
            setPadding(padPx, padPx, padPx, padPx)
            contentDescription = contentDesc
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            if (onClick != null) {
                applySelectableRowBackground()
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }
    }

    private fun formatFeeDescription(fee: Fee): CharSequence {
        val percent = formatPercent(fee.percent, fee.isMarkup)
        if (fee.isActive) return percent
        return SpannableStringBuilder(percent)
            .append(SUMMARY_SEPARATOR)
            .append(getString(R.string.fee_inactive_marker))
    }

    private fun displayNameOf(fee: Fee): String = fee.name.ifBlank { getString(R.string.fee_untitled) }

    private fun <T : Fee> populate(
        category: PreferenceCategory,
        entries: List<T>,
        summaryFor: (T) -> CharSequence?,
        onAdd: () -> Unit,
        onEdit: (T) -> Unit,
    ) {
        category.removeAll()
        val ctx = category.context
        if (entries.isEmpty()) {
            category.addPreference(
                Preference(ctx).apply {
                    title = getString(R.string.fee_empty)
                    isSelectable = false
                    isIconSpaceReserved = false
                },
            )
        } else {
            entries.forEach { fee ->
                category.addPreference(
                    Preference(ctx).apply {
                        title = formatFeeTitle(fee)
                        summary = buildFeeSummary(summaryFor(fee), fee.isActive)
                        isIconSpaceReserved = false
                        setOnPreferenceClickListener {
                            onEdit(fee)
                            true
                        }
                    },
                )
            }
        }
        category.addPreference(
            Preference(ctx).apply {
                title = getString(R.string.fee_add)
                setIcon(R.drawable.ic_add)
                setOnPreferenceClickListener {
                    onAdd()
                    true
                }
            },
        )
    }

    private fun formatFeeTitle(fee: Fee): CharSequence {
        val percent = formatPercent(fee.percent, fee.isMarkup)
        return if (fee.name.isBlank()) percent else "${fee.name}  ·  $percent"
    }

    private fun buildFeeSummary(
        base: CharSequence?,
        isActive: Boolean,
    ): CharSequence? {
        val inactive = if (!isActive) getString(R.string.fee_inactive_marker) else null
        return when {
            base == null && inactive == null -> null
            base == null -> inactive
            inactive == null -> base
            else -> SpannableStringBuilder(base).append(SUMMARY_SEPARATOR).append(inactive)
        }
    }

    private fun formatPercent(
        percent: BigDecimal,
        isMarkup: Boolean,
    ): String {
        val sign =
            if (isMarkup) {
                getString(R.string.fee_edit_sign_positive)
            } else {
                getString(R.string.fee_edit_sign_negative)
            }
        return "$sign ${percent.toHumanReadableNumber(requireContext(), suffix = "%")}"
    }

    private fun buildNameInput(
        ctx: Context,
        initial: String?,
    ): EditText =
        EditText(ctx).apply {
            hint = getString(R.string.fee_edit_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            if (initial != null) setText(initial)
        }

    private fun buildPercentInput(
        ctx: Context,
        initial: BigDecimal?,
    ): EditText =
        EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (initial != null) setText(initial.toPlainString())
        }

    private fun buildActiveCheckbox(
        ctx: Context,
        initial: Boolean,
    ): CheckBox =
        CheckBox(ctx).apply {
            text = getString(R.string.fee_edit_active)
            isChecked = initial
        }

    private fun sectionLabel(
        ctx: Context,
        @StringRes res: Int,
    ): TextView = TextView(ctx).apply { text = getString(res) }

    /**
     * Snapshot of the fields shared between the global-fee and specific-pair
     * edit dialogs. Threading a struct instead of five positional args keeps
     * the create/update lambdas in [renderGlobalCategory] readable and avoids
     * accidentally swapping arg order at call sites.
     */
    private data class FeeDraft(
        val name: String,
        val percent: BigDecimal,
        val isMarkup: Boolean,
        val isActive: Boolean,
        val feeSide: FeeSide,
    )

    /**
     * Bundle of the inputs that appear in both fee-editor dialogs. Each dialog
     * builds one via [buildSharedFeeInputs], places the views in whatever
     * order it wants, and asks [toDraft] for the confirm payload.
     */
    private data class SharedFeeInputs(
        val nameInput: EditText,
        val percentInput: EditText,
        val signToggle: SignToggle,
        val feeSideChooser: FeeSideChooser,
        val activeCheckbox: CheckBox,
    ) {
        fun toDraft(): FeeDraft =
            FeeDraft(
                name = nameInput.text.toString().trim(),
                percent =
                    percentInput.text
                        .toString()
                        .toBigDecimalOrNull()
                        ?.abs() ?: BigDecimal.ZERO,
                isMarkup = signToggle.isMarkup(),
                isActive = activeCheckbox.isChecked,
                feeSide = feeSideChooser.current(),
            )
    }

    private fun buildSharedFeeInputs(
        ctx: Context,
        existing: Fee?,
    ): SharedFeeInputs =
        SharedFeeInputs(
            nameInput = buildNameInput(ctx, existing?.name),
            percentInput = buildPercentInput(ctx, existing?.percent),
            signToggle = buildSignToggle(ctx, existing?.isMarkup),
            feeSideChooser = buildFeeSideChooser(ctx, existing?.feeSide ?: FeeSide.ORIGINAL),
            activeCheckbox = buildActiveCheckbox(ctx, existing?.isActive != false),
        )

    /**
     * Append the meta section — sign toggle, fee-side chooser, active switch —
     * to a dialog's container. Shared so both fee-editor dialogs render the
     * trailing block identically.
     */
    private fun addFeeMetaViews(
        container: LinearLayout,
        inputs: SharedFeeInputs,
    ) {
        addSignToggleWithTopMargin(container, inputs.signToggle.view)
        container.addView(inputs.feeSideChooser.view)
        container.addView(inputs.activeCheckbox)
    }

    private fun buildEditorContainer(ctx: Context): LinearLayout =
        paddedDialogContainer(ctx, topPadding = resources.getDimensionPixelSize(R.dimen.margin2x))

    private fun showGlobalFeeDialog(
        existing: Fee?,
        onDelete: (() -> Unit)? = null,
        onConfirm: (FeeDraft) -> Unit,
    ) {
        val ctx = requireContext()
        val container = buildEditorContainer(ctx)
        val inputs = buildSharedFeeInputs(ctx, existing)

        container.addView(sectionLabel(ctx, R.string.fee_edit_name))
        container.addView(inputs.nameInput)
        container.addView(sectionLabel(ctx, R.string.fee_edit_percent))
        container.addView(inputs.percentInput)
        addFeeMetaViews(container, inputs)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.fee_edit_percent)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm(inputs.toDraft()) }
            .setNegativeButton(android.R.string.cancel, null)
            .withDeleteButton(onDelete)
            .show()
    }

    private fun showSpecificPairDialog(
        existing: Fee.SpecificPair?,
        onDelete: (() -> Unit)? = null,
        onConfirm: (Fee.SpecificPair) -> Unit,
    ) {
        val ctx = requireContext()
        val container = buildEditorContainer(ctx)
        val inputs = buildSharedFeeInputs(ctx, existing)

        var pickedFrom: String? = existing?.from
        var pickedTo: String? = existing?.to
        val placeholder = getString(R.string.fee_pair_pick_currency)

        val fromButton =
            buildCurrencyPickerButton(
                ctx,
                pickedFrom,
                placeholder,
                // Opposite side's current pick — greyed out in this picker so
                // the user can't save a same-currency specific-pair fee (e.g.
                // AUD → AUD is meaningless).
                disabled = { pickedTo },
            ) { pickedFrom = it }
        val toButton =
            buildCurrencyPickerButton(
                ctx,
                pickedTo,
                placeholder,
                disabled = { pickedFrom },
            ) { pickedTo = it }
        val bothWays =
            CheckBox(ctx).apply {
                text = getString(R.string.fee_pair_both_ways)
                isChecked = existing?.bothWays == true
            }

        container.addView(sectionLabel(ctx, R.string.fee_edit_name))
        container.addView(inputs.nameInput)
        container.addView(sectionLabel(ctx, R.string.fee_pair_from))
        container.addView(fromButton)
        container.addView(sectionLabel(ctx, R.string.fee_pair_to))
        container.addView(toButton)
        container.addView(bothWays)
        container.addView(sectionLabel(ctx, R.string.fee_edit_percent))
        container.addView(inputs.percentInput)
        addFeeMetaViews(container, inputs)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.fee_section_specific_pair)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val from = pickedFrom ?: return@setPositiveButton
                val to = pickedTo ?: return@setPositiveButton
                val draft = inputs.toDraft()
                onConfirm(
                    Fee.SpecificPair(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        name = draft.name,
                        percent = draft.percent,
                        isMarkup = draft.isMarkup,
                        from = from,
                        to = to,
                        bothWays = bothWays.isChecked,
                        isActive = draft.isActive,
                        feeSide = draft.feeSide,
                    ),
                )
            }.setNegativeButton(android.R.string.cancel, null)
            .withDeleteButton(onDelete)
            .show()
    }

    private fun MaterialAlertDialogBuilder.withDeleteButton(onDelete: (() -> Unit)?): MaterialAlertDialogBuilder {
        if (onDelete != null) {
            setNeutralButton(R.string.fee_delete) { _, _ -> onDelete() }
        }
        return this
    }

    /**
     * Inline fee-side picker for the edit dialogs. Shows the currently-picked
     * side (title + one-line explainer) as a tappable row; opening the picker
     * reuses the same choice-explainer dialog as the app-wide preference
     * pickers so users see identical wording for each option.
     */
    private data class FeeSideChooser(
        val view: View,
        val current: () -> FeeSide,
    )

    private fun buildFeeSideChooser(
        ctx: Context,
        initial: FeeSide,
    ): FeeSideChooser {
        var picked = initial
        val titleView =
            TextView(ctx).apply {
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            }
        val descView =
            TextView(ctx).apply {
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                alpha = CHOICE_DESC_ALPHA
            }

        fun refresh() {
            val opt = feeSideOption(picked)
            titleView.text = opt.title
            descView.text = opt.description
        }
        refresh()

        val padV = ctx.resources.getDimensionPixelSize(R.dimen.margin2x)
        val row =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, padV, 0, padV)
                isClickable = true
                applySelectableRowBackground()
                addView(titleView)
                addView(descView)
                setOnClickListener {
                    val sides = listOf(FeeSide.ORIGINAL, FeeSide.CONVERTED)
                    showChoiceExplainerDialog(
                        ctx = ctx,
                        titleRes = R.string.fee_side_label,
                        options = sides.map(::feeSideOption),
                        selectedIndex = sides.indexOf(picked).coerceAtLeast(0),
                    ) { index ->
                        picked = sides[index]
                        refresh()
                    }
                }
            }
        return FeeSideChooser(row, { picked })
    }

    /**
     * Two-button +/− toggle group. The wrapper hides the "is minus selected?"
     * predicate behind [SignToggle.isMarkup] so callers don't reach into the
     * button ids directly. Selection defaults to + unless [initialMarkup] is
     * explicitly false.
     */
    private data class SignToggle(
        val view: MaterialButtonToggleGroup,
        val plusId: Int,
        val minusId: Int,
    ) {
        fun isMarkup(): Boolean = view.checkedButtonId != minusId
    }

    private fun buildSignToggle(
        ctx: Context,
        initialMarkup: Boolean?,
    ): SignToggle {
        val group = MaterialButtonToggleGroup(ctx).apply { isSingleSelection = true }
        val btnHeight = SIGN_TOGGLE_BUTTON_HEIGHT_DP.dpToPx().toInt()
        val btnWidth = SIGN_TOGGLE_BUTTON_WIDTH_DP.dpToPx().toInt()

        fun makeButton(labelRes: Int): MaterialButton =
            outlinedMaterialButton(ctx).apply {
                id = View.generateViewId()
                text = getString(labelRes)
                textSize = SIGN_TOGGLE_TEXT_SIZE_SP
                insetTop = 0
                insetBottom = 0
            }
        val btnPlus = makeButton(R.string.fee_edit_sign_positive)
        val btnMinus = makeButton(R.string.fee_edit_sign_negative)
        group.addView(btnPlus, LinearLayout.LayoutParams(btnWidth, btnHeight))
        group.addView(btnMinus, LinearLayout.LayoutParams(btnWidth, btnHeight))
        group.check(if (initialMarkup == false) btnMinus.id else btnPlus.id)
        return SignToggle(group, btnPlus.id, btnMinus.id)
    }

    private fun outlinedMaterialButton(ctx: Context): MaterialButton =
        MaterialButton(
            ctx,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        )

    private fun buildCurrencyPickerButton(
        ctx: Context,
        initial: String?,
        placeholder: String,
        disabled: () -> String? = { null },
        onPicked: (String) -> Unit,
    ): MaterialButton =
        outlinedMaterialButton(ctx).apply {
            applyCurrencyIcon(this, initial, placeholder, ctx)
            setOnClickListener {
                // Resolve the disabled side lazily at click time so a "from"
                // picked *after* the button was built still greys out inside
                // the "to" picker (and vice-versa).
                openCurrencyPicker(disabled = disabled()?.let(Currency::fromString)) { iso ->
                    applyCurrencyIcon(this, iso, placeholder, ctx)
                    onPicked(iso)
                }
            }
        }

    private fun openCurrencyPicker(
        disabled: Currency? = null,
        onPicked: (String) -> Unit,
    ) {
        val dialog = SearchableSpinnerDialog(requireContext())
        dialog.setDisabledCurrency(disabled)
        dialog.onRateClicked = { rate, _ -> onPicked(rate.currency.iso4217Alpha()) }
        dialog.show(parentFragmentManager, null)
    }

    private fun applyCurrencyIcon(
        button: MaterialButton,
        iso: String?,
        placeholder: String,
        ctx: Context,
    ) {
        button.text = iso ?: placeholder
        button.icon = iso?.let { Currency.fromString(it)?.flag(ctx) }
        // Preserve the flag's own colors; the default MaterialButton icon tint
        // would flatten it to a solid rectangle.
        button.iconTint = null
    }

    private fun buildPairSummary(
        from: String,
        to: String,
        bothWays: Boolean,
        ctx: Context,
    ): CharSequence {
        val arrow = if (bothWays) ARROW_BOTH_WAYS else ARROW_ONE_WAY
        val sb = SpannableStringBuilder()
        appendFlag(sb, from, ctx)
        sb
            .append(' ')
            .append(from)
            .append("  ")
            .append(arrow)
            .append("  ")
        appendFlag(sb, to, ctx)
        sb.append(' ').append(to)
        return sb
    }

    private fun appendFlag(
        sb: SpannableStringBuilder,
        iso: String,
        ctx: Context,
    ) {
        val currency = Currency.fromString(iso) ?: return
        val drawable = currency.flag(ctx)
        val heightPx =
            TypedValue
                .applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    FLAG_INLINE_HEIGHT_SP,
                    ctx.resources.displayMetrics,
                ).toInt()
        val intrinsicW = drawable.intrinsicWidth.coerceAtLeast(1)
        val intrinsicH = drawable.intrinsicHeight.coerceAtLeast(1)
        val widthPx = (heightPx.toFloat() * intrinsicW / intrinsicH).toInt()
        drawable.setBounds(0, 0, widthPx, heightPx)
        val start = sb.length
        sb.append(' ')
        sb.setSpan(
            ImageSpan(drawable, ImageSpan.ALIGN_BASELINE),
            start,
            sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}
