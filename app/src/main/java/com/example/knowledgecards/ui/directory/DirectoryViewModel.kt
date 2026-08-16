package com.example.knowledgecards.ui.directory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.knowledgecards.KnowledgeCardsApp
import com.example.knowledgecards.data.SortMode
import com.example.knowledgecards.domain.CategoryNode
import com.example.knowledgecards.domain.CategoryTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DirectoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as KnowledgeCardsApp

    val tree: StateFlow<List<CategoryNode>> =
        app.container.cardRepository.observeCards(SortMode.TITLE)
            .map { cards -> CategoryTree.build(cards) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Full paths of expanded nodes; root level starts expanded. */
    val expanded: MutableStateFlow<Set<String>> =
        MutableStateFlow(emptySet())

    fun toggle(path: String) {
        expanded.value = expanded.value.takeIf { path in it }
            ?.minus(path)
            ?: expanded.value + path
    }

    fun expandRoots(roots: List<CategoryNode>) {
        if (expanded.value.isEmpty() && roots.isNotEmpty()) {
            expanded.value = roots.map { it.fullPath }.toSet()
        }
    }

    fun firstCardId(node: CategoryNode): Long? =
        CategoryTree.collectCardIds(node).firstOrNull()
}
