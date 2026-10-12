package com.example.countapp.domain

import org.junit.Assert.*
import org.junit.Test

class PaymentNotificationAnalyzerTest {
    private val pkg = PaymentNotificationAnalyzer.MPAY_PACKAGE

    @Test fun `無編號消費收款轉出轉入通知各自保留金額及方向`() {
        val examples = listOf(
            "MPay 消費 MOP 12.50" to false,
            "MPay 付款成功 MOP 12.50 收款方：測試商戶" to false,
            "MPay 收款成功 MOP 12.50" to true,
            "MPay 成功轉賬 MOP 12.50 給測試用戶" to false,
            "MPay 收到測試用戶轉賬 MOP 12.50" to true,
            "MPay 轉入 MOP 12.50" to true,
        )
        examples.forEach { (text, income) ->
            val result = PaymentNotificationAnalyzer.decide(pkg, text)
            assertNotNull(text, result)
            assertEquals(text, 12.50, result!!.amount, 0.001)
            assertEquals(text, income, result.isIncome)
            assertNull(result.transactionId)
        }
    }

    @Test fun `通知中的待付款請求失敗退款廣告均不入帳`() {
        listOf("收到轉賬請求 MOP 100", "請付款 MOP 100", "轉賬待確認 MOP 100",
            "支付失敗 MOP 100", "退款成功 MOP 100", "優惠券 消費 MOP 100").forEach {
            assertNull(it, PaymentNotificationAnalyzer.decide(pkg, it))
        }
    }
}
