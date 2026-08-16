package com.eliormachlev.currencix.view.preference

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.app.TaskStackBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.eliormachlev.currencix.BuildConfig
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.AppTheme
import com.eliormachlev.currencix.repository.KEYBOARD_TYPE_BASIC
import com.eliormachlev.currencix.repository.KEYBOARD_TYPE_EXPANDED
import com.eliormachlev.currencix.util.ChoiceOption
import com.eliormachlev.currencix.util.DECIMAL_PLACES_DEFAULT
import com.eliormachlev.currencix.util.DECIMAL_PLACES_MAX
import com.eliormachlev.currencix.util.DECIMAL_PLACES_MIN
import com.eliormachlev.currencix.util.URL_REPO
import com.eliormachlev.currencix.util.asPreferenceSummary
import com.eliormachlev.currencix.util.showChoiceExplainerDialog
import com.eliormachlev.currencix.view.main.MainActivity
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import com.eliormachlev.currencix.widget.LongSummaryPreference
import timber.log.Timber
import java.util.Calendar

// Sentinel returned by ApiProvider.fromId when the stored value is unknown /
// unset; the pref layer treats this as "use the default provider".
private const val UNKNOWN_PROVIDER_ID = -1

// Build flavor served through Play; other flavors get the donation entry
// instead of the "rate on Play" entry.
private const val FLAVOR_PLAY = "play"

private const val URL_RELEASES_TAG = "$URL_REPO/releases/tag/v"
private const val URL_PULLS = "$URL_REPO/pulls"
private const val URL_COMMIT = "$URL_REPO/commit/"
private const val URL_PLAY_MARKET = "market://details?id=com.eliormachlev.currencix"
private const val URL_PLAY_WEB = "https://play.google.com/store/apps/details?id=com.eliormachlev.currencix"

// Debug APKs prefer the PR URL, then fall back to the commit page for the
// SHA the APK was built from (GitHub renders the associated PR badge on the
// commit page — one click from the exact PR). If neither is available (git
// unavailable at build time), fall back to the repo pulls list.
private fun releaseNotesUrl(): String =
    if (BuildConfig.DEBUG) {
        BuildConfig.PR_URL.takeIf { it.isNotBlank() }
            ?: BuildConfig.COMMIT_SHA.takeIf { it.isNotBlank() }?.let { "$URL_COMMIT$it" }
            ?: URL_PULLS
    } else {
        "$URL_RELEASES_TAG${BuildConfig.VERSION_NAME}"
    }

