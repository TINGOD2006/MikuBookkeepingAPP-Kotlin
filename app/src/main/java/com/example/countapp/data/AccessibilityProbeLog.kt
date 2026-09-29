package com.example.countapp.data

import org.json.JSONObject

/**
 * 一筆無障礙讀屏的診斷結果。
 *
 * 由 [com.example.countapp.notification.PaymentAccessibilityService] 在偵測到
 * 白名單支付 App 的視窗時產生，用來回答實機上的兩個問題：
 *   - 對方的付款結果畫面在無障礙節點樹裡到底有沒有可讀文字
 *   - 這個畫面最後有沒有被自動記帳（[verdict] 會寫出實際結果，
 *     例如「已自動記帳…」或「符合條件但未記錄：自動記錄已關閉…」）
 */
data class AccessibilityProbeLog(
    val timeMillis: Long,
    val packageName: String,
    val className: String,
    val eventType: String,
    val nodeCount: Int,
    val textCount: Int,
    val paymentLike: Boolean,
    val advertisement: Boolean,
    val amount: Double?,
    val isIncome: Boolean,
    val verdict: String,
    val text: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("time", timeMillis)
        put("package", packageName)
        put("className", className)
        put("eventType", eventType)
        put("nodeCount", nodeCount)
        put("textCount", textCount)
        put("paymentLike", paymentLike)
        put("advertisement", advertisement)
        put("amount", amount ?: JSONObject.NULL)
        put("isIncome", isIncome)
        put("verdict", verdict)
        put("text", text)
    }

    companion object {
        fun fromJson(json: JSONObject): AccessibilityProbeLog = AccessibilityProbeLog(
            timeMillis = json.optLong("time", 0L),
            packageName = json.optString("package"),
            className = json.optString("className"),
            eventType = json.optString("eventType"),
            nodeCount = json.optInt("nodeCount", 0),
            textCount = json.optInt("textCount", 0),
            paymentLike = json.optBoolean("paymentLike", false),
            advertisement = json.optBoolean("advertisement", false),
            amount = if (json.isNull("amount")) null else json.optDouble("amount", 0.0),
            isIncome = json.optBoolean("isIncome", false),
            verdict = json.optString("verdict"),
            text = json.optString("text"),
        )
    }
}
