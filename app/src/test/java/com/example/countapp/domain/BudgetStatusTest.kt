package com.example.countapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 預算使用狀態（[calculateBudgetStatus]）的單元測試。
 *
 * 這裡釘住的是預算頁「警示」的判斷規則：
 *   - 沒設定預算（含 0／負數／NaN）→ 不顯示進度、不顯示警示
 *   - 使用率 >= 80% → 警示；> 100% 才算超支（剛好 100% 不是超支）
 *
 * 這些數字同時決定「月份進度條」與「各分類進度條」的顏色與文字，
 * 因此必須是純函式（不碰 Android），才能在 JVM 上直接驗證。
 */
class BudgetStatusTest {

    // ========== 未設定預算 ==========

    @Test
    fun `沒有預算時等級是 UNKNOWN 且不顯示進度`() {
        val status = calculateBudgetStatus(spent = 500.0, budget = null)

        assertEquals(BudgetAlertLevel.UNKNOWN, status.level)
        assertFalse(status.isConfigured)
        assertFalse(status.isWarning)
        assertFalse(status.isOver)
        assertNull(status.budget)
        assertEquals(500.0, status.spent, 0.001)
        assertEquals(0f, status.progressFraction, 0.0001f)
        assertEquals(0, status.usedPercent)
        assertEquals(0.0, status.overspent, 0.001)
        assertEquals(0.0, status.remaining, 0.001)
    }

    @Test
    fun `預算為 0 視為未設定`() {
        val status = calculateBudgetStatus(spent = 500.0, budget = 0.0)

        assertEquals(BudgetAlertLevel.UNKNOWN, status.level)
        assertFalse(status.isConfigured)
        assertEquals(0f, status.progressFraction, 0.0001f)
        assertEquals(0, status.usedPercent)
    }

    @Test
    fun `預算為負數視為未設定`() {
        val status = calculateBudgetStatus(spent = 500.0, budget = -1000.0)

        assertEquals(BudgetAlertLevel.UNKNOWN, status.level)
        assertFalse(status.isConfigured)
    }

    @Test
    fun `預算為 NaN 或無限大視為未設定`() {
        assertEquals(
            BudgetAlertLevel.UNKNOWN,
            calculateBudgetStatus(500.0, Double.NaN).level,
        )
        assertEquals(
            BudgetAlertLevel.UNKNOWN,
            calculateBudgetStatus(500.0, Double.POSITIVE_INFINITY).level,
        )
        assertEquals(
            BudgetAlertLevel.UNKNOWN,
            calculateBudgetStatus(500.0, Double.NEGATIVE_INFINITY).level,
        )
    }

    @Test
    fun `花費為負數或 NaN 時視為 0`() {
        val negative = calculateBudgetStatus(spent = -300.0, budget = 1000.0)
        assertEquals(0.0, negative.spent, 0.001)
        assertEquals(BudgetAlertLevel.NORMAL, negative.level)
        assertEquals(1000.0, negative.remaining, 0.001)

        val nan = calculateBudgetStatus(spent = Double.NaN, budget = 1000.0)
        assertEquals(0.0, nan.spent, 0.001)
        assertEquals(BudgetAlertLevel.NORMAL, nan.level)
    }

    // ========== 使用率門檻 ==========

    @Test
    fun `使用率 0 趴是正常`() {
        val status = calculateBudgetStatus(spent = 0.0, budget = 1000.0)

        assertEquals(BudgetAlertLevel.NORMAL, status.level)
        assertEquals(0, status.usedPercent)
        assertEquals(0f, status.progressFraction, 0.0001f)
        assertEquals(1000.0, status.remaining, 0.001)
        assertEquals(0.0, status.overspent, 0.001)
    }

    @Test
    fun `未滿 80 趴不算警示`() {
        val status = calculateBudgetStatus(spent = 799.0, budget = 1000.0)

        assertEquals(BudgetAlertLevel.NORMAL, status.level)
        assertEquals(79, status.usedPercent)
    }

    @Test
    fun `剛好 80 趴算警示`() {
        val status = calculateBudgetStatus(spent = 800.0, budget = 1000.0)

        assertEquals(BudgetAlertLevel.WARNING, status.level)
        assertTrue(status.isWarning)
        assertFalse(status.isOver)
        assertEquals(80, status.usedPercent)
        assertEquals(0.8f, status.progressFraction, 0.0001f)
        assertEquals(200.0, status.remaining, 0.001)
        assertEquals(0.0, status.overspent, 0.001)
    }

    @Test
    fun `99 趴仍是警示尚未超支`() {
        val status = calculateBudgetStatus(spent = 990.0, budget = 1000.0)

        assertEquals(BudgetAlertLevel.WARNING, status.level)
        assertEquals(99, status.usedPercent)
        assertEquals(0.0, status.overspent, 0.001)
        assertEquals(10.0, status.remaining, 0.001)
    }

    @Test
    fun `剛好 100 趴是警示不是超支`() {
        val status = calculateBudgetStatus(spent = 1000.0, budget = 1000.0)

        assertEquals(BudgetAlertLevel.WARNING, status.level)
        assertTrue(status.isWarning)
        assertFalse(status.isOver)
        assertEquals(100, status.usedPercent)
        assertEquals(1f, status.progressFraction, 0.0001f)
        assertEquals(0.0, status.overspent, 0.001)
        assertEquals(0.0, status.remaining, 0.001)
    }

    @Test
    fun `超過 100 趴是超支並算出超出金額`() {
        val status = calculateBudgetStatus(spent = 1250.0, budget = 1000.0)

        assertEquals(BudgetAlertLevel.OVER, status.level)
        assertTrue(status.isOver)
        assertFalse(status.isWarning)
        assertEquals(125, status.usedPercent)
        assertEquals(250.0, status.overspent, 0.001)
        assertEquals(-250.0, status.remaining, 0.001)
        assertEquals(1.25, status.ratio, 0.0001)
        // 進度條比例夾在 0..1，超出的部分由警示文字表達，不會畫成超過滿格
        assertEquals(1f, status.progressFraction, 0.0001f)
    }

    @Test
    fun `進度條比例一律夾在 0 到 1 之間`() {
        assertEquals(1f, calculateBudgetStatus(3000.0, 100.0).progressFraction, 0.0001f)
        assertEquals(0f, calculateBudgetStatus(0.0, 100.0).progressFraction, 0.0001f)
    }

    @Test
    fun `小額預算的百分比採無條件捨去`() {
        val status = calculateBudgetStatus(spent = 1.0, budget = 3.0)

        assertEquals(33, status.usedPercent)
        assertEquals(BudgetAlertLevel.NORMAL, status.level)
    }

    @Test
    fun `月份與分類共用同一套門檻`() {
        // 月份：花費 9000、預算 10000；分類：花費 90、預算 100
        val month = calculateBudgetStatus(spent = 9000.0, budget = 10000.0)
        val category = calculateBudgetStatus(spent = 90.0, budget = 100.0)

        assertEquals(BudgetAlertLevel.WARNING, month.level)
        assertEquals(month.level, category.level)
        assertEquals(90, month.usedPercent)
        assertEquals(90, category.usedPercent)
    }
}
