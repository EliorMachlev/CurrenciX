package com.eliormachlev.currencix.view

import android.os.Build
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.hapticTap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BaseActivity : AppCompatActivity() {
    /**
     * Whether this screen opens and closes with the app's own transition
     * (res/anim/screen_*). The launcher activity opts out so the system's
     * app-launch animation stays untouched.
     */
    protected open val usesScreenTransition: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // pure black — night mode itself is set once in CurrenciesApplication
        // so this setTheme call resolves against the correct night qualifier.
        setTheme(
            if (Database(this).isPureBlackEnabled()) {
                R.style.AppTheme_PureBlack
            } else {
                R.style.AppTheme
            },
        )

        super.onCreate(savedInstanceState)
        if (usesScreenTransition) applyScreenTransition()
    }

    // Android 14+: overrideActivityTransition, which also drives the
    // predictive-back cross-activity animation. Earlier versions: the legacy
    // pending transition, here for opening and in finish() for closing.
    private fun applyScreenTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.screen_enter, R.anim.screen_hold)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.screen_hold, R.anim.screen_exit_pop)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.screen_enter, R.anim.screen_hold)
        }
    }

    override fun finish() {
        super.finish()
        if (usesScreenTransition && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.screen_hold, R.anim.screen_exit_pop)
        }
    }

    /**
     * Fires a haptic tap whenever the options-menu overflow opens — covers the
     * "open the popup" gesture that item-selection haptic wouldn't reach.
     */
    override fun onMenuOpened(
        featureId: Int,
        menu: Menu,
    ): Boolean {
        hapticTap()
        return super.onMenuOpened(featureId, menu)
    }

    /**
     * Subscribe to [FoldingFeature] changes for the current window and forward
     * the first feature (if any) to [onFeature] whenever it changes. Handles
     * the lifecycle-aware collection so subclasses only supply the reaction.
     */
    protected fun observeFoldingFeature(onFeature: (FoldingFeature) -> Unit) {
        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker
                    .getOrCreate(this@BaseActivity)
                    .windowLayoutInfo(this@BaseActivity)
                    .collect { info ->
                        info.displayFeatures
                            .filterIsInstance(FoldingFeature::class.java)
                            .firstOrNull()
                            ?.let(onFeature)
                    }
            }
        }
    }
}
