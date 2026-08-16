package com.example.knowledgecards

import com.example.knowledgecards.data.Card
import com.example.knowledgecards.domain.CategoryTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTreeTest {

    private fun card(id: Long, title: String, path: String) =
        Card(id = id, title = title, content = "", path = path, sortOrder = id.toInt())

    @Test
    fun `builds nested tree from paths`() {
        val tree = CategoryTree.build(
            listOf(
                card(1, "麻黄汤", "方剂学/解表剂/辛温解表"),
                card(2, "桂枝汤", "方剂学/解表剂/辛温解表"),
                card(3, "银翘散", "方剂学/解表剂/辛凉解表"),
                card(4, "四君子汤", "方剂学/补益剂")
            )
        )
        assertEquals(1, tree.size)
        val root = tree[0]
        assertEquals("方剂学", root.name)
        assertEquals(2, root.children.size)
        val jieBiao = root.children.first { it.name == "解表剂" }
        assertEquals(2, jieBiao.children.size)
        assertEquals(4, root.totalCards)
    }

    @Test
    fun `uncategorized cards are grouped under root pseudo node`() {
        val tree = CategoryTree.build(
            listOf(
                card(1, "随手笔记", ""),
                card(2, "药名速查", "")
            )
        )
        assertEquals(1, tree.size)
        assertEquals("未分类", tree[0].name)
        assertEquals(2, tree[0].cards.size)
    }

    @Test
    fun `nodes with no direct cards still count descendants`() {
        val tree = CategoryTree.build(
            listOf(card(1, "A", "方剂学/解表剂/辛温解表"))
        )
        val root = tree[0]
        assertEquals(0, root.cards.size)
        assertEquals(1, root.totalCards)
        assertEquals("方剂学", root.fullPath)
        val last = root.children[0].children[0]
        assertEquals("方剂学/解表剂/辛温解表", last.fullPath)
        assertEquals(listOf(1L), CategoryTree.collectCardIds(last))
    }

    @Test
    fun `collectCardIds includes descendants in tree display order`() {
        val cards = listOf(
            card(1, "麻黄汤", "方剂学/解表剂/辛温解表"),
            card(2, "银翘散", "方剂学/解表剂/辛凉解表"),
            card(3, "四君子汤", "方剂学/补益剂")
        )
        // 辛凉解表 sorts before 辛温解表 (Unicode), matching the tree order.
        val ids = CategoryTree.collectCardIds(cards, "方剂学/解表剂")
        assertEquals(listOf(2L, 1L), ids)
    }

    @Test
    fun `empty path matches everything`() {
        val cards = listOf(card(1, "A", "x/y"), card(2, "B", ""))
        assertEquals(2, CategoryTree.collectCardIds(cards, "").size)
    }

    @Test
    fun `breadcrumb collapses middle segments`() {
        assertEquals("方剂学 / … / 辛温解表", CategoryTree.breadcrumb("方剂学/解表剂/辛温解表"))
        assertEquals("方剂学 / 解表剂", CategoryTree.breadcrumb("方剂学/解表剂"))
        assertEquals("未分类", CategoryTree.breadcrumb(""))
    }

    @Test
    fun `lastSegment returns final segment`() {
        assertEquals("辛温解表", CategoryTree.lastSegment("方剂学/解表剂/辛温解表"))
        assertEquals("未分类", CategoryTree.lastSegment(""))
    }

    @Test
    fun `tree ordering is deterministic by name`() {
        val tree = CategoryTree.build(
            listOf(
                card(1, "B卡", "乙类"),
                card(2, "A卡", "甲类")
            )
        )
        assertEquals(listOf("乙类", "甲类"), tree.map { it.name })
    }
}
