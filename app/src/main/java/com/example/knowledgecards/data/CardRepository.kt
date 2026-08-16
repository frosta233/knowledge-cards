package com.example.knowledgecards.data

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over card storage so that import logic and the category tree
 * can be unit-tested with an in-memory fake.
 */
interface CardRepository {
    fun observeCards(sortMode: SortMode): Flow<List<Card>>
    suspend fun getCards(sortMode: SortMode): List<Card>
    suspend fun getById(id: Long): Card?
    suspend fun findByPathAndTitle(path: String, title: String): Card?
    suspend fun maxSortOrder(): Int
    suspend fun count(): Int
    suspend fun insert(card: Card): Long
    suspend fun update(card: Card)
    suspend fun delete(id: Long)

    fun observeCategoryOrders(): Flow<List<CategoryOrder>>
    suspend fun getCategoryOrders(): List<CategoryOrder>
    suspend fun setCategoryOrder(path: String, position: Int)
    suspend fun removeCategoryOrder(path: String)

    /** Renames one category level, moving all descendant cards with it. */
    suspend fun renameCategory(oldPrefix: String, newPrefix: String)

    /** Moves every card under [path] (including descendants) to 未分类. */
    suspend fun clearCategory(path: String)

    /** Deletes every card under [path] (including descendants). */
    suspend fun deleteCategory(path: String)
}

enum class SortMode { TITLE, IMPORT }

class RoomCardRepository(private val dao: CardDao) : CardRepository {

    override fun observeCards(sortMode: SortMode): Flow<List<Card>> =
        when (sortMode) {
            SortMode.TITLE -> dao.observeAllByTitle()
            SortMode.IMPORT -> dao.observeAllByImportOrder()
        }

    override suspend fun getCards(sortMode: SortMode): List<Card> =
        when (sortMode) {
            SortMode.TITLE -> dao.getAllByTitle()
            SortMode.IMPORT -> dao.getAllByImportOrder()
        }

    override suspend fun getById(id: Long): Card? = dao.getById(id)
    override suspend fun findByPathAndTitle(path: String, title: String): Card? =
        dao.findByPathAndTitle(path, title)

    override suspend fun maxSortOrder(): Int = dao.maxSortOrder()
    override suspend fun count(): Int = dao.count()
    override suspend fun insert(card: Card): Long = dao.insert(card)
    override suspend fun update(card: Card) = dao.update(card)
    override suspend fun delete(id: Long) = dao.deleteById(id)

    override fun observeCategoryOrders(): Flow<List<CategoryOrder>> =
        dao.observeCategoryOrders()

    override suspend fun getCategoryOrders(): List<CategoryOrder> =
        dao.getAllCategoryOrders()

    override suspend fun setCategoryOrder(path: String, position: Int) =
        dao.upsertCategoryOrder(CategoryOrder(path, position))

    override suspend fun removeCategoryOrder(path: String) =
        dao.deleteCategoryOrder(path)

    override suspend fun renameCategory(oldPrefix: String, newPrefix: String) {
        dao.renamePathPrefix(oldPrefix, newPrefix, oldPrefix.length)
        val orders = dao.getAllCategoryOrders()
        for (order in orders) {
            if (order.path == oldPrefix || order.path.startsWith("$oldPrefix/")) {
                dao.deleteCategoryOrder(order.path)
                dao.upsertCategoryOrder(
                    order.copy(path = newPrefix + order.path.removePrefix(oldPrefix))
                )
            }
        }
    }

    override suspend fun clearCategory(path: String) {
        dao.clearPath(path)
        dao.deleteCategoryOrder(path)
    }

    override suspend fun deleteCategory(path: String) {
        dao.deleteByPath(path)
        dao.deleteCategoryOrder(path)
    }
}
