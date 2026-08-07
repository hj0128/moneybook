package com.hj0128.moneybook

import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.ui.category.toNodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTreeTest {

    private fun category(id: Long, name: String, parentId: Long? = null) = CategoryEntity(
        id = id,
        name = name,
        type = TransactionType.EXPENSE,
        colorArgb = 0,
        sortOrder = 0,
        parentId = parentId,
    )

    @Test
    fun `소분류는 부모 밑으로 묶인다`() {
        val nodes = listOf(
            category(1, "식비"),
            category(2, "교통"),
            category(10, "외식", parentId = 1),
            category(11, "마트", parentId = 1),
        ).toNodes()

        assertEquals(2, nodes.size)
        assertEquals("식비", nodes[0].category.name)
        assertEquals(listOf("외식", "마트"), nodes[0].children.map { it.name })
        assertTrue(nodes[1].children.isEmpty())
    }

    @Test
    fun `소분류가 없는 대분류도 노드로 남는다`() {
        val nodes = listOf(category(1, "식비")).toNodes()

        assertEquals(1, nodes.size)
        assertTrue(nodes[0].children.isEmpty())
    }

    @Test
    fun `부모가 사라진 소분류는 버리지 않고 대분류로 올린다`() {
        // 화면에서 사라지면 사용자가 지울 방법조차 없어진다.
        val nodes = listOf(
            category(1, "식비"),
            category(10, "외식", parentId = 1),
            category(99, "고아", parentId = 404),
        ).toNodes()

        assertEquals(listOf("식비", "고아"), nodes.map { it.category.name })
        assertEquals(listOf("외식"), nodes[0].children.map { it.name })
        assertTrue(nodes[1].children.isEmpty())
    }

    @Test
    fun `빈 목록은 빈 트리`() {
        assertTrue(emptyList<CategoryEntity>().toNodes().isEmpty())
    }
}
