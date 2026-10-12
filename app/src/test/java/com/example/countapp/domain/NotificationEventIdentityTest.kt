package com.example.countapp.domain

import com.example.countapp.data.*
import org.junit.Assert.*
import org.junit.Test

class NotificationEventIdentityTest {
    private val pkg = PaymentNotificationAnalyzer.MPAY_PACKAGE
    private fun notification(key: String, time: Long) = Record.create(-50.0, "其他", "轉賬支出",
        dateMillis = time, id = NotificationEventIdentity.id(pkg, key, time),
        sourcePackage = pkg, sourceChannel = "notification")

    @Test fun `同通知更新去重但同額不同通知與同ID下一次交易均保留`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val first = notification("mpay:1", 1000L)
        assertEquals(AutoRecordResult.ADDED, repo.ingestAutomatic(first))
        assertEquals(AutoRecordResult.DUPLICATE, repo.ingestAutomatic(first.copy(note = "已更新通知內容")))
        assertEquals(AutoRecordResult.ADDED, repo.ingestAutomatic(notification("mpay:2", 1000L)))
        assertEquals(AutoRecordResult.ADDED, repo.ingestAutomatic(notification("mpay:1", 1001L)))
        assertEquals(3, repo.snapshot().size)
    }

    @Test fun `無編號成功頁與通知任何順序只由通知新增一次`() {
        for (screenFirst in listOf(true, false)) {
            val repo = RecordRepository(FakeSharedPreferences())
            val notice = notification("mpay:1", 1000L)
            val screen = notice.copy(id = "screen", sourceChannel = "accessibility")
            fun readScreen() = repo.ingestAutomatic(screen,
                allowCreate = AutoRecordMode.ACCESSIBILITY.canCreateFromScreen(pkg))
            if (screenFirst) assertEquals(AutoRecordResult.IGNORED, readScreen())
            assertEquals(AutoRecordResult.ADDED, repo.ingestAutomatic(notice))
            assertEquals(AutoRecordResult.IGNORED, readScreen())
            assertEquals(1, repo.snapshot().size)
            assertNull(repo.snapshot().single().transactionId)
        }
    }

    @Test fun `舊版通知重送遷移後內容更新與永久刪除均不能重新入帳`() {
        val prefs = FakeSharedPreferences()
        val repo = RecordRepository(prefs)
        val legacy = notification("mpay:1", 1000L).copy(id =
            NotificationEventIdentity.legacyId(pkg, 50.0, "mpay:1", "轉賬成功 MOP 50", 1000L))
        repo.ingestAutomatic(legacy)
        val updated = notification("mpay:1", 1000L).copy(note = "已更新內容")
        assertTrue(repo.rememberAutomaticEventAlias(legacy.id, updated.id))
        assertEquals(AutoRecordResult.DUPLICATE, repo.ingestAutomatic(updated))
        repo.deleteById(legacy.id)
        assertEquals(AutoRecordResult.DUPLICATE, RecordRepository(prefs).ingestAutomatic(updated))
        assertFalse(repo.rememberAutomaticEventAlias("不存在的通知", "另一筆通知"))
    }

    @Test fun `舊通知只剩短期processed集合仍須永久遷移避免淘汰後再記`() {
        val prefs = FakeSharedPreferences()
        val repo = RecordRepository(prefs)
        val updated = notification("mpay:1", 1000L)
        // 對應服務命中 legacy processed 集合，帳目早已移除的分支。
        assertTrue(repo.rememberAutomaticEventAlias("old-event", updated.id, wasProcessed = true))
        prefs.edit().remove("processed_notification_ids").apply()
        assertEquals(AutoRecordResult.DUPLICATE, RecordRepository(prefs).ingestAutomatic(updated))
        // 首次升級的 active 基準也永久登記，而非僅加入最多 1000 項的集合。
        val baseline = notification("mpay:2", 1000L)
        assertTrue(repo.rememberAutomaticEventAlias(baseline.id, baseline.id, wasProcessed = true))
        assertEquals(AutoRecordResult.DUPLICATE, RecordRepository(prefs).ingestAutomatic(baseline))
    }
}
