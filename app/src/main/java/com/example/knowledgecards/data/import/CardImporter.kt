package com.example.knowledgecards.data.import

import com.example.knowledgecards.data.Card
import com.example.knowledgecards.data.CardRepository
import com.example.knowledgecards.data.UNCATEGORIZED

/**
 * Imports parsed Markdown files into one book of the repository.
 *
 * Dedup rule: inside a book a card is identified by (path + title). If it
 * already exists the content is updated (no duplicate rows are ever created).
 * New cards receive a monotonically increasing [Card.sortOrder] so "import
 * order" stays stable.
 */
class CardImporter(
    private val repository: CardRepository,
    private val bookId: Long,
    private val parser: MarkdownParser = MarkdownParser
) {

    suspend fun import(files: List<MarkdownFile>): ImportResult {
        var newCount = 0
        var updatedCount = 0
        val failed = mutableListOf<Pair<String, String>>()
        var nextSortOrder = repository.maxSortOrder(bookId) + 1

        for (file in files) {
            try {
                val parsed = parser.parse(file)
                val existing = repository.findByPathAndTitle(bookId, parsed.path, parsed.title)
                val now = System.currentTimeMillis()
                if (existing != null) {
                    repository.update(
                        existing.copy(
                            content = parsed.content,
                            updatedAt = now
                        )
                    )
                    updatedCount++
                } else {
                    repository.insert(
                        Card(
                            bookId = bookId,
                            title = parsed.title,
                            content = parsed.content,
                            path = parsed.path,
                            sortOrder = nextSortOrder++,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    newCount++
                }
            } catch (e: Exception) {
                failed += file.fileName to (e.message ?: e.javaClass.simpleName)
            }
        }
        return ImportResult(newCount, updatedCount, failed)
    }

    /**
     * The display name of the root pseudo-category for cards with an empty
     * path; also the display label used for an empty path in the UI.
     */
    fun rootLabel(): String = UNCATEGORIZED
}
