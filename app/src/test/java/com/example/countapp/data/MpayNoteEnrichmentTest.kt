package com.example.countapp.data

import org.junit.Assert.*
import org.junit.Test

class MpayNoteEnrichmentTest {
    private val pkg = "com.macaupass.rechargeEasy"
    private fun record(id: String, time: Long?, bill: String? = null) = Record.create(-20.0, "其他", "支付成功 成功交易20元",
        dateMillis = 1000L, id = id, sourcePackage = pkg, sourceChannel = "notification",
        transactionTimeMillis = time, transactionId = bill, automaticNote = "支付成功 成功交易20元")
    private fun evidence(time: Long?, bill: String? = null) = record("screen", time, bill).copy(
        sourceChannel = "accessibility", note = "於 測試商戶 消費", automaticNote = "於 測試商戶 消費")

    @Test fun `編號或唯一共同原交易時間可補備註而不改金額分類日期`() {
        for (bill in listOf(null, "BILL0001")) {
            val repo = RecordRepository(FakeSharedPreferences())
            val original = record("notification", 1000L, bill)
            repo.ingestAutomatic(original)
            assertTrue(repo.enrichAutomaticNote(evidence(1000L, bill)))
            val updated = repo.snapshot().single()
            assertEquals("於 測試商戶 消費", updated.note)
            assertEquals(original.id, updated.id)
            assertEquals(original.amount, updated.amount, 0.001)
            assertEquals(original.dateMillis, updated.dateMillis)
            assertEquals(original.category, updated.category)
            assertEquals(updated, Record.fromJson(updated.toJson()))
        }
    }
    @Test fun `用戶保存過備註或同額但缺共同身份均不得自動覆寫`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val original = record("notification", null)
        repo.ingestAutomatic(original)
        assertFalse(repo.enrichAutomaticNote(evidence(null)))
        assertFalse(repo.enrichAutomaticNote(evidence(1000L)))
        repo.update(original.copy(transactionId = "BILL0001").edited(-20.0, "其他", "我的午餐", 1000L))
        assertFalse(repo.enrichAutomaticNote(evidence(null, "BILL0001")))
        assertEquals("我的午餐", repo.snapshot().single().note)
    }
    @Test fun `同額同時多個候選與垃圾桶不補備註`() {
        val repo = RecordRepository(FakeSharedPreferences())
        repo.ingestAutomatic(record("one", 1000L))
        repo.ingestAutomatic(record("two", 1000L))
        assertFalse(repo.enrichAutomaticNote(evidence(1000L)))
        repo.moveToTrash("one"); repo.moveToTrash("two")
        assertFalse(repo.enrichAutomaticNote(evidence(1000L)))
    }
}
