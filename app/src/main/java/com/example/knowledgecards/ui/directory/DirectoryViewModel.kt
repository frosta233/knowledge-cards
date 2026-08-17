package com.example.knowledgecards.ui.directory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.knowledgecards.KnowledgeCardsApp
import com.example.knowledgecards.data.SortMode
import com.example.knowledgecards.data.UNCATEGORIZED
import com.example.knowledgecards.domain.CategoryNode
import com.example.knowledgecards.domain.CategoryTree
import com.example.knowledgecards.domain.TreeCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A category whose name matches the search query. */
data class CategoryHit(
    val name: String,
    val fullPath: String,
    val totalCards: Int
)

/** Search results: matching categories and matching cards. */
data class SearchResult(
    val categories: List<CategoryHit> = emptyList(),
    val cards: List<TreeCard> = emptyList()
)

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

    // ---- Search ----

    val searchQuery: MutableStateFlow<String> = MutableStateFlow("")

    val searchResults: StateFlow<SearchResult> =
        combine(searchQuery, tree) { query, tree ->
            val q = query.trim()
            if (q.isEmpty()) return@combine SearchResult()
            val categories = mutableListOf<CategoryHit>()
            val cards = mutableListOf<TreeCard>()
            fun walk(nodes: List<CategoryNode>) {
                for (node in nodes) {
                    if (node.name.contains(q)) {
                        categories += CategoryHit(node.name, node.fullPath, node.totalCards)
                    }
                    cards += node.cards.filter { it.title.contains(q) }
                    walk(node.children)
                }
            }
            walk(tree)
            SearchResult(categories, cards)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResult())

    /** Card id to highlight in the tree (empty = none); set by search reveals. */
    val highlightCardId: MutableStateFlow<Long> = MutableStateFlow(0L)

    /** Category path to highlight in the tree ("" = none); set by search reveals. */
    val highlightCategoryPath: MutableStateFlow<String> = MutableStateFlow("")

    /** Category path the tree should scroll to ("" = none); consumed by UI. */
    val revealCategoryPath: MutableStateFlow<String> = MutableStateFlow("")

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    /** Clears the highlighted card/category (any tree interaction does this). */
    fun clearHighlight() {
        highlightCardId.value = 0L
        highlightCategoryPath.value = ""
    }

    /**
     * Reveals a matched category in the tree: expands its ancestors and its
     * whole subtree, so it displays exactly like the tree itself, and asks
     * the UI to scroll to it.
     */
    fun revealCategory(fullPath: String) {
        expanded.value = expanded.value + ancestorsOf(fullPath)
        expandSubtree(tree.value, fullPath)
        highlightCardId.value = 0L
        highlightCategoryPath.value = fullPath
        revealCategoryPath.value = fullPath
        closeSearch()
    }

    fun consumeRevealCategory() {
        revealCategoryPath.value = ""
    }

    /**
     * Reveals a matched card in the tree: expands the card's direct parent
     * (ancestors + whole parent subtree) and highlights the card row.
     */
    fun revealCard(card: TreeCard) {
        val parentPath = card.path.ifEmpty { UNCATEGORIZED }
        expanded.value = expanded.value + ancestorsOf(parentPath)
        expandSubtree(tree.value, parentPath)
        highlightCategoryPath.value = ""
        highlightCardId.value = card.id
        closeSearch()
    }

    fun closeSearch() {
        searchQuery.value = ""
    }

    private fun ancestorsOf(path: String): List<String> {
        val result = mutableListOf<String>()
        var acc = ""
        for (part in path.split('/').filter { it.isNotBlank() }) {
            acc = if (acc.isEmpty()) part else "$acc/$part"
            result += acc
        }
        return result
    }

    private fun expandSubtree(roots: List<CategoryNode>, path: String) {
        val node = findNode(roots, path) ?: return
        val toExpand = mutableListOf<String>()
        fun collect(n: CategoryNode) {
            toExpand += n.fullPath
            n.children.forEach { collect(it) }
        }
        collect(node)
        expanded.value = expanded.value + toExpand
    }

    private fun findNode(nodes: List<CategoryNode>, path: String): CategoryNode? {
        for (node in nodes) {
            if (node.fullPath == path) return node
            findNode(node.children, path)?.let { return it }
        }
        return null
    }

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
