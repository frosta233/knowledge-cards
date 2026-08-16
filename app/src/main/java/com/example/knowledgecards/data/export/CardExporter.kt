package com.example.knowledgecards.data.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.knowledgecards.data.Card
import com.example.knowledgecards.data.import.MarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports the library as Markdown files (one file per card) into a SAF folder.
 * The written files follow the same import spec (path declaration on line 1,
 * optional heading, body), so the export can be re-imported as a backup.
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
                    val displayName = "${MarkdownParser.sanitizeForFileName(card.title)}.md"
                    val file = root.findFile(displayName)
                        ?: root.createFile("text/markdown", displayName)
                    if (file != null) {
                        val text = buildString {
                            if (card.path.isNotEmpty()) {
                                appendLine("路径: ${card.path}")
                            }
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
}
