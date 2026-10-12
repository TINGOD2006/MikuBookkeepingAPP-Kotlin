package com.example.countapp.domain

import org.junit.Assert.*
import org.junit.Test

class MpayCaptureGateTest {
    private val base = java.time.LocalDateTime.of(2026, 10, 5, 10, 1, 2).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val completion = "支付成功\n成功交易\nMOP 50\n點擊查看詳情"
    private val detail = "轉賬詳情\n轉賬成功\n交易金額\nMOP 50\n交易編號\nMPAY00001\n交易時間\n2026-10-05 10:01:02"

    @Test fun `實機轉帳詳情節點保留原單號但反覆查看不建立帳目`() {
        // 保留實機欄位結構，姓名電話編號均以測試值替換。
        val text = "交易詳情\n轉賬成功\n轉給\u00a0測試用戶\nMOP\n0.10\n付款方式\n錢包餘額\n付款項目\n轉賬\n對方賬號\n+853 60000000\n對方姓名\nTEST U***\n付款時間\n2026-10-04 04:15:24\n交易編號\n2026100404152098300000\n立即轉賬\n查看來往紀錄"
        val receipt = MpayScreenAnalyzer.receipt(text)!!
        assertEquals("2026100404152098300000", receipt.transactionId)
        assertEquals(0.10, receipt.amount, 0.001)
        assertFalse(receipt.isIncome)
        assertEquals("測試用戶", receipt.merchant)
        val gate = MpayCaptureGate()
        repeat(3) { assertNull(gate.evaluate(text, "com.macaupass.mvp.recordList.v.ActivityPayTradeDetail", emptySet(), base + it)) }
    }

    @Test fun `查看歷史詳情及反覆開啟不允許新增`() {
        val gate = MpayCaptureGate()
        repeat(3) { assertNull(gate.evaluate(detail, "ActivityTransferDetail", emptySet(), (base + 1000L) + it)) }
        assertNull(PaymentScreenAnalyzer.decide("com.macaupass.rechargeEasy", detail))
        assertEquals("MPAY00001", MpayScreenAnalyzer.receipt(detail)?.transactionId)
    }

    @Test fun `新完成頁無須編號反覆事件只接受一次詳情仍不能新增`() {
        val gate = MpayCaptureGate()
        val result = gate.evaluate(completion, "ActivityPayResult", emptySet(), base + 1000L)
        assertNotNull(result)
        assertNull(result?.transactionId)
        assertNull(gate.evaluate(completion, "ActivityPayResult", emptySet(), base + 1001L))
        assertNull(gate.evaluate(detail, "ActivityTransferDetail", emptySet(), (base + 2000L)))
        assertNull(gate.evaluate(detail, "ActivityTransferDetail", emptySet(), (base + 2001L)))
    }

    @Test fun `離開結果頁後另一筆相同金額無編號成功頁仍可辨識`() {
        val gate = MpayCaptureGate()
        assertNotNull(gate.evaluate(completion, "ActivityPayResult", emptySet(), base))
        gate.evaluate("輸入金額", "ActivityTransferForm", emptySet(), base + 1L)
        assertNotNull(gate.evaluate(completion, "ActivityPayResult", emptySet(), base + 2L))
    }

    @Test fun `實機已向用戶轉賬成功頁無編號也能解析但輸入及歷史頁仍拒絕`() {
        val text = "您已向 測試用戶 轉賬\nTEST U***\nMOP 0.10\n完成\n分享到\n微信\nWhatsApp"
        val gate = MpayCaptureGate()
        val result = gate.evaluate(text, "com.macaupass.mvp.transferpay.v.ActivityTransferEnd", emptySet(), base)
        assertNotNull(result)
        assertEquals(0.10, result!!.amount, 0.001)
        assertFalse(result.isIncome)
        assertEquals("測試用戶", result.merchant)
        assertNull(result.transactionId)
        assertNull(gate.evaluate(text, "ActivityTransferDetail", emptySet(), base + 1L))
        assertNull(gate.evaluate(text.replace("您已向", "向"), "ActivityTransferForm", emptySet(), base + 2L))
    }

