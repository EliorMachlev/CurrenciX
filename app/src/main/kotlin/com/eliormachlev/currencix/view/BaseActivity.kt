package com.eliormachlev.currencix.view

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
