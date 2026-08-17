package com.example.knowledgecards.domain

import com.example.knowledgecards.data.Card
import com.example.knowledgecards.data.UNCATEGORIZED
import com.example.knowledgecards.data.isPathWithin

/** A leaf of the category tree: one card. */
data class TreeCard(
    val id: Long,
    val title: String,
    val path: String
)

/** A node of the multi-level category tree. */
data class CategoryNode(
    val name: String,
    val fullPath: String,
    val children: List<CategoryNode> = emptyList(),
    val cards: List<TreeCard> = emptyList()
) {
    val totalCards: Int get() = cards.size + children.sumOf { it.totalCards }
}

/**
 * Builds a multi-level category tree from cards, deriving categories purely
 * from each card's [Card.path]. Cards with an empty path are grouped under
 * the [UNCATEGORIZED] root pseudo-category.
 *
 * Children and cards are ordered by name so the tree is deterministic.
 */
object CategoryTree {

    /**
     * Builds the tree from cards, ordering sibling nodes by their manual
     * [orders] position when present (falling back to name order after all
     * manually ordered siblings).
     */
    fun build(cards: List<Card>, orders: Map<String, Int> = emptyMap()): List<CategoryNode> {
        data class MutableNode(
            val name: String,
            val fullPath: String,
            val children: MutableMap<String, MutableNode> = linkedMapOf(),
            val cards: MutableList<TreeCard> = mutableListOf()
        )

        val roots = linkedMapOf<String, MutableNode>()

        fun nodeFor(parts: List<String>): MutableNode {
            val first = parts.first()
            var node = roots.getOrPut(first) { MutableNode(first, first) }
            for (i in 1 until parts.size) {
                val segment = parts[i]
                val parentPath = node.fullPath
                node = node.children.getOrPut(segment) { MutableNode(segment, "$parentPath/$segment") }
            }
            return node
        }

        for (card in cards.sortedWith(compareBy({ it.path }, { it.title }))) {
            val parts = if (card.path.isBlank()) {
                listOf(UNCATEGORIZED)
            } else {
                card.path.split('/').map { it.trim() }.filter { it.isNotEmpty() }
            }
            nodeFor(parts).cards += TreeCard(card.id, card.title, card.path)
        }

        fun freeze(node: MutableNode): CategoryNode {
            val children = node.children.values
                .sortedWith(
                    compareBy<MutableNode>(
                        { orders[it.fullPath] ?: Int.MAX_VALUE },
                        { it.name },
                        { it.fullPath }
                    )
                )
                .map { freeze(it) }
            val cards = node.cards.sortedBy { it.title }
            return CategoryNode(node.name, node.fullPath, children, cards)
        }

        return roots.values
            .sortedWith(
                compareBy<MutableNode>(
                    { orders[it.fullPath] ?: Int.MAX_VALUE },
                    { it.name },
                    { it.fullPath }
                )
            )
            .map { freeze(it) }
    }

    /** All card ids under [node], including descendants, in display order. */
    fun collectCardIds(node: CategoryNode): List<Long> =
        node.cards.map { it.id } + node.children.flatMap { collectCardIds(it) }

    /** Depth-first card ids across the whole tree (tree display order). */
    fun flattenCardIds(nodes: List<CategoryNode>): List<Long> =
        nodes.flatMap { node ->
            node.cards.map { it.id } + flattenCardIds(node.children)
        }

    /**
     * Cards flattened in tree display order: depth-first, siblings ordered by
     * their manual position (then name). This is the single source of truth
     * for the DIRECTORY browse sequence, shared by the browse screen and the
     * widget so both always agree on the current card and its neighbours.
     */
    fun orderCardsByTree(cards: List<Card>, orders: Map<String, Int>): List<Card> {
        val ids = flattenCardIds(build(cards, orders))
        val byId = cards.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    /** All card ids under the category with [path], in display order. */
    fun collectCardIds(cards: List<Card>, path: String): List<Long> {
        val roots = build(cards)
        if (path.isEmpty()) return roots.flatMap { collectCardIds(it) }
        val node = findNode(roots, path) ?: return emptyList()
        return collectCardIds(node)
    }

    private fun findNode(nodes: List<CategoryNode>, path: String): CategoryNode? {
        for (node in nodes) {
            if (node.fullPath == path) return node
            findNode(node.children, path)?.let { return it }
        }
        return null
    }

    /**
     * Breadcrumb for the top bar: shows the first and last segment when the
     * path has more than two segments ("方剂学 / … / 辛温解表").
     */
    fun breadcrumb(path: String): String {
        if (path.isBlank()) return UNCATEGORIZED
        val parts = path.split('/').filter { it.isNotBlank() }
        return when {
            parts.size <= 2 -> parts.joinToString(" / ")
            else -> "${parts.first()} / … / ${parts.last()}"
        }
    }

    /** Last path segment, used by the widget path bar. */
    fun lastSegment(path: String): String =
        path.split('/').filter { it.isNotBlank() }.lastOrNull() ?: UNCATEGORIZED
}
