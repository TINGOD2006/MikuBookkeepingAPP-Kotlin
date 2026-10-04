package com.example.countapp.data

import com.example.countapp.domain.CategoryCatalog
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class CategoryManagementTest {
    private val expense = CategoryCatalog.TYPE_EXPENSE
    private val income = CategoryCatalog.TYPE_INCOME

    @Test fun `預設分類改名圖示及順序重開後保留且舊名不復活`() {
        val prefs = FakeSharedPreferences()
        val store = CategoryStore(prefs)
        assertTrue(store.editCategory(expense, "食物", "餐飲", "star", 0xFF123456))
        val order = store.categoriesFor(expense).map { it.name }.reversed()
        assertTrue(store.reorder(expense, order))
        val reopened = CategoryStore(prefs)
        assertEquals(order, reopened.categoriesFor(expense).map { it.name })
        assertNull(reopened.find(expense, "食物"))
        assertEquals("star", reopened.find(expense, "餐飲")?.iconKey)
        assertEquals(0xFF123456L, reopened.find(expense, "餐飲")?.colorArgb)
    }

    @Test fun `刪除預設分類不復活且歷史圖示保留`() {
        val prefs = FakeSharedPreferences()
        assertTrue(CategoryStore(prefs).deleteCategory(expense, "食物"))
        val reopened = CategoryStore(prefs)
        assertNull(reopened.find(expense, "食物"))
        assertEquals("restaurant", reopened.findForRecord(expense, "食物")?.iconKey)
    }

    @Test fun `非法改名與不完整排序不寫入且收支清單独立`() {
        val store = CategoryStore(FakeSharedPreferences())
        val initial = store.categoriesFor(expense)
        assertFalse(store.editCategory(expense, "食物", "交通", "star", 0L))
        assertFalse(store.editCategory(expense, "食物", "  ", "star", 0L))
        assertFalse(store.reorder(expense, listOf("食物", "食物")))
        assertEquals(initial, store.categoriesFor(expense))
        val incomeInitial = store.categoriesFor(income)
        assertTrue(store.reorder(expense, initial.map { it.name }.reversed()))
        assertEquals(incomeInitial, store.categoriesFor(income))
    }

    @Test fun `分類改名同步該方向帳目垃圾桶全部月份預算與規則`() {
        val prefs = FakeSharedPreferences()
        val store = CategoryStore(prefs)
        val records = RecordRepository(prefs)
        val budgets = BudgetRepository(prefs)
        val settings = SettingsStore(prefs)
        val manager = CategoryManager(store, records, budgets, settings)
        val paid = Record.create(amount = -50.0, category = "食物", note = "午餐")
        val trash = Record.create(amount = -20.0, category = "食物", note = "舊帳")
        val incoming = Record.create(amount = 10.0, category = "食物", note = "不改此方向")
        records.add(paid); records.add(trash); records.add(incoming)
        records.moveToTrash(trash.id)
        val trashed = records.trashSnapshot().single()
        val months = listOf(YearMonth.of(2025, 1), YearMonth.of(2026, 10))
        months.forEach { budgets.saveCategoryBudget(it, "食物", 123.0) }
        settings.customRules = mapOf("食物" to listOf("午餐"))
        assertTrue(manager.edit(expense, "食物", "餐飲", "star", 0xFF123456))
        assertEquals(paid.copy(category = "餐飲"), records.snapshot().first { it.id == paid.id })
        assertEquals(trashed.copy(category = "餐飲"), records.trashSnapshot().single())
        assertEquals(incoming, records.snapshot().first { it.id == incoming.id })
        months.forEach {
            assertNull(budgets.loadCategoryBudget(it, "食物"))
            assertEquals(123.0, budgets.loadCategoryBudget(it, "餐飲")!!, 0.001)
        }
        assertEquals(listOf("午餐"), settings.effectiveRules()["餐飲"])
        assertFalse(settings.effectiveRules().containsKey("食物"))
        assertEquals(records.snapshot(), RecordRepository(prefs).snapshot())
    }

    @Test fun `同名收支分類改名保留另一方向規則收入不改支出預算`() {
        val prefs = FakeSharedPreferences()
        val store = CategoryStore(prefs)
        val budgets = BudgetRepository(prefs)
        val settings = SettingsStore(prefs)
        val month = YearMonth.of(2026, 10)
        settings.customRules = mapOf("禮金" to listOf("紅包"))
        budgets.saveCategoryBudget(month, "禮金", 100.0)
        val manager = CategoryManager(store, RecordRepository(prefs), budgets, settings)
        assertTrue(manager.edit(income, "禮金", "收到紅包", "star", 1L))
        assertEquals(listOf("紅包"), settings.effectiveRules()["禮金"])
        assertEquals(listOf("紅包"), settings.effectiveRules()["收到紅包"])
        assertEquals(100.0, budgets.loadCategoryBudget(month, "禮金")!!, 0.001)
    }

    @Test fun `刪除分類不刪歷史帳目或預算且不再參與自動分類`() {
        val prefs = FakeSharedPreferences()
        val store = CategoryStore(prefs)
        val records = RecordRepository(prefs)
        val budgets = BudgetRepository(prefs)
        val settings = SettingsStore(prefs)
        val record = Record.create(amount = -50.0, category = "食物", note = "午餐")
        records.add(record)
        budgets.saveCategoryBudget(YearMonth.of(2026, 10), "食物", 100.0)
        assertTrue(store.deleteCategory(expense, "食物"))
        assertEquals(listOf(record), records.snapshot())
        assertNotNull(budgets.loadCategoryBudget(YearMonth.of(2026, 10), "食物"))
        assertFalse(settings.effectiveRules().containsKey("食物"))
    }

    @Test fun `背景分類先算完改名後才新增仍使用新分類且不影響收入同名`() {
        val prefs = FakeSharedPreferences()
        val store = CategoryStore(prefs)
        val records = RecordRepository(prefs)
        val manager = CategoryManager(store, records, BudgetRepository(prefs), SettingsStore(prefs))
        val delayed = Record.create(amount = -10.0, category = "食物", note = "延遲通知")
        assertTrue(manager.edit(expense, "食物", "餐飲", "star", 1L))
        assertTrue(manager.edit(expense, "餐飲", "吃飯", "star", 1L))
        records.add(delayed)
        assertEquals("吃飯", records.snapshot().single().category)
        assertFalse(records.updateIfUnchanged(delayed, delayed.copy(category = "交通")))
        val current = records.snapshot().single()
        assertTrue(records.updateIfUnchanged(current, current.copy(category = "餐飲")))
        assertEquals("吃飯", records.snapshot().single().category)
        records.update(current.copy(category = "食物"))
        assertEquals("吃飯", records.snapshot().single().category)
        val opposite = Record.create(amount = 10.0, category = "食物", note = "另一方向")
        records.add(opposite)
        assertEquals(opposite, records.snapshot().first { it.id == opposite.id })
        assertEquals("吃飯", RecordRepository(prefs).snapshot().first { it.id == delayed.id }.category)
    }

    @Test fun `改回舊名及重新新增舊名不形成循環或錯誤映射`() {
        val prefs = FakeSharedPreferences()
        val store = CategoryStore(prefs)
        val records = RecordRepository(prefs)
        val manager = CategoryManager(store, records, BudgetRepository(prefs), SettingsStore(prefs))
        assertTrue(manager.edit(expense, "食物", "餐飲", "star", 1L))
        assertTrue(manager.edit(expense, "餐飲", "食物", "star", 1L))
        val delayed = Record.create(amount = -10.0, category = "餐飲", note = "延遲")
        records.add(delayed)
        assertEquals("食物", records.snapshot().single().category)
        assertTrue(store.addCustomCategory(expense, "餐飲", "star", 1L))
        val newRecord = Record.create(amount = -10.0, category = "餐飲", note = "重新新增")
        records.add(newRecord)
        assertEquals("餐飲", records.snapshot().first { it.id == newRecord.id }.category)
    }

    @Test fun `改名撞上已刪分類歷史預算時拒絕且所有資料保持原樣`() {
        val prefs = FakeSharedPreferences()
        val store = CategoryStore(prefs)
        val records = RecordRepository(prefs)
        val budgets = BudgetRepository(prefs)
        val settings = SettingsStore(prefs)
        val month = YearMonth.of(2025, 3)
        budgets.saveCategoryBudget(month, "交通", 100.0)
        budgets.saveCategoryBudget(month, "食物", 200.0)
        store.deleteCategory(expense, "交通")
        val record = Record.create(amount = -10.0, category = "食物", note = "餐飲")
        records.add(record)
        val categories = store.categoriesFor(expense)
        val rules = settings.effectiveRules()
        val manager = CategoryManager(store, records, budgets, settings)
        assertFalse(manager.edit(expense, "食物", "交通", "star", 1L))
        assertEquals(categories, store.categoriesFor(expense))
        assertEquals(listOf(record), records.snapshot())
        assertEquals(mapOf("交通" to 100.0, "食物" to 200.0), budgets.loadCategoryBudgets(month))
        assertEquals(rules, settings.effectiveRules())
    }
}
