package com.example.knowledgecards

import com.example.knowledgecards.data.Book
import com.example.knowledgecards.data.bookNameFromFile
import com.example.knowledgecards.data.effectiveBookId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Book-name derivation and current-book fallback. */
class BookNamingTest {

    @Test
    fun `archive extension is stripped`() {
        assertEquals("中药学", bookNameFromFile("中药学.zip"))
        assertEquals("方剂学", bookNameFromFile("方剂学.tar"))
        assertEquals("BOOK", bookNameFromFile("BOOK.ZIP"))
    }

    @Test
    fun `folder names keep their dots`() {
        assertEquals("1.解表药", bookNameFromFile("1.解表药"))
        assertEquals("第 2 版 中药学", bookNameFromFile("第 2 版 中药学"))
    }

    @Test
    fun `saf document ids keep only the display part`() {
        assertEquals("知识库", bookNameFromFile("primary:Download/知识库"))
        assertEquals("书", bookNameFromFile("raw:书"))
    }

    @Test
    fun `blank names fall back to a placeholder`() {
        assertEquals("未命名书架", bookNameFromFile(""))
        assertEquals("未命名书架", bookNameFromFile("   "))
    }

    @Test
    fun `current book falls back to the first shelf entry`() {
        val books = listOf(Book(3L, "甲"), Book(7L, "乙"))
        assertEquals(7L, effectiveBookId(7L, books))
        assertEquals(3L, effectiveBookId(99L, books))
        assertEquals(0L, effectiveBookId(3L, emptyList()))
    }
}
