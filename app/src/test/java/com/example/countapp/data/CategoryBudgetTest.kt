package com.example.countapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * 「各分類每月預算」的儲存測試。
 *
 * 這裡釘住三件事：
 *   1. 分類預算**可以完全不設定**：未設定讀出來是 null／空 map，不是 0
 *      （回傳 0 會讓預算頁畫出 0% 的假進度）。
 *   2. 可以個別設定／覆蓋／清除，且不同月份、不同分類互不影響。
 *   3. 與既有的整月預算（`budget_YYYY_MM`，Float）完全分開：
 *      鍵名不重疊、`allMonths()` 不會被分類預算污染。
 */
class CategoryBudgetTest {

    private val month: YearMonth = YearMonth.of(2026, 9)
    private val nextMonth: YearMonth = YearMonth.of(2026, 10)

    // ========== 未設定 ==========

    @Test
    fun `完全沒設定時回傳空集合與 null`() {
        val repository = BudgetRepository(FakeSharedPreferences())

        assertTrue(repository.loadCategoryBudgets(month).isEmpty())
        assertNull(repository.loadCategoryBudget(month, "食物"))
    }

    @Test
    fun `未設定預算的分類回傳 null 而不是 0`() {
        val repository = BudgetRepository(FakeSharedPreferences())
        repository.saveCategoryBudget(month, "食物", 3000.0)

        // 有設定的是 3000，沒設定的是 null：兩者不可混為一談
        assertEquals(3000.0, repository.loadCategoryBudget(month, "食物")!!, 0.001)
        assertNull(repository.loadCategoryBudget(month, "交通"))
    }

    @Test
    fun `其他月份沒有預算`() {
        val repository = BudgetRepository(FakeSharedPreferences())
        repository.saveCategoryBudget(month, "食物", 3000.0)

        assertNull(repository.loadCategoryBudget(nextMonth, "食物"))
        assertTrue(repository.loadCategoryBudgets(nextMonth).isEmpty())
    }

    // ========== 新增／覆蓋 ==========

    @Test
    fun `可以為單一分類設定預算並重新讀出`() {
        val prefs = FakeSharedPreferences()
        BudgetRepository(prefs).saveCategoryBudget(month, "食物", 3000.0)

        // 換一個實例讀同一份儲存區，確認真的寫進去了（不是只留在記憶體）
        val reopened = BudgetRepository(prefs)
        assertEquals(3000.0, reopened.loadCategoryBudget(month, "食物")!!, 0.001)
        assertEquals(setOf("食物"), reopened.loadCategoryBudgets(month).keys)
    }

    @Test
    fun `重新設定同一分類會覆蓋舊值`() {
        val repository = BudgetRepository(FakeSharedPreferences())
        repository.saveCategoryBudget(month, "食物", 3000.0)
        repository.saveCategoryBudget(month, "食物", 2500.0)

        assertEquals(2500.0, repository.loadCategoryBudget(month, "食物")!!, 0.001)
        assertEquals(1, repository.loadCategoryBudgets(month).size)
    }

    @Test
    fun `多個分類可以各自設定`() {
        val repository = BudgetRepository(FakeSharedPreferences())
        repository.saveCategoryBudget(month, "食物", 3000.0)
        repository.saveCategoryBudget(month, "交通", 500.0)

        val budgets = repository.loadCategoryBudgets(month)
        assertEquals(2, budgets.size)
        assertEquals(3000.0, budgets["食物"]!!, 0.001)
        assertEquals(500.0, budgets["交通"]!!, 0.001)
    }

    @Test
    fun `金額會四捨五入到兩位小數`() {
        val repository = BudgetRepository(FakeSharedPreferences())
        repository.saveCategoryBudget(month, "食物", 1234.5678)

        assertEquals(1234.57, repository.loadCategoryBudget(month, "食物")!!, 0.001)
    }

    // ========== 清除 ==========

    @Test
    fun `清除單一分類不會影響其他分類`() {
        val repository = BudgetRepository(FakeSharedPreferences())
        repository.saveCategoryBudget(month, "食物", 3000.0)
        repository.saveCategoryBudget(month, "交通", 500.0)

        repository.clearCategoryBudget(month, "食物")

        assertNull(repository.loadCategoryBudget(month, "食物"))
        assertEquals(500.0, repository.loadCategoryBudget(month, "交通")!!, 0.001)
    }

