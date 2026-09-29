package com.example.countapp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.countapp.MainActivity
import com.example.countapp.R
import com.example.countapp.data.Record
import com.example.countapp.domain.AmountFormatter

/**
 * 系統通知（記帳結果、預算提醒、背景常駐）。
 *
 * 由 Flutter 版的 `local_notification_service.dart` 移植。
 */
class RecordNotifier(private val context: Context) {

    /** 建立通知頻道（Android 8+ 必須先建立頻道才能發通知）。 */
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDS,
                context.getString(R.string.channel_records_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_records_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BUDGET,
                context.getString(R.string.channel_budget_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_budget_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOREGROUND,
                context.getString(R.string.channel_foreground_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_foreground_desc)
                setShowBadge(false)
            }
        )
    }

    /** 自動記帳成功後的通知。 */
    fun showRecordAdded(record: Record) {
        val sign = if (record.isExpense) "-" else "+"
        val amount = AmountFormatter.format(record.absoluteAmount)
        val note = record.note.trim()
        val details = if (note.isEmpty()) record.category else "${record.category}・$note"
        val type = if (record.isExpense) "支出" else "收入／轉帳"

        notify(
            id = record.id.hashCode(),
            channel = CHANNEL_RECORDS,
            title = "${type}已新增",
            text = "$details  $sign$amount",
        )
    }

    /**
     * 自動記錄後請使用者補分類／備註的通知。
     *
     * 點擊會帶著記錄 id 開啟 App，App 收到後直接跳出確認頁
     * （Android 10+ 背景服務不能直接啟動畫面，通知的 PendingIntent 是標準做法）。
     */
    fun showAutoRecordNeedsCategory(record: Record) {
        val sign = if (record.isExpense) "-" else "+"
        val amount = AmountFormatter.format(record.absoluteAmount)
        val note = record.note.trim()

        notify(
            id = record.id.hashCode(),
            channel = CHANNEL_RECORDS,
            title = "已自動記錄，點一下選分類",
            text = "${if (note.isEmpty()) record.category else note}  $sign$amount",
            promptRecordId = record.id,
        )
    }

    /** 預算超支／接近超支提醒。 */
    fun showBudgetAlert(year: Int, month: Int, expense: Double, budget: Double) {
        if (budget <= 0) return

        when {
            expense > budget -> {
                val over = AmountFormatter.format(expense - budget)
                notify(
                    id = "budget_over_${year}_$month".hashCode(),
                    channel = CHANNEL_BUDGET,
                    title = "⚠️ 預算超支提醒",
                    text = "【$month 月】累積支出 \$${AmountFormatter.format(expense)}，" +
                        "已超過預算 \$${AmountFormatter.format(budget)}（超支 \$$over）",
                )
            }
            expense >= budget * 0.8 -> {
                val percentage = ((expense / budget) * 100).toInt()
                notify(
                    id = "budget_near_${year}_$month".hashCode(),
                    channel = CHANNEL_BUDGET,
                    title = "⚠️ 預算即將超支提醒",
                    text = "【$month 月】累積支出 \$${AmountFormatter.format(expense)}，" +
                        "已使用 $percentage% 的預算",
                )
            }
        }
    }

    private fun notify(
        id: Int,
        channel: String,
        title: String,
        text: String,
        promptRecordId: String? = null,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // 要求 App 開啟後直接跳到「選分類／寫備註」頁
            promptRecordId?.let { putExtra(EXTRA_PROMPT_RECORD_ID, it) }
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Android 13+ 使用者未授予 POST_NOTIFICATIONS：通知只是加值功能，
            // 不該影響記帳本身，因此靜默忽略。
        }
    }

    companion object {
        const val CHANNEL_RECORDS: String = "record_updates"
        const val CHANNEL_BUDGET: String = "budget_alerts"
        const val CHANNEL_FOREGROUND: String = "miku_foreground"

        /** 通知點擊後要開啟「自動記錄確認頁」的記錄 id。 */
        const val EXTRA_PROMPT_RECORD_ID: String = "extra_auto_record_prompt_id"
    }
}
