package com.eliormachlev.currencix.view.main

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.AppDispatchers
import com.eliormachlev.currencix.util.resolveThemeColor
import com.eliormachlev.currencix.view.cart.CartRoute
import com.eliormachlev.currencix.view.compose.AppSnackbar
import com.eliormachlev.currencix.view.compose.AppSnackbarHost
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.compose.LayerCapture
import com.eliormachlev.currencix.view.compose.LocalAppSnackbar
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.compose.theme.Motion
import com.eliormachlev.currencix.view.navigation.AppNavHost
import com.eliormachlev.currencix.view.navigation.AppNavigator
import com.eliormachlev.currencix.view.navigation.LocalScreenBackground
import com.eliormachlev.currencix.view.navigation.Screen
import com.eliormachlev.currencix.view.navigation.rememberAppNavigator
import com.eliormachlev.currencix.view.preference.BackupRoute
import com.eliormachlev.currencix.view.preference.FeesRoute
import com.eliormachlev.currencix.view.preference.SettingsRoute
import com.eliormachlev.currencix.view.timeline.TimelineRoute
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import com.eliormachlev.currencix.viewmodel.main.Operator
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// Splash → wordmark hand-off overlap (#155). The platform splash icon
// fades out over this window while the Compose wordmark's × reveal
// (Motion.LONG_MILLIS) is already running — a small overlap hides the seam
// that would otherwise show if we waited for the icon to disappear before
// starting the reveal. Ending at the reveal's first quarter means the eye
// never catches a hard cut; derived so it tracks any change to the reveal.
private const val SPLASH_EXIT_FADE_MILLIS = Motion.LONG_MILLIS / 4L

