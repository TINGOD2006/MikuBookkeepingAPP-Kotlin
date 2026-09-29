package com.example.countapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 預算頁「各分類預算」顯示清單的規則測試。
 *
 * 這裡釘住需求：**不預設所有分類**。沒設定、也沒按新增的分類，
 * 就算存在於分類表裡也必須完全不出現。
 */
class CategoryBudgetSelectionTest {

    private val catalog = listOf("食物", "交通", "購物", "娛樂", "醫療")

    @Test
    fun `完全沒設定也沒新增時是空清單`() {
        assertEquals(
            emptyList<String>(),
            visibleCategoryBudgetNames(catalog, emptySet(), emptySet()),
        )
    }

    @Test
    fun `只有已設定的分類會出現`() {
        assertEquals(
            listOf("交通"),
            visibleCategoryBudgetNames(catalog, setOf("交通"), emptySet()),
        )
    }

    @Test
    fun `剛新增還沒填金額的分類也會出現`() {
        assertEquals(
            listOf("娛樂"),
            visibleCategoryBudgetNames(catalog, emptySet(), setOf("娛樂")),
        )
    }

    @Test
    fun `順序依分類表而不是設定或新增的先後`() {
        // 刻意用「反序」的集合：結果仍要是分類表的順序
        assertEquals(
            listOf("食物", "購物", "醫療"),
            visibleCategoryBudgetNames(
                catalog,
                linkedSetOf("醫療", "購物", "食物"),
                emptySet(),
            ),
        )
    }

    @Test
    fun `已設定與草稿是同一分類時只出現一次`() {
        assertEquals(
            listOf("食物"),
            visibleCategoryBudgetNames(catalog, setOf("食物"), setOf("食物")),
        )
    }

    @Test
    fun `被刪掉的分類若有預算仍要列出並排在後面`() {
        // 「訂閱」已經不在分類表裡了，但那筆預算不能因此消失
        assertEquals(
            listOf("食物", "訂閱"),
            visibleCategoryBudgetNames(catalog, setOf("食物", "訂閱"), emptySet()),
        )
    }

    @Test
    fun `多個不在分類表的分類依名稱排序`() {
        assertEquals(
            listOf("食物", "AAA", "ZZZ"),
            visibleCategoryBudgetNames(catalog, setOf("食物", "ZZZ", "AAA"), emptySet()),
        )
    }

    @Test
    fun `分類表為空時仍列出已設定的分類`() {
        assertEquals(
            listOf("食物"),
            visibleCategoryBudgetNames(emptyList(), setOf("食物"), emptySet()),
        )
    }
}
