package com.example.knowledgecards.data.import

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Reads `.zip` / `.tar` archives and extracts the Markdown files inside.
 * RAR is not supported (proprietary format, no reliable open-source decoder).
 *
 * A single top-level folder that wraps all entries is stripped automatically,
 * so both `知识库/方剂学/...` and `方剂学/...` layouts import identically.
 */
object ArchiveImporter {

    fun supports(uri: Uri): Boolean {
        val name = uri.lastPathSegment.orEmpty().lowercase()
        return name.endsWith(".zip") || name.endsWith(".tar")
    }

    fun unsupportedReason(uri: Uri): String =
        if (uri.lastPathSegment.orEmpty().lowercase().endsWith(".rar")) {
            "暂不支持 RAR 格式（专利格式且无可靠开源实现），请改用 zip 或 tar"
        } else {
            "仅支持 .zip 和 .tar 压缩包"
        }

    suspend fun extract(context: Context, uri: Uri): List<MarkdownFile> =
        withContext(Dispatchers.IO) {
            val name = uri.lastPathSegment.orEmpty().lowercase()
            val raw = mutableListOf<MarkdownFile>()
            context.contentResolver.openInputStream(uri)?.use { input ->
                when {
                    name.endsWith(".zip") -> readZip(input, raw)
                    name.endsWith(".tar") -> readTar(input, raw)
                    else -> throw IOException("不支持的压缩格式（仅支持 zip / tar）")
                }
            } ?: throw IOException("无法读取压缩包")
            stripCommonRoot(raw)
        }

    internal fun readZip(input: java.io.InputStream, out: MutableList<MarkdownFile>) {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".md", ignoreCase = true)) {
                    val content = zip.readBytes().toString(Charsets.UTF_8)
                    out += toMarkdownFile(entry.name, content)
                }
                zip.closeEntry()
            }
        }
    }

    private fun readTar(input: java.io.InputStream, out: MutableList<MarkdownFile>) {
        TarArchiveInputStream(input).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".md", ignoreCase = true)) {
                    val content = tar.readBytes().toString(Charsets.UTF_8)
                    out += toMarkdownFile(entry.name, content)
                }
            }
        }
    }

    private fun toMarkdownFile(entryName: String, content: String): MarkdownFile {
        val parts = entryName.split('/').filter { it.isNotBlank() }
        val fileName = parts.lastOrNull() ?: entryName
        val relativePath = parts.dropLast(1).joinToString("/")
        return MarkdownFile(fileName = fileName, content = content, relativePath = relativePath)
    }

    /** Strips one common top-level folder when every entry lives under it. */
    internal fun stripCommonRoot(files: List<MarkdownFile>): List<MarkdownFile> {
        if (files.size < 2) return files
        val first = files.first().relativePath.split('/').filter { it.isNotBlank() }
        if (first.isEmpty()) return files
        val common = first.takeWhile { segment ->
            files.all { it.relativePath.split('/').firstOrNull() == segment }
        }
        if (common.isEmpty()) return files
        val prefix = common.joinToString("/")
        return files.map { f ->
            val rel = f.relativePath.removePrefix(prefix).trim('/')
            f.copy(relativePath = rel)
        }
    }
}