    @Test fun `經歷史列表或返回主頁後不能把後看的帳單當新交易`() {
        for (middle in listOf("我的錢包\n賬單", "首頁\n付款\n收款")) {
            val gate = MpayCaptureGate()
            gate.evaluate(completion, "ActivityPayResult", emptySet(), (base + 1000L))
            gate.evaluate(middle, "ActivityUsrWallet", setOf("tv_wallet_trade_time"), (base + 1100L))
            assertNull(gate.evaluate(detail, "ActivityTransferDetail", emptySet(), (base + 2000L)))
        }
    }

    @Test fun `錯金額與超時詳情不能套用舊完成頁`() {
        val gate = MpayCaptureGate()
        gate.evaluate(completion, "ActivityPayResult", emptySet(), (base + 1000L))
        assertNull(gate.evaluate(detail.replace("MOP 50", "MOP 51"), "ActivityTransferDetail", emptySet(), (base + 1100L)))
        gate.evaluate(completion, "ActivityPayResult", emptySet(), (base + 100000L))
        assertNull(gate.evaluate(detail, "ActivityTransferDetail", emptySet(), (base + 300000L)))
    }

    @Test fun `新完成後開同額舊帳單也不能視為新交易`() {
        val gate = MpayCaptureGate()
        gate.evaluate(completion, "ActivityPayResult", emptySet(), base + 1000L)
        assertNull(gate.evaluate(detail.replace("10:01:02", "09:01:02"), "ActivityTransferDetail", emptySet(), base + 2000L))
    }

    @Test fun `離開付款流程或等待超時後不再授權詳情新增`() {
        val gate = MpayCaptureGate()
        gate.evaluate(completion, "ActivityPayResult", emptySet(), base)
        gate.reset()
        assertNull(gate.evaluate(detail, "ActivityTransferDetail", emptySet(), base + 1000L))
        gate.evaluate(completion, "ActivityPayResult", emptySet(), base)
        assertNull(gate.evaluate(detail, "ActivityTransferDetail", emptySet(), base + 30_002L))
    }

    @Test fun `付款結果有編號可新增而不同同額帳單各自接受`() {
        val gate = MpayCaptureGate()
        val first = completion + "\n交易編號\nMPAY00001"
        val second = completion + "\n交易編號\nMPAY00002"
        assertNotNull(gate.evaluate(first, "ActivityPayResult", emptySet(), base))
        assertNull(gate.evaluate(first, "ActivityPayResult", emptySet(), base + 1L))
        assertNotNull(gate.evaluate(second, "ActivityPayResult", emptySet(), base + 2L))
        assertNull(gate.evaluate(detail.replace("10:01:02", "10:00:02"), "ActivityTransferDetail", emptySet(), base + 3L))
    }

    @Test fun `實機活動命名空間不同仍沿用活動上下文到widget事件`() {
        val context = PaymentWindowContext()
        val pkg = "com.macaupass.rechargeEasy"
        val activity = "com.macaupass.mvp.pay.v.ActivityPayResult"
        assertEquals(activity, context.observe(pkg, activity, true))
        assertEquals(activity, context.observe(pkg, "android.widget.TextView", false))
        val gate = MpayCaptureGate()
        assertNotNull(gate.evaluate("支付成功\nMOP 50\n交易編號\nMPAY00001",
            context.observe(pkg, "android.widget.TextView", false), emptySet(), base))
    }

    @Test fun `輸入待確認退款失敗頁以及非付款視窗不記`() {
        val gate = MpayCaptureGate()
        for (prefix in listOf("確認轉賬", "轉賬請求", "待付款", "退款成功", "交易失敗")) {
            assertNull(gate.evaluate("$prefix\n支付成功\nMOP 50\n交易編號\nMPAY00001", "ActivityForm", emptySet(), (base + 1000L)))
        }
        assertNull(gate.evaluate("支付成功\nMOP 50\n交易編號\nMPAY00001", "ActivityUsrWallet", setOf("tv_wallet_trade_time"), (base + 1000L)))
    }

    @Test fun `編號提取支援繁簡通知行文並排除金額與電話`() {
        assertEquals("20261005000001", TransactionIdentity.extract("轉賬成功 MOP 50 交易編號：20261005000001"))
        assertEquals("ABC-20261005", TransactionIdentity.extract("交易编号\nABC-20261005\n复制"))
        assertNull(TransactionIdentity.extract("交易金額\n50\n電話\n66666666"))
    }
}
