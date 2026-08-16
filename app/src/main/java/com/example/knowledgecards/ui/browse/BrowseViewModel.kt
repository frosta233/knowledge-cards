package com.example.knowledgecards.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.knowledgecards.KnowledgeCardsApp
import com.example.knowledgecards.data.Card
import com.example.knowledgecards.data.CardRepository
import com.example.knowledgecards.data.SortMode
import com.example.knowledgecards.domain.ProgressStore
import com.example.knowledgecards.widget.WidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private var lastSavedCardId = 0L

    val uiState: StateFlow<BrowseUiState> = store.settings
        .flatMapLatest { settings ->
            repository.observeCards(settings.sortMode)
                .map { cards -> BrowseUiState(cards, settings.fontSizeSp, settings.sortMode) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState())

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
