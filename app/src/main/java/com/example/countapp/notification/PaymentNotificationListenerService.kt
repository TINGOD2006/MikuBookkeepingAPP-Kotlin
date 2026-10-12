package com.example.countapp.notification

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import com.example.countapp.MainActivity
import kotlinx.coroutines.flow.combine
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.countapp.appContainer
import com.example.countapp.data.Record
import com.example.countapp.data.AutoRecordResult
import com.example.countapp.domain.TransactionTimeParser
import com.example.countapp.domain.PaymentNotificationAnalyzer
import com.example.countapp.domain.NotificationEventIdentity
import com.example.countapp.domain.MpayNoteExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 通知監聽服務：自動記帳的主要來源。
 *
 * 與 Flutter 版最大的差別：**不再需要跨 runtime 的握手協定**。
 *
 * Flutter 版因為 UI 在 Dart、通知在原生，必須額外處理：
 *   - `saved / duplicate / disabled / ignored / failed` 狀態回報
 *   - 「Dart 端 5 秒沒回應就由原生端自己存一份」的 fallback
 *   - App 未執行時把記錄暫存到 SharedPreferences 等下次匯入
 *   - Flutter engine 被銷毀時清空 methodChannel 參照
 *
 * 這些複雜度全部消失：服務與 UI 在同一個行程、同一份儲存，
 * 直接寫進 [com.example.countapp.data.RecordRepository] 即可。
 */
