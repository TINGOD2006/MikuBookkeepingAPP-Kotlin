package com.example.countapp.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RecordReceiptTest {
    private val mpay = "com.macaupass.rechargeEasy"
    private fun payment(bill: String?, id: String, channel: String = "accessibility", time: Long? = 123456L) =
        Record.create(-50.0, "食物", "午餐", dateMillis = 123456L, id = id,
            sourcePackage = mpay, sourceChannel = channel, transactionId = bill, transactionTimeMillis = time)

    @Test fun `升級前已有單號帳目在永久移除後也不再記錄`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(RecordRepository.KEY_RECORDS,
            """[{"id":"a11y_com.macaupass.rechargeEasy|tx|OLD00001","category":"其他","amount":-50,"date":1,"createdAt":1}]""").apply()
        val repo = RecordRepository(prefs)
        repo.deleteById("a11y_com.macaupass.rechargeEasy|tx|OLD00001")
        assertEquals(AutoRecordResult.DUPLICATE, RecordRepository(prefs).ingestAutomatic(payment("OLD00001", "notification")))
    }

    @Test fun `同額不同編號都記錄同編號跨來源重開及清空垃圾桶不重記`() {
        val prefs = FakeSharedPreferences()
        val repo = RecordRepository(prefs)
        assertEquals(AutoRecordResult.ADDED, repo.ingestAutomatic(payment("BILL0001", "screen1")))
        assertEquals(AutoRecordResult.ADDED, repo.ingestAutomatic(payment("BILL0002", "screen2")))
        assertEquals(AutoRecordResult.DUPLICATE, repo.ingestAutomatic(payment("BILL0001", "notification1", "notification")))
        repo.moveToTrash("screen1"); repo.emptyTrash()
        assertEquals(AutoRecordResult.DUPLICATE, RecordRepository(prefs).ingestAutomatic(payment("BILL0001", "later")))
        assertEquals(1, repo.snapshot().size)
    }

    @Test fun `同編號不同支付應用不衝突`() {
        val repo = RecordRepository(FakeSharedPreferences())
        assertEquals(AutoRecordResult.ADDED, repo.ingestAutomatic(payment("BILL0001", "mpay")))
        assertEquals(AutoRecordResult.ADDED, repo.ingestAutomatic(payment("BILL0001", "wechat").copy(sourcePackage = "com.tencent.mm")))
    }

    @Test fun `歷史詳情只補相同時間的唯一既有帳目不改用戶編輯內容`() {
        val prefs = FakeSharedPreferences()
        val repo = RecordRepository(prefs)
        val original = payment(null, "notification1", "notification")
        repo.ingestAutomatic(original)
        repo.update(original.copy(category = "交通", note = "自訂", amount = -60.0))
        // 已改金額不再能靠金額推斷身分，拒絕猜測。
        assertEquals(AutoRecordResult.IGNORED, repo.ingestAutomatic(payment("BILL0001", "detail"), allowCreate = false))
        repo.update(original.copy(category = "交通", note = "自訂"))
        assertEquals(AutoRecordResult.ENRICHED, repo.ingestAutomatic(payment("BILL0001", "detail"), allowCreate = false))
        val updated = repo.snapshot().single()
        assertEquals("notification1", updated.id)
        assertEquals("自訂", updated.note)
        assertEquals("交通", updated.category)
        assertEquals("BILL0001", updated.transactionId)
        assertEquals(updated, RecordRepository(prefs).snapshot().single())
    }

    @Test fun `沒有共同交易時間不能用金額合併且歷史頁絕不新增`() {
        val repo = RecordRepository(FakeSharedPreferences())
        repo.ingestAutomatic(payment(null, "n1", "notification", null))
        assertEquals(AutoRecordResult.IGNORED, repo.ingestAutomatic(payment("BILL0001", "detail"), false))
        assertNull(repo.snapshot().single().transactionId)
        assertEquals(AutoRecordResult.ADDED, repo.ingestAutomatic(payment(null, "n2", "notification", null)))
        assertEquals(2, repo.snapshot().size)
    }

    @Test fun `多筆相同時間同額候選不猜測編號`() {
        val repo = RecordRepository(FakeSharedPreferences())
        repo.ingestAutomatic(payment(null, "n1", "notification"))
        repo.ingestAutomatic(payment(null, "n2", "notification"))
        assertEquals(AutoRecordResult.IGNORED, repo.ingestAutomatic(payment("BILL0001", "detail"), false))
        assertTrue(repo.snapshot().all { it.transactionId == null })
    }

    @Test fun `來源編號與原交易時間在編輯及JSON往返後保留舊版可讀`() {
        val record = payment("BILL0001", "id")
        val edited = record.edited(-10.0, "交通", "新備註", 999L)
        assertEquals("BILL0001", edited.transactionId)
        assertEquals(123456L, edited.transactionTimeMillis)
        assertEquals(edited, Record.fromJson(edited.toJson()))
        val legacy = Record.fromJson(JSONObject("""{"id":"a11y_com.macaupass.rechargeEasy|tx|OLD00001","category":"其他","amount":-1,"date":1,"createdAt":1}"""))!!
        assertEquals(mpay, legacy.sourcePackage)
        assertEquals("OLD00001", legacy.transactionId)
        assertEquals("MPay", legacy.sourceLabel)
    }
}
