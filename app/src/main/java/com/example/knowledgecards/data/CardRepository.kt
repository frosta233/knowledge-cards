package com.example.knowledgecards.data

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the library storage (books + their cards) so that import
 * logic and the category tree can be unit-tested with an in-memory fake.
 *
 * Every card query is scoped to one book; [bookId] `0` means "no book"
 * (empty shelf) and simply returns nothing.
 */
interface CardRepository {
    fun observeCards(bookId: Long, sortMode: SortMode): Flow<List<Card>>
    suspend fun getCards(bookId: Long, sortMode: SortMode): List<Card>
    suspend fun getById(id: Long): Card?
    suspend fun findByPathAndTitle(bookId: Long, path: String, title: String): Card?
    suspend fun maxSortOrder(bookId: Long): Int
    suspend fun count(bookId: Long): Int
    suspend fun insert(card: Card): Long
    suspend fun update(card: Card)
    suspend fun delete(id: Long)

    // ---- Books ----

    fun observeBooks(): Flow<List<Book>>
    fun observeBookStats(): Flow<List<BookStats>>
    suspend fun getBooks(): List<Book>
    suspend fun getBook(id: Long): Book?
    suspend fun findBookByName(name: String): Book?

    /** Creates a book at the end of the shelf and returns its id. */
    suspend fun createBook(name: String): Long

    suspend fun renameBook(id: Long, name: String)

    /** Deletes a book together with all of its cards and category orders. */
    suspend fun deleteBook(id: Long)

    // ---- Category ordering (per book) ----

    fun observeCategoryOrders(bookId: Long): Flow<List<CategoryOrder>>
    suspend fun getCategoryOrders(bookId: Long): List<CategoryOrder>
    suspend fun setCategoryOrder(bookId: Long, path: String, position: Int)
    suspend fun removeCategoryOrder(bookId: Long, path: String)

    /** Renames one category level, moving all descendant cards with it. */
    suspend fun renameCategory(bookId: Long, oldPrefix: String, newPrefix: String)

    /** Moves every card under [path] (including descendants) to 未分类. */
    suspend fun clearCategory(bookId: Long, path: String)

    /** Deletes every card under [path] (including descendants). */
    suspend fun deleteCategory(bookId: Long, path: String)
}

enum class SortMode { DIRECTORY, TITLE, IMPORT }

class RoomCardRepository(
    private val dao: CardDao,
    private val bookDao: BookDao
) : CardRepository {

    override fun observeCards(bookId: Long, sortMode: SortMode): Flow<List<Card>> =
        when (sortMode) {
            SortMode.DIRECTORY, SortMode.TITLE -> dao.observeAllByTitle(bookId)
            SortMode.IMPORT -> dao.observeAllByImportOrder(bookId)
        }

    override suspend fun getCards(bookId: Long, sortMode: SortMode): List<Card> =
        when (sortMode) {
            SortMode.DIRECTORY, SortMode.TITLE -> dao.getAllByTitle(bookId)
            SortMode.IMPORT -> dao.getAllByImportOrder(bookId)
        }

    override suspend fun getById(id: Long): Card? = dao.getById(id)
    override suspend fun findByPathAndTitle(bookId: Long, path: String, title: String): Card? =
        dao.findByPathAndTitle(bookId, path, title)

    override suspend fun maxSortOrder(bookId: Long): Int = dao.maxSortOrder(bookId)
    override suspend fun count(bookId: Long): Int = dao.count(bookId)
    override suspend fun insert(card: Card): Long = dao.insert(card)
    override suspend fun update(card: Card) = dao.update(card)
    override suspend fun delete(id: Long) = dao.deleteById(id)

    // ---- Books ----

    override fun observeBooks(): Flow<List<Book>> = bookDao.observeBooks()

    override fun observeBookStats(): Flow<List<BookStats>> = bookDao.observeBookStats()

    override suspend fun getBooks(): List<Book> = bookDao.getBooks()

    override suspend fun getBook(id: Long): Book? = bookDao.getById(id)

    override suspend fun findBookByName(name: String): Book? = bookDao.findByName(name)

    override suspend fun createBook(name: String): Long =
        bookDao.insert(Book(name = name, sortOrder = bookDao.maxSortOrder() + 1))

    override suspend fun renameBook(id: Long, name: String) {
        val book = bookDao.getById(id) ?: return
        bookDao.update(book.copy(name = name))
    }

    override suspend fun deleteBook(id: Long) = bookDao.deleteBookCascade(id)

    // ---- Category ordering ----

    override fun observeCategoryOrders(bookId: Long): Flow<List<CategoryOrder>> =
        dao.observeCategoryOrders(bookId)

    override suspend fun getCategoryOrders(bookId: Long): List<CategoryOrder> =
        dao.getAllCategoryOrders(bookId)

    override suspend fun setCategoryOrder(bookId: Long, path: String, position: Int) =
        dao.upsertCategoryOrder(CategoryOrder(bookId, path, position))

    override suspend fun removeCategoryOrder(bookId: Long, path: String) =
        dao.deleteCategoryOrder(bookId, path)

    override suspend fun renameCategory(bookId: Long, oldPrefix: String, newPrefix: String) {
        dao.renamePathPrefix(bookId, oldPrefix, newPrefix, oldPrefix.length)
        val orders = dao.getAllCategoryOrders(bookId)
        for (order in orders) {
            if (order.path == oldPrefix || order.path.startsWith("$oldPrefix/")) {
                dao.deleteCategoryOrder(bookId, order.path)
                dao.upsertCategoryOrder(
                    order.copy(path = newPrefix + order.path.removePrefix(oldPrefix))
                )
            }
        }
    }

    override suspend fun clearCategory(bookId: Long, path: String) {
        dao.clearPath(bookId, path)
        dao.deleteCategoryOrder(bookId, path)
    }

    override suspend fun deleteCategory(bookId: Long, path: String) {
        dao.deleteByPath(bookId, path)
        dao.deleteCategoryOrder(bookId, path)
    }
}