class PaymentNotificationListenerService : NotificationListenerService() {

    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foregroundSettingsJob: Job? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        seedExistingNotificationIdentities()
        Log.i(TAG, "✅ 通知監聽服務已連接")
        foregroundSettingsJob?.cancel()
        foregroundSettingsJob = settingsScope.launch {
            val settings = applicationContext.appContainer.settingsStore
            combine(settings.backgroundNotificationEnabledFlow, settings.autoRecordModeFlow) { _, _ -> Unit }.collect {
                updateForegroundState()
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        foregroundSettingsJob?.cancel()
        stopForegroundCompat()
        Log.w(TAG, "⚠️ 通知監聽服務已斷開")
        if (isNotificationAccessGranted(this)) requestRebindService(this)
    }

    override fun onDestroy() {
        settingsScope.cancel()
        stopForegroundCompat()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // ⚠️ 一定要在背景執行緒處理：分類若走 AI 會做網路 I/O，
        //    而 onNotificationPosted 是在主執行緒被呼叫的，直接做會 ANR。
        serviceScope.launch {
            try {
                handleNotification(sbn)
            } catch (e: Exception) {
                // 單一通知處理失敗不可讓服務掛掉
                Log.e(TAG, "處理通知失敗: ${e.message}", e)
            }
        }
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        val container = applicationContext.appContainer
        val settings = container.settingsStore

        if (!settings.autoRecordMode.notificationsEnabled) return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        // 合併所有文字來源，避免漏掉內容（銀行／支付 App 常把內容放在 bigText）
        val fields = listOf(title, bigText, text, subText)
            .filter { it.isNotBlank() }
        val fullText = fields.distinct().joinToString("\n").trim()

        if (fullText.isEmpty()) return

        // 1) 包名白名單
        if (!settings.isPackageAllowed(packageName, applicationContext.packageName)) return

        // 2) 廣告過濾 + 交易句型 + 金額（共用判斷邏輯，與讀屏來源一致）
        val decision = PaymentNotificationAnalyzer.decide(packageName, fullText) ?: run {
            Log.i(TAG, "通知未入帳：交易格式、金額或完成狀態不符 pkg=$packageName")
            return
        }

        // 3) 去重（同一筆通知可能重複送達）
        val eventTime = sbn.notification.`when`.takeIf { it > 0 } ?: sbn.postTime
        val eventId = NotificationEventIdentity.id(packageName, sbn.key, eventTime)
        val legacyId = NotificationEventIdentity.legacyId(packageName, decision.amount, sbn.key,
            fields.joinToString(" ").trim(), eventTime)
        if (isAlreadyProcessed(eventId)) return
        if (container.recordRepository.rememberAutomaticEventAlias(legacyId, eventId,
                wasProcessed = isAlreadyProcessed(legacyId))) {
            markProcessed(eventId)
            Log.d(TAG, "⏭️ 忽略重複通知: $eventId")
            return
        }

        // 5) 分類
        val category = com.example.countapp.domain.ClassificationRules.classifyWith(fullText, settings.effectiveRules())

        val automaticNote = if (packageName == PaymentNotificationAnalyzer.MPAY_PACKAGE)
            MpayNoteExtractor.describe(decision, fullText) ?: decision.note else decision.note
        val record = Record.create(
            amount = if (decision.isIncome) decision.amount else -decision.amount,
            category = category,
            note = automaticNote,
            dateMillis = TransactionTimeParser.parse(fullText) ?: eventTime,
            createdAtMillis = System.currentTimeMillis(),
            id = eventId,
            sourcePackage = packageName,
            sourceChannel = "notification",
            transactionId = decision.transactionId,
            transactionTimeMillis = TransactionTimeParser.parse(fullText),
            automaticNote = automaticNote,
        )

        // 6) 寫入（同一行程、同一份儲存，不需要任何回報協定）
        // 排隊期間用戶可能切回手動或取消來源；寫入前再次核對。
        if (!settings.autoRecordMode.notificationsEnabled ||
            !settings.isPackageAllowed(packageName, applicationContext.packageName)) return
        val result = container.recordRepository.ingestAutomatic(record)
        markProcessed(eventId)

        if (result != AutoRecordResult.ADDED) {
            Log.d(TAG, "⏭️ 記錄已存在，略過: $eventId")
            return
        }

        Log.i(TAG, "通知自動記帳成功 pkg=$packageName receipt=${decision.transactionId != null}")

        // 7) 通知、分類確認頁、預算提醒（失敗不影響記帳）
        val needsCategory = needsCategoryPrompt(packageName)
        if (needsCategory) {
            // MPay 這類通知：先請使用者確認分類／備註（通知也可點進來）
            container.requestAutoRecordPrompt(record)
            runCatching { container.notifier.showAutoRecordNeedsCategory(record) }
        } else {
            runCatching { container.notifier.showRecordAdded(record) }
        }
        runCatching { notifyIfBudgetExceeded(container, record) }
        container.refineAutoRecordCategory(record, fullText)
    }

    /** 這筆自動記錄是否需要跳出「選分類／寫備註」的確認頁。 */
    private fun needsCategoryPrompt(packageName: String): Boolean =
        AUTO_PROMPT_PACKAGES.any { it.equals(packageName, ignoreCase = true) }

    // ============================================================
    // ✅ 去重（記憶體 + SharedPreferences 持久化）
    // ============================================================

    private fun isAlreadyProcessed(eventId: String): Boolean = synchronized(dedupeLock) {
        val now = System.currentTimeMillis()
        recentEventIds.entries.removeIf { now - it.value > DEDUPE_WINDOW_MS }
        if (recentEventIds.containsKey(eventId)) return true

        val prefs = applicationContext.getSharedPreferences(
            com.example.countapp.AppContainer.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val ids = prefs.getStringSet(KEY_PROCESSED_IDS, emptySet()).orEmpty()
        ids.contains(eventId)
    }

    private fun markProcessed(eventId: String) = synchronized(dedupeLock) {
        recentEventIds[eventId] = System.currentTimeMillis()

        val prefs = applicationContext.getSharedPreferences(
            com.example.countapp.AppContainer.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        // ⚠️ getStringSet 回傳的可能是不可修改的副本，務必複製
        val ids = HashSet(prefs.getStringSet(KEY_PROCESSED_IDS, emptySet()).orEmpty())
        ids.add(eventId)

        // 限制大小，避免無限成長
        val trimmed = if (ids.size > MAX_PROCESSED_IDS) {
            ids.toList().takeLast(MAX_PROCESSED_IDS).toHashSet()
        } else {
            ids
        }
        prefs.edit().putStringSet(KEY_PROCESSED_IDS, trimmed).apply()
    }

    /** 僅首次升級時登記已在通知欄的舊通知；不補記安裝前的交易。 */
    private fun seedExistingNotificationIdentities() {
        val prefs = getSharedPreferences(com.example.countapp.AppContainer.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IDENTITY_V2_READY, false)) return
        // 系統權限已連接；不讀正文，只登記 Android 的通知身份及時間。
        runCatching {
            val settings = applicationContext.appContainer.settingsStore
            activeNotifications.orEmpty().filter { settings.isPackageAllowed(it.packageName, packageName) }
                .forEach {
                    val time = it.notification.`when`.takeIf { value -> value > 0 } ?: it.postTime
                    val id = NotificationEventIdentity.id(it.packageName, it.key, time)
                    applicationContext.appContainer.recordRepository.rememberAutomaticEventAlias(id, id, wasProcessed = true)
                    markProcessed(id)
                }
            prefs.edit().putBoolean(KEY_IDENTITY_V2_READY, true).apply()
        }.onFailure { Log.w(TAG, "舊通知身份登記未完成：${it.javaClass.simpleName}") }
    }

    // ============================================================
    // ✅ 背景常駐通知
    // ============================================================

    /** 依設定啟動／停止前台服務。 */
    fun updateForegroundState() {
        val container = applicationContext.appContainer
        if (container.settingsStore.backgroundNotificationEnabled) {
            startForegroundCompat()
        } else {
            stopForegroundCompat()
        }
    }

    private fun startForegroundCompat() {
        try {
            val notification = androidx.core.app.NotificationCompat
                .Builder(this, RecordNotifier.CHANNEL_FOREGROUND)
                .setContentTitle("📊 Miku 記帳")
                .setContentText(if (applicationContext.appContainer.settingsStore.autoRecordEnabled)
                    "正在監聽支付通知，自動記錄中…" else "通知監聽已連接，自動記錄已暫停")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(PendingIntent.getActivity(this, 1001,
                    Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .build()
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Android 12+ 對「從背景啟動前景服務」有諸多限制，
            // 這只是讓服務更不容易被回收的加值功能，失敗不影響記帳。
            Log.w(TAG, "無法啟動前景服務: ${e.message}")
        }
    }

    private fun stopForegroundCompat() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "停止前景服務失敗: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PaymentListener"

        private const val KEY_PROCESSED_IDS = "processed_notification_ids"
        private const val KEY_IDENTITY_V2_READY = "notification_identity_v2_ready"
        private const val MAX_PROCESSED_IDS = 1000
        private const val DEDUPE_WINDOW_MS = 300_000L
        private const val FOREGROUND_NOTIFICATION_ID = 1001

        /**
         * 這些支付 App 自動記帳後會跳出「選分類／寫備註」確認頁。
         *
         * 使用者要求 MPay 通知要能即時補分類與備註；要讓其他 App 也這樣做，
         * 把包名加進來即可。
         */
        private val AUTO_PROMPT_PACKAGES = listOf(
            "com.tencent.mm",
            "com.eg.android.AlipayGphone",
            "hk.alipay.wallet",
            "com.alipay.android.app",
            "com.macaupass.rechargeEasy", // MPay 澳門通
        )

        /** 服務層級的工作範圍：與行程同壽命，處理完單筆通知即結束。 */
        private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val recentEventIds = HashMap<String, Long>()
        private val dedupeLock = Any()

        /** 通知使用權限是否已授予。 */
        fun isNotificationAccessGranted(context: Context): Boolean = try {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            val component = ComponentName(context, PaymentNotificationListenerService::class.java)
                .flattenToString()
            flat.split(":").any { it.equals(component, ignoreCase = true) }
        } catch (e: Exception) {
            false
        }

        /** 開啟系統的通知使用權限設定頁。 */
        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        /**
         * 要求系統重新綁定服務。
         *
         * 用於啟動 App 或服務斷線後請求恢復；已連接時由設定 Flow 即時更新通知。
         */
        fun requestRebindService(context: Context) {
            try {
                requestRebind(
                    ComponentName(context, PaymentNotificationListenerService::class.java)
                )
            } catch (e: Exception) {
                Log.w(TAG, "重新綁定通知服務失敗: ${e.message}")
            }
        }
    }
}
