package com.eliormachlev.currencix.view.preference.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.ocr.TessLanguage
import com.eliormachlev.currencix.util.normalizeForSearch
import com.eliormachlev.currencix.view.compose.LedgerRow
import com.eliormachlev.currencix.view.compose.SupportingText
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPromptSheet
import com.eliormachlev.currencix.view.compose.dialogs.PromptMessage
import com.eliormachlev.currencix.view.compose.dialogs.SheetListEnd
import com.eliormachlev.currencix.view.compose.fieldName
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.viewmodel.preference.ReceiptLanguageRow
import com.eliormachlev.currencix.viewmodel.preference.ReceiptLanguagesState
import com.eliormachlev.currencix.viewmodel.preference.ReceiptLanguagesViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// A trip's last day is often a week or so out: the picker starts there, at
// the day's end, so the language is there for all of it.
private const val DEFAULT_KEEP_DAYS = 7L
private val DEFAULT_KEEP_TIME: LocalTime = LocalTime.of(23, 59)

/** What the screen is asking about, if anything. */
private sealed interface LanguagePrompt {
    data object Pick : LanguagePrompt

    /** Download [language], kept for good or until a time. */
    data class Add(
        val language: TessLanguage,
    ) : LanguagePrompt

    /** How long to keep [row]'s language, or delete it. */
    data class Edit(
        val row: ReceiptLanguageRow,
    ) : LanguagePrompt

    data class Delete(
        val language: TessLanguage,
    ) : LanguagePrompt
}

/**
 * Settings → Receipt languages: the languages receipts are read in, on the
 * phone or downloading, each with its size and how long it's kept; a tap
 * changes that or deletes it, the bin deletes it, and "Add a language"
 * downloads another once the user agrees to it (kept for good, or until a
 * time they pick, for a trip).
 */
@Composable
fun ReceiptLanguagesScreen(viewModel: ReceiptLanguagesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var prompt by remember { mutableStateOf<LanguagePrompt?>(null) }
    ReceiptLanguagesList(
        state = state,
        onAdd = { prompt = LanguagePrompt.Pick },
        onEdit = { prompt = LanguagePrompt.Edit(it) },
        onDelete = { prompt = LanguagePrompt.Delete(it) },
    )
    val close = { prompt = null }
    when (val open = prompt) {
        LanguagePrompt.Pick -> {
            ReceiptLanguagePickerSheet(available = state.available, onDismiss = close, onPicked = { prompt = LanguagePrompt.Add(it) })
        }

        is LanguagePrompt.Add -> {
            KeepLanguageSheet(
                language = open.language,
                initialUntil = null,
                confirmLabel = stringResource(R.string.receipt_setup_download),
                onConfirm = { until ->
                    viewModel.add(open.language, until)
                    close()
                },
                onDismiss = close,
            )
        }

        is LanguagePrompt.Edit -> {
            KeepLanguageSheet(
                language = open.row.language,
                initialUntil = open.row.keepUntil,
                confirmLabel = stringResource(R.string.receipt_languages_save),
                onConfirm = { until ->
                    viewModel.keep(open.row.language, until)
                    close()
                },
                onDismiss = close,
                onDelete = { prompt = LanguagePrompt.Delete(open.row.language) },
            )
        }

        is LanguagePrompt.Delete -> {
            DeleteLanguageSheet(
                language = open.language,
                onConfirm = {
                    viewModel.delete(open.language)
                    close()
                },
                onDismiss = close,
            )
        }

        null -> {}
    }
}

/** The screen's list: what reading uses, the languages here, and the way to add one. */
@Composable
internal fun ReceiptLanguagesList(
    state: ReceiptLanguagesState,
    onAdd: () -> Unit,
    onEdit: (ReceiptLanguageRow) -> Unit,
    onDelete: (TessLanguage) -> Unit,
) {
    SettingsList {
        item(key = "intro") {
            Column(Modifier.padding(horizontal = Spacing.XLarge, vertical = Spacing.Medium)) {
                SupportingText(stringResource(R.string.receipt_languages_intro))
            }
        }
        item(key = "installed") {
            PreferenceSection(text = stringResource(R.string.receipt_languages_installed)) {
                if (state.rows.isEmpty()) {
                    PreferenceRow(title = stringResource(R.string.receipt_languages_empty))
                }
                state.rows.forEach { row -> InstalledLanguageRow(row, onEdit = { onEdit(row) }, onDelete = { onDelete(row.language) }) }
                PreferenceRow(
                    title = stringResource(R.string.receipt_languages_add),
                    iconRes = R.drawable.ic_language,
                    onClick = onAdd,
                    enabled = state.available.isNotEmpty(),
                )
            }
        }
    }
}

// One language here: its name, size and how long it's kept; a tap to change that, the bin to delete it.
@Composable
private fun InstalledLanguageRow(
    row: ReceiptLanguageRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val name = row.language.displayName(locale)
    val size = row.language.downloadSize(context)
    val summary =
        when {
            row.downloading -> stringResource(R.string.receipt_languages_downloading, size)
            row.keepUntil != null -> stringResource(R.string.receipt_languages_kept_until, size, formatKeepUntil(row.keepUntil, locale))
            else -> stringResource(R.string.receipt_languages_kept, size)
        }
    PreferenceRow(
        title = name,
        summary = summary,
        onClick = onEdit,
        trailing = {
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.receipt_languages_delete, name))
            }
        },
    )
}

