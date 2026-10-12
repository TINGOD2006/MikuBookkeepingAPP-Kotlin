package com.example.countapp.domain

import com.example.countapp.data.Record
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class AnalysisRecordsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun record(id: String, time: String, amount: Double) = Record.create(amount, "其他", "",
        dateMillis = Instant.parse(time).toEpochMilli(), id = id)

    @Test fun `月界線按裝置時區分類而全部月份仍包含歷史資料`() {
        val records = listOf(record("sep", "2026-09-30T15:59:59Z", -10.0),
            record("oct", "2026-09-30T16:00:00Z", -20.0),
            record("income", "2026-10-01T00:00:00Z", 30.0))
        assertEquals(listOf("oct"), recordsForAnalysis(records, CategoryCatalog.TYPE_EXPENSE,
            YearMonth.of(2026, 10), zone).map { it.id })
        assertEquals(2, recordsForAnalysis(records, CategoryCatalog.TYPE_EXPENSE, null, zone).size)
        assertEquals(listOf("income"), recordsForAnalysis(records, CategoryCatalog.TYPE_INCOME,
            YearMonth.of(2026, 10), zone).map { it.id })
    }

    @Test fun `空月份與垃圾桶不計入圖表`() {
        val trashed = record("deleted", "2026-10-01T00:00:00Z", -100.0).copy(deletedAtMillis = 1L)
        assertTrue(recordsForAnalysis(listOf(trashed), CategoryCatalog.TYPE_EXPENSE, null, zone).isEmpty())
        assertTrue(recordsForAnalysis(listOf(record("old", "2025-01-01T00:00:00Z", -10.0)),
            CategoryCatalog.TYPE_EXPENSE, YearMonth.of(2026, 10), zone).isEmpty())
    }
}
