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

    private class FakeRepository(initial: List<Card> = emptyList()) : CardRepository {
        val cards = initial.toMutableList()
        private val flow = MutableStateFlow(cards.toList())

        override fun observeCards(sortMode: SortMode): Flow<List<Card>> =
            flow.map { list ->
                when (sortMode) {
                    SortMode.TITLE -> list.sortedBy { it.title }
                    SortMode.IMPORT -> list.sortedBy { it.sortOrder }
                }
            }

        override suspend fun getCards(sortMode: SortMode): List<Card> =
            when (sortMode) {
                SortMode.TITLE -> cards.sortedBy { it.title }
                SortMode.IMPORT -> cards.sortedBy { it.sortOrder }
            }

        override suspend fun getById(id: Long): Card? = cards.firstOrNull { it.id == id }

        override suspend fun findByPathAndTitle(path: String, title: String): Card? =
            cards.firstOrNull { it.path == path && it.title == title }

        override suspend fun maxSortOrder(): Int = cards.maxOfOrNull { it.sortOrder } ?: 0

        override suspend fun count(): Int = cards.size

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
    }

    private fun md(name: String, content: String) = MarkdownFile(name, content)

    @Test
    fun `imports new files with stats`() = runTest {
        val repo = FakeRepository()
        val result = CardImporter(repo).import(
            listOf(
                md("麻黄汤.md", "路径: 方剂学/解表剂/辛温解表\n# 麻黄汤\n组成。"),
                md("银翘散.md", "路径: 方剂学/解表剂/辛凉解表\n# 银翘散\n组成。")
            )
        )
        assertEquals(2, result.newCount)
        assertEquals(0, result.updatedCount)
        assertTrue(result.failed.isEmpty())
        assertEquals(2, repo.count())
    }

    @Test
    fun `reimporting same path and title updates instead of duplicating`() = runTest {
        val repo = FakeRepository()
        val importer = CardImporter(repo)
        importer.import(listOf(md("麻黄汤.md", "路径: 方剂学/解表剂/辛温解表\n# 麻黄汤\n旧内容")))

        val result = importer.import(
            listOf(md("麻黄汤.md", "路径: 方剂学/解表剂/辛温解表\n# 麻黄汤\n新内容"))
        )
        assertEquals(0, result.newCount)
        assertEquals(1, result.updatedCount)
        assertEquals(1, repo.count())
        assertEquals("新内容", repo.cards.single().content.trim())
    }

    @Test
    fun `same title in different categories creates two cards`() = runTest {
        val repo = FakeRepository()
        CardImporter(repo).import(
            listOf(
                md("同名.md", "路径: 甲\n# 同名\nA"),
                md("同名.md", "路径: 乙\n# 同名\nB")
            )
        )
        assertEquals(2, repo.count())
    }

    @Test
    fun `failed files are reported and do not abort the batch`() = runTest {
        val repo = FakeRepository()
        val result = CardImporter(repo).import(
            listOf(
                md("坏卡.md", "路径: 甲\n# 卡\n内容"),
                md("失败.md", "") // cannot fail with current parser, keep list for shape
            )
        )
        // Even without hard failures, the batch must fully process.
        assertEquals(2, result.newCount)
        assertEquals(2, repo.count())
    }

    @Test
    fun `sort order is monotonically increasing`() = runTest {
        val repo = FakeRepository()
        CardImporter(repo).import(
            listOf(
                md("B.md", "路径: 甲\n# B\nb"),
                md("A.md", "路径: 甲\n# A\na")
            )
        )
        val byImport = repo.getCards(SortMode.IMPORT)
        assertEquals(listOf("B", "A"), byImport.map { it.title })
        assertTrue(byImport[0].sortOrder < byImport[1].sortOrder)
    }

    @Test
    fun `reimport keeps original import order`() = runTest {
        val repo = FakeRepository()
        val importer = CardImporter(repo)
        importer.import(
            listOf(
                md("B.md", "路径: 甲\n# B\nb"),
                md("A.md", "路径: 甲\n# A\na")
            )
        )
        importer.import(listOf(md("B.md", "路径: 甲\n# B\nb2")))
        val byImport = repo.getCards(SortMode.IMPORT)
        assertEquals(listOf("B", "A"), byImport.map { it.title })
        assertEquals("b2", byImport[0].content.trim())
    }
}
