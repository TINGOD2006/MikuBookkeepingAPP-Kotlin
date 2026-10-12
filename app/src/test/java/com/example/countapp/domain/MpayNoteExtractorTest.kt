package com.example.countapp.domain

import org.junit.Assert.*
import org.junit.Test

class MpayNoteExtractorTest {
    @Test fun `支出收款轉帳備註只保留明確對象不夾帶成功或金額資訊`() {
        assertEquals("於 測試商戶 消費", MpayNoteExtractor.fromScreen("支付成功\n成功交易\nMOP 20\n商戶名稱\n測試商戶"))
        assertEquals("收到 測試付款人 收款", MpayNoteExtractor.fromScreen("收款成功\nMOP 20\n付款方\n測試付款人"))
        assertEquals("轉帳給 測試用戶", MpayNoteExtractor.fromScreen("您已向 測試用戶 轉賬\nMOP 0.10\n完成"))
        assertEquals("收到 測試用戶 轉帳", MpayNoteExtractor.fromScreen("轉入成功\nMOP 20\n付款方\n測試用戶\n交易類型\n轉賬"))
    }
    @Test fun `沒有可靠對象或未完成畫面不編造備註`() {
        assertNull(MpayNoteExtractor.fromScreen("支付成功\nMOP 20"))
        assertNull(MpayNoteExtractor.fromScreen("確認轉賬\nMOP 20\n收款方\n測試用戶"))
        assertNull(MpayNoteExtractor.fromScreen("支付成功\nMOP 20\n商戶名稱\n付款方式\n錢包餘額"))
    }

    @Test fun `Samsung三種詳情頁用標題對象補備註且轉帳仍只有收支方向`() {
        // 依實機欄位結構，對象、金額、時間與單號皆替換為虛構值。
        val expense = "交易詳情\n交易成功\n測試商戶\nMOP\n20.50\n付款方式\n錢包餘額\n付款項目\n消費\n付款時間\n2026-10-07 10:00:00\n交易編號\nTESTBILL00000001"
        val incoming = "交易詳情\n收款成功\n來自 測試付款人\nMOP\n1,000.00\n收款方式\n錢包餘額\n收款項目\n轉賬\n對方賬號\nTESTPHONE\n收款時間\n2026-10-07 10:01:00\n交易編號\nTESTBILL00000002"
        val outgoing = "交易詳情\n轉賬成功\n測試收款人\nMOP\n20.00\n付款方式\n錢包餘額\n付款項目\n轉賬\n付款時間\n2026-10-07 10:02:00\n交易編號\nTESTBILL00000003"
        val expenseReceipt = MpayScreenAnalyzer.receipt(expense, "測試商戶")!!
        val incomingReceipt = MpayScreenAnalyzer.receipt(incoming, "來自 測試付款人")!!
        val outgoingReceipt = MpayScreenAnalyzer.receipt(outgoing, "測試收款人")!!
        assertFalse(expenseReceipt.isIncome)
        assertTrue(incomingReceipt.isIncome)
        assertFalse(outgoingReceipt.isIncome)
        assertEquals("於 測試商戶 消費", MpayNoteExtractor.describe(expenseReceipt, expense))
        assertEquals("收到 測試付款人 轉帳", MpayNoteExtractor.describe(incomingReceipt, incoming))
        assertEquals("轉帳給 測試收款人", MpayNoteExtractor.describe(outgoingReceipt, outgoing))
        assertNull(MpayScreenAnalyzer.decide(outgoing))
    }

    @Test fun `歷史頁的轉帳功能按鈕不會把商戶消費描述為轉帳`() {
        val text = "交易詳情\n交易成功\nMOP 20\n付款方式\n錢包餘額\n付款項目\n消費\n再次轉賬"
        val receipt = MpayScreenAnalyzer.receipt(text, "測試商戶")!!
        assertEquals("於 測試商戶 消費", MpayNoteExtractor.describe(receipt, text))
    }
}
