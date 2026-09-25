package com.example.knowledgecards.data.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.knowledgecards.data.CardRepository
import com.example.knowledgecards.data.bookNameFromFile

/** Outcome of importing one archive / folder into the bookshelf. */
data class BookImportResult(
    val bookId: Long,
    val bookName: String,
    /** True when this import created a new book instead of updating one. */
    val createdBook: Boolean,
    val result: ImportResult
) {
    val imported: Int get() = result.newCount + result.updatedCount
}

/**
 * Turns one import into one book: the archive / folder name is the book name,
 * and everything inside it becomes that book's cards.
 *
 * Importing something with the same name again updates that book in place
 * (cards are matched by category + title, like before), so a re-import
 * refreshes a book instead of piling up duplicates on the shelf.
 */
class BookImporter(private val repository: CardRepository) {

    suspend fun importArchive(context: Context, uri: Uri): BookImportResult {
        val name = bookNameFromFile(displayName(context, uri) ?: uri.lastPathSegment.orEmpty())
        val files = ArchiveImporter.extract(context, uri)
        return importFiles(name, files)
    }

    suspend fun importTree(context: Context, uri: Uri): BookImportResult {
        val folderName = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        val name = bookNameFromFile(folderName ?: uri.lastPathSegment.orEmpty())
        val files = SafTreeScanner.scan(context, uri)
        return importFiles(name, files)
    }

    private suspend fun importFiles(name: String, files: List<MarkdownFile>): BookImportResult {
        val existing = repository.findBookByName(name)
        val bookId = existing?.id ?: repository.createBook(name)
        val result = CardImporter(repository, bookId).import(files)
        return BookImportResult(
            bookId = bookId,
            bookName = name,
            createdBook = existing == null,
            result = result
        )
    }

    /** Real display name of a picked document ("中药学.zip"), when available. */
    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}
