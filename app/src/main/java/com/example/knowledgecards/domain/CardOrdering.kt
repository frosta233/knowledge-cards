package com.example.knowledgecards.domain

import com.example.knowledgecards.data.Card
import com.example.knowledgecards.data.CardRepository
import com.example.knowledgecards.data.SortMode

/**
 * All cards in the exact browse order for [sortMode], shared by the browse
 * screen and the widget so both always agree on the sequence.
 *
 * TITLE / IMPORT are expressible in SQL; DIRECTORY is not — it flattens the
 * category tree depth-first with manual sibling order applied (see
 * [CategoryTree]), which is what the browse screen shows.
 */
suspend fun CardRepository.cardsInBrowseOrder(sortMode: SortMode): List<Card> {
    val all = getCards(sortMode)
    if (sortMode != SortMode.DIRECTORY) return all
    val orders = getCategoryOrders().associate { it.path to it.position }
    return CategoryTree.orderCardsByTree(all, orders)
}
