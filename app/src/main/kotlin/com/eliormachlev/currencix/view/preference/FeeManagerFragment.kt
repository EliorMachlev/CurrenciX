package com.eliormachlev.currencix.view.preference

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
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
import com.eliormachlev.currencix.util.choiceExplainerRow
import com.eliormachlev.currencix.util.dpToPx
import com.eliormachlev.currencix.util.paddedDialogContainer
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.main.spinner.SearchableSpinnerDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal
import java.util.UUID

// Preference keys for UI-only rows. The leading __ marks them as ignored by
// the settings back-up/restore pipeline.
private const val PREF_KEY_FEE_SIDE = "__fee_side"
private const val PREF_KEY_ACTIVE_EXCHANGE = "__active_exchange"
private const val PREF_KEY_ACTIVE_BANK = "__active_bank"

// Sign-toggle button geometry (dp) and text size (sp).
private const val SIGN_TOGGLE_BUTTON_HEIGHT_DP = 56f
private const val SIGN_TOGGLE_BUTTON_WIDTH_DP = 80f
private const val SIGN_TOGGLE_TEXT_SIZE_SP = 20f

// Flag glyph height for inline flag spans (sp so it scales with body text).
private const val FLAG_INLINE_HEIGHT_SP = 14f

// Arrows used in the "specific pair" summary.
private const val ARROW_ONE_WAY = "\u2192"
private const val ARROW_BOTH_WAYS = "\u2194"

// Summary parts separator, e.g. "USD → EUR · Inactive".
private const val SUMMARY_SEPARATOR = "  ·  "

// Show the active-picker row only when the user has a real choice to make
// (i.e. ≥2 entries in the category). With 0 or 1 entries the FeeCalculator
// fallback picks the sole entry (or nothing), so the picker adds no value.
private const val MIN_ENTRIES_FOR_PICKER = 2

class FeeManagerFragment : PreferenceFragmentCompat() {
    private lateinit var db: Database

    private lateinit var activeExchangePref: Preference
    private lateinit var activeBankPref: Preference
    private lateinit var categoryExchange: PreferenceCategory
    private lateinit var categoryBank: PreferenceCategory
    private lateinit var categoryPair: PreferenceCategory

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        db = Database(requireContext())
        val ctx = preferenceManager.context
        val screen: PreferenceScreen = preferenceManager.createPreferenceScreen(ctx)

        screen.addPreference(buildFeeSidePreference(ctx))

        activeExchangePref =
            buildActivePickerPreference(
                ctx = ctx,
                key = PREF_KEY_ACTIVE_EXCHANGE,
                titleRes = R.string.fee_active_exchange_title,
            )
        activeBankPref =
            buildActivePickerPreference(
                ctx = ctx,
                key = PREF_KEY_ACTIVE_BANK,
                titleRes = R.string.fee_active_bank_title,
            )
        screen.addPreference(activeExchangePref)
        screen.addPreference(activeBankPref)

        categoryExchange = addCategory(screen, ctx, R.string.fee_section_global_exchange)
        categoryBank = addCategory(screen, ctx, R.string.fee_section_global_bank)
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

