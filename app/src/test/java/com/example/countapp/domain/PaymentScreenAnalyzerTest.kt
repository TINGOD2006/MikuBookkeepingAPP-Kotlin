package com.example.countapp.domain

import org.junit.Assert.*
import org.junit.Test

class PaymentScreenAnalyzerTest {
    private val wechat = "com.tencent.mm"
    private val alipay = "com.eg.android.AlipayGphone"

    @Test fun `微信支付完成節點提取商戶及四位金額`() {
        val result = PaymentScreenAnalyzer.decide(wechat, "支付成功\n￥1234.56\n收款方\n早餐店")!!
        assertEquals(1234.56, result.amount, 0.001)
        assertFalse(result.isIncome)
        assertEquals("早餐店", result.merchant)
    }

    @Test fun `支付寶實付優先於原價和優惠`() {
        val result = PaymentScreenAnalyzer.decide(alipay, "支付成功\n￥100.00\n实付金额\n￥88.00\n优惠\n12.00\n商户名称\n便利店")!!
        assertEquals(88.0, result.amount, 0.001)
        assertFalse(result.isIncome)
        assertEquals("便利店", result.merchant)
    }

    @Test fun `簡體轉帳成功及收款方不能當作收入`() {
        val result = PaymentScreenAnalyzer.decide(wechat, "转账成功\n转账金额：1000.00\n收款人：小明")!!
        assertFalse(result.isIncome)
        assertEquals("轉帳給 小明", result.note)
    }

    @Test fun `已存入零錢是收入`() {
        val result = PaymentScreenAnalyzer.decide(wechat, "已存入零钱\n+1,234.50")!!
        assertTrue(result.isIncome)
        assertEquals(1234.5, result.amount, 0.001)
    }

    @Test fun `付款輸入失敗取消退款不記帳`() {
        listOf("输入金额", "确认付款", "支付失败", "交易取消", "退款成功", "待支付").forEach {
            assertNull(PaymentScreenAnalyzer.decide(alipay, "$it\n￥100.00"))
            assertNull(PaymentScreenAnalyzer.decide(alipay, "支付成功\n$it\n￥100.00"))
        }
    }

    @Test fun `多筆金額與多筆完成狀態無法確定不記帳`() {
        assertNull(PaymentScreenAnalyzer.decide(wechat, "支付成功\n￥20\n￥30"))
        assertNull(PaymentScreenAnalyzer.decide(wechat, "支付成功\n￥20\n付款成功\n￥30"))
        assertNull(PaymentScreenAnalyzer.decide(wechat, "支付成功\n￥20\n支付成功\n￥30"))
    }

    @Test fun `聊天引用與促銷句不視為付款結果`() {
        assertNull(PaymentScreenAnalyzer.decide(wechat, "我支付成功了\n￥20"))
        assertNull(PaymentScreenAnalyzer.decide(alipay, "支付成功即送優惠\n￥20"))
    }

    @Test fun `單號使動態頁面文字不影響去重`() {
        val a = PaymentScreenAnalyzer.decide(alipay, "支付成功\n￥20\n交易单号\n2026092800000001\n剩餘倒計時 3")!!
        val b = PaymentScreenAnalyzer.decide(alipay, "支付成功\n￥20\n交易单号\n2026092800000001\n剩餘倒計時 2")!!
        val c = PaymentScreenAnalyzer.decide(alipay, "支付成功\n￥20\n交易单号\n2026092800000002")!!
        assertEquals(a.fingerprint, b.fingerprint)
        assertNotEquals(a.fingerprint, c.fingerprint)
    }

    @Test fun `MPay轉帳輸入頁的成功提示與錢包餘額不可記帳`() {
        assertNull(PaymentScreenAnalyzer.decide("com.macaupass.rechargeEasy", "轉賬\nMOP\n50.00\n錢包餘額\nMOP 1,234.00\n-可轉賬金額\nMOP 1,234.00\n-不可轉賬金額\nMOP 0\n. 請仔細檢查您輸入的轉賬收款方電話號碼, 確認其為最新及有效，成功轉賬後無法撤回。"))
    }

    @Test fun `MPay必須有獨立完成節點而非同一句提示`() {
        assertNull(PaymentScreenAnalyzer.decide("com.macaupass.rechargeEasy", "支付成功 MOP 50"))
    }

    @Test fun `MPay付款詳情取交易金額而非餘額且保留單號`() {
        val result = PaymentScreenAnalyzer.decide("com.macaupass.rechargeEasy", "交易詳情\n交易成功\n測試商戶\nMOP\n74.26\n付款方式\n錢包餘額\n付款時間\n2026-10-04 04:30\n交易編號\n2026100400000001")!!
        assertEquals(74.26, result.amount, 0.001)
        assertFalse(result.isIncome)
        assertEquals("2026100400000001", result.transactionId)
    }

    @Test fun `MPay轉帳方向不明或列出多筆交易不記`() {
        assertNull(PaymentScreenAnalyzer.decide("com.macaupass.rechargeEasy", "成功交易\nMOP 50\n轉賬"))
        assertNull(PaymentScreenAnalyzer.decide("com.macaupass.rechargeEasy", "交易記錄\n交易成功\nMOP 50\n付款方式\n交易成功\nMOP 40"))
    }

    @Test fun `MPay獨立收款完成節點是收入`() {
        val result = PaymentScreenAnalyzer.decide("com.macaupass.rechargeEasy", "收款成功\n收款金額\nMOP 50\n交易編號\n2026100400000002")!!
        assertTrue(result.isIncome)
        assertEquals(50.0, result.amount, 0.001)
    }

    @Test fun `MPay雙完成節點不依賴遍歷次序`() {
        listOf("支付成功\n成功交易", "成功交易\n支付成功").forEach { statuses ->
            val result = PaymentScreenAnalyzer.decide("com.macaupass.rechargeEasy", "$statuses\nMOP 50")
            assertNotNull(result)
            assertFalse(result!!.isIncome)
        }
    }

    @Test fun `共用通知金額不截斷四位數`() {
        assertEquals(1234.56, PaymentTextAnalyzer.extractAmount("支付成功 ¥1234.56")!!, 0.001)
    }
}
