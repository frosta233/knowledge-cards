package com.example.knowledgecards.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.example.knowledgecards.KnowledgeCardsApp
import com.example.knowledgecards.data.Card
import com.example.knowledgecards.data.SortMode
import com.example.knowledgecards.domain.CategoryTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditorViewModel(
    application: Application,
    private val cardId: Long
) : AndroidViewModel(application) {

    private val app = application as KnowledgeCardsApp
    private val repository = app.container.cardRepository

    val title = MutableStateFlow("")
    val content = MutableStateFlow("")
    val path = MutableStateFlow("")

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    /** Existing categories for the path picker (path must be picked, not typed). */
    val categories: StateFlow<List<String>> =
        repository.observeCards(SortMode.TITLE)
            .map { cards -> cards.map { it.path }.filter { it.isNotEmpty() }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val card = repository.getById(cardId)
            if (card != null) {
                title.value = card.title
                content.value = card.content
                path.value = card.path
            } else {
                _saved.value = true // nothing to edit
            }
        }
    }

    fun setTitle(value: String) {
        title.value = value
    }

    fun setContent(value: String) {
        content.value = value
    }

    fun setPath(value: String) {
        path.value = value
    }

    fun save() {
        viewModelScope.launch {
            val existing = repository.getById(cardId) ?: return@launch
            repository.update(
                existing.copy(
                    title = title.value.trim().ifEmpty { existing.title },
                    content = content.value,
                    path = path.value.trim().trim('/'),
                    updatedAt = System.currentTimeMillis()
                )
            )
            _saved.value = true
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.delete(cardId)
            _deleted.value = true
        }
    }

    companion object {
        fun factory(cardId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EditorViewModel(
                    application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        ?: error("Application missing"),
                    cardId = cardId
                )
            }
        }
    }
}
