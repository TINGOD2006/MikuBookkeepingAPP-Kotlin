package com.example.countapp.notification

import com.example.countapp.data.FakeSharedPreferences
import com.example.countapp.domain.AutoRecordDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 自動記帳去重的單元測試。
 *
 * 這一段是「同一筆消費會不會被記兩次／會不會被誤吞」的關鍵：
 *   - 跨來源（付款通知 vs 讀屏畫面）用包名 + 方向 + 金額的 90 秒時間窗
 *   - 同一個畫面文字用持久化紀錄擋掉（服務被系統重啟也有效）
 */
class AutoRecordGuardTest {

    private val mpay = "com.macaupass.rechargeEasy"
    private val wechat = "com.tencent.mm"

    @Before
    fun setUp() {
        // 每個測試從乾淨的記憶體狀態開始
        AutoRecordGuard.reset()
    }

    // ========== 跨來源即時去重 ==========

    @Test
    fun `時間窗內同一筆消費只能記一次`() {
        val now = 1_000_000L

        assertTrue(AutoRecordGuard.tryClaim(mpay, 50.0, isIncome = false, now = now))
        assertFalse(AutoRecordGuard.tryClaim(mpay, 50.0, isIncome = false, now = now + 1_000))
    }

    @Test
    fun `收入與支出金額相同不會被誤認為同一筆`() {
        val now = 1_000_000L

        // 付 50 之後馬上收到 50：兩筆都必須留下來
        assertTrue(AutoRecordGuard.tryClaim(mpay, 50.0, isIncome = false, now = now))
        assertTrue(AutoRecordGuard.tryClaim(mpay, 50.0, isIncome = true, now = now + 1))
    }

    @Test
    fun `不同金額或不同 App 各自獨立`() {
        val now = 1_000_000L

        assertTrue(AutoRecordGuard.tryClaim(mpay, 50.0, isIncome = false, now = now))
        assertTrue(AutoRecordGuard.tryClaim(mpay, 51.0, isIncome = false, now = now))
        assertTrue(AutoRecordGuard.tryClaim(wechat, 50.0, isIncome = false, now = now))
    }

    @Test
    fun `超過時間窗後同一筆金額可以再記`() {
        val now = 1_000_000L

        assertTrue(AutoRecordGuard.tryClaim(mpay, 50.0, isIncome = false, now = now))
        assertTrue(
            AutoRecordGuard.tryClaim(
                mpay,
                50.0,
                isIncome = false,
                now = now + AutoRecordGuard.WINDOW_MS + 1,
            ),
        )
    }

    @Test
    fun `去重鍵含方向`() {
        assertNotEquals(
            AutoRecordGuard.claimKey(mpay, 50.0, isIncome = false),
            AutoRecordGuard.claimKey(mpay, 50.0, isIncome = true),
        )
    }

    // ========== 同一個畫面的持久化去重 ==========

    @Test
    fun `同一個畫面文字在時間窗內不會重複記錄`() {
        val prefs = FakeSharedPreferences()
        val now = 1_000_000L
        val fingerprint = "abc123_7"

        assertFalse(AutoRecordGuard.isScreenRecorded(prefs, mpay, fingerprint, now))
        AutoRecordGuard.rememberScreen(prefs, mpay, fingerprint, now)

        assertTrue(AutoRecordGuard.isScreenRecorded(prefs, mpay, fingerprint, now + 60_000))
    }

    @Test
    fun `畫面紀錄可以跨服務重啟`() {
        val prefs = FakeSharedPreferences()
        val now = 1_000_000L

        AutoRecordGuard.rememberScreen(prefs, mpay, "same-screen", now)
        AutoRecordGuard.reset() // 模擬服務被系統重啟（記憶體狀態清空）

        assertTrue(AutoRecordGuard.isScreenRecorded(prefs, mpay, "same-screen", now + 60_000))
    }

    @Test
    fun `畫面紀錄過期後可以再記`() {
        val prefs = FakeSharedPreferences()
        val now = 1_000_000L

        AutoRecordGuard.rememberScreen(prefs, mpay, "old-screen", now)

        val later = now + AutoRecordGuard.SCREEN_TTL_MS + 1
        assertFalse(AutoRecordGuard.isScreenRecorded(prefs, mpay, "old-screen", later))
    }

    @Test
    fun `不同畫面文字互不影響`() {
        val prefs = FakeSharedPreferences()
        val now = 1_000_000L

        AutoRecordGuard.rememberScreen(prefs, mpay, "screen-a", now)

        assertFalse(AutoRecordGuard.isScreenRecorded(prefs, mpay, "screen-b", now + 1))
        // 不同 App 也是一樣
        assertFalse(AutoRecordGuard.isScreenRecorded(prefs, wechat, "screen-a", now + 1))
    }

    // ========== 讀屏記錄 id 的穩定性（同一筆讀很多次也不重複） ==========

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
