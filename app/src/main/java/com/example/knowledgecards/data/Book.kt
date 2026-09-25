package com.example.knowledgecards.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One book on the shelf. Every archive / folder import creates (or reuses) a
 * book, and the directory, the flashcards and the widget only ever show the
 * cards of the selected book.
 *
 * Books are matched by [name], so importing "中药学.zip" again updates the
 * existing book instead of piling up a duplicate shelf entry.
 */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

/** Per-book aggregates shown on the shelf. */
data class BookStats(
    val bookId: Long,
    val cardCount: Int,
    val categoryCount: Int
)

/**
 * The book currently shown: the stored preference while it still exists,
 * otherwise the first book on the shelf. `0` means "no book at all"
 * (empty shelf), which every screen renders as an empty state.
 */
fun effectiveBookId(preferred: Long, books: List<Book>): Long =
    if (books.any { it.id == preferred }) preferred else books.firstOrNull()?.id ?: 0L

/** Archive extensions stripped when deriving a book name. */
private val ARCHIVE_EXTENSIONS = listOf(".zip", ".tar")

/**
 * Derives a book name from an archive file name or a folder name:
 * "中药学.zip" -> "中药学", "content://…/primary:Download/知识库" -> "知识库".
 * Only known archive extensions are stripped, so folder names that contain
 * dots ("1.解表药") survive untouched.
 */
fun bookNameFromFile(fileName: String): String {
    var base = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
    // SAF document ids look like "primary:Download/知识库" / "raw:书".
    if (base.contains(':')) {
        base = base.substringAfterLast(':').trim().ifEmpty { base }
    }
    if (base.isEmpty()) return "未命名书架"
    val stripped = ARCHIVE_EXTENSIONS
        .firstOrNull { base.endsWith(it, ignoreCase = true) }
        ?.let { base.dropLast(it.length) }
        ?.trim()
        .orEmpty()
    return stripped.ifEmpty { base }
}