    @Test
    fun `清除最後一個分類後該月回到空集合`() {
        val prefs = FakeSharedPreferences()
        val repository = BudgetRepository(prefs)
        repository.saveCategoryBudget(month, "食物", 3000.0)

        repository.clearCategoryBudget(month, "食物")

        assertTrue(repository.loadCategoryBudgets(month).isEmpty())
        // 空殼不會一直留在儲存區裡（月份物件整個被移除）
        assertEquals("{}", prefs.stored[BudgetRepository.KEY_CATEGORY_BUDGETS])
    }

    @Test
    fun `清除不存在的分類不會出錯`() {
        val repository = BudgetRepository(FakeSharedPreferences())

        repository.clearCategoryBudget(month, "食物")
        repository.clearCategoryBudget(nextMonth, "食物")

        assertTrue(repository.loadCategoryBudgets(month).isEmpty())
    }

    @Test
    fun `0 或負數的金額視為清除`() {
        val repository = BudgetRepository(FakeSharedPreferences())
        repository.saveCategoryBudget(month, "食物", 3000.0)
        repository.saveCategoryBudget(month, "交通", 500.0)

        repository.saveCategoryBudget(month, "食物", 0.0)
        repository.saveCategoryBudget(month, "交通", -100.0)

        assertNull(repository.loadCategoryBudget(month, "食物"))
        assertNull(repository.loadCategoryBudget(month, "交通"))
    }

    @Test
    fun `空白的分類名稱不會寫入`() {
        val repository = BudgetRepository(FakeSharedPreferences())

        repository.saveCategoryBudget(month, "   ", 1000.0)

        assertTrue(repository.loadCategoryBudgets(month).isEmpty())
    }

    // ========== 與整月預算互不干擾 ==========

    @Test
    fun `分類預算與整月預算使用不同鍵且互不覆蓋`() {
        val prefs = FakeSharedPreferences()
        val repository = BudgetRepository(prefs)

        repository.save(month, 20000.0)
        repository.saveCategoryBudget(month, "食物", 3000.0)

        // 整月預算還在，分類預算也在
        assertEquals(20000.0, repository.load(month)!!, 0.001)
        assertEquals(3000.0, repository.loadCategoryBudget(month, "食物")!!, 0.001)
        // 分類預算只佔一個 JSON 鍵，不會多出一堆 budget_ 開頭的鍵
        assertEquals(
            setOf(BudgetRepository.key(2026, 9), BudgetRepository.KEY_CATEGORY_BUDGETS),
            prefs.stored.keys,
        )
    }

    @Test
    fun `整月預算仍以 Float 儲存（向後相容）`() {
        val prefs = FakeSharedPreferences()
        BudgetRepository(prefs).save(month, 12345.0)

        // 既有行為：鍵名不變、型別是 Float（BudgetAlertCheck 依賴 load(month)）
        assertEquals(12345f, prefs.stored[BudgetRepository.key(2026, 9)])
        assertEquals(listOf(month), BudgetRepository(prefs).allMonths())
    }

    @Test
    fun `allMonths 不會被分類預算污染`() {
        val repository = BudgetRepository(FakeSharedPreferences())
        repository.saveCategoryBudget(month, "食物", 3000.0)

        // 只有分類預算、沒有整月預算時，allMonths() 必須是空的
        assertTrue(repository.allMonths().isEmpty())
    }

    @Test
    fun `清除分類預算後整月預算不受影響`() {
        val repository = BudgetRepository(FakeSharedPreferences())
        repository.save(month, 20000.0)
        repository.saveCategoryBudget(month, "食物", 3000.0)

        repository.clearCategoryBudget(month, "食物")

        assertEquals(20000.0, repository.load(month)!!, 0.001)
        assertTrue(repository.loadCategoryBudgets(month).isEmpty())
    }

    // ========== 壞資料 ==========

    @Test
    fun `分類預算 JSON 壞掉時回傳空值且不影響整月預算`() {
        val prefs = FakeSharedPreferences()
        val repository = BudgetRepository(prefs)
        repository.save(month, 20000.0)
        prefs.edit().putString(BudgetRepository.KEY_CATEGORY_BUDGETS, "{ 這不是 JSON").apply()

        assertTrue(repository.loadCategoryBudgets(month).isEmpty())
        assertNull(repository.loadCategoryBudget(month, "食物"))
        assertEquals(20000.0, repository.load(month)!!, 0.001)

        // 壞資料不會讓之後的寫入失效（重新寫入即可修復）
        repository.saveCategoryBudget(month, "食物", 500.0)
        assertEquals(500.0, repository.loadCategoryBudget(month, "食物")!!, 0.001)
    }
}
