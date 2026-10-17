package com.eliormachlev.currencix.view.widget

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.eliormachlev.currencix.repository.persistence.WidgetRefreshBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.hours

// The system rate-limits preview updates; more often than this is wasted work.
private val PREVIEW_MIN_INTERVAL = 1.hours

/**
 * Keeps the widget picker's preview of [CurrencyWidget] current on Android 15+
 * (older versions show the static `previewLayout`): once at start, then after
 * the rates or the converter's pair change ([WidgetRefreshBus]), at most once
 * per [PREVIEW_MIN_INTERVAL] — changes in between are folded into the next one.
 */
fun CoroutineScope.publishWidgetPreviews(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val appContext = context.applicationContext
    launch {
        WidgetRefreshBus.events
            .onStart { emit(Unit) }
            .conflate()
            .collect {
                val result = GlanceAppWidgetManager(appContext).setWidgetPreviews(CurrencyWidget::class)
                if (result != GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
                    Timber.d("Widget preview not updated (result %d)", result)
                }
                delay(PREVIEW_MIN_INTERVAL)
            }
    }
}
