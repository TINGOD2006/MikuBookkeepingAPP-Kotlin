package com.example.countapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自動記帳判斷邏輯的單元測試。
 *
 * 這段邏輯是「通知」與「無障礙讀屏」兩個來源共用的，也是最容易在實機上
 * 出錯（誤記廣告、記到尚未完成的轉帳頁）的部分，因此在純 JVM 上釘住行為。
 */
class AutoRecordDecisionTest {

    // ========== 基本交易 ==========

    @Test
    fun `支付成功通知會產生支出`() {
        val decision = AutoRecordDecisionMaker.decide("微信支付 支付成功 MOP 28.00 麥當勞")

        assertNotNull(decision)
        assertEquals(28.0, decision!!.amount, 0.001)
        assertFalse(decision.isIncome)
    }

    @Test
    fun `轉賬收入會產生收入`() {
        val decision = AutoRecordDecisionMaker.decide("收到 陳大文 轉賬 MOP 500")

        assertNotNull(decision)
        assertTrue(decision!!.isIncome)
        assertEquals(500.0, decision.amount, 0.001)
        assertTrue(decision.note.contains("陳大文"))
    }

    @Test
    fun `廣告推播不會被記錄`() {
        val ad = "MPay 限時優惠 支付成功即送 買一送一 消費 MOP100"

        assertNull(AutoRecordDecisionMaker.decide(ad))
    }

    @Test
    fun `非交易文字不會被記錄`() {
        assertNull(AutoRecordDecisionMaker.decide("你今天好嗎"))
        assertNull(AutoRecordDecisionMaker.decide(""))
        assertNull(AutoRecordDecisionMaker.decide("   "))
    }

    @Test
    fun `有交易句型但取不到金額不會被記錄`() {
        assertNull(AutoRecordDecisionMaker.decide("支付成功，詳情請開啟 App 查看"))
    }

    @Test
    fun `交易失敗不會被記錄`() {
        // 「失敗」在 PaymentTextAnalyzer 的句型裡也算交易訊號，這裡必須再擋一層
        assertNull(AutoRecordDecisionMaker.decide("支付失敗 MOP 100 餘額不足"))
        assertNull(AutoRecordDecisionMaker.decide("交易失敗 MOP 50"))
        assertNull(AutoRecordDecisionMaker.decide("付款已取消 MOP 30"))
        assertNull(AutoRecordDecisionMaker.decide("已退款 MOP 88 已退回錢包"))
    }

    // ========== 收支方向 ==========

    @Test
    fun `付款頁的收款方欄位不會讓支出變收入`() {
        // 付款頁常出現「收款方：麥當勞」；若直接拿整段文字判斷方向，
        // 「收款」會讓這筆支出被記成收入（方向與統計全錯）。
        val paymentPage = "支付成功 MOP 28.00 收款方：麥當勞 付款方式：餘額"

        val decision = AutoRecordDecisionMaker.decide(paymentPage, requireCompletionSignal = true)

        assertNotNull(decision)
        assertFalse(decision!!.isIncome)
    }

    @Test
    fun `付款方欄位不會影響收入判定`() {
        // 收入頁面常見「付款方：陳大文」；方向應該由「收款成功」決定
        val incomePage = "收款成功 MOP 500 付款方：陳大文"

        val decision = AutoRecordDecisionMaker.decide(incomePage, requireCompletionSignal = true)

        assertNotNull(decision)
        assertTrue(decision!!.isIncome)
    }

    @Test
    fun `收到轉賬也被視為已完成`() {
        val incomePage = "收到 陳大文 轉賬 MOP 500"

        val decision = AutoRecordDecisionMaker.decide(incomePage, requireCompletionSignal = true)

        assertNotNull(decision)
        assertTrue(decision!!.isIncome)
        assertEquals(500.0, decision.amount, 0.001)
    }

    @Test
    fun `未完成的轉賬請求頁不會被讀屏記錄`() {
        // 「收到」本身不是完成訊號：這些都是尚未完成的頁面
        assertNull(
            AutoRecordDecisionMaker.decide(
                "收到轉賬請求 MOP 100 請確認收款",
                requireCompletionSignal = true,
            ),
        )
        assertNull(
            AutoRecordDecisionMaker.decide(
                "待確認收款 MOP 100",
                requireCompletionSignal = true,
            ),
        )
        assertNull(
            AutoRecordDecisionMaker.decide(
                "請輸入金額 轉賬給 陳大文",
                requireCompletionSignal = true,
            ),
        )
        assertNull(
            AutoRecordDecisionMaker.decide(
                "確認付款 MOP 100 收款方：麥當勞",
                requireCompletionSignal = true,
            ),
        )
    }

    // ========== 讀屏來源要更嚴格 ==========

    @Test
    fun `讀屏預設會記到轉帳動詞的畫面所以需要成功字樣`() {
        // 「轉賬 MOP 100」這種輸入頁文字：一般判定會過，讀屏判定不該過
        val inputPage = "轉賬 收款人 陳大文 金額 MOP 100"

        assertNotNull(AutoRecordDecisionMaker.decide(inputPage))
        assertNull(AutoRecordDecisionMaker.decide(inputPage, requireCompletionSignal = true))
    }

    @Test
    fun `讀屏接受明確的付款成功畫面`() {
        val resultPage = "轉賬成功 已轉賬給 陳大文 MOP 100"

        val decision = AutoRecordDecisionMaker.decide(resultPage, requireCompletionSignal = true)

        assertNotNull(decision)
        assertEquals(100.0, decision!!.amount, 0.001)
    }

    @Test
    fun `讀屏接受已支付字樣`() {
        val resultPage = "您已支付 MOP 12.50 給 星巴克"

        val decision = AutoRecordDecisionMaker.decide(resultPage, requireCompletionSignal = true)

        assertNotNull(decision)
        assertEquals(12.5, decision!!.amount, 0.001)
    }

    // ========== 備註 ==========

    @Test
    fun `轉帳備註會帶對象與方向`() {
        assertEquals(
            "轉帳給 陳大文",
            AutoRecordDecisionMaker.buildNote("轉賬給 陳大文 MOP 100", "陳大文", isIncome = false),
        )
        assertEquals(
            "收到 陳大文 轉帳",
            AutoRecordDecisionMaker.buildNote("收到 陳大文 轉賬 MOP 100", "陳大文", isIncome = true),
        )
    }

    @Test
    fun `沒有對象時備註仍有內容`() {
        val decision = AutoRecordDecisionMaker.decide("支付成功 MOP 30")

        assertNotNull(decision)
        assertTrue(decision!!.note.isNotEmpty())
    }

    // ========== 指紋（去重） ==========

    @Test
    fun `指紋忽略空白與大小寫`() {
        val a = AutoRecordDecisionMaker.fingerprint("支付成功  MOP 100")
        val b = AutoRecordDecisionMaker.fingerprint("  支付成功    mop 100 ")

        assertEquals(a, b)
    }

    @Test
    fun `不同內容的指紋不同`() {
        assertTrue(
            AutoRecordDecisionMaker.fingerprint("支付成功 MOP 100") !=
                AutoRecordDecisionMaker.fingerprint("支付成功 MOP 200"),
        )
    }
}
