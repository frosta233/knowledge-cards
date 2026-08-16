package com.example.knowledgecards.data.import

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Recursively scans a SAF folder tree for ".md" files and reads their text.
 * No storage permission is required because access flows through the tree URI
 * granted by the system file picker. Each file records its folder position
 * relative to the tree root, which becomes the card's category.
 */
object SafTreeScanner {

    suspend fun scan(context: Context, treeUri: Uri): List<MarkdownFile> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IOException("无法打开所选文件夹")
            val files = mutableListOf<MarkdownFile>()
            collect(context, root, "", files)
            files
        }

    private fun collect(
        context: Context,
        dir: DocumentFile,
        relativePath: String,
        out: MutableList<MarkdownFile>
    ) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                val childPath = child.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { if (relativePath.isEmpty()) it else "$relativePath/$it" }
                    ?: relativePath
                collect(context, child, childPath, out)
            } else if (child.isFile && child.name?.endsWith(".md", ignoreCase = true) == true) {
                val content = readText(context, child)
                if (content != null) {
                    out += MarkdownFile(
                        fileName = child.name ?: "unknown.md",
                        content = content,
                        relativePath = relativePath
                    )
                }
            }
        }
    }

    private fun readText(context: Context, file: DocumentFile): String? = try {
        context.contentResolver.openInputStream(file.uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        // A single unreadable file must not abort the whole scan; the importer
        // reports failures per file via the MarkdownFile list being incomplete.
        null
    }
}
