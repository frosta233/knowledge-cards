package com.example.knowledgecards.data.import

/** A raw Markdown file discovered by the importer. */
data class MarkdownFile(
    /** File name including extension, e.g. "麻黄汤.md". */
    val fileName: String,
    /** Full text content of the file. */
    val content: String,
    /**
     * Folder path of the file relative to the import root ("" = root level).
     * Slash-separated; the category is derived from this folder position.
     */
    val relativePath: String = ""
) {
    /** Title derived from the file name (without extension). */
    val titleFromName: String
        get() = fileName.substringBeforeLast('.').trim()
}
