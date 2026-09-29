package com.example.countapp.data

import com.example.countapp.domain.CategoryCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自訂分類的單元測試。
 *
 * 這裡釘住一件真實出過包的事：`CategoryCatalog.find()` 只看預設分類，
 * 因此自訂分類在明細、分析、預算、編輯頁都會退成灰色的預設圖示。
 * [CategoryStore.findAny] / [CategoryStore.find] 必須看得到自訂分類。
 */
class CategoryStoreTest {

    @Test
    fun `新增的自訂分類會出現在清單最後`() {
        val store = CategoryStore(FakeSharedPreferences())

        assertTrue(store.addCustomCategory(TYPE_EXPENSE, "咖啡", "local_cafe", 0xFF795548))

        val categories = store.categoriesFor(TYPE_EXPENSE)
        assertEquals("咖啡", categories.last().name)
        assertEquals("local_cafe", categories.last().iconKey)
        assertEquals(0xFF795548, categories.last().colorArgb)
        assertTrue(categories.last().isCustom)
    }

    @Test
    fun `findAny 找得到自訂分類`() {
        val store = CategoryStore(FakeSharedPreferences())
        store.addCustomCategory(TYPE_EXPENSE, "咖啡", "local_cafe", 0xFF795548)

        // 預設分類照舊找得到
        assertEquals("restaurant", store.findAny("食物")?.iconKey)
        // 自訂分類也找得到（CategoryCatalog.find 找不到，這正是原本的 bug）
        assertNull(CategoryCatalog.find("咖啡"))
        assertNotNull(store.findAny("咖啡"))
        assertEquals("local_cafe", store.findAny("咖啡")?.iconKey)
        assertEquals(0xFF795548, store.findAny("咖啡")?.colorArgb)
    }

    @Test
    fun `find 會依收支類型過濾`() {
        val store = CategoryStore(FakeSharedPreferences())
        store.addCustomCategory(CategoryCatalog.TYPE_INCOME, "副業", "work", 0xFF2196F3)

        assertNotNull(store.find(CategoryCatalog.TYPE_INCOME, "副業"))
        assertNull(store.find(CategoryCatalog.TYPE_EXPENSE, "副業"))
        // 不分收支的查詢仍找得到
        assertNotNull(store.findAny("副業"))
    }

    @Test
    fun `重複名稱不可新增`() {
        val store = CategoryStore(FakeSharedPreferences())

        assertFalse(store.addCustomCategory(TYPE_EXPENSE, "食物", "label", 0xFF9E9E9E))
        assertTrue(store.addCustomCategory(TYPE_EXPENSE, "咖啡", "local_cafe", 0xFF795548))
        assertFalse(store.addCustomCategory(TYPE_EXPENSE, "咖啡", "star", 0xFFFFC107))
    }

    @Test
    fun `刪除自訂分類後 findAny 回傳 null`() {
        val store = CategoryStore(FakeSharedPreferences())
        store.addCustomCategory(TYPE_EXPENSE, "咖啡", "local_cafe", 0xFF795548)

        assertTrue(store.deleteCustomCategory(TYPE_EXPENSE, "咖啡"))
        assertNull(store.findAny("咖啡"))
        assertFalse(store.deleteCustomCategory(TYPE_EXPENSE, "咖啡"))
    }

    @Test
    fun `自訂分類會寫進儲存區並可重新讀出`() {
        val prefs = FakeSharedPreferences()
        CategoryStore(prefs).addCustomCategory(TYPE_EXPENSE, "咖啡", "local_cafe", 0xFF795548)

        val reopened = CategoryStore(prefs)
        assertEquals("咖啡", reopened.find(TYPE_EXPENSE, "咖啡")?.name)
        assertEquals("local_cafe", reopened.find(TYPE_EXPENSE, "咖啡")?.iconKey)
    }

    @Test
    fun `找不到的名稱回傳 null`() {
        val store = CategoryStore(FakeSharedPreferences())
        assertNull(store.findAny("不存在的分類"))
    }

    private companion object {
        const val TYPE_EXPENSE: String = CategoryCatalog.TYPE_EXPENSE
    }
}
