package com.example.countapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 交易時間解析的單元測試。
 *
 * 這一段是「無障礙讀屏記出來的日期對不對」的關鍵：
 *   - 抓得到畫面（或分開的節點）上的交易時間
 *   - 抓不到、或抓到的是別的日期欄位時**必須回 null**，
 *     讓呼叫端退回當下時間，而不是把消費記到錯誤的日期
 */
class TransactionTimeParserTest {

    // 固定時區（澳門／香港皆為 UTC+8），避免測試結果隨執行環境改變
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 以裝置時區組出毫秒數（測試中所有期望值都用它表示）。 */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    /** 測試基準：2026/06/30 15:00。 */
    private val now: Long = at(2026, 6, 30, 15, 0)

    private fun parse(text: String, now: Long = this.now): Long? =
        TransactionTimeParser.parse(text, now, zone)

    // ========== 抓得到的情況 ==========

    @Test
    fun `破折號日期時間`() {
        assertEquals(at(2026, 6, 30, 14, 32, 5), parse("支付成功\n2026-06-30 14:32:05"))
    }

    @Test
    fun `斜線與標籤`() {
        assertEquals(at(2026, 6, 30, 14, 32), parse("交易時間：2026/06/30 14:32"))
    }

    @Test
    fun `中文年月日`() {
        assertEquals(at(2026, 6, 30, 14, 32), parse("交易時間 2026年6月30日 14:32"))
    }

    @Test
    fun `日期與時間是兩個節點（中間只有換行）`() {
        assertEquals(at(2026, 6, 30, 14, 32, 5), parse("支付成功\n2026-06-30\n14:32:05\n商戶全稱 麥當勞"))
    }

    @Test
    fun `沒有年份時用今年`() {
        assertEquals(at(2026, 6, 30, 14, 32), parse("交易時間 6月30日 14:32"))
    }

    @Test
    fun `跨年時沒有年份要用去年`() {
        // 2026/01/02 看「12月31日 23:10」→ 指的是 2025 年
        val newYear = at(2026, 1, 2, 10, 0)
        assertEquals(at(2025, 12, 31, 23, 10), parse("交易時間 12月31日 23:10", newYear))
    }

    @Test
    fun `今天與昨天`() {
        assertEquals(at(2026, 6, 30, 14, 32), parse("今天 14:32 支付成功"))
        assertEquals(at(2026, 6, 29, 21, 5), parse("昨天 21:05 支付成功"))
    }

    @Test
    fun `有標籤的交易時間優先於畫面上其他日期`() {
        // 兩組都在合理窗內（差一天），必須選有「交易時間」標籤的那一組
        val text = "訂單創建 2026-06-29 09:00\n交易時間 2026-06-30 14:32"
        assertEquals(at(2026, 6, 30, 14, 32), parse(text))
    }

    @Test
    fun `簡體時間標籤也認得`() {
        assertEquals(at(2026, 6, 30, 14, 32), parse("交易时间：2026-06-30 14:32"))
    }

    // ========== 抓不到／必須放棄的情況 ==========

    @Test
    fun `畫面只有時間沒有日期時放棄`() {
        assertNull(parse("支付成功\n14:32\nMOP 28.00"))
    }

    @Test
    fun `完全沒有日期時間`() {
        assertNull(parse("支付成功\nMOP 28.00\n商戶全稱 麥當勞"))
    }

    @Test
    fun `未來的日期不算交易時間`() {
        // 畫面上的優惠券到期日：不可以被當成交易時間
        assertNull(parse("優惠券有效期至 2026-08-30 23:59\n支付成功 MOP 28.00"))
    }

    @Test
    fun `太舊的日期不算交易時間`() {
        // 超過 7 天前 → 寧可放棄（呼叫端會退回當下時間）
        assertNull(parse("交易時間 2026-05-01 10:00"))
    }

    @Test
    fun `中間還有其他數字就不是同一組`() {
        assertNull(parse("2026-06-30 12345678 14:32"))
    }

    @Test
    fun `空字串與空白`() {
        assertNull(parse(""))
        assertNull(parse("   "))
    }

    @Test
    fun `不合法的日期不會拋例外`() {
        assertNull(parse("2026-02-30 10:00"))
        assertNull(parse("2026-13-01 10:00"))
    }

    @Test
    fun `秒數可有可無`() {
        assertEquals(at(2026, 6, 30, 9, 5), parse("2026-06-30 9:05"))
    }
}
