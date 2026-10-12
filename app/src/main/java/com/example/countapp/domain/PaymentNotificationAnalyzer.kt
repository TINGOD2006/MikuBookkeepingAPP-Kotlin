package com.example.countapp.domain

/** 通知入口的解析規則，與要求完整成功畫面的讀屏入口分開。 */
object PaymentNotificationAnalyzer {
    const val MPAY_PACKAGE = "com.macaupass.rechargeEasy"

    fun decide(packageName: String, text: String): AutoRecordDecision? {
        // 通知可用「消費 MOP 50」等交易行文，無須成功字樣或交易編號；請求仍需拒絕。
        return AutoRecordDecisionMaker.decide(text, rejectPending = true)
    }
}
