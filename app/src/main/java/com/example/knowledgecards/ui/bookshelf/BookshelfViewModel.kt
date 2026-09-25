package com.example.knowledgecards.ui.bookshelf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.knowledgecards.KnowledgeCardsApp
import com.example.knowledgecards.data.effectiveBookId
import com.example.knowledgecards.widget.WidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One book on the shelf, with the counts shown under its name. */
data class BookItem(
    val id: Long,
    val name: String,
    val cardCount: Int,
    val categoryCount: Int,
    val createdAt: Long
)

data class BookshelfUiState(
    val books: List<BookItem> = emptyList(),
    /** Book the rest of the app is currently showing (0 = none). */
    val currentBookId: Long = 0L
) {
    val totalCards: Int get() = books.sumOf { it.cardCount }
}

class BookshelfViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as KnowledgeCardsApp
    private val repository = app.container.cardRepository
    private val store = app.container.progressStore

    val uiState: StateFlow<BookshelfUiState> =
        combine(
            repository.observeBooks(),
            repository.observeBookStats(),
            store.settings
        ) { books, stats, settings ->
            val byBook = stats.associateBy { it.bookId }
            BookshelfUiState(
                books = books.map { book ->
                    BookItem(
                        id = book.id,
                        name = book.name,
                        cardCount = byBook[book.id]?.cardCount ?: 0,
                        categoryCount = byBook[book.id]?.categoryCount ?: 0,
                        createdAt = book.createdAt
                    )
                },
                currentBookId = effectiveBookId(settings.currentBookId, books)
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookshelfUiState())

    /** Makes [id] the book shown by 目录 / 闪卡 / 微件. */
    fun selectBook(id: Long) {
        viewModelScope.launch {
            store.setCurrentBookId(id)
            WidgetUpdater.update(getApplication())
        }
    }

    fun renameBook(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.renameBook(id, clean)
            WidgetUpdater.update(getApplication())
        }
    }

    /** Removes the book with all of its cards (irreversible). */
    fun deleteBook(id: Long) {
        viewModelScope.launch {
            repository.deleteBook(id)
            WidgetUpdater.update(getApplication())
        }
    }
}
