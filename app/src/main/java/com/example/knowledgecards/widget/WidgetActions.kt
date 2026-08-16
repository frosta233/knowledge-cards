package com.example.knowledgecards.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.knowledgecards.MainActivity

/**
 * Widget interactions are plain broadcasts handled by [CardWidgetReceiver]:
 *  - prev / next: move the shared progress in [com.example.knowledgecards.domain.ProgressStore]
 *  - toggle path: expand/collapse the full category path
 *  - open card: launch the app and jump to the current card
 */
object CardWidgetActions {
    const val ACTION_PREV = "com.example.knowledgecards.widget.PREV"
    const val ACTION_NEXT = "com.example.knowledgecards.widget.NEXT"
    const val ACTION_TOGGLE_PATH = "com.example.knowledgecards.widget.TOGGLE_PATH"
    const val ACTION_OPEN_CARD = "com.example.knowledgecards.widget.OPEN_CARD"

    const val EXTRA_CARD_ID = "com.example.knowledgecards.widget.extra.CARD_ID"

    fun broadcastAction(context: Context, key: String): Action {
        val intent = Intent(context, CardWidgetReceiver::class.java).setAction(key)
        return actionSendBroadcast(intent)
    }

    fun openCardAction(context: Context, cardId: Long): Action =
        actionStartActivity(openCardIntent(context, cardId))

    fun openCardIntent(context: Context, cardId: Long): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_CARD_ID, cardId)
}

/**
 * Re-renders all placed widget instances and pushes the RemoteViews directly.
 *
 * Glance's update()/session pipeline does not reliably re-run provideGlance()
 * when triggered from inside its own callbacks (known 1.1 limitation), so we
 * bypass it: [androidx.glance.appwidget.GlanceRemoteViews.compose] renders the
 * same composable synchronously and [AppWidgetManager] applies it immediately.
 * No-op when no widgets are placed.
 */
object WidgetUpdater {
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    suspend fun update(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(CardWidget::class.java)
        if (ids.isEmpty()) return
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val state = CardWidget.loadState(context)
        for (id in ids) {
            val widgetId = (id as? AppWidgetId)?.appWidgetId ?: continue
            val size = widgetSize(appWidgetManager.getAppWidgetOptions(widgetId))
            val remoteViews = CardWidget.render(context, size, state)
            appWidgetManager.updateAppWidget(widgetId, remoteViews)
        }
    }

    /** Approximates the current widget size from AppWidget options (in dp). */
    private fun widgetSize(options: Bundle): DpSize {
        // Use the minimum reported size so the direct-push render never
        // overflows the actual cell; the layout adapts via LocalSize anyway.
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
        return DpSize(minW.dp, minH.dp)
    }
}

