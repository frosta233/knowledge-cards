package com.example.knowledgecards.widget

import android.content.Context
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import com.example.knowledgecards.KnowledgeCardsApp
import com.example.knowledgecards.data.Card
import com.example.knowledgecards.domain.CategoryTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Snapshot of the data needed to render the widget. */
internal data class WidgetState(
    val card: Card?,
    val index: Int,
    val total: Int,
    val pathExpanded: Boolean
)

/**
 * Home-screen widget showing the current card:
 *
 *  ┌─────────────────────────────┐
 *  │ 辛温解表  (tap: 完整路径)      │
 *  │ 麻黄汤                        │
 *  │ 组成：麻黄、桂枝、杏仁、炙甘草。 │  ← 可上下滚动
 *  │ 功效：发汗解表，宣肺平喘。       │
 *  │ ◀ 上一张     3 / 24     下一张 ▶ │
 *  └─────────────────────────────┘
 *
 * Shares "current card" progress with the app through DataStore; updates only
 * happen on user interaction (buttons / path toggle) or app progress changes.
 */
class CardWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(320.dp, 180.dp), // 最小 3×5
            DpSize(320.dp, 250.dp), // 常用 4×5
            DpSize(320.dp, 320.dp),
            DpSize(440.dp, 250.dp),
            DpSize(440.dp, 320.dp),
            DpSize(500.dp, 400.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val state = loadState(context)
        provideContent {
            CardWidgetContent(state)
        }
    }

    companion object {
        internal suspend fun loadState(context: Context): WidgetState {
            val container = (context.applicationContext as KnowledgeCardsApp).container
            return withContext(Dispatchers.IO) {
                val settings = container.progressStore.current()
                val cards = container.cardRepository.getCards(settings.sortMode)
                val currentIndex = cards.indexOfFirst { it.id == settings.lastCardId }
                    .let { if (it >= 0) it else 0 }
                WidgetState(
                    card = cards.getOrNull(currentIndex),
                    index = currentIndex,
                    total = cards.size,
                    pathExpanded = settings.widgetPathExpanded
                )
            }
        }

        @androidx.compose.runtime.Composable
        private fun renderContent(state: WidgetState) {
            CardWidgetContent(state)
        }

        @OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
        internal suspend fun render(
            context: Context,
            size: androidx.compose.ui.unit.DpSize,
            state: WidgetState
        ): android.widget.RemoteViews {
            val result = androidx.glance.appwidget.GlanceRemoteViews()
                .compose(context, size) { CardWidgetContent(state) }
            return result.remoteViews
        }
    }
}

@androidx.compose.runtime.Composable
private fun CardWidgetContent(state: WidgetState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val colors = GlanceTheme.colors
    val card = state.card

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = if (size.width >= 300.dp) 16.dp else 12.dp)
            .padding(vertical = if (size.height >= 220.dp) 14.dp else 10.dp)
    ) {
        if (card == null) {
            Text(
                text = "尚未导入卡片，点此打开 App",
                style = TextStyle(fontSize = 13.sp, color = colors.onSurface),
                modifier = GlanceModifier.clickable(
                    CardWidgetActions.openCardAction(context, 0L)
                )
            )
            return@Column
        }

        // 1) Path bar — shows last segment, tap toggles the full path.
        val pathText = if (state.pathExpanded) {
            card.path.ifEmpty { "未分类" }
        } else {
            CategoryTree.lastSegment(card.path)
        }
        Text(
            text = pathText,
            maxLines = if (state.pathExpanded) 2 else 1,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.primary
            ),
            modifier = GlanceModifier.clickable(
                CardWidgetActions.broadcastAction(context, CardWidgetActions.ACTION_TOGGLE_PATH)
            )
        )

        Spacer(GlanceModifier.fillMaxWidth().height(4.dp))

        // 2) Title — tap opens the app at this card.
        Text(
            text = card.title,
            maxLines = if (size.height >= 220.dp) 2 else 1,
            style = TextStyle(
                fontSize = if (size.height >= 220.dp) 17.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            ),
            modifier = GlanceModifier.clickable(
                CardWidgetActions.openCardAction(context, card.id)
            )
        )

        Spacer(GlanceModifier.fillMaxWidth().height(6.dp))

        // 3) Scrollable body — a Glance LazyColumn provides widget-internal
        //    scrolling for long card text (equivalent of a ScrollView).
        //    A concrete height is required (ListView can't use weights).
        val bodyHeight = (size.height - 130.dp).coerceAtLeast(40.dp)
        LazyColumn(
            modifier = GlanceModifier.fillMaxWidth().height(bodyHeight)
        ) {
            items(listOf(card.content)) { body ->
                Text(
                    text = body,
                    style = TextStyle(
                        fontSize = if (size.height >= 220.dp) 13.sp else 12.sp,
                        color = colors.onSurface
                    ),
                    modifier = GlanceModifier.clickable(
                        CardWidgetActions.openCardAction(context, card.id)
                    )
                )
            }
        }

        Spacer(GlanceModifier.fillMaxWidth().height(6.dp))

        // 4) Paging controls — widgets don't support swipe gestures, so
        //    flipping cards is done with explicit buttons.
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val prevEnabled = state.index > 0
            val nextEnabled = state.index < state.total - 1
            val disabledColor = androidx.glance.unit.ColorProvider(
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)
            )
            val prevAction = CardWidgetActions.broadcastAction(
                context, CardWidgetActions.ACTION_PREV
            )
            val nextAction = CardWidgetActions.broadcastAction(
                context, CardWidgetActions.ACTION_NEXT
            )
            Text(
                text = "◀ 上一张",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (prevEnabled) colors.primary else disabledColor
                ),
                modifier = if (prevEnabled) {
                    GlanceModifier.defaultWeight().clickable(prevAction)
                } else {
                    GlanceModifier.defaultWeight()
                }
            )
            Text(
                text = "${state.index + 1} / ${state.total}",
                style = TextStyle(fontSize = 12.sp, color = colors.onSurface),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "下一张 ▶",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (nextEnabled) colors.primary else disabledColor
                ),
                modifier = if (nextEnabled) {
                    GlanceModifier.defaultWeight().clickable(nextAction)
                } else {
                    GlanceModifier.defaultWeight()
                }
            )
        }
    }
}
