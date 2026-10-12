package com.example.countapp.domain

import java.security.MessageDigest
import java.util.Locale

/** Android 通知身份及來源時間去重；內容更新和金額不構成另一筆交易。 */
object NotificationEventIdentity {
    fun id(packageName: String, notificationKey: String, eventTime: Long): String =
        "notification_v2_" + hash("$packageName|$notificationKey|$eventTime")

    /** 升級後仍辨認舊版已處理通知，保留舊欄位拼接格式。 */
    fun legacyId(packageName: String, amount: Double, key: String, text: String, eventTime: Long): String {
        val normalized = "$key|$text".trim().replace(Regex("\\s+"), " ").lowercase()
        return hash("$packageName|${String.format(Locale.US, "%.2f", amount)}|$eventTime|$normalized")
    }

    private fun hash(source: String): String = MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
