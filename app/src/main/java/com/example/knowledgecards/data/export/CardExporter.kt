package com.example.knowledgecards.data.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.knowledgecards.data.Card
import com.example.knowledgecards.data.import.MarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports the whole shelf as Markdown files (one file per card) into a SAF
 * folder: every book becomes a top-level folder, the category becomes the
 * nested folder structure and the file body starts with an optional heading —
 * no path declaration needed.
 *
 * Importing an exported book folder again recreates that book, so the export
 * is a usable backup.
 */
object CardExporter {

    data class ExportResult(val exported: Int, val failed: Int)

    /** One book and its cards, ready to be written to disk. */
    data class BookExport(val bookName: String, val cards: List<Card>)

    suspend fun export(
        context: Context,
        treeUri: Uri,
        books: List<BookExport>
    ): ExportResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        if (root == null || !root.isDirectory) {
            return@withContext ExportResult(0, books.sumOf { it.cards.size })
        }
        var exported = 0
        var failed = 0
        for (book in books) {
            if (book.cards.isEmpty()) continue
            val bookDir = ensureDirectory(context, root, book.bookName)
            for (card in book.cards) {
                try {
                    val dir = ensureDirectory(context, bookDir, card.path)
                    val displayName = "${MarkdownParser.sanitizeForFileName(card.title)}.md"
                    val file = dir.findFile(displayName)
                        ?: dir.createFile("text/markdown", displayName)
                    if (file != null) {
                        val text = buildString {
                            appendLine("# ${card.title}")
                            append(card.content)
                        }
                        context.contentResolver.openOutputStream(file.uri)?.use { out ->
                            out.write(text.toByteArray(Charsets.UTF_8))
                            exported++
                        } ?: run { failed++ }
                    } else {
                        failed++
                    }
                } catch (e: Exception) {
                    failed++
                }
            }
        }
        ExportResult(exported, failed)
    }

    private fun ensureDirectory(context: Context, root: DocumentFile, path: String): DocumentFile {
        var dir = root
        for (segment in path.split('/').filter { it.isNotBlank() }) {
            val safe = MarkdownParser.sanitizeForFileName(segment)
            val next = dir.findFile(safe)
                ?: dir.createDirectory(safe)
                ?: return dir
            dir = next
        }
        return dir
    }
}
