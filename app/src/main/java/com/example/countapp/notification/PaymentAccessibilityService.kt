package com.example.countapp.notification

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.countapp.AppContainer
import com.example.countapp.appContainer
import com.example.countapp.data.AccessibilityProbeLog
import com.example.countapp.data.Record
import com.example.countapp.domain.PaymentTextAnalyzer
import com.example.countapp.domain.PaymentScreenAnalyzer
import com.example.countapp.domain.ClassificationRules
import com.example.countapp.domain.TransactionTimeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 讀取用戶選取 App 的已完成交易畫面，保留本機診斷並在需要時顯示記帳浮球。
 * 系統包名篩選隨設定即時更新，讀取節點及寫入前再各檢查一次白名單。
 * 節點快照在主執行緒取得，解析與儲存放在背景執行。
 */
class PaymentAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var overlay: AutoRecordOverlay? = null
    private var connectionJob: Job? = null

    /** 節點樹走訪結果。 */
    private class NodeDump {
        var nodeCount = 0
        val texts = mutableListOf<String>()
    }

    /** 上一次 dump 的簽章，避免同一個畫面被反覆記錄。 */
    private var lastSignature: String? = null

    /** 上一次「已寫入記錄」的畫面文字與時間（同畫面事件風暴的快速去重）。 */
    private var lastRecordedText: String? = null
    private var lastRecordedAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectionJob?.cancel()
        overlay?.hide()
        overlay = AutoRecordOverlay(this)
        val container = applicationContext.appContainer
        connectionJob = serviceScope.launch(Dispatchers.Main.immediate) {
            launch {
                container.settingsStore.allowedPackagesFlow.collect { packages ->
                    // 空陣列可能被系統視為「所有 App」，改用未安裝的佔位包名阻擋事件。
                    val allowed = packages.filter { container.settingsStore.isPackageAllowed(it, packageName) }
                    serviceInfo = serviceInfo.apply {
                        packageNames = allowed.ifEmpty { listOf("$packageName.no_selected_apps") }.toTypedArray()
                    }
                    lastSignature = null
                }
            }
            launch {
                combine(
                    container.pendingAutoRecordPrompts,
                    container.appInForeground,
                    container.settingsStore.floatingBallEnabledFlow,
                ) { pending, foreground, ballEnabled -> Triple(pending, foreground, ballEnabled) }
                    .collectLatest { (pending, foreground, ballEnabled) ->
                        do {
                            runCatching { overlay?.render(pending, foreground, ballEnabled) }
                                .onFailure { Log.w(TAG, "無法顯示記帳浮球：${it.javaClass.simpleName}") }
                            // 中斷或系統暫時拒絕視窗後重試；只在背景有待處理提示時運作。
                            if (pending.isEmpty() || foreground || !ballEnabled) break
                            delay(2000)
                        } while (isActive)
                    }
            }
        }
        Log.i(TAG, "無障礙自動記帳已連接，按用戶選擇的 App 篩選")
    }

    override fun onInterrupt() {
        overlay?.hide()
        Log.w(TAG, "⚠️ 無障礙自動記帳被中斷")
    }

    override fun onDestroy() {
        overlay?.hide()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return
        if (!applicationContext.appContainer.settingsStore.isPackageAllowed(packageName, applicationContext.packageName)) return

        val root = rootInActiveWindow ?: event.source ?: return
        if (root.packageName?.toString() != packageName) return
        // event 與節點在回調後可能被系統回收，只把不可變快照交給背景工作。
        val eventClass = event.className?.toString().orEmpty()
        val eventType = event.eventType

        // ⚠️ 節點樹走訪必須留在主執行緒（AccessibilityNodeInfo 生命週期很短），
        //    但後續的判定、AI 分類（會做網路 I/O）、寫入都要丟到背景，
        //    否則 onAccessibilityEvent 在主執行緒上做這些事會 ANR。
        val dump = NodeDump()
        collectTexts(root, dump, 0)

        val joined = dump.texts.joinToString("\n")
        if (joined.isEmpty()) return

        val signature = "$packageName|${event.eventType}|$joined"
        val isNewScreen = signature != lastSignature
        if (isNewScreen) lastSignature = signature

        serviceScope.launch {
            try {
                // 先嘗試自動記帳，再依「實際結果」寫診斷紀錄：
                // 這樣「我的 → 無障礙自動記錄」看到的字才不會與事實不符
                // （例如自動記錄關閉時卻顯示「已自動記帳」）。
                // 同一個畫面的事件可能連續好幾個同時進來，寫入端要序列化。
                val outcome = synchronized(recordLock) { tryAutoRecord(packageName, joined) }

                // 重記保護照常執行，診斷列表只顯示新的判斷，不以重複略過訊息洗版。
                if (isNewScreen && outcome?.startsWith("略過：") != true) {
                    val entry = buildEntry(eventClass, eventType, packageName, dump, joined, outcome)
                    appendLog(entry)

                    Log.i(TAG, "📥 ${entry.verdict}")
                    Log.i(TAG, "   pkg=$packageName nodes=${dump.nodeCount} texts=${dump.texts.size}")
                    // 不在 Logcat 輸出付款全文；完整診斷僅存於 App 本機的私有資料。
                }
            } catch (e: Exception) {
                // 單一事件處理失敗不可讓服務掛掉
                Log.e(TAG, "處理畫面事件失敗: ${e.message}", e)
            }
        }
    }

    // ============================================================
    // ✅ 自動記帳
    // ============================================================

    /**
     * 嘗試自動記帳。
     *
     * @return null 表示「這個畫面不符合任何自動記帳條件」；
     *         非 null 是一句給診斷紀錄用的結果說明（成功或略過的原因）
     */
    private fun tryAutoRecord(packageName: String, text: String): String? {
        val container = applicationContext.appContainer
        val settings = container.settingsStore

        // 只接受「已完成」的付款畫面：金額 + 成功字樣（避免記到輸入中的頁面）
        val decision = PaymentScreenAnalyzer.decide(packageName, text)
            ?: return null

        val direction = if (decision.isIncome) "+" else "-"

        // ===== 日期：優先採用畫面顯示的交易時間 =====
        // 讀屏只在使用者操作支付 App 的當下看到畫面，因此舊版直接用「讀到的當下」；
        // 但同一個畫面可能被重複讀到（事件風暴、或使用者回頭重看同一頁），
        // 那樣第二筆就會帶著後面的日期進明細。畫面上的交易時間才是這筆消費真正的日期，
        // 取不到（或解析結果不合理）才退回當下時間——絕不因為解析不出來就不記帳。
        val now = System.currentTimeMillis()
        val screenTime = TransactionTimeParser.parse(text, now)
        val dateMillis = screenTime ?: now

        val summary = "金額 $direction${decision.amount}（${decision.note}）" +
            "・${formatTimeText(dateMillis)}"

        // 主開關 + 包名白名單（與通知來源相同的規則）
        if (!settings.autoRecordEnabled) return "符合條件但未記錄：自動記錄已關閉（$summary）"
        if (!settings.isPackageAllowed(packageName, applicationContext.packageName)) {
            return "符合條件但未記錄：$packageName 不在白名單（$summary）"
        }

        val prefs = applicationContext.getSharedPreferences(AppContainer.PREFS_NAME, Context.MODE_PRIVATE)

        // 同一個畫面的事件風暴（內容變更可能連續送達數十次）先快速擋掉
        if (text == lastRecordedText && now - lastRecordedAt < REPEAT_WINDOW_MS) {
            return "略過：同一個畫面剛記過（$summary）"
        }

        // 同一個畫面文字的重記保護（寫進 prefs，服務被系統重啟也有效）
        if (AutoRecordGuard.isScreenRecorded(prefs, packageName, decision.fingerprint, now)) {
            return "略過：這個畫面已記錄過（$summary）"
        }

        // 跨來源去重：付款通知可能已經先記過同一筆消費
        // ⚠️ 方向要一起比對，否則「付 50」與「收 50」會被誤認為同一筆
        if (!AutoRecordGuard.tryClaim(packageName, decision.amount, decision.isIncome, now)) {
            return "略過：同一筆消費已由其他來源（通知）記錄（$summary）"
        }

        val category = ClassificationRules.classifyWith(text, settings.effectiveRules())
        val record = Record.create(
            amount = if (decision.isIncome) decision.amount else -decision.amount,
            category = category,
            note = decision.note,
            // 交易日期（見上方說明）；createdAt 才是「我們何時寫入」，
            // 兩者刻意分開，重複讀到時才看得出是同一筆。
            dateMillis = dateMillis,
            createdAtMillis = now,
            // id 不含「讀到的當下」：同一筆交易不管被讀幾次都是同一個 id，
            // 儲存庫的 id 去重就能永久擋掉重複（連服務重啟也有效）
            id = AutoRecordGuard.screenRecordId(packageName, decision, dateMillis),
        )

        val added = container.recordRepository.add(record)
        lastRecordedText = text
        lastRecordedAt = now
        AutoRecordGuard.rememberScreen(prefs, packageName, decision.fingerprint, now)

        if (!added) return "略過：同一筆交易已記錄過（$summary）"

        Log.i(TAG, "✅ 讀屏自動記帳：$category / ${decision.note} / ${decision.amount} / $dateMillis")

        // 通知、分類確認頁、預算提醒（失敗不影響記帳）
        if (needsCategoryPrompt(packageName)) {
            container.requestAutoRecordPrompt(record)
            runCatching { container.notifier.showAutoRecordNeedsCategory(record) }
        } else {
            runCatching { container.notifier.showRecordAdded(record) }
        }
        runCatching { notifyIfBudgetExceeded(container, record) }
        container.refineAutoRecordCategory(record, text)

        return "已自動記帳：$category $summary"
    }

    /**
     * 診斷紀錄用的日期時間文字。
     *
     * 為什麼要寫進診斷紀錄：使用者回報「同一筆被記了好幾次」時，光看金額分不出
     * 是「同一筆重複」還是「兩筆真的同金額交易」，把採用的日期時間寫出來，
     * 就能在「我的 → 無障礙自動記錄」直接核對。
     */
    private fun formatTimeText(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(SUMMARY_TIME_FORMAT)

    /** 這筆自動記錄是否需要跳出「選分類／寫備註」的確認頁。 */
    private fun needsCategoryPrompt(packageName: String): Boolean =
        AUTO_PROMPT_PACKAGES.any { it.equals(packageName, ignoreCase = true) }

    // ============================================================
    // ✅ 節點樹走訪
    // ============================================================

    private fun collectTexts(node: AccessibilityNodeInfo?, dump: NodeDump, depth: Int) {
        if (node == null) return
        if (depth > MAX_DEPTH || dump.nodeCount >= MAX_NODES) return

        dump.nodeCount++

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) dump.texts.add(text)

        // contentDescription 也要收：很多自繪元件把文字放在這裡
        val description = node.contentDescription?.toString()?.trim()
        if (!description.isNullOrEmpty() && description != text) {
            dump.texts.add(description)
        }

        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), dump, depth + 1)
        }
    }

    // ============================================================
    // ✅ 建立探針結果
    // ============================================================

    private fun buildEntry(
        eventClass: String,
        eventType: Int,
        packageName: String,
        dump: NodeDump,
        joined: String,
        outcome: String?,
    ): AccessibilityProbeLog {
        val nodeCount = dump.nodeCount
        val textCount = dump.texts.size

        val paymentLike = PaymentTextAnalyzer.isPaymentText(joined)
        val advertisement = PaymentTextAnalyzer.isAdvertisement(joined)
        val amount = PaymentTextAnalyzer.extractAmount(joined)
        val isIncome = PaymentTextAnalyzer.isIncomeTransfer(joined)

        val verdict = when {
            nodeCount == 0 -> "⚠️ 完全取不到節點 → 讀不到這個畫面"
            textCount == 0 ->
                "❌ 節點樹沒有任何文字（自繪視圖／WebView）→ 讀屏這條路線不可行"
            advertisement -> "📢 讀到 $textCount 段文字，但判定為廣告推播（不會記錄）"
            outcome != null -> if (outcome.startsWith("已自動記帳")) "✅ $outcome" else "🟡 $outcome"
            paymentLike && amount != null ->
                "🟡 看到類似交易，但完成狀態、交易金額或收支方向未能確定 → 尚未記錄"
            paymentLike -> "🟡 讀到 $textCount 段文字，像交易但取不到金額"
            else -> "🟡 讀到 $textCount 段文字，但不符合已知交易格式（不會記錄）"
        }

        return AccessibilityProbeLog(
            timeMillis = System.currentTimeMillis(),
            packageName = packageName,
            className = eventClass,
            eventType = eventTypeName(eventType),
            nodeCount = nodeCount,
            textCount = textCount,
            paymentLike = paymentLike,
            advertisement = advertisement,
            amount = amount,
            isIncome = isIncome,
            verdict = verdict,
            text = joined.take(MAX_TEXT_LENGTH),
        )
    }

    private fun eventTypeName(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "VIEW_TEXT_CHANGED"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "WINDOWS_CHANGED"
        else -> type.toString()
    }

    /** 追加一筆探針結果，只保留最近 [MAX_LOGS] 筆。 */
    private fun appendLog(entry: AccessibilityProbeLog) {
        try {
            val prefs = applicationContext.getSharedPreferences(
                AppContainer.PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            val existing = prefs.getString(KEY_LOGS, null)
            val array = if (existing.isNullOrEmpty()) JSONArray() else JSONArray(existing)
            array.put(entry.toJson())

            // 只保留最近的幾筆
            val trimmed = JSONArray()
            val start = maxOf(0, array.length() - MAX_LOGS)
            for (i in start until array.length()) {
                trimmed.put(array.get(i))
            }

            prefs.edit().putString(KEY_LOGS, trimmed.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "寫入探針結果失敗: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "A11yAutoRecord"

        /** 序列化「判定 → 寫入」，避免同一畫面的多個事件同時寫入。 */
        private val recordLock = Any()

        /** 探針結果的儲存鍵（放在原生版自己的 prefs 檔）。 */
        const val KEY_LOGS: String = "accessibility_probe_logs"

        private const val MAX_LOGS = 30
        private const val MAX_NODES = 1500
        private const val MAX_DEPTH = 40
        private const val MAX_TEXT_LENGTH = 4000

        /** 同一個畫面文字的重記時間窗（比對跨來源去重更長，避免同一頁反覆記錄）。 */
        private const val REPEAT_WINDOW_MS = 600_000L

        /** 診斷紀錄裡「這筆記錄採用的日期時間」格式。 */
        private val SUMMARY_TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

        /** 這些支付 App 自動記帳後會跳出「選分類／寫備註」確認頁。 */
        private val AUTO_PROMPT_PACKAGES = listOf(
            "com.tencent.mm",
            "com.eg.android.AlipayGphone",
            "hk.alipay.wallet",
            "com.alipay.android.app",
            "com.macaupass.rechargeEasy", // MPay 澳門通
        )

        /** 讀取探針結果（最新在最後）。 */
        fun readLogs(context: Context): List<AccessibilityProbeLog> {
            val prefs = context.getSharedPreferences(
                AppContainer.PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
            if (raw.isEmpty()) return emptyList()

            return try {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        array.optJSONObject(i)?.let { add(AccessibilityProbeLog.fromJson(it)) }
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        /** 清空探針結果。 */
        fun clearLogs(context: Context) {
            context.getSharedPreferences(AppContainer.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LOGS)
                .apply()
        }

        /** 這個無障礙服務目前是否已由使用者在系統設定中啟用。 */
        fun isEnabled(context: Context): Boolean = try {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

            val target = ComponentName(context, PaymentAccessibilityService::class.java)
            flat.split(":").any { entry ->
                val parsed = ComponentName.unflattenFromString(entry)
                parsed != null &&
                    parsed.packageName == target.packageName &&
                    parsed.className == target.className
            }
        } catch (e: Exception) {
            false
        }

        /** 開啟系統的無障礙設定頁。 */
        fun openSettings(context: Context) {
            val intent = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
