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

    @Test fun `其他支付App仍走原有嚴格判斷`() {
        assertNotNull(PaymentScreenAnalyzer.decide("com.macaupass.rechargeEasy", "支付成功 MOP 50"))
    }

    @Test fun `共用通知金額不截斷四位數`() {
        assertEquals(1234.56, PaymentTextAnalyzer.extractAmount("支付成功 ¥1234.56")!!, 0.001)
    }
}
