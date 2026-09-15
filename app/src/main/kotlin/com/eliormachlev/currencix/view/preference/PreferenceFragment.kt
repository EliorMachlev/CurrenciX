package com.eliormachlev.currencix.view.preference

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.TaskStackBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.eliormachlev.currencix.BuildConfig
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.main.MainActivity
import com.eliormachlev.currencix.view.preference.compose.PreferenceScreen
import com.eliormachlev.currencix.view.preference.compose.PreferenceScreenCallbacks
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import timber.log.Timber

private const val FLAVOR_PLAY = "play"
private const val URL_PLAY_MARKET = "market://details?id=com.eliormachlev.currencix"
private const val URL_PLAY_WEB = "https://play.google.com/store/apps/details?id=com.eliormachlev.currencix"

/**
 * Compose-hosted preferences root. Replaces the old
 * `PreferenceFragmentCompat`-backed implementation with a `ComposeView`
 * rendering [PreferenceScreen], plus glue for the non-preference actions
 * that still need fragment/intent machinery: pushing Fees/Backup fragments
 * onto the container back-stack, opening the existing Credits and Graph
 * dialogs, and firing external intents (Play, changelog).
 */
class PreferenceFragment : Fragment() {
    private lateinit var viewModel: PreferenceViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        viewModel = ViewModelProvider(this)[PreferenceViewModel::class.java]
        activity?.setTitle(R.string.title_preferences)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    PreferenceScreen(
                        viewModel = viewModel,
                        callbacks = buildCallbacks(),
                    )
                }
            }
        }
    }

    private fun buildCallbacks(): PreferenceScreenCallbacks =
        PreferenceScreenCallbacks(
            onOpenFees = { pushFragment(::FeeManagerFragment) },
            onOpenBackup = { pushFragment(::BackupFragment) },
            onOpenGraphOptions = { GraphOptionsDialog().show(childFragmentManager, null) },
            onOpenCredits = { CreditsDialog().show(childFragmentManager, null) },
            onRateApp = ::openPlayStore,
            onThemeRequiresRestart = ::rebuildActivityStack,
        )

    private fun pushFragment(factory: () -> Fragment) {
        parentFragmentManager
            .beginTransaction()
            .replace(R.id.preferences_fragment, factory())
            .addToBackStack(null)
            .commit()
    }

    private fun openPlayStore() {
        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR != FLAVOR_PLAY) return
        try {
            startActivity(playIntent(URL_PLAY_MARKET))
        } catch (e: ActivityNotFoundException) {
            Timber.tag("PreferenceFragment").d(e, "Play Store not available, opening browser")
            startActivity(playIntent(URL_PLAY_WEB))
        }
    }

    private fun playIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NO_HISTORY
                    or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    or Intent.FLAG_ACTIVITY_NEW_DOCUMENT,
            )
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