    private fun feeSideLabels(side: FeeSide): Pair<String, String> =
        when (side) {
            FeeSide.CONVERTED ->
                getString(R.string.fee_side_converted) to
                    getString(R.string.fee_side_summary_converted)
            else ->
                getString(R.string.fee_side_original) to
                    getString(R.string.fee_side_summary_original)
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.fee_manager_title)
    }

    private fun buildFeeSidePreference(ctx: Context): Preference =
        Preference(ctx).apply {
            key = PREF_KEY_FEE_SIDE
            title = getString(R.string.fee_side_label)
            isIconSpaceReserved = false
            summary = formatFeeSideSummary(db.getFeeSideBlocking())
            setOnPreferenceClickListener {
                showFeeSideDialog { newSide ->
                    summary = formatFeeSideSummary(newSide)
                }
                true
            }
        }

    private fun formatFeeSideSummary(side: FeeSide): CharSequence {
        val (name, desc) = feeSideLabels(side)
        return "$name\n$desc"
    }

    private fun showFeeSideDialog(onPicked: (FeeSide) -> Unit) {
        val ctx = requireContext()
        val sides = arrayOf(FeeSide.ORIGINAL, FeeSide.CONVERTED)
        val current = db.getFeeSideBlocking()

        val container = paddedDialogContainer(ctx)
        val radios = mutableListOf<RadioButton>()
        val dialogHolder = arrayOfNulls<AlertDialog>(1)

        sides.forEachIndexed { index, side ->
            val radio =
                RadioButton(ctx).apply {
                    isChecked = side == current
                    isClickable = false
                }
            radios += radio
            val (titleText, descText) = feeSideLabels(side)
            container.addView(
                choiceExplainerRow(
                    ctx = ctx,
                    title = titleText,
                    description = descText,
                    leadingView = radio,
                ) {
                    radios.forEachIndexed { i, r -> r.isChecked = i == index }
                    db.setFeeSide(side)
                    onPicked(side)
                    dialogHolder[0]?.dismiss()
                },
            )
        }

        dialogHolder[0] =
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.fee_side_label)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun buildActivePickerPreference(
        ctx: Context,
        key: String,
        titleRes: Int,
    ): Preference =
        Preference(ctx).apply {
            this.key = key
            title = getString(titleRes)
            isIconSpaceReserved = false
            isVisible = false // populated when fees load
        }

    private fun renderFees(fees: List<Fee>) {
        val activeExchangeId = db.getActiveExchangeIdBlocking()
        val activeBankId = db.getActiveBankIdBlocking()

        populate(
            category = categoryExchange,
            entries = fees.filterIsInstance<Fee.GlobalExchange>(),
            summaryFor = { null },
            onAdd = {
                showGlobalFeeDialog(existing = null) { name, percent, isMarkup, isActive ->
                    db.addFee(
                        Fee.GlobalExchange(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            percent = percent,
                            isMarkup = isMarkup,
                            isActive = isActive,
                        ),
                    )
                }
            },
            onEdit = { fee ->
                showGlobalFeeDialog(
                    existing = fee,
                    onDelete = { db.deleteFee(fee.id) },
                ) { name, percent, isMarkup, isActive ->
                    db.updateFee(
                        fee.copy(
                            name = name,
                            percent = percent,
                            isMarkup = isMarkup,
                            isActive = isActive,
                        ),
                    )
                }
            },
        )
        populate(
            category = categoryBank,
            entries = fees.filterIsInstance<Fee.GlobalBank>(),
            summaryFor = { null },
            onAdd = {
                showGlobalFeeDialog(existing = null) { name, percent, isMarkup, isActive ->
                    db.addFee(
                        Fee.GlobalBank(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            percent = percent,
                            isMarkup = isMarkup,
                            isActive = isActive,
                        ),
                    )
                }
            },
            onEdit = { fee ->
                showGlobalFeeDialog(
                    existing = fee,
                    onDelete = { db.deleteFee(fee.id) },
                ) { name, percent, isMarkup, isActive ->
                    db.updateFee(
                        fee.copy(
                            name = name,
                            percent = percent,
                            isMarkup = isMarkup,
                            isActive = isActive,
                        ),
                    )
                }
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

        renderActivePicker(
            preference = activeExchangePref,
            entries = fees.filterIsInstance<Fee.GlobalExchange>(),
            activeId = activeExchangeId,
            onPicked = { id -> db.setActiveExchangeId(id) },
        )
        renderActivePicker(
            preference = activeBankPref,
            entries = fees.filterIsInstance<Fee.GlobalBank>(),
            activeId = activeBankId,
            onPicked = { id -> db.setActiveBankId(id) },
        )
    }

    private fun <T : Fee> renderActivePicker(
        preference: Preference,
        entries: List<T>,
        activeId: String?,
        onPicked: (String) -> Unit,
    ) {
        val activeEntries = entries.filter { it.isActive }
        if (activeEntries.size < MIN_ENTRIES_FOR_PICKER) {
            preference.isVisible = false
            return
        }
        preference.isVisible = true

        val explicit = activeId?.let { id -> activeEntries.firstOrNull { it.id == id } }
        val effective = explicit ?: activeEntries.first()
        val displayName = displayNameOf(effective)
        preference.summary =
            if (explicit == null) getString(R.string.fee_active_auto, displayName) else displayName

        preference.setOnPreferenceClickListener {
            showActivePickerDialog(activeEntries, effective.id, preference.title.toString()) { picked ->
                onPicked(picked)
                renderActivePicker(preference, entries, picked, onPicked)
            }
            true
        }
    }

    private fun <T : Fee> showActivePickerDialog(
        entries: List<T>,
        currentId: String,
        title: String,
        onPicked: (String) -> Unit,
    ) {
        val ctx = requireContext()
        val labels = entries.map { formatEntryForPicker(it) }.toTypedArray()
        val currentIndex = entries.indexOfFirst { it.id == currentId }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                onPicked(entries[which].id)
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatEntryForPicker(fee: Fee): String {
        val percent = formatPercent(fee.percent, fee.isMarkup)
        val name = displayNameOf(fee)
        return "$name  ·  $percent"
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

    private fun buildActiveCheckbox(
        ctx: Context,
        initial: Boolean,
    ): CheckBox =
        CheckBox(ctx).apply {
            text = getString(R.string.fee_edit_active)
            isChecked = initial
        }

    private fun showGlobalFeeDialog(
        existing: Fee?,
        onDelete: (() -> Unit)? = null,
        onConfirm: (name: String, percent: BigDecimal, isMarkup: Boolean, isActive: Boolean) -> Unit,
    ) {
        val ctx = requireContext()
        val padV = resources.getDimensionPixelSize(R.dimen.margin2x)
        val container = paddedDialogContainer(ctx, topPadding = padV)

        val nameLabel = TextView(ctx).apply { text = getString(R.string.fee_edit_name) }
        val nameInput = buildNameInput(ctx, existing?.name)
        val percentLabel = TextView(ctx).apply { text = getString(R.string.fee_edit_percent) }
        val percentInput =
            EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                if (existing != null) setText(existing.percent.toPlainString())
            }
        val signGroup = buildSignToggle(ctx, existing?.isMarkup)
        val activeCheckbox = buildActiveCheckbox(ctx, existing?.isActive != false)

        container.addView(nameLabel)
        container.addView(nameInput)
        container.addView(percentLabel)
        container.addView(percentInput)
        addSignToggleWithTopMargin(container, signGroup.view)
        container.addView(activeCheckbox)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.fee_edit_percent)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val percent =
                    percentInput.text.toString().toBigDecimalOrNull()
                        ?: BigDecimal.ZERO
                onConfirm(
                    nameInput.text.toString().trim(),
                    percent.abs(),
                    signGroup.isMarkup(),
                    activeCheckbox.isChecked,
                )
            }.setNegativeButton(android.R.string.cancel, null)
            .withDeleteButton(onDelete)
            .show()
    }

    private fun showSpecificPairDialog(
        existing: Fee.SpecificPair?,
        onDelete: (() -> Unit)? = null,
        onConfirm: (Fee.SpecificPair) -> Unit,
    ) {
        val ctx = requireContext()
        val padV = resources.getDimensionPixelSize(R.dimen.margin2x)
        val container = paddedDialogContainer(ctx, topPadding = padV)

        var pickedFrom: String? = existing?.from
        var pickedTo: String? = existing?.to
        val placeholder = getString(R.string.fee_pair_pick_currency)

        val nameLabel = TextView(ctx).apply { text = getString(R.string.fee_edit_name) }
        val nameInput = buildNameInput(ctx, existing?.name)
        val fromLabel = TextView(ctx).apply { text = getString(R.string.fee_pair_from) }
        val fromButton =
            MaterialButton(
                ctx,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
                applyCurrencyIcon(this, pickedFrom, placeholder, ctx)
                setOnClickListener {
                    openCurrencyPicker { iso ->
                        pickedFrom = iso
                        applyCurrencyIcon(this, iso, placeholder, ctx)
                    }
                }
            }
        val toLabel = TextView(ctx).apply { text = getString(R.string.fee_pair_to) }
        val toButton =
            MaterialButton(
                ctx,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
                applyCurrencyIcon(this, pickedTo, placeholder, ctx)
                setOnClickListener {
                    openCurrencyPicker { iso ->
                        pickedTo = iso
                        applyCurrencyIcon(this, iso, placeholder, ctx)
                    }
                }
            }
        val bothWays =
            CheckBox(ctx).apply {
                text = getString(R.string.fee_pair_both_ways)
                isChecked = existing?.bothWays == true
            }
        val percentLabel = TextView(ctx).apply { text = getString(R.string.fee_edit_percent) }
        val percentInput =
            EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                if (existing != null) setText(existing.percent.toPlainString())
            }
        val signToggle = buildSignToggle(ctx, existing?.isMarkup)
        val activeCheckbox = buildActiveCheckbox(ctx, existing?.isActive != false)

        listOf(
            nameLabel,
            nameInput,
            fromLabel,
            fromButton,
            toLabel,
            toButton,
            bothWays,
            percentLabel,
            percentInput,
        ).forEach { container.addView(it) }
        addSignToggleWithTopMargin(container, signToggle.view)
        container.addView(activeCheckbox)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.fee_section_specific_pair)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val from = pickedFrom
                val to = pickedTo
                if (from == null || to == null) return@setPositiveButton
                val percent =
                    percentInput.text.toString().toBigDecimalOrNull()
                        ?: BigDecimal.ZERO
                onConfirm(
                    Fee.SpecificPair(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        name = nameInput.text.toString().trim(),
                        percent = percent.abs(),
                        isMarkup = signToggle.isMarkup(),
                        from = from,
                        to = to,
                        bothWays = bothWays.isChecked,
                        isActive = activeCheckbox.isChecked,
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
            MaterialButton(
                ctx,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
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

    private fun openCurrencyPicker(onPicked: (String) -> Unit) {
        val dialog = SearchableSpinnerDialog(requireContext())
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