@Suppress("unused")
class PreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var viewModel: PreferenceViewModel

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.title_preferences)
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.prefs, rootKey)
        viewModel = ViewModelProvider(this)[PreferenceViewModel::class.java]
        setupFeePreference()
        setupBackupPreference()
        setupDisplayPreferences()
        setupGraphOptionsPreference()
        setupApiPreferences()
        setupAboutPreferences()
    }

    private fun setupGraphOptionsPreference() {
        findPreference<Preference>(getString(R.string.graph_options_key))?.apply {
            setOnPreferenceClickListener {
                GraphOptionsDialog().show(childFragmentManager, null)
                true
            }
        }
    }

    private fun setupFeePreference() {
        pushFragmentOnClick(R.string.fee_key, ::FeeManagerFragment)
    }

    private fun setupBackupPreference() {
        pushFragmentOnClick(R.string.backup_key, ::BackupFragment)
    }

    private fun pushFragmentOnClick(
        keyRes: Int,
        factory: () -> Fragment,
    ) {
        findPreference<Preference>(getString(keyRes))?.setOnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.preferences_fragment, factory())
                .addToBackStack(null)
                .commit()
            true
        }
    }

    // Two-option picker rebuilt as a title-plus-explainer dialog (matching the
    // Fee-side preference) so each option carries a short description instead
    // of being a bare radio list.
    private fun setupKeyboardPreference() {
        val pref = findPreference<Preference>(getString(R.string.keyboard_key)) ?: return
        pref.summary = summaryForKeyboardType(viewModel.getKeyboardTypeBlocking())
        pref.setOnPreferenceClickListener {
            showKeyboardPickerDialog { picked ->
                viewModel.setKeyboardType(picked)
                pref.summary = summaryForKeyboardType(picked)
            }
            true
        }
    }

    private fun keyboardOptions(): List<Pair<Int, ChoiceOption>> =
        listOf(
            KEYBOARD_TYPE_BASIC to
                ChoiceOption(
                    getString(R.string.keyboard_option_default),
                    getString(R.string.keyboard_summary_default),
                ),
            KEYBOARD_TYPE_EXPANDED to
                ChoiceOption(
                    getString(R.string.keyboard_option_expanded),
                    getString(R.string.keyboard_summary_expanded),
                ),
        )

    private fun summaryForKeyboardType(type: Int): CharSequence {
        val options = keyboardOptions()
        val option = options.firstOrNull { it.first == type }?.second ?: options.first().second
        return option.asPreferenceSummary()
    }

    private fun showKeyboardPickerDialog(onPicked: (Int) -> Unit) {
        val options = keyboardOptions()
        val current = viewModel.getKeyboardTypeBlocking()
        val selectedIndex = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
        showChoiceExplainerDialog(
            ctx = requireContext(),
            titleRes = R.string.keyboard_title,
            options = options.map { it.second },
            selectedIndex = selectedIndex,
        ) { index ->
            onPicked(options[index].first)
        }
    }

    private fun setupDisplayPreferences() {
        findPreference<SwitchPreferenceCompat>(getString(R.string.previewConversion_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setPreviewConversionEnabled(newValue.toString().toBoolean())
                true
            }
        }
        setupKeyboardPreference()
        findPreference<ListPreference>(getString(R.string.decimal_places_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setDecimalPlaces(
                    (newValue.toString().toIntOrNull() ?: DECIMAL_PLACES_DEFAULT)
                        .coerceIn(DECIMAL_PLACES_MIN, DECIMAL_PLACES_MAX),
                )
                true
            }
        }
        findPreference<SwitchPreferenceCompat>(getString(R.string.haptic_feedback_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setHapticFeedbackEnabled(newValue.toString().toBoolean())
                true
            }
        }
        findPreference<ListPreference>(getString(R.string.theme_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                val theme = AppTheme.fromId(newValue.toString().toInt())
                if (viewModel.setTheme(theme)) {
                    rebuildActivityStack()
                }
                true
            }
        }
        findPreference<LanguagePickerPreference>(getString(R.string.language_key))?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setLanguage(newValue.toString())
                true
            }
        }
        findPreference<ListPreference>(getString(R.string.date_format_key))?.apply {
            summaryProvider = Preference.SummaryProvider<ListPreference> { pref -> pref.value }
        }
    }

    private fun setupApiPreferences() {
        val apiKeyPref = findPreference<EditTextPreference>(getString(R.string.api_open_exchangerates_id_key))
        apiKeyPref?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.setOpenExchangeratesApiKey(newValue.toString().trim())
                true
            }
            dialogMessage = getText(R.string.api_open_exchangerates_api_key_message)
        }
        viewModel.getOpenExchangeratesApiKey().observe(this) { id ->
            apiKeyPref?.summaryProvider =
                Preference.SummaryProvider<EditTextPreference> {
                    if (id.isNullOrBlank()) getText(R.string.api_open_exchangerates_api_key_missing) else id
                }
        }
        findPreference<ProviderPickerPreference>(getString(R.string.api_key))?.apply {
            val providers = ApiProvider.entries
            entries = providers.map { it.getName() }.toTypedArray()
            entryValues = providers.map { it.id.toString() }.toTypedArray()
            setOnPreferenceChangeListener { _, newValue ->
                val provider = ApiProvider.fromId(newValue.toString().toIntOrNull() ?: UNKNOWN_PROVIDER_ID)
                viewModel.setApiProvider(provider)
                apiKeyPref?.isVisible = provider == ApiProvider.OPEN_EXCHANGERATES
                true
            }
            if (entry == null) {
                val defaultProvider = ApiProvider.fromId(UNKNOWN_PROVIDER_ID)
                viewModel.setApiProvider(defaultProvider)
                value = defaultProvider.id.toString()
            }
            apiKeyPref?.isVisible = ApiProvider.fromId(value.toIntOrNull() ?: UNKNOWN_PROVIDER_ID) == ApiProvider.OPEN_EXCHANGERATES
        }
        viewModel.getApiProvider().observe(this) {
            findPreference<LongSummaryPreference>(getString(R.string.key_apiProvider))?.apply {
                title = resources.getString(R.string.api_about_title, it.getName())
                summary = it.getDescriptionLong(context)
            }
            findPreference<LongSummaryPreference>(getString(R.string.key_refreshPeriod))?.summary =
                it.getDescriptionUpdateInterval(requireContext())
        }
    }

    private fun setupAboutPreferences() {
        findPreference<Preference>(getString(R.string.credits_key))?.apply {
            setOnPreferenceClickListener {
                CreditsDialog().show(childFragmentManager, null)
                true
            }
        }
        findPreference<Preference>(getString(R.string.rate_key))?.apply {
            @Suppress("KotlinConstantConditions")
            isVisible = BuildConfig.FLAVOR == FLAVOR_PLAY
            setOnPreferenceClickListener {
                try {
                    startActivity(createIntent(URL_PLAY_MARKET))
                } catch (e: ActivityNotFoundException) {
                    Timber.tag("PreferenceFragment").d(e, "Play Store not available, opening browser")
                    startActivity(createIntent(URL_PLAY_WEB))
                }
                true
            }
        }
        findPreference<Preference>(getString(R.string.changelog_key))?.apply {
            setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseNotesUrl())))
                true
            }
        }
        findPreference<Preference>(getString(R.string.version_key))?.apply {
            title = BuildConfig.VERSION_NAME
            summary = getString(R.string.version_summary, Calendar.getInstance().get(Calendar.YEAR).toString())
        }
    }

    private fun createIntent(url: String): Intent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NO_HISTORY
                or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                or Intent.FLAG_ACTIVITY_NEW_DOCUMENT,
        )
        return intent
    }

    // Rebuild the MainActivity → PreferenceActivity stack and finish the
    // current activity so the pure-black theme is reapplied everywhere.
    // Matches the pre-fork OLED toggle behavior.
    private fun rebuildActivityStack() {
        val activity = requireActivity()
        TaskStackBuilder
            .create(activity)
            .addNextIntent(
                Intent(activity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                },
            ).addNextIntent(Intent(activity, PreferenceActivity::class.java))
            .startActivities()
        activity.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }
}
