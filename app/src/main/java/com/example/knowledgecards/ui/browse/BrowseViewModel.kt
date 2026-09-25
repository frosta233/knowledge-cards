package com.example.knowledgecards.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.knowledgecards.KnowledgeCardsApp
import com.example.knowledgecards.data.Card
import com.example.knowledgecards.data.CardRepository
import com.example.knowledgecards.data.CategoryOrder
import com.example.knowledgecards.data.SortMode
import com.example.knowledgecards.data.UNCATEGORIZED
import com.example.knowledgecards.data.effectiveBookId
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BrowseUiState(
    val cards: List<Card> = emptyList(),
    val fontSizeSp: Float = 18f,
    val sortMode: SortMode = SortMode.TITLE,
    /** Selected book (0 = empty shelf); the browse list never crosses books. */
    val bookId: Long = 0L,
    val bookName: String = "",
    /** Active category scope inside the book; empty = the whole book. */
    val scopePath: String = ""
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

    /** Browse scope inside the selected book: empty = all of its cards. */
    private val _scopePath = MutableStateFlow("")

    private var lastSavedCardId = 0L

    val uiState: StateFlow<BrowseUiState> =
        combine(
            store.settings,
            repository.observeBooks(),
            _scopePath
        ) { settings, books, scope ->
            Triple(settings, books, scope)
        }
            .flatMapLatest { (settings, books, requestedScope) ->
                val bookId = effectiveBookId(settings.currentBookId, books)
                combine(
                    repository.observeCards(bookId, settings.sortMode),
                    repository.observeCategoryOrders(bookId)
                ) { all, orders ->
                    // A scope inherited from another book simply does not match
                    // anything here; fall back to the whole book in that case.
                    var scoped = filterByScope(all, requestedScope)
                    var scope = requestedScope
                    if (scope.isNotEmpty() && scoped.isEmpty()) {
                        scoped = all
                        scope = ""
                    }
                    val cards = if (settings.sortMode == SortMode.DIRECTORY) {
                        orderByTree(scoped, orders)
                    } else {
                        scoped
                    }
                    BrowseUiState(
                        cards = cards,
                        fontSizeSp = settings.fontSizeSp,
                        sortMode = settings.sortMode,
                        bookId = bookId,
                        bookName = books.firstOrNull { it.id == bookId }?.name.orEmpty(),
                        scopePath = scope
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState())

    private fun filterByScope(all: List<Card>, scope: String): List<Card> = when {
        scope.isEmpty() -> all
        // The 未分类 root node is a display name; its cards have an empty path.
        scope == UNCATEGORIZED -> all.filter { it.path.isEmpty() }
        else -> all.filter { isPathWithin(scope, it.path) }
    }

    /** Orders cards by the category tree (manual order, then name), depth-first. */
    private fun orderByTree(
        cards: List<Card>,
        orders: List<CategoryOrder>
    ): List<Card> =
        CategoryTree.orderCardsByTree(
            cards,
            orders.associate { it.path to it.position }
        )

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

    /**
     * Aligns with the shared progress when the browse screen returns to the
     * foreground (hot start / tab switch): if the widget flipped cards while
     * the app was in the background, jump to the card it selected. No-op when
     * the progress already matches [currentCardId] (the card on screen).
     */
    fun resumeToSharedProgress(currentCardId: Long) {
        viewModelScope.launch {
            val lastId = store.current().lastCardId
            if (lastId > 0L && lastId != currentCardId) {
                _pendingJump.value = lastId
            }
        }
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
