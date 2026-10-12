package com.example.countapp.notification

import com.example.countapp.domain.AutoRecordDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 舊版 ID 相容性；帳單身份的永久防重複另見 RecordReceiptTest。 */
class AutoRecordGuardTest {
    private val mpay = "com.macaupass.rechargeEasy"
    private val wechat = "com.tencent.mm"
    private val zone = ZoneId.of("Asia/Shanghai")

    /** 2026/06/30 14:32（+08:00）。 */
    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun decision(amount: Double, fingerprint: String, transactionId: String?) =
        AutoRecordDecision(
            amount = amount,
            isIncome = false,
            merchant = "麥當勞",
            note = "麥當勞",
            fingerprint = fingerprint,
            transactionId = transactionId,
        )

    @Test
    fun `有交易單號時同一筆交易永遠是同一個 id`() {
        val tx = "4200001234567890"
        val first = AutoRecordGuard.screenRecordId(
            wechat, decision(50.0, "fp_1", tx), millisOf(2026, 6, 30, 23, 58), zone,
        )
        // 跨過午夜才又讀到同一頁（舊版會因為時間桶不同而多記一筆）
        val later = AutoRecordGuard.screenRecordId(
            wechat, decision(50.0, "fp_1", tx), millisOf(2026, 7, 1, 0, 5), zone,
        )
        assertEquals(first, later)
    }

    @Test
    fun `沒有單號時同一天同畫面文字只會有一個 id`() {
        val morning = AutoRecordGuard.screenRecordId(
            mpay, decision(50.0, "fp_1", null), millisOf(2026, 6, 30, 9, 0), zone,
        )
        val evening = AutoRecordGuard.screenRecordId(
            mpay, decision(50.0, "fp_1", null), millisOf(2026, 6, 30, 21, 30), zone,
        )
        assertEquals(morning, evening)
    }

    @Test
    fun `沒有單號時不同日期是不同 id（不同天的同金額消費不能被吞掉）`() {
        val today = AutoRecordGuard.screenRecordId(
            mpay, decision(50.0, "fp_1", null), millisOf(2026, 6, 30, 9, 0), zone,
        )
        val tomorrow = AutoRecordGuard.screenRecordId(
            mpay, decision(50.0, "fp_1", null), millisOf(2026, 7, 1, 9, 0), zone,
        )
        assertNotEquals(today, tomorrow)
    }

    @Test
    fun `沒有單號時金額或畫面文字不同就是不同 id`() {
        val base = AutoRecordGuard.screenRecordId(
            mpay, decision(50.0, "fp_1", null), millisOf(2026, 6, 30, 9, 0), zone,
        )
        val otherAmount = AutoRecordGuard.screenRecordId(
            mpay, decision(51.0, "fp_1", null), millisOf(2026, 6, 30, 9, 0), zone,
        )
        val otherScreen = AutoRecordGuard.screenRecordId(
            mpay, decision(50.0, "fp_2", null), millisOf(2026, 6, 30, 9, 0), zone,
        )
        assertNotEquals(base, otherAmount)
        assertNotEquals(base, otherScreen)
    }
}
