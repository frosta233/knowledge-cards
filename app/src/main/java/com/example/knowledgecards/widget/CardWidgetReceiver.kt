package com.example.knowledgecards.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.knowledgecards.KnowledgeCardsApp
import com.example.knowledgecards.domain.cardsInBrowseOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives widget broadcasts:
 *  - system APPWIDGET_UPDATE (handled by [GlanceAppWidgetReceiver])
 *  - our own prev / next / toggle-path / open-card actions
 *
 * All state mutations go through the shared [ProgressStore] and re-render via
 * [WidgetUpdater] (direct RemoteViews push, bypassing Glance's session
 * pipeline which does not reliably re-run provideGlance from callbacks).
 */
class CardWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = CardWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            CardWidgetActions.ACTION_PREV -> handleMove(context, delta = -1)
            CardWidgetActions.ACTION_NEXT -> handleMove(context, delta = +1)
            CardWidgetActions.ACTION_TOGGLE_PATH -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val store = (context.applicationContext as KnowledgeCardsApp)
                            .container.progressStore
                        store.setWidgetPathExpanded(!store.current().widgetPathExpanded)
                        WidgetUpdater.update(context)
                    } catch (e: Exception) {
                        android.util.Log.e("CardWidgetReceiver", "toggle failed", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            CardWidgetActions.ACTION_OPEN_CARD -> {
                val cardId = intent.getLongExtra(CardWidgetActions.EXTRA_CARD_ID, 0L)
                context.startActivity(CardWidgetActions.openCardIntent(context, cardId))
            }
        }
    }

    private fun handleMove(context: Context, delta: Int) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as KnowledgeCardsApp).container
                val store = container.progressStore
                val settings = store.current()
                val books = container.cardRepository.getBooks()
                val bookId = com.example.knowledgecards.data.effectiveBookId(
                    settings.currentBookId, books
                )
                val cards = container.cardRepository.cardsInBrowseOrder(
                    bookId, settings.sortMode
                )
                if (cards.isEmpty()) return@launch
                val currentIndex =
                    cards.indexOfFirst { it.id == settings.lastCardId }.let { if (it >= 0) it else 0 }
                val target = (currentIndex + delta).coerceIn(0, cards.lastIndex)
                if (target != currentIndex) {
                    store.setLastCardId(cards[target].id)
                }
                WidgetUpdater.update(context)
            } catch (e: Exception) {
                android.util.Log.e("CardWidgetReceiver", "handleMove failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
