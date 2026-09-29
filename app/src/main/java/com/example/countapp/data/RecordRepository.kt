package com.example.countapp.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 記錄的儲存庫。
 *
 * 設計與 Flutter 版的 `StorageService` 相同精神，但因為已經沒有 Dart／原生
 * 兩個 runtime，所以不需要再處理跨行程競態：
 *   - 通知服務與 UI 都直接寫進這裡
 *   - 以 [lock] 序列化「讀取 → 修改 → 寫回」，避免同時寫入互相覆蓋
 *   - 透過 [records] / [trashedRecords] 兩個 StateFlow 讓 Compose 自動重繪
 *
 * 刪除採「軟刪除」：記錄會被標上刪除時間並移出 [records]，改放在
 * [trashedRecords] 保留 [TRASH_RETENTION_DAYS] 天，期間可隨時 [restore]。
 * 逾期記錄會在 [reload] 或 [purgeExpiredTrash] 時真正從儲存區移除。
 */
class RecordRepository(
    private val prefs: SharedPreferences,
) {
    private val lock = Any()

    private val _records = MutableStateFlow<List<Record>>(emptyList())

    /** 目前所有記錄（依日期新到舊排序）。不含垃圾桶內的記錄。 */
    val records: StateFlow<List<Record>> = _records.asStateFlow()

    private val _trashedRecords = MutableStateFlow<List<Record>>(emptyList())

    /** 垃圾桶內的記錄（依刪除時間新到舊排序）。 */
    val trashedRecords: StateFlow<List<Record>> = _trashedRecords.asStateFlow()

    init {
        reload()
    }

    /** 從 SharedPreferences 重新載入，並順手清掉逾期記錄。 */
    fun reload(): List<Record> = synchronized(lock) {
        val all = readFromPrefs()
        val kept = all.filterNot { it.isTrashExpired() }

        // 逾期記錄直接從儲存區移除，不要讓它們一直躺在 JSON 裡
        if (kept.size != all.size) writeToPrefs(kept)
        publish(kept)

        return _records.value
    }

    /** 目前記憶體中的快照（不含垃圾桶）。 */
    fun snapshot(): List<Record> = _records.value

    /** 目前垃圾桶快照。 */
    fun trashSnapshot(): List<Record> = _trashedRecords.value

    /**
     * 新增一筆記錄。
     *
     * @return true 表示真的寫入；false 表示 id 重複（已存在，不重複記錄）。
     */
    fun add(record: Record): Boolean = synchronized(lock) {
        val current = readFromPrefs()
        if (current.any { it.id == record.id }) return false

        val updated = current + record
        writeToPrefs(updated)
        publish(updated)
        return true
    }

    /**
     * 以同一個 id 覆蓋既有記錄（編輯功能用）。
     *
     * ⚠️ 垃圾桶狀態一律沿用「儲存區裡的那一份」，不接受呼叫端傳入的
     * `deletedAtMillis`：編輯畫面可能拿著一份過期的複本（例如記錄在編輯期間
     * 被刪除），若照抄就會讓已經刪除的記錄悄悄復活到明細與統計裡。
     * 找不到 id 時回傳 false。
     */
    fun update(record: Record): Boolean = synchronized(lock) {
        val current = readFromPrefs()
        val index = current.indexOfFirst { it.id == record.id }
        if (index < 0) return false

        val stored = current[index]
        val updated = current.toMutableList().also {
            it[index] = record.copy(deletedAtMillis = stored.deletedAtMillis)
        }
        writeToPrefs(updated)
        publish(updated)
        return true
    }

    /** 背景分類只可更新仍未被使用者修改的記錄。比較與寫入必須同一把鎖。 */
    fun updateIfUnchanged(expected: Record, replacement: Record): Boolean = synchronized(lock) {
        if (replacement.id != expected.id) return false
        val current = readFromPrefs()
        val index = current.indexOfFirst { it.id == expected.id }
        if (index < 0 || current[index] != expected || current[index].isTrashed) return false
        val updated = current.toMutableList().also { it[index] = replacement }
        writeToPrefs(updated)
        publish(updated)
        true
    }

    /** 依 id 永久刪除（不進垃圾桶）。垃圾桶對話框的「永久刪除」用。 */
    fun deleteById(id: String): Boolean = synchronized(lock) {
        val current = readFromPrefs()
        val updated = current.filterNot { it.id == id }
        if (updated.size == current.size) return false

        writeToPrefs(updated)
        publish(updated)
        return true
    }

    // ========== 垃圾桶 ==========

    /**
     * 把記錄移到垃圾桶（軟刪除）。
     *
     * @return true 表示確實有記錄被移入；false 表示找不到 id 或已經在垃圾桶內。
     */
    fun moveToTrash(id: String, now: Long = System.currentTimeMillis()): Boolean =
        synchronized(lock) {
            val current = readFromPrefs()
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return false
            if (current[index].isTrashed) return false

            val updated = current.toMutableList().also {
                it[index] = it[index].copy(deletedAtMillis = now)
            }
            writeToPrefs(updated)
            publish(updated)
            return true
        }

    /** 從垃圾桶還原回明細。 */
    fun restore(id: String): Boolean = synchronized(lock) {
        val current = readFromPrefs()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return false
        if (!current[index].isTrashed) return false

        val updated = current.toMutableList().also {
            it[index] = it[index].copy(deletedAtMillis = null)
        }
        writeToPrefs(updated)
        publish(updated)
        return true
    }

    /** 清空垃圾桶（永久刪除所有垃圾桶內的記錄），回傳清掉的筆數。 */
    fun emptyTrash(): Int = synchronized(lock) {
        val current = readFromPrefs()
        val updated = current.filterNot { it.isTrashed }
        val removed = current.size - updated.size
        if (removed > 0) {
            writeToPrefs(updated)
            publish(updated)
        }
        return removed
    }

    /**
     * 清除已超過保留期的垃圾桶記錄（預設 30 天），回傳清掉的筆數。
     *
     * [now] 可注入，方便單元測試驗證 30 天的邊界。
     */
    fun purgeExpiredTrash(now: Long = System.currentTimeMillis()): Int = synchronized(lock) {
        val current = readFromPrefs()
        val updated = current.filterNot { it.isTrashExpired(now) }
        val removed = current.size - updated.size
        if (removed > 0) {
            writeToPrefs(updated)
            publish(updated)
        }
        return removed
    }

    // ========== 查詢輔助 ==========

    fun forMonth(year: Int, month: Int, zone: ZoneId = ZoneId.systemDefault()): List<Record> =
        _records.value.filter { it.localDate(zone).let { d -> d.year == year && d.monthValue == month } }

    fun forMonth(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): List<Record> =
        forMonth(month.year, month.monthValue, zone)

    fun totalExpense(records: List<Record> = _records.value): Double =
        records.filter { it.amount < 0 }.sumOf { it.absoluteAmount }

    fun totalIncome(records: List<Record> = _records.value): Double =
        records.filter { it.amount > 0 }.sumOf { it.amount }

    fun balance(records: List<Record> = _records.value): Double =
        totalIncome(records) - totalExpense(records)

    /** 某一日的支出合計。 */
    fun dailyExpense(records: List<Record>): Double =
        records.filter { it.amount < 0 }.sumOf { it.absoluteAmount }

    /** 某一日的收入合計。 */
    fun dailyIncome(records: List<Record>): Double =
        records.filter { it.amount > 0 }.sumOf { it.amount }

    /** 某一日的淨額。 */
    fun dailyTotal(records: List<Record>): Double =
        records.sumOf { it.amount }

    // ========== 內部 ==========

    /** 更新兩個 StateFlow（明細依日期、垃圾桶依刪除時間，皆為新到舊）。 */
    private fun publish(all: List<Record>) {
        _records.value = all.filterNot { it.isTrashed }.sortedByDescending { it.dateMillis }
        _trashedRecords.value = all.filter { it.isTrashed }
            .sortedByDescending { it.deletedAtMillis ?: 0L }
    }

    private fun readFromPrefs(): List<Record> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()

        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    Record.fromJson(obj)?.let { add(it) }
                }
            }
        } catch (e: Exception) {
            // ⚠️ 整份 JSON 壞掉時回傳空清單，而「下一次寫入」會以這份空清單覆寫儲存區，
            //    等於把使用者所有記錄洗掉。這裡先把原始字串備份到另一個鍵（只留最近一次），
            //    至少資料還在裝置上、有機會救回來。
            runCatching { prefs.edit().putString(KEY_RECORDS_BACKUP, raw).apply() }
            emptyList()
        }
    }

    private fun writeToPrefs(records: List<Record>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    companion object {
        const val KEY_RECORDS: String = "records"

        /** 整份 records JSON 解析失敗時，原始字串的備份位置（僅保留最近一次）。 */
        const val KEY_RECORDS_BACKUP: String = "records_corrupted_backup"

        /** 垃圾桶保留天數：刪除後 30 天內都可以還原。 */
        const val TRASH_RETENTION_DAYS: Int = 30
    }
}
