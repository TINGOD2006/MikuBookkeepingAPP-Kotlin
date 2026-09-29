package com.example.countapp.domain

import com.example.countapp.data.Record

/**
 * 記錄搜尋。
 *
 * 給獨立的「尋找」頁使用：不分月份，直接在使用者所有記錄裡找。
 * 刻意做成純 Kotlin（不依賴 Android / Compose），因此可以用 JVM 單元測試釘住行為。
 *
 * 可搜尋的欄位：
 *   - 分類名稱（例：食物、薪水、自訂的「咖啡」）
 *   - 備註
 *   - 金額（例：120、120.5；支出輸入 -120 也找得到）
 *   - 日期（例：2026/09、9/10）
 *
 * 比對不分大小寫，前後空白會被忽略；空查詢回傳原清單。
 */
object RecordSearch {

    /** 依關鍵字篩選，維持傳入清單的順序。 */
    fun query(records: List<Record>, rawQuery: String): List<Record> {
        val keyword = rawQuery.trim().lowercase()
        if (keyword.isEmpty()) return records
        return records.filter { matches(it, keyword) }
    }

    private fun matches(record: Record, keyword: String): Boolean {
        if (record.category.lowercase().contains(keyword)) return true
        if (record.note.lowercase().contains(keyword)) return true

        val amount = AmountFormatter.format(record.absoluteAmount)
        if (amount.contains(keyword)) return true
        // 「-120」這種帶正負號的輸入也要能命中支出
        if (record.isExpense && "-$amount".contains(keyword)) return true

        // 日期：完整日期（2026/09/10）或月份、月/日片段都可比對
        if (Record.formatDate(record.localDate()).contains(keyword)) return true

        return false
    }
}
