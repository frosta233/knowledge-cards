package com.example.knowledgecards.data.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.knowledgecards.data.Card
import com.example.knowledgecards.data.import.MarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports the library as Markdown files (one file per card) into a SAF folder,
 * mirroring the import layout: the category becomes the folder structure and
 * the file body starts with an optional heading — no path declaration needed.
 * The exported folder can be re-imported as a backup.
 */
object CardExporter {

    data class ExportResult(val exported: Int, val failed: Int)

    suspend fun export(context: Context, treeUri: Uri, cards: List<Card>): ExportResult =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri)
            if (root == null || !root.isDirectory) {
                return@withContext ExportResult(0, cards.size)
            }
            var exported = 0
            var failed = 0
            for (card in cards) {
                try {
                    val dir = ensureDirectory(context, root, card.path)
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
            ExportResult(exported, failed)
        }

    private fun ensureDirectory(context: Context, root: DocumentFile, path: String): DocumentFile {
        var dir = root
        for (segment in path.split('/').filter { it.isNotBlank() }) {
            val next = dir.findFile(segment)
                ?: dir.createDirectory(segment)
                ?: return dir
            dir = next
        }
        return dir
    }
}
