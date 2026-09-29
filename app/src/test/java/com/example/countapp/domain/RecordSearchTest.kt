package com.example.countapp.domain

import com.example.countapp.data.Record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 「尋找」頁的搜尋邏輯單元測試（純 Kotlin，不需要 Android）。
 *
 * 尋找頁不分月份，會在使用者全部記錄裡比對分類、備註、金額與日期。
 */
class RecordSearchTest {

    private val food = record(amount = -120.0, category = "食物", note = "午餐 便當", date = date(2026, 9, 10))
    private val salary = record(amount = 30000.0, category = "薪水", note = "", date = date(2026, 9, 1))
    private val coffee = record(amount = -85.5, category = "咖啡", note = "Latte", date = date(2026, 8, 20))
    private val all = listOf(salary, food, coffee)

    @Test
    fun `空查詢回傳全部記錄`() {
        assertEquals(all, RecordSearch.query(all, ""))
        assertEquals(all, RecordSearch.query(all, "   "))
    }

    @Test
    fun `可用分類搜尋`() {
        assertEquals(listOf(food), RecordSearch.query(all, "食物"))
        assertEquals(listOf(coffee), RecordSearch.query(all, "咖啡"))
    }

    @Test
    fun `可用備註搜尋且不分大小寫`() {
        assertEquals(listOf(food), RecordSearch.query(all, "便當"))
        assertEquals(listOf(coffee), RecordSearch.query(all, "latte"))
        assertEquals(listOf(coffee), RecordSearch.query(all, "LATTE"))
    }

    @Test
    fun `可用金額搜尋`() {
        assertEquals(listOf(food), RecordSearch.query(all, "120"))
        assertEquals(listOf(coffee), RecordSearch.query(all, "85.5"))
    }

    @Test
    fun `支出可以用負號搜尋`() {
        assertEquals(listOf(food), RecordSearch.query(all, "-120"))
    }

    @Test
    fun `可用日期搜尋`() {
        assertEquals(listOf(salary, food), RecordSearch.query(all, "2026/09"))
        assertEquals(listOf(food), RecordSearch.query(all, "9/10"))
    }

    @Test
    fun `關鍵字前後空白會被忽略`() {
        assertEquals(listOf(food), RecordSearch.query(all, "  食物  "))
    }

    @Test
    fun `找不到時回傳空清單`() {
        assertTrue(RecordSearch.query(all, "不存在的字串").isEmpty())
    }

    @Test
    fun `維持原本的排序`() {
        val result = RecordSearch.query(all, "2026")
        assertEquals(listOf(salary, food, coffee), result)
    }

    @Test
    fun `自訂分類也搜得到`() {
        val custom = record(amount = -50.0, category = "寵物美容", note = "", date = date(2026, 9, 5))
        assertEquals(listOf(custom), RecordSearch.query(all + custom, "寵物"))
    }

    // ========== 輔助 ==========

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun record(amount: Double, category: String, note: String, date: Long): Record =
        Record.create(
            amount = amount,
            category = category,
            note = note,
            dateMillis = date,
            createdAtMillis = date,
            id = "$category-$amount",
        )
}
