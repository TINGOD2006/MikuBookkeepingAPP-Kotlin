package com.example.countapp.domain

/** 辨識新付款結果，編號可選；歷史頁不授予新增資格。 */
class MpayCaptureGate {
    private var resultIdentity: String? = null
    fun reset() { resultIdentity = null }

    @Suppress("UNUSED_PARAMETER")
    fun evaluate(text: String, className: String, viewIds: Set<String>, now: Long): AutoRecordDecision? {
        if (className.contains("UsrWallet", true) || viewIds.any { it.contains("tv_wallet_trade_") } ||
            MpayScreenAnalyzer.isHistory(text) || className.contains("Detail", true)) {
            reset()
            return null
        }
        val receipt = MpayScreenAnalyzer.receipt(text) ?: run { reset(); return null }
        val resultPage = className.contains("Result", true) ||
            className == "com.macaupass.mvp.transferpay.v.ActivityTransferEnd" ||
            text.contains("點擊查看詳情") || text.contains("点击查看详情")
        if (!resultPage) { reset(); return null }
        // 只抑制留在同一結果頁時的事件；離開後同額交易仍可辨識。
        val id = receipt.transactionId ?: "result:${receipt.fingerprint}"
        if (resultIdentity == id) return null
        resultIdentity = id
        return receipt
    }
}
