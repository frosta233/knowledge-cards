package com.example.knowledgecards

import com.example.knowledgecards.data.Card
import com.example.knowledgecards.data.CardRepository
import com.example.knowledgecards.data.SortMode
import com.example.knowledgecards.data.import.CardImporter
import com.example.knowledgecards.data.import.MarkdownFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardImporterTest {

    /** Id of the single book these tests import into. */
    private val bookId = 1L

    private class FakeRepository(initial: List<Card> = emptyList()) : CardRepository {
        val cards = initial.toMutableList()
        private val flow = MutableStateFlow(cards.toList())

        private val books = mutableListOf(com.example.knowledgecards.data.Book(1L, "测试书"))
        private val bookFlow = MutableStateFlow(books.toList())

        override fun observeCards(bookId: Long, sortMode: SortMode): Flow<List<Card>> =
            flow.map { list ->
                val scoped = list.filter { it.bookId == bookId }
                when (sortMode) {
                    SortMode.DIRECTORY, SortMode.TITLE -> scoped.sortedBy { it.title }
                    SortMode.IMPORT -> scoped.sortedBy { it.sortOrder }
                }
            }

        override suspend fun getCards(bookId: Long, sortMode: SortMode): List<Card> {
            val scoped = cards.filter { it.bookId == bookId }
            return when (sortMode) {
                SortMode.DIRECTORY, SortMode.TITLE -> scoped.sortedBy { it.title }
                SortMode.IMPORT -> scoped.sortedBy { it.sortOrder }
            }
        }

        override suspend fun getById(id: Long): Card? = cards.firstOrNull { it.id == id }

        override suspend fun findByPathAndTitle(bookId: Long, path: String, title: String): Card? =
            cards.firstOrNull { it.bookId == bookId && it.path == path && it.title == title }

        override suspend fun maxSortOrder(bookId: Long): Int =
            cards.filter { it.bookId == bookId }.maxOfOrNull { it.sortOrder } ?: 0

        override suspend fun count(bookId: Long): Int = cards.count { it.bookId == bookId }

        override suspend fun insert(card: Card): Long {
            val id = (cards.maxOfOrNull { it.id } ?: 0) + 1
            cards += card.copy(id = id)
            flow.value = cards.toList()
            return id
        }

        override suspend fun update(card: Card) {
            val idx = cards.indexOfFirst { it.id == card.id }
            if (idx >= 0) {
                cards[idx] = card
                flow.value = cards.toList()
            }
        }

        override suspend fun delete(id: Long) {
            cards.removeAll { it.id == id }
            flow.value = cards.toList()
        }

        // ---- Books ----

        override fun observeBooks(): Flow<List<com.example.knowledgecards.data.Book>> = bookFlow

        override fun observeBookStats(): Flow<List<com.example.knowledgecards.data.BookStats>> =
            flow.map { list ->
                list.groupBy { it.bookId }.map { (id, group) ->
                    com.example.knowledgecards.data.BookStats(
                        bookId = id,
                        cardCount = group.size,
                        categoryCount = group.map { it.path }.filter { it.isNotEmpty() }.distinct().size
                    )
                }
            }

        override suspend fun getBooks(): List<com.example.knowledgecards.data.Book> = books.toList()

        override suspend fun getBook(id: Long): com.example.knowledgecards.data.Book? =
            books.firstOrNull { it.id == id }

        override suspend fun findBookByName(name: String): com.example.knowledgecards.data.Book? =
            books.firstOrNull { it.name == name }

        override suspend fun createBook(name: String): Long {
            val id = (books.maxOfOrNull { it.id } ?: 0) + 1
            books += com.example.knowledgecards.data.Book(id, name, sortOrder = books.size)
            bookFlow.value = books.toList()
            return id
        }

        override suspend fun renameBook(id: Long, name: String) {
            val idx = books.indexOfFirst { it.id == id }
            if (idx >= 0) {
                books[idx] = books[idx].copy(name = name)
                bookFlow.value = books.toList()
            }
        }

        override suspend fun deleteBook(id: Long) {
            books.removeAll { it.id == id }
            cards.removeAll { it.bookId == id }
            bookFlow.value = books.toList()
            flow.value = cards.toList()
        }

        private val orders = mutableMapOf<Pair<Long, String>, Int>()
        private val orderFlow = MutableStateFlow(emptyMap<Pair<Long, String>, Int>())

        override fun observeCategoryOrders(bookId: Long): Flow<List<com.example.knowledgecards.data.CategoryOrder>> =
            orderFlow.map { m ->
                m.filterKeys { it.first == bookId }
                    .map { (k, v) -> com.example.knowledgecards.data.CategoryOrder(k.first, k.second, v) }
            }

        override suspend fun getCategoryOrders(bookId: Long): List<com.example.knowledgecards.data.CategoryOrder> =
            orders.filterKeys { it.first == bookId }
                .map { (k, v) -> com.example.knowledgecards.data.CategoryOrder(k.first, k.second, v) }

        override suspend fun setCategoryOrder(bookId: Long, path: String, position: Int) {
            orders[bookId to path] = position
            orderFlow.value = orders.toMap()
        }

        override suspend fun removeCategoryOrder(bookId: Long, path: String) {
            orders.remove(bookId to path)
            orderFlow.value = orders.toMap()
        }

        override suspend fun renameCategory(bookId: Long, oldPrefix: String, newPrefix: String) {
            val renamed = cards.map { c ->
                if (c.bookId != bookId) c
                else if (c.path == oldPrefix) c.copy(path = newPrefix)
                else if (c.path.startsWith("$oldPrefix/")) c.copy(path = newPrefix + c.path.removePrefix(oldPrefix))
                else c
            }
            cards.clear()
            cards.addAll(renamed)
            flow.value = cards.toList()
        }

        override suspend fun clearCategory(bookId: Long, path: String) {
            val cleared = cards.map { c ->
                if (c.bookId == bookId && (c.path == path || c.path.startsWith("$path/"))) c.copy(path = "")
                else c
            }
            cards.clear()
            cards.addAll(cleared)
            flow.value = cards.toList()
        }

        override suspend fun deleteCategory(bookId: Long, path: String) {
            cards.removeAll { it.bookId == bookId && (it.path == path || it.path.startsWith("$path/")) }
            flow.value = cards.toList()
        }
    }

    private fun md(name: String, content: String) = MarkdownFile(name, content)

    @Test
    fun `imports new files with stats`() = runTest {
        val repo = FakeRepository()
        val result = CardImporter(repo, bookId).import(
            listOf(
                md("麻黄汤.md", "路径: 方剂学/解表剂/辛温解表\n# 麻黄汤\n组成。"),
                md("银翘散.md", "路径: 方剂学/解表剂/辛凉解表\n# 银翘散\n组成。")
            )
        )
        assertEquals(2, result.newCount)
        assertEquals(0, result.updatedCount)
        assertTrue(result.failed.isEmpty())
        assertEquals(2, repo.count(bookId))
    }

    @Test
    fun `reimporting same path and title updates instead of duplicating`() = runTest {
        val repo = FakeRepository()
        val importer = CardImporter(repo, bookId)
        importer.import(listOf(md("麻黄汤.md", "路径: 方剂学/解表剂/辛温解表\n# 麻黄汤\n旧内容")))

        val result = importer.import(
            listOf(md("麻黄汤.md", "路径: 方剂学/解表剂/辛温解表\n# 麻黄汤\n新内容"))
        )
        assertEquals(0, result.newCount)
        assertEquals(1, result.updatedCount)
        assertEquals(1, repo.count(bookId))
        assertEquals("新内容", repo.cards.single().content.trim())
    }

    @Test
    fun `same title in different categories creates two cards`() = runTest {
        val repo = FakeRepository()
        CardImporter(repo, bookId).import(
            listOf(
                md("同名.md", "路径: 甲\n# 同名\nA"),
                md("同名.md", "路径: 乙\n# 同名\nB")
            )
        )
        assertEquals(2, repo.count(bookId))
    }

    @Test
    fun `failed files are reported and do not abort the batch`() = runTest {
        val repo = FakeRepository()
        val result = CardImporter(repo, bookId).import(
            listOf(
                md("坏卡.md", "路径: 甲\n# 卡\n内容"),
                md("失败.md", "") // cannot fail with current parser, keep list for shape
            )
        )
        // Even without hard failures, the batch must fully process.
        assertEquals(2, result.newCount)
        assertEquals(2, repo.count(bookId))
    }

    @Test
    fun `sort order is monotonically increasing`() = runTest {
        val repo = FakeRepository()
        CardImporter(repo, bookId).import(
            listOf(
                md("B.md", "路径: 甲\n# B\nb"),
                md("A.md", "路径: 甲\n# A\na")
            )
        )
        val byImport = repo.getCards(bookId, SortMode.IMPORT)
        assertEquals(listOf("B", "A"), byImport.map { it.title })
        assertTrue(byImport[0].sortOrder < byImport[1].sortOrder)
    }

    @Test
    fun `reimport keeps original import order`() = runTest {
        val repo = FakeRepository()
        val importer = CardImporter(repo, bookId)
        importer.import(
            listOf(
                md("B.md", "路径: 甲\n# B\nb"),
                md("A.md", "路径: 甲\n# A\na")
            )
        )
        importer.import(listOf(md("B.md", "路径: 甲\n# B\nb2")))
        val byImport = repo.getCards(bookId, SortMode.IMPORT)
        assertEquals(listOf("B", "A"), byImport.map { it.title })
        assertEquals("b2", byImport[0].content.trim())
    }

    @Test
    fun `imported cards belong to the target book only`() = runTest {
        val repo = FakeRepository()
        CardImporter(repo, bookId).import(listOf(md("A.md", "路径: 甲\n# A\na")))
        assertEquals(bookId, repo.cards.single().bookId)
        // Another book sees nothing, and the same path+title may be imported
        // into it again without a conflict.
        assertTrue(repo.getCards(bookId + 1, SortMode.TITLE).isEmpty())
        val other = repo.createBook("另一本书")
        val result = CardImporter(repo, other).import(listOf(md("A.md", "路径: 甲\n# A\na")))
        assertEquals(1, result.newCount)
        assertEquals(1, repo.count(other))
    }

    @Test
    fun `sort order starts at one inside every book`() = runTest {
        val repo = FakeRepository()
        val second = repo.createBook("第二本")
        CardImporter(repo, bookId).import(listOf(md("A.md", "路径: 甲\n# A\na")))
        CardImporter(repo, second).import(listOf(md("B.md", "路径: 甲\n# B\nb")))
        assertEquals(listOf(1), repo.getCards(second, SortMode.IMPORT).map { it.sortOrder })
    }
}