/** The languages that can be added, filtered as the user types (by their name in the app's language or their own). */
@Composable
private fun ReceiptLanguagePickerSheet(
    available: List<TessLanguage>,
    onDismiss: () -> Unit,
    onPicked: (TessLanguage) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val queryState = rememberTextFieldState()
    val query =
        queryState.text
            .toString()
            .trim()
            .normalizeForSearch()
    val sorted = remember(available, locale) { available.sortedBy { it.displayName(locale).normalizeForSearch() } }
    val shown = remember(sorted, query) { if (query.isEmpty()) sorted else sorted.filter { it.matches(query, locale) } }
    LedgerBottomSheet(title = stringResource(R.string.receipt_languages_add), onDismiss = onDismiss) {
        OutlinedTextField(
            state = queryState,
            lineLimits = TextFieldLineLimits.SingleLine,
            leadingIcon = { Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.XLarge)
                    .fieldName(stringResource(R.string.a11y_search_languages)),
        )
        Spacer(Modifier.height(Spacing.Medium))
        shown.forEachIndexed { index, language ->
            LedgerRow(
                onClick = { onPicked(language) },
                showDivider = index != shown.lastIndex,
                label = {
                    Column(Modifier.weight(1f)) {
                        Text(language.displayName(locale), style = MaterialTheme.typography.titleMedium)
                        Text(
                            language.downloadSize(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
        SheetListEnd()
    }
}

// Whether [query] (normalized) matches the language's name in the app's language, in English or in its own.
private fun TessLanguage.matches(
    query: String,
    locale: Locale,
): Boolean {
    val own = Locale.forLanguageTag(tag)
    return listOf(displayName(locale), displayName(Locale.ENGLISH), displayName(own)).any { query in it.normalizeForSearch() }
}

/**
 * How long to keep [language]: for good, or until a date and time ([onConfirm]
 * gets null or that instant). Adding a language says its size first; editing
 * one offers to delete it too ([onDelete]).
 */
@Composable
internal fun KeepLanguageSheet(
    language: TessLanguage,
    initialUntil: Instant?,
    confirmLabel: String,
    onConfirm: (Instant?) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val name = language.displayName(locale)
    var until by remember { mutableStateOf(initialUntil) }
    var pickingUntil by remember { mutableStateOf(false) }
    val untilChosen = until
    LedgerPromptSheet(
        title = name,
        confirmLabel = confirmLabel,
        onConfirm = { onConfirm(until) },
        onDismiss = onDismiss,
        leadingAction =
            onDelete?.let { delete ->
                { TextButton(onClick = delete) { Text(stringResource(R.string.a11y_delete), color = MaterialTheme.colorScheme.error) } }
            },
    ) {
        val message =
            if (onDelete == null) {
                stringResource(R.string.receipt_languages_download_message, name, language.downloadSize(context))
            } else {
                stringResource(R.string.receipt_languages_keep_message, name, language.downloadSize(context))
            }
        PromptMessage(message)
        Spacer(Modifier.height(Spacing.Small))
        Column(Modifier.selectableGroup()) {
            KeepChoice(text = stringResource(R.string.receipt_languages_keep), selected = untilChosen == null, onSelect = { until = null })
            KeepChoice(
                text =
                    if (untilChosen == null) {
                        stringResource(R.string.receipt_languages_keep_until_pick)
                    } else {
                        stringResource(R.string.receipt_languages_keep_until, formatKeepUntil(untilChosen, locale))
                    },
                selected = untilChosen != null,
                onSelect = { pickingUntil = true },
            )
        }
    }
    if (pickingUntil) {
        KeepUntilSheet(
            initial = until,
            onPick = {
                until = it
                pickingUntil = false
            },
            onDismiss = { pickingUntil = false },
        )
    }
}

@Composable
private fun KeepChoice(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(vertical = Spacing.Tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** A date (today or later) and a time, in the phone's time zone, for a language to go. */
@Composable
internal fun KeepUntilSheet(
    initial: Instant?,
    onPick: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val start =
        initial?.atZone(zone) ?: LocalDate
            .now(zone)
            .plusDays(DEFAULT_KEEP_DAYS)
            .atTime(DEFAULT_KEEP_TIME)
            .atZone(zone)
    val dateState =
        rememberDatePickerState(
            initialSelectedDateMillis =
                start
                    .toLocalDate()
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
            initialDisplayMode = DisplayMode.Picker,
            selectableDates = TodayOrLater,
        )
    val timeState = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)
    LedgerPromptSheet(
        title = stringResource(R.string.receipt_languages_until_title),
        confirmLabel = stringResource(R.string.receipt_languages_save),
        onConfirm = {
            val date =
                dateState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: start.toLocalDate()
            onPick(date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant())
        },
        onDismiss = onDismiss,
        scrollableBody = true,
    ) {
        DatePicker(state = dateState, title = null, headline = null, showModeToggle = false)
        HorizontalDivider()
        Spacer(Modifier.height(Spacing.Medium))
        TimeInput(state = timeState, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

// The date picker's days: today and after (a language can't be kept until the past).
private object TodayOrLater : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !Instant
            .ofEpochMilli(utcTimeMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .isBefore(LocalDate.now())

    override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now().year
}

@Composable
private fun DeleteLanguageSheet(
    language: TessLanguage,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = language.displayName(LocalConfiguration.current.locales[0])
    LedgerPromptSheet(
        title = stringResource(R.string.receipt_languages_delete, name),
        confirmLabel = stringResource(R.string.a11y_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    ) {
        PromptMessage(stringResource(R.string.receipt_languages_delete_message, name))
    }
}

// "14 Nov 2026, 23:59" in the user's language and the phone's time zone.
private fun formatKeepUntil(
    instant: Instant,
    locale: Locale,
): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .format(instant.atZone(ZoneId.systemDefault()))
