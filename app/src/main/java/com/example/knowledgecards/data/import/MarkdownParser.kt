package com.example.knowledgecards.data.import

import com.example.knowledgecards.data.normalizePath

/** A raw Markdown file discovered by the importer. */
data class MarkdownFile(
    /** File name including extension, e.g. "麻黄汤.md". */
    val fileName: String,
    /** Full text content of the file. */
    val content: String
) {
    /** Title derived from the file name (without extension). */
    val titleFromName: String
        get() = fileName.substringBeforeLast('.').trim()
}

/** A card parsed from a Markdown file. */
data class ParsedCard(
    val title: String,
    val path: String,
    val content: String
)

/**
 * Parses one Markdown file into a card according to the import spec:
 *
 *  - Line 1 (first non-empty line): optional path declaration
 *    `路径: 方剂学/解表剂/辛温解表` (`路径:` or `Path:` prefix, "/" or "\" separators).
 *    Missing declaration -> "未分类" (empty path).
 *  - A `# 标题` heading line overrides the title from the file name.
 *  - Everything else is the body text.
 */
object MarkdownParser {

    private val PATH_PREFIXES = listOf("路径:", "path:", "Path:", "PATH:")

    fun parse(file: MarkdownFile): ParsedCard {
        val lines = file.content.lines()
        val pathLineIndex = lines.indexOfFirst { isPathLine(it) }
        val path = if (pathLineIndex >= 0) {
            normalizePath(extractPathValue(lines[pathLineIndex]))
        } else {
            ""
        }

        // Body = everything after the path line.
        val bodyLines = if (pathLineIndex >= 0) lines.drop(pathLineIndex + 1) else lines

        // Optional `# 标题` override: only when the heading is the first
        // non-blank line of the body; the line is consumed (not part of the
        // body text). Headings deeper inside the body stay as markdown text.
        val firstNonBlank = bodyLines.indexOfFirst { it.isNotBlank() }
        val headingLineIndex = if (firstNonBlank >= 0 &&
            bodyLines[firstNonBlank].trimStart().startsWith("# ")
        ) firstNonBlank else -1
        val heading = if (headingLineIndex >= 0) {
            bodyLines[headingLineIndex].trimStart().removePrefix("# ").trim()
        } else {
            ""
        }

        val title = heading.ifEmpty { file.titleFromName }

        val content = buildString {
            var started = false
            bodyLines.forEachIndexed { index, line ->
                if (index == headingLineIndex) return@forEachIndexed
                if (started || line.isNotBlank()) {
                    started = true
                    appendLine(line)
                }
            }
        }.trimEnd()

        return ParsedCard(title = title, path = path, content = content)
    }

    private fun isPathLine(line: String): Boolean {
        val trimmed = line.trim()
        return PATH_PREFIXES.any { trimmed.startsWith(it) }
    }

    private fun extractPathValue(line: String): String {
        val trimmed = line.trim()
        for (prefix in PATH_PREFIXES) {
            if (trimmed.startsWith(prefix)) {
                return trimmed.removePrefix(prefix).trim()
            }
        }
        return ""
    }

    /** Escapes a title so it can be re-exported safely. */
    fun sanitizeForFileName(title: String): String =
        title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "untitled" }
}
