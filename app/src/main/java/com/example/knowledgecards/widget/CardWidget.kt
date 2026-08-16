package com.example.knowledgecards.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
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
    val pathExpanded: Boolean,
    val accentColor: com.example.knowledgecards.domain.AccentColor =
        com.example.knowledgecards.domain.AccentColor.SYSTEM
)

/**
 * Home-screen widget showing the current card, in a translucent
 * acrylic-style panel:
 *
 *  ┌──────────────────────────────┐
 *  │ 辛温解表        (tap: 完整路径) │
 *  │ 麻黄汤                         │
 *  │ 组成：麻黄、桂枝、杏仁、炙甘草。 │ ← 可上下滚动
 *  │ 功效：发汗解表，宣肺平喘。       │
 *  │ ◀ 上一张      3 / 24     下一张 ▶│
 *  └──────────────────────────────┘
 *
 * Shares "current card" progress with the app through DataStore; updates only
 * happen on user interaction (buttons / path toggle) or app progress changes.
 */
class CardWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 180.dp), // 4×3 最小
            DpSize(320.dp, 180.dp), // 5×3
            DpSize(320.dp, 250.dp), // 5×4
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
                    pathExpanded = settings.widgetPathExpanded,
                    accentColor = settings.accentColor
                )
            }
        }

        @OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
        internal suspend fun render(
            context: Context,
            size: DpSize,
            state: WidgetState
        ): android.widget.RemoteViews {
            val result = androidx.glance.appwidget.GlanceRemoteViews()
                .compose(context, size) { CardWidgetContent(state) }
            return result.remoteViews
        }
    }
}

@Composable
private fun CardWidgetContent(state: WidgetState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val colors = GlanceTheme.colors
    val card = state.card

    val dark = (context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // Accent color follows the app setting (GlanceTheme colors are system-only).
    val primaryColor: ColorProvider = when (state.accentColor) {
        com.example.knowledgecards.domain.AccentColor.SYSTEM -> colors.primary
        com.example.knowledgecards.domain.AccentColor.GREEN -> ColorProvider(
            if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        )
        com.example.knowledgecards.domain.AccentColor.BLUE -> ColorProvider(
            if (dark) Color(0xFFAAC7FF) else Color(0xFF1565C0)
        )
        com.example.knowledgecards.domain.AccentColor.ORANGE -> ColorProvider(
            if (dark) Color(0xFFFFB68C) else Color(0xFFE65100)
        )
        com.example.knowledgecards.domain.AccentColor.PURPLE -> ColorProvider(
            if (dark) Color(0xFFD7B8FF) else Color(0xFF6A1B9A)
        )
        com.example.knowledgecards.domain.AccentColor.SAGE -> ColorProvider(
            if (dark) Color(0xFFA9BFAE) else Color(0xFF6B7F6E)
        )
        com.example.knowledgecards.domain.AccentColor.DUSTY_BLUE -> ColorProvider(
            if (dark) Color(0xFFA5C2D2) else Color(0xFF6E8A99)
        )
        com.example.knowledgecards.domain.AccentColor.TERRACOTTA -> ColorProvider(
            if (dark) Color(0xFFE0B49F) else Color(0xFFB07B68)
        )
    }

    // Acrylic-style translucent panel (stable, no blur needed).
    val panelColor = if (dark) {
        Color(0x8C1C1C22) // ~55% black panel in dark mode
    } else {
        Color(0xD9F4F4EF) // ~85% light panel in light mode
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(panelColor))
            .cornerRadius(22.dp)
            .padding(horizontal = 16.dp)
            .padding(vertical = 12.dp)
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

        // 1) Path bar — last segment, tap toggles the full path.
        val pathText = if (state.pathExpanded) {
            card.path.ifEmpty { "未分类" }
        } else {
            CategoryTree.lastSegment(card.path)
        }
        Text(
            text = pathText,
            maxLines = if (state.pathExpanded) 2 else 1,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = primaryColor
            ),
            modifier = GlanceModifier.clickable(
                CardWidgetActions.broadcastAction(context, CardWidgetActions.ACTION_TOGGLE_PATH)
            )
        )

        Spacer(GlanceModifier.fillMaxWidth().height(4.dp))

        // 2) Title.
        Text(
            text = card.title,
            maxLines = 2,
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            ),
            modifier = GlanceModifier.clickable(
                CardWidgetActions.openCardAction(context, card.id)
            )
        )

        Spacer(GlanceModifier.fillMaxWidth().height(6.dp))

        // 3) Scrollable body — Glance LazyColumn provides widget-internal
        //    scrolling for long card text (equivalent of a ScrollView).
        //    A concrete height is required (ListView can't use weights).
        LazyColumn(
            // match_parent in the column layout: ListView fills the remaining
            // space so the paging row always sits at the bottom edge.
            modifier = GlanceModifier.fillMaxWidth().fillMaxHeight()
        ) {
            items(listOf(card.content)) { body ->
                Text(
                    text = body,
                    style = TextStyle(fontSize = 18.sp, color = colors.onSurface),
                    modifier = GlanceModifier.clickable(
                        CardWidgetActions.openCardAction(context, card.id)
                    )
                )
            }
        }

        Spacer(GlanceModifier.fillMaxWidth().height(6.dp))

        // 4) Paging controls — prev flush left, counter centered, next flush
        //    right (widgets don't support swipe gestures, so flipping cards
        //    uses explicit buttons).
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val prevEnabled = state.index > 0
            val nextEnabled = state.index < state.total - 1
            val disabledColor = ColorProvider(Color.White.copy(alpha = 0.45f))
            val prevTextColor = if (prevEnabled) primaryColor else disabledColor
            val nextTextColor = if (nextEnabled) primaryColor else disabledColor
            val prevAction = CardWidgetActions.broadcastAction(
                context, CardWidgetActions.ACTION_PREV
            )
            val nextAction = CardWidgetActions.broadcastAction(
                context, CardWidgetActions.ACTION_NEXT
            )
            Text(
                text = "◀ 上一张",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = prevTextColor
                ),
                modifier = if (prevEnabled) {
                    GlanceModifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clickable(prevAction)
                } else {
                    GlanceModifier.padding(horizontal = 6.dp, vertical = 2.dp)
                }
            )
            Spacer(GlanceModifier.fillMaxWidth().defaultWeight())
            Text(
                text = "${state.index + 1} / ${state.total}",
                style = TextStyle(fontSize = 16.sp, color = colors.onSurface)
            )
            Spacer(GlanceModifier.fillMaxWidth().defaultWeight())
            Text(
                text = "下一张 ▶",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = nextTextColor
                ),
                modifier = if (nextEnabled) {
                    GlanceModifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clickable(nextAction)
                } else {
                    GlanceModifier.padding(horizontal = 6.dp, vertical = 2.dp)
                }
            )
        }
    }
}
