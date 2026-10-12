package com.example.countapp.data

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class BudgetLockTest {
    private val oct = YearMonth.of(2026, 10)
    @Test fun `鎖定總預算跨月跨年生效重開保持且不覆寫過去`() {
        val prefs = FakeSharedPreferences()
        val repo = BudgetRepository(prefs)
        repo.save(oct.minusMonths(1), 100.0); repo.save(oct, 500.0)
        assertTrue(repo.lock(oct, nowMonth = oct))
        val reopened = BudgetRepository(prefs)
        assertEquals(500.0, reopened.load(oct.plusMonths(4))!!, 0.01)
        assertEquals(100.0, reopened.load(oct.minusMonths(1))!!, 0.01)
        reopened.save(oct.plusMonths(1), 999.0)
        assertEquals(500.0, reopened.load(oct.plusMonths(1))!!, 0.01)
        assertTrue(reopened.isLocked(oct.plusMonths(1)))
    }

    @Test fun `解鎖保留已生效月份的金額後續可逐月設定`() {
        val repo = BudgetRepository(FakeSharedPreferences())
        repo.save(oct, 500.0); repo.lock(oct, nowMonth = oct)
        assertTrue(repo.unlock(nowMonth = oct.plusMonths(2)))
        assertEquals(500.0, repo.load(oct.plusMonths(1))!!, 0.01)
        assertFalse(repo.isLocked(oct.plusMonths(3)))
        assertNull(repo.load(oct.plusMonths(3)))
        repo.save(oct.plusMonths(2), 600.0)
        assertEquals(600.0, repo.load(oct.plusMonths(2))!!, 0.01)
    }

    @Test fun `分類鎖獨立於總額與其他分類且鎖定時不准清除`() {
        val repo = BudgetRepository(FakeSharedPreferences())
        repo.saveCategoryBudget(oct, "食物", 200.0)
        repo.saveCategoryBudget(oct, "交通", 100.0)
        assertTrue(repo.lock(oct, "食物", oct))
        assertEquals(mapOf("食物" to 200.0), repo.loadCategoryBudgets(oct.plusMonths(1)))
        repo.clearCategoryBudget(oct.plusMonths(1), "食物")
        assertEquals(200.0, repo.loadCategoryBudget(oct.plusMonths(1), "食物")!!, 0.01)
        assertNull(repo.load(oct.plusMonths(1)))
    }

    @Test fun `分類改名同步鎖定模板且歷史鎖名稱也攔衝突`() {
        val repo = BudgetRepository(FakeSharedPreferences())
        repo.saveCategoryBudget(oct, "食物", 200.0); repo.lock(oct, "食物", oct)
        repo.renameCategory("食物", "餐飲")
        assertTrue(repo.isLocked(oct.plusMonths(1), "餐飲"))
        assertFalse(repo.isLocked(oct.plusMonths(1), "食物"))
        assertEquals(200.0, repo.loadCategoryBudget(oct.plusMonths(1), "餐飲")!!, 0.01)
        assertFalse(repo.canRenameCategory("交通", "餐飲"))
    }

    @Test fun `無金額過去月份及無效金額不能鎖定`() {
        val repo = BudgetRepository(FakeSharedPreferences())
        assertFalse(repo.lock(oct, nowMonth = oct))
        repo.save(oct.minusMonths(1), 100.0)
        assertFalse(repo.lock(oct.minusMonths(1), nowMonth = oct))
        assertFalse(repo.isLocked(oct))
    }
}