/**
 * The app's only Activity. Every screen — converter, timeline, cart,
 * settings, fees, backup — is a Compose destination on one back stack
 * ([AppNavHost]), so moving between them is an in-window transition, with
 * shared elements flying between screens.
 *
 * Owns what outlives any one screen: the splash hand-off, the XML theme
 * (pure black), foldable posture, and hardware-keyboard input for the
 * converter.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var converterHost: ConverterHost

    // Set while composed; lets hardware-keyboard input check which screen is up.
    private var navigator: AppNavigator? = null

    private val foldingFeatureState = mutableStateOf<FoldingFeature?>(null)

    // A pair / amount / screen an intent asked for (ConverterLaunch), applied
    // once the navigator exists, then cleared.
    private val launchRequest = mutableStateOf<ConverterLaunch.Request?>(null)

    // Messages from any screen, drawn over all of them (AppContent).
    private val snackbar by lazy { AppSnackbar(lifecycleScope) }

    // Splash-screen keep-on-screen gate (#155). Flipped to true by the
    // wordmark's first frame so the platform splash holds until Compose is
    // pixel-ready to run its reveal, then releases into the exit animation.
    // Plain Boolean — the platform polls it from a pre-draw listener on the
    // main thread and the wordmark writes it from the main thread too.
    private var firstContentReady: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() must run before super.onCreate() per the
        // androidx docs — it swaps the launcher-splash theme (AppTheme.Splash)
        // for postSplashScreenTheme (AppTheme) and installs the exit-animation
        // listener. Only a cold start gets the animated hand-off: a
        // recreation (rotation, theme change, process-death restore) skips
        // the reveal, so the gate opens immediately.
        val isColdStart = savedInstanceState == null
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !firstContentReady }
        if (isColdStart) splashScreen.setOnExitAnimationListener(::fadeOutSplashIcon) else firstContentReady = true

        // Pure black is an XML theme variant — night mode itself is set once
        // in CurrenciesApplication, so this resolves against the right
        // night qualifier.
        val database = Database(this)
        val pureBlack = database.display.isPureBlackEnabled()
        setTheme(if (pureBlack) R.style.AppTheme_PureBlack else R.style.AppTheme)
        super.onCreate(savedInstanceState)
        // Compose owns the whole window, system bars included: the top bars
        // pad for the status bar, each screen for the navigation bar.
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this, MainViewModel.factory(application))[MainViewModel::class.java]
        converterHost = createConverterHost(revealPending = isColdStart)
        // Only a fresh launch: a recreated Activity already applied it.
        if (savedInstanceState == null) launchRequest.value = ConverterLaunch.parse(intent)

        val themeBackground = Color(resolveThemeColor(android.R.attr.colorBackground))
        setContent {
            val dynamicColor by database.display
                .isDynamicColorEnabledFlow()
                .collectAsStateWithLifecycle(database.display.isDynamicColorEnabledBlocking())
            AppTheme(dynamicColor = dynamicColor) {
                // Wallpaper colors bring their own background — except pure
                // black, which stays black whatever the palette.
                val screenBackground =
                    if (dynamicColor && !pureBlack) MaterialTheme.colorScheme.background else themeBackground
                CompositionLocalProvider(LocalScreenBackground provides screenBackground) {
                    AppContent()
                }
            }
        }

        observeFoldingFeature()
        keepShortcutsInStep()
    }

    // Launcher shortcuts follow the recent pairs (AppShortcuts).
    private fun keepShortcutsInStep() {
        lifecycleScope.launch(AppDispatchers.production.default) {
            Database(applicationContext).lastState.getRecentPairsFlow().distinctUntilChanged().collect { recents ->
                AppShortcuts.update(applicationContext, recents)
            }
        }
    }

    @Composable
    private fun AppContent() {
        val nav = rememberAppNavigator()
        val foldingFeature by foldingFeatureState
        val request by launchRequest
        LaunchedEffect(request, nav) {
            request?.let { apply(it, nav) }
            launchRequest.value = null
        }
        DisposableEffect(nav) {
            navigator = nav
            onDispose { navigator = null }
        }
        CompositionLocalProvider(LocalAppSnackbar provides snackbar) {
            Box(Modifier.fillMaxSize()) {
                AppNavHost(navigator = nav) { screen -> Destination(screen, nav, foldingFeature) }
                AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ConverterLaunch.parse(intent)?.let { launchRequest.value = it }
    }

    // Converter on the asked-for pair (and amount), or the cart — over
    // whatever was open, since the request came from outside the app.
    private fun apply(
        request: ConverterLaunch.Request,
        nav: AppNavigator,
    ) {
        when (request) {
            is ConverterLaunch.Request.Convert -> {
                AppShortcuts.reportUsed(this, request.pair)
                viewModel.setCurrencyPair(request.pair)
                request.amount?.let(viewModel.input::setAmount)
                nav.navigate(Screen.Converter)
            }
            ConverterLaunch.Request.OpenCart ->
                nav.navigate(Screen.Cart(viewModel.getBaseCurrency().value, viewModel.getDestinationCurrency().value))
        }
    }

    @Composable
    private fun Destination(
        screen: Screen,
        nav: AppNavigator,
        foldingFeature: FoldingFeature?,
    ) {
        when (screen) {
            Screen.Converter -> ConverterRoute(host = converterHost, navigator = nav, foldingFeature = foldingFeature)
            is Screen.Timeline -> TimelineRoute(screen = screen, onBack = nav::pop, foldingFeature = foldingFeature)
            is Screen.Cart -> CartRoute(screen = screen, onBack = nav::pop, onOpenFees = { nav.navigate(Screen.Fees) })
            // The settings screens are prose: laid out the way the language reads.
            Screen.Settings ->
                ReadingDirection {
                    SettingsRoute(
                        onBack = nav::pop,
                        onOpenFees = { nav.navigate(Screen.Fees) },
                        onOpenBackup = { nav.navigate(Screen.Backup) },
                        onThemeRequiresRestart = ::recreate,
                    )
                }
            Screen.Fees -> ReadingDirection { FeesRoute(onBack = nav::pop) }
            Screen.Backup -> ReadingDirection { BackupRoute(onBack = nav::pop) }
        }
    }

    private fun createConverterHost(revealPending: Boolean): ConverterHost {
        val status = ConverterStatus(this, viewModel, snackbar).also { it.observe(this) }
        val heroCapture = LayerCapture()
        return ConverterHost(
            viewModel = viewModel,
            preferenceModel = ViewModelProvider(this)[PreferenceViewModel::class.java],
            status = status,
            share = ConversionShare(this, viewModel, heroCapture, status),
            revealPending = revealPending,
            onWordmarkFirstFrame = { firstContentReady = true },
        )
    }

    // Foldable posture, forwarded to the converter and timeline layouts.
    private fun observeFoldingFeature() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker
                    .getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { info ->
                        info.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .firstOrNull()
                            ?.let { foldingFeatureState.value = it }
                    }
            }
        }
    }

    // Hardware keyboard: typing goes to the converter, and only while it's
    // the screen on top.
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        if (navigator?.current != Screen.Converter) return super.onKeyDown(keyCode, event)
        // IMPORTANT: can't work with simple keyCodes here, as depending on the keyboard
        // configuration, wrong values will be returned (e.g. KEYCODE_8 instead of KEYCODE_PLUS).
        val key = event?.keyCharacterMap?.get(keyCode, event.metaState)?.let { Char(it) }
        return handleCharKey(key) || handleControlKey(keyCode) || super.onKeyDown(keyCode, event)
    }

    private fun handleCharKey(key: Char?): Boolean {
        key ?: return false
        val input = viewModel.input
        val operator = Operator.fromHardware(key)
        when {
            operator != null -> input.addOperator(operator.display)
            key.isDigit() -> input.addNumber(key.toString())
            key == '.' || key == ',' -> input.addDecimal()
            key == '(' -> input.addOpenParen()
            key == ')' -> input.addCloseParen()
            key == '%' -> input.addPercent()
            else -> return false
        }
        return true
    }

    private fun handleControlKey(keyCode: Int): Boolean {
        if (keyCode != KeyEvent.KEYCODE_DEL) return false
        viewModel.input.delete()
        return true
    }

    // Splash exit-animation listener. Fades the platform splash icon over
    // SPLASH_EXIT_FADE_MILLIS while the Compose wordmark's own × reveal
    // (already started on its first frame — see Wordmark.kt) runs
    // underneath, then removes the SplashScreenView so later frames aren't
    // overdrawn by the leftover splash surface.
    //
    // Only iconView fades (not the whole SplashScreenView) so the paper
    // background stays solid underneath until removal — otherwise the
    // Compose background would briefly show through a semi-transparent
    // splash surface and read as a flash.
    private fun fadeOutSplashIcon(provider: SplashScreenViewProvider) {
        // Some OEM ROMs (observed on MIUI) return a null iconView from
        // SplashScreenViewProvider.ViewImpl31 — nothing to fade in that case,
        // just remove the splash surface directly so we don't NPE.
        val icon = runCatching { provider.iconView }.getOrNull()
        if (icon == null) {
            provider.remove()
            return
        }
        icon
            .animate()
            .alpha(0f)
            .setDuration(SPLASH_EXIT_FADE_MILLIS)
            .withEndAction { provider.remove() }
            .start()
    }
}
