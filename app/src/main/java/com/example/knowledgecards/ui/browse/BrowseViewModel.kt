package com.example.knowledgecards.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.knowledgecards.KnowledgeCardsApp
import com.example.knowledgecards.data.Card
import com.example.knowledgecards.data.CardRepository
import com.example.knowledgecards.data.SortMode
import com.example.knowledgecards.data.UNCATEGORIZED
import com.example.knowledgecards.data.isPathWithin
import com.example.knowledgecards.domain.CategoryTree
import com.example.knowledgecards.domain.ProgressStore
import com.example.knowledgecards.widget.WidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BrowseUiState(
    val cards: List<Card> = emptyList(),
    val fontSizeSp: Float = 18f,
    val sortMode: SortMode = SortMode.TITLE
)

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as KnowledgeCardsApp
    private val repository: CardRepository = app.container.cardRepository
    private val store: ProgressStore = app.container.progressStore

    /**
     * Requests to jump to a specific card (from directory / widget). Kept as a
     * state instead of a one-shot flow so the request survives while the browse
     * screen is not composed (e.g. directory on top).
     */
    private val _pendingJump = MutableStateFlow(0L)
    val pendingJump: StateFlow<Long> = _pendingJump

    /** The card id to land on at startup (widget deep link); 0 = restore. */
    private val startCardId = MutableStateFlow(0L)

    /** Browse scope: empty = all cards, otherwise only cards under this path. */
    private val _scopePath = MutableStateFlow("")
    val scopePath: StateFlow<String> = _scopePath

    private var lastSavedCardId = 0L

    val uiState: StateFlow<BrowseUiState> =
        combine(store.settings, _scopePath, repository.observeCategoryOrders()) { s, scope, orders ->
            Triple(s, scope, orders)
        }
        .flatMapLatest { (settings, scope, orders) ->
            repository.observeCards(settings.sortMode)
                .map { all ->
                    val scoped = when {
                        scope.isEmpty() -> all
                        // The 未分类 root node is a display name; its cards
                        // have an empty path.
                        scope == UNCATEGORIZED -> all.filter { it.path.isEmpty() }
                        else -> all.filter { isPathWithin(scope, it.path) }
                    }
                    val cards = if (settings.sortMode == SortMode.DIRECTORY) {
                        orderByTree(scoped, orders)
                    } else {
                        scoped
                    }
                    BrowseUiState(cards, settings.fontSizeSp, settings.sortMode)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState())

    /** Orders cards by the category tree (manual order, then name), depth-first. */
    private fun orderByTree(
        cards: List<Card>,
        orders: List<com.example.knowledgecards.data.CategoryOrder>
    ): List<Card> {
        val orderMap = orders.associate { it.path to it.position }
        val roots = CategoryTree.build(cards, orderMap)
        val ids = CategoryTree.flattenCardIds(roots)
        val byId = cards.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    /**
     * Index to show on first composition: the requested start card, else a
     * pending jump (directory / widget), else the last browsed card restored
     * from the shared progress store.
     */
    suspend fun initialIndex(cards: List<Card>): Int {
        val target = when {
            startCardId.value > 0 -> startCardId.value
            _pendingJump.value > 0 -> _pendingJump.value
            else -> store.current().lastCardId
        }
        val index = cards.indexOfFirst { it.id == target }
        return if (index >= 0) index else 0
    }

    fun startAt(cardId: Long) {
        startCardId.value = cardId
    }

    fun jumpTo(cardId: Long) {
        _pendingJump.value = cardId
    }

    /** Selects a flashcard set (scope path) and jumps to [cardId] inside it. */
    fun setScope(path: String, cardId: Long) {
        _scopePath.value = path
        _pendingJump.value = cardId
    }

    fun clearScope() {
        _scopePath.value = ""
        _pendingJump.value = 0L
    }

    fun consumeJump() {
        _pendingJump.value = 0L
    }

    /** Called whenever a card becomes the visible page (settled or paused). */
    fun onCardShown(cardId: Long) {
        if (cardId == lastSavedCardId) return
        lastSavedCardId = cardId
        viewModelScope.launch {
            store.setLastCardId(cardId)
            // Keep the widget in sync with in-app progress changes.
            WidgetUpdater.update(getApplication())
        }
    }
}
