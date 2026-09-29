package com.example.countapp.notification

import android.content.SharedPreferences
import java.util.Locale

/**
 * 自動記帳的去重。
 *
 * 同一筆消費可能**同時**被兩個來源看到：
 *   - `PaymentNotificationListenerService`：支付 App 發出的付款通知
 *   - `PaymentAccessibilityService`：付款結果畫面的讀屏文字
 *
 * 兩者的記錄 id 來源不同，光靠 `RecordRepository.add()` 的 id 去重擋不住，
 * 因此這裡提供兩層保護：
 *
 *   1. [tryClaim]：**跨來源**的即時去重（記憶體）。鍵是「包名 + 收支方向 + 金額」，
 *      同一個鍵在 [WINDOW_MS] 內只准記一次。方向一定要進鍵，否則「付 50 元」
 *      與「收到 50 元」會被誤認為同一筆，其中一筆會憑空消失。
 *   2. [isScreenRecorded]／[rememberScreen]：**同一個畫面文字**的重記保護
 *      （寫進 SharedPreferences，所以服務被系統重啟也有效）。使用者回頭重看
 *      同一張交易結果頁時不會再記一筆。
 */
object AutoRecordGuard {

    /** 跨來源即時去重時間窗：同包名 + 同方向 + 同金額在此期間內只記一次。 */
    const val WINDOW_MS: Long = 90_000L

    /** 同一個畫面文字的重記保護時間窗（跨服務重啟）。 */
    const val SCREEN_TTL_MS: Long = 30 * 60 * 1000L

    /** 已記錄畫面指紋的儲存鍵。 */
    const val KEY_RECORDED_SCREENS: String = "auto_record_recent_screens"

    private const val MAX_SCREENS = 200
    private const val SEPARATOR = "\u0001"

    private val lastClaimed = HashMap<String, Long>()
    private val lock = Any()

    /**
     * 嘗試取得「這一筆由我記錄」的權利（跨來源）。
     *
     * @param isIncome true = 收入，false = 支出（**必須**傳，方向是鍵的一部分）
     * @return true 表示可以記錄；false 表示時間窗內已經被另一個來源記過了
     */
    fun tryClaim(
        packageName: String,
        amount: Double,
        isIncome: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        // 清掉過期的鍵，避免長期執行下無限成長
        lastClaimed.entries.removeIf { now - it.value > WINDOW_MS }

        val key = claimKey(packageName, amount, isIncome)
        val previous = lastClaimed[key]
        if (previous != null && now - previous <= WINDOW_MS) return false

        lastClaimed[key] = now
        return true
    }

    /** 去重鍵（也給測試與診斷用）。 */
    fun claimKey(packageName: String, amount: Double, isIncome: Boolean): String =
        "$packageName|${if (isIncome) "+" else "-"}|${String.format(Locale.US, "%.2f", amount)}"

    /**
     * 這個畫面文字是不是已經記過了？
     *
     * 讀取時順手清掉過期項目，避免鍵無限成長。
     */
    fun isScreenRecorded(
        prefs: SharedPreferences,
        packageName: String,
        fingerprint: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val entry = screenKey(packageName, fingerprint)
        val alive = readAliveScreens(prefs, now)
        val recorded = alive.any { it.first == entry }
        if (alive.size != readScreens(prefs).size) writeScreens(prefs, alive)
        return recorded
    }

    /** 記下「這個畫面已經記過一筆」。 */
    fun rememberScreen(
        prefs: SharedPreferences,
        packageName: String,
        fingerprint: String,
        now: Long = System.currentTimeMillis(),
    ) {
        val entry = screenKey(packageName, fingerprint)
        val alive = readAliveScreens(prefs, now).filterNot { it.first == entry } +
            (entry to now)

        // 只保留最近 MAX_SCREENS 筆（依時間）
        val trimmed = alive.sortedByDescending { it.second }.take(MAX_SCREENS)
        writeScreens(prefs, trimmed)
    }

    /** 測試／診斷用：清空記憶體狀態（持久化的畫面紀錄不動）。 */
    fun reset() = synchronized(lock) { lastClaimed.clear() }

    // ========== 內部 ==========

    private fun screenKey(packageName: String, fingerprint: String): String =
        "$packageName$SEPARATOR$fingerprint"

    private fun readScreens(prefs: SharedPreferences): List<Pair<String, Long>> =
        prefs.getStringSet(KEY_RECORDED_SCREENS, emptySet()).orEmpty().mapNotNull { raw ->
            val index = raw.lastIndexOf(SEPARATOR)
            if (index <= 0) return@mapNotNull null
            val time = raw.substring(index + 1).toLongOrNull() ?: return@mapNotNull null
            raw.substring(0, index) to time
        }

    private fun readAliveScreens(prefs: SharedPreferences, now: Long): List<Pair<String, Long>> =
        readScreens(prefs).filter { now - it.second <= SCREEN_TTL_MS }

    private fun writeScreens(prefs: SharedPreferences, entries: List<Pair<String, Long>>) {
        val set = entries.mapTo(HashSet()) { (key, time) -> "$key$SEPARATOR$time" }
        prefs.edit().putStringSet(KEY_RECORDED_SCREENS, set).apply()
    }
}
