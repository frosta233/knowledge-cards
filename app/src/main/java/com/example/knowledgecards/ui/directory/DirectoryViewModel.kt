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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DirectoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as KnowledgeCardsApp
    private val repository = app.container.cardRepository

    val tree: StateFlow<List<CategoryNode>> =
        combine(
            repository.observeCards(SortMode.TITLE),
            repository.observeCategoryOrders()
        ) { cards, orders ->
            CategoryTree.build(cards, orders.associate { it.path to it.position })
        }
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

    /** Moves [fullPath] up/down among its siblings and persists the order. */
    fun moveCategory(fullPath: String, delta: Int) {
        val roots = tree.value
        val siblings = findSiblings(roots, fullPath) ?: return
        val index = siblings.indexOfFirst { it.fullPath == fullPath }
        if (index < 0) return
        val target = index + delta
        if (target < 0 || target >= siblings.size) return
        val reordered = siblings.toMutableList().apply {
            add(target, removeAt(index))
        }
        viewModelScope.launch {
            reordered.forEachIndexed { position, node ->
                repository.setCategoryOrder(node.fullPath, position)
            }
        }
    }

    private fun findSiblings(
        roots: List<CategoryNode>,
        fullPath: String
    ): List<CategoryNode>? {
        for (node in roots) {
            if (node.fullPath == fullPath) return roots
            val inChildren = findSiblings(node.children, fullPath)
            if (inChildren != null) return inChildren
        }
        return null
    }

    /** Renames one category level; all descendant cards move with it. */
    fun renameCategory(fullPath: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty() || clean.contains('/')) return
        val parent = fullPath.substringBeforeLast('/', "")
        val newPath = if (parent.isEmpty()) clean else "$parent/$clean"
        if (newPath == fullPath) return
        viewModelScope.launch {
            repository.renameCategory(fullPath, newPath)
        }
    }

    /** Deletes a category: either its cards move to 未分类 or are removed. */
    fun deleteCategory(fullPath: String, deleteCards: Boolean) {
        viewModelScope.launch {
            if (deleteCards) {
                repository.deleteCategory(fullPath)
            } else {
                repository.clearCategory(fullPath)
            }
        }
    }
}
