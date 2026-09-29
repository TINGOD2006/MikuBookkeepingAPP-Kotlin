package com.example.countapp.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs

/**
 * 一筆記帳記錄。
 *
 * 對應 Flutter 版的 `lib/models/record.dart`：
 *   - [amount] 為負數代表支出、正數代表收入
 *   - 時間改以 epoch millis 儲存（原本是 ISO 字串），避免時區資訊在往返中遺失
 *
 * [deletedAtMillis] 為「軟刪除」時間戳：
 *   - `null`：正常記錄，會出現在明細與統計中
 *   - 有值：已移到垃圾桶的記錄，只出現在垃圾桶畫面，超過
 *     [RecordRepository.TRASH_RETENTION_DAYS] 天後由儲存庫自動清除
 *
 * 舊版本的 JSON 沒有這個欄位，解析時視為 `null`（相容舊資料）。
 */
data class Record(
    val amount: Double,
    val category: String,
    val note: String,
    val dateMillis: Long,
    val createdAtMillis: Long,
    val id: String,
    val deletedAtMillis: Long? = null,
) {
    val isExpense: Boolean get() = amount < 0

    /** 不含正負號的金額。 */
    val absoluteAmount: Double get() = abs(amount)

    /** 是否已在垃圾桶中。 */
    val isTrashed: Boolean get() = deletedAtMillis != null

    fun localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()

    /**
     * 垃圾桶還剩下幾天（[RecordRepository.TRASH_RETENTION_DAYS] → 0）。
     *
     * 未進垃圾桶的記錄回傳 0。
     */
    fun trashRemainingDays(nowMillis: Long = System.currentTimeMillis()): Int {
        val deletedAt = deletedAtMillis ?: return 0
        val elapsedDays = (nowMillis - deletedAt) / DAY_MILLIS
        return (RecordRepository.TRASH_RETENTION_DAYS - elapsedDays).coerceAtLeast(0L).toInt()
    }

    /** 編輯後的複本：只換掉內容欄位，保留 id、建立時間與垃圾桶狀態。 */
    fun edited(
        amount: Double,
        category: String,
        note: String,
        dateMillis: Long,
    ): Record = copy(
        amount = amount,
        category = category,
        note = note,
        dateMillis = dateMillis,
    )

    /** 是否已超過垃圾桶保留期。 */
    fun isTrashExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val deletedAt = deletedAtMillis ?: return false
        return nowMillis - deletedAt >= TRASH_RETENTION_MILLIS
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("amount", amount)
        put("category", category)
        put("note", note)
        put("date", dateMillis)
        put("createdAt", createdAtMillis)
        put("id", id)
        // 只有進垃圾桶的記錄才寫這個欄位，舊版／明細資料維持原本的 JSON 形狀
        deletedAtMillis?.let { put("deletedAt", it) }
    }

    companion object {
        /** 顯示用的日期格式（與 Flutter 版一致：2026/09/10）。 */
        private val DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy/MM/dd")

        /** 一天的毫秒數。 */
        private const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

        /** 垃圾桶保留期（毫秒）。 */
        val TRASH_RETENTION_MILLIS: Long =
            DAY_MILLIS * RecordRepository.TRASH_RETENTION_DAYS

        private val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm")

        /**
         * 解析 JSON。
         *
         * ⚠️ 採防禦式解析：任何一筆壞資料都只回傳 null，不可讓整份明細載入失敗
         *    （Flutter 版就是因為單筆壞資料會讓整頁空白才特別處理）。
         */
        fun fromJson(json: JSONObject): Record? {
            return try {
                val category = json.optString("category")
                if (category.isEmpty()) return null

                Record(
                    amount = json.optDouble("amount", 0.0),
                    category = category,
                    note = json.optString("note"),
                    dateMillis = json.optLong("date", System.currentTimeMillis()),
                    createdAtMillis = json.optLong("createdAt", System.currentTimeMillis()),
                    id = json.optString("id").ifEmpty {
                        UUID.randomUUID().toString()
                    },
                    // 舊資料沒有 deletedAt → 0 → 視為未刪除
                    deletedAtMillis = json.optLong("deletedAt", 0L).takeIf { it > 0L },
                )
            } catch (e: Exception) {
                null
            }
        }

        /** 建立一筆新記錄（支出 amount 需自行帶負號）。 */
        fun create(
            amount: Double,
            category: String,
            note: String,
            dateMillis: Long = System.currentTimeMillis(),
            createdAtMillis: Long = System.currentTimeMillis(),
            id: String = UUID.randomUUID().toString(),
        ): Record = Record(
            amount = amount,
            category = category,
            note = note,
            dateMillis = dateMillis,
            createdAtMillis = createdAtMillis,
            id = id,
        )

        fun formatDate(date: LocalDate): String = date.format(DATE_FORMAT)

        fun formatTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).format(TIME_FORMAT)
    }
}
