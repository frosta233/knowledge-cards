package com.example.knowledgecards

import com.example.knowledgecards.data.import.MarkdownFile
import com.example.knowledgecards.data.import.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    private fun parse(content: String, fileName: String = "麻黄汤.md") =
        MarkdownParser.parse(MarkdownFile(fileName, content))

    @Test
    fun `parses path line with Chinese prefix`() {
        val card = parse(
            """
            路径: 方剂学/解表剂/辛温解表
            # 麻黄汤
            组成：麻黄、桂枝、杏仁、炙甘草。
            """.trimIndent()
        )
        assertEquals("方剂学/解表剂/辛温解表", card.path)
        assertEquals("麻黄汤", card.title)
        assertEquals("组成：麻黄、桂枝、杏仁、炙甘草。", card.content.trim())
    }

    @Test
    fun `parses path line with English prefix`() {
        val card = parse(
            """
            Path: 方剂学/解表剂
            # 桂枝汤
            正文
            """.trimIndent()
        )
        assertEquals("方剂学/解表剂", card.path)
        assertEquals("桂枝汤", card.title)
    }

    @Test
    fun `supports backslash separators`() {
        val card = parse("路径: 方剂学\\解表剂\\辛温解表\n正文内容")
        assertEquals("方剂学/解表剂/辛温解表", card.path)
    }

    @Test
    fun `missing path line goes to uncategorized`() {
        val card = parse("# 无路径卡\n只有正文")
        assertEquals("", card.path)
        assertEquals("无路径卡", card.title)
    }

    @Test
    fun `title falls back to file name when no heading`() {
        val card = parse("路径: 解表剂\n只有正文没有标题")
        assertEquals("麻黄汤", card.title)
        assertEquals("只有正文没有标题", card.content.trim())
    }

    @Test
    fun `heading overrides file name`() {
        val card = parse("路径: 解表剂\n# 真正的标题\n正文")
        assertEquals("真正的标题", card.title)
    }

    @Test
    fun `empty file is tolerated`() {
        val card = parse("")
        assertEquals("麻黄汤", card.title)
        assertEquals("", card.path)
        assertEquals("", card.content)
    }

    @Test
    fun `content keeps multi line body and drops the path line`() {
        val card = parse(
            """
            路径: 方剂学/解表剂
            组成：麻黄、桂枝、杏仁、炙甘草。
            功效：发汗解表，宣肺平喘。

            方歌：麻黄汤中臣桂枝，杏仁甘草四般施。
            """.trimIndent()
        )
        assertTrue(card.content.contains("组成"))
        assertTrue(card.content.contains("方歌"))
        assertTrue(!card.content.contains("路径:"))
        assertEquals("麻黄汤", card.title)
    }

    @Test
    fun `crlf line endings are handled`() {
        val card = parse("路径: 方剂学/解表剂\r\n# 标题\r\n正文\r\n")
        assertEquals("方剂学/解表剂", card.path)
        assertEquals("标题", card.title)
        assertTrue(card.content.contains("正文"))
    }

    @Test
    fun `path line can appear with surrounding whitespace`() {
        val card = parse("  路径:  方剂学 / 解表剂  \n正文")
        assertEquals("方剂学/解表剂", card.path)
    }

    @Test
    fun `sanitizeForFileName escapes illegal characters`() {
        assertEquals("a_b_c", MarkdownParser.sanitizeForFileName("a/b\\c"))
        assertEquals("untitled", MarkdownParser.sanitizeForFileName(""))
    }
}
