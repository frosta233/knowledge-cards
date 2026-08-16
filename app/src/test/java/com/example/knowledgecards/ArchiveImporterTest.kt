package com.example.knowledgecards

import com.example.knowledgecards.data.import.ArchiveImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveImporterTest {

    private fun zipOf(entries: Map<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun `zip entries keep their folder positions`() {
        val bytes = zipOf(
            mapOf(
                "方剂学/解表剂/麻黄汤.md" to "# 麻黄汤\n组成。",
                "随手笔记.md" to "# 笔记\n内容"
            )
        )
        val out = mutableListOf<com.example.knowledgecards.data.import.MarkdownFile>()
        ArchiveImporter.readZip(ByteArrayInputStream(bytes), out)
        val files = out
        assertEquals(2, files.size)
        val mahuang = files.first { it.fileName == "麻黄汤.md" }
        assertEquals("方剂学/解表剂", mahuang.relativePath)
        val note = files.first { it.fileName == "随手笔记.md" }
        assertEquals("", note.relativePath)
    }

    @Test
    fun `common top-level folder is stripped`() {
        val bytes = zipOf(
            mapOf(
                "知识库/方剂学/解表剂/麻黄汤.md" to "# 麻黄汤\n组成。",
                "知识库/方剂学/补益剂/四君子汤.md" to "# 四君子汤\n组成。"
            )
        )
        val out = mutableListOf<com.example.knowledgecards.data.import.MarkdownFile>()
        ArchiveImporter.readZip(ByteArrayInputStream(bytes), out)
        val files = ArchiveImporter.stripCommonRoot(out)
        assertEquals(2, files.size)
        assertTrue(files.all { !it.relativePath.startsWith("知识库") })
        assertEquals("方剂学/解表剂", files.first { it.fileName == "麻黄汤.md" }.relativePath)
    }

    @Test
    fun `non markdown entries are ignored`() {
        val bytes = zipOf(
            mapOf(
                "方剂学/readme.txt" to "not a card",
                "方剂学/麻黄汤.md" to "# 麻黄汤\n组成。"
            )
        )
        val out = mutableListOf<com.example.knowledgecards.data.import.MarkdownFile>()
        ArchiveImporter.readZip(ByteArrayInputStream(bytes), out)
        val files = out
        assertEquals(1, files.size)
        assertEquals("麻黄汤.md", files[0].fileName)
    }
}
