package com.example.countapp.notification

import com.example.countapp.domain.AutoRecordDecision
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** 沿用舊版讀屏記錄 ID；跨來源及永久防重複由 RecordRepository 原子處理。 */
object AutoRecordGuard {
    fun screenRecordId(
        packageName: String,
        decision: AutoRecordDecision,
        dateMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val transactionId = decision.transactionId
        if (transactionId != null) return "a11y_$packageName|tx|$transactionId"

        val amount = String.format(Locale.US, "%.2f", decision.amount)
        val epochDay = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate().toEpochDay()
        return "a11y_$packageName|${decision.fingerprint}|$amount|$epochDay"
    }

}
