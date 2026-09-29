package com.example.countapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 支付通知文字分析的單元測試。
 *
 * 與 Flutter 版 `test/mpay_notification_test.dart`、`test/ad_filter_test.dart` 對應。
 */
class PaymentTextAnalyzerTest {

    /**
     * 實際被誤記成 268 元消費的 MPay 廣告全文（來自使用者的截圖）。
     * title 會與內容合併，所以刻意在前面加上 App 名稱。
     */
    private val adText =
        "MPay 澳門皇冠假日酒店限量秒殺 全年抵價 海鮮自助晚餐 268起 生蠔 三文魚刺身 " +
            "燒虎蝦任食 畀你五折體驗 即刻入mPass搶購 全年抵價 海鮮自助晚餐 26"

    // ========== 廣告過濾 ==========

    @Test
    fun `MPay 優惠推播會被判定為廣告`() {
        assertTrue(PaymentTextAnalyzer.isAdvertisement(adText))
        assertFalse(PaymentTextAnalyzer.isPaymentText(adText))
    }

    @Test
    fun `廣告文案中的價格不會被當成交易金額`() {
        // 舊版「關鍵字後 40 字內第一個整數」會抓到 268
        assertNull(PaymentTextAnalyzer.extractAmount(adText))
    }

    @Test
    fun `只有 App 名稱與成功字樣不算交易通知`() {
        assertFalse(PaymentTextAnalyzer.isPaymentText("MPay 成功登入，歡迎使用"))
        assertFalse(PaymentTextAnalyzer.isPaymentText("MPay 系統維護通知"))
        assertFalse(PaymentTextAnalyzer.isPaymentText("您的驗證碼是 123456"))
    }

    @Test
    fun `真實交易通知不會被誤判為廣告`() {
        val realNotifications = listOf(
            "轉賬成功 成功轉賬MOP1.00，點擊查看詳情。",
            "支付成功 成功交易 MOP13.00 點擊查看詳情",
            "支付成功 成功交易 MOP8.80 點擊查看詳情",
            "收到 阿強 轉賬 MOP\$50.00",
            "轉賬給 老陳 MOP\$20.00",
            "消費 HK\$1,234.50",
            "您已轉賬 MOP\$1.00 給 小明",
            "您已支付 12.00 元",
            "金額：HK\$ 12.00",
        )
        realNotifications.forEach { text ->
            assertFalse("不該被判為廣告: $text", PaymentTextAnalyzer.isAdvertisement(text))
            assertTrue("應被接受為交易通知: $text", PaymentTextAnalyzer.isPaymentText(text))
        }
    }

    // ========== 金額提取 ==========

    @Test
    fun `成功轉賬MOP1點00 提取 1 元`() {
        assertEquals(1.0, PaymentTextAnalyzer.extractAmount("轉賬成功 成功轉賬MOP1.00，點擊查看詳情。")!!, 1e-9)
    }

    @Test
    fun `MOP 與金額之間可有空格`() {
        assertEquals(1.0, PaymentTextAnalyzer.extractAmount("成功轉賬 MOP 1.00")!!, 1e-9)
    }

    @Test
    fun `MOP 加貨幣符號`() {
        assertEquals(1.0, PaymentTextAnalyzer.extractAmount("您已轉賬 MOP\$1.00 給 小明")!!, 1e-9)
    }

    @Test
    fun `千分位小數`() {
        assertEquals(1234.5, PaymentTextAnalyzer.extractAmount("消費 HK\$1,234.50")!!, 1e-9)
    }

    @Test
    fun `金額欄位`() {
        assertEquals(12.0, PaymentTextAnalyzer.extractAmount("金額：HK\$ 12.00")!!, 1e-9)
    }

    @Test
    fun `時間不會被誤判為金額`() {
        assertEquals(1.0, PaymentTextAnalyzer.extractAmount("03:49:39 成功轉賬MOP1.00")!!, 1e-9)
    }

    @Test
    fun `日期不會被誤判為金額`() {
        assertEquals(5.0, PaymentTextAnalyzer.extractAmount("2026-09-10 轉賬 5.00 成功")!!, 1e-9)
    }

    @Test
    fun `長訂單號不會被誤判為金額`() {
        assertEquals(
            1.0,
            PaymentTextAnalyzer.extractAmount("訂單號 2026091003453572166504，成功轉賬MOP1.00")!!,
            1e-9,
        )
    }

    @Test
    fun `沒有金額時回傳 null`() {
        assertNull(PaymentTextAnalyzer.extractAmount("轉賬成功，點擊查看詳情。"))
    }

    // ========== 商家與方向 ==========

    @Test
    fun `extractMerchant 支援給格式`() {
        assertEquals("翁", PaymentTextAnalyzer.extractMerchant("您已轉賬 MOP\$1.00 給 翁"))
    }

    @Test
    fun `extractMerchant 支援向某某轉賬`() {
        assertEquals("小明", PaymentTextAnalyzer.extractMerchant("向 小明 轉賬 MOP\$10.00"))
    }

    @Test
    fun `extractMerchant 支援收到某某轉賬`() {
        assertEquals("阿強", PaymentTextAnalyzer.extractMerchant("收到 阿強 轉賬 MOP\$50.00"))
    }

    @Test
    fun `extractMerchant 無對象時回傳 null`() {
        assertNull(PaymentTextAnalyzer.extractMerchant("成功轉賬MOP1.00，點擊查看詳情。"))
    }

    @Test
    fun `收到款項判定為收入`() {
        assertTrue(PaymentTextAnalyzer.isIncomeTransfer("收到 阿強 轉賬 MOP\$50.00"))
        assertFalse(PaymentTextAnalyzer.isIncomeTransfer("成功轉賬MOP1.00"))
    }
}
