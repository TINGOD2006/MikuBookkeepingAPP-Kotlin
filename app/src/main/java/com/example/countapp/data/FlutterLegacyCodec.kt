package com.example.countapp.data

import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64

/**
 * Flutter `shared_preferences` 的舊資料解碼器。
 *
 * ⚠️ 為什麼需要這個：
 *    Flutter 的 shared_preferences 在 Android 上**不是**把 StringList 存成 JSON。
 *    底層實作（LegacySharedPreferencesPlugin.ListEncoder）是
 *    `LIST_PREFIX + Base64(Java ObjectOutputStream 序列化過的 ArrayList<String>)`，
 *    Double 則是 `DOUBLE_PREFIX + 數字字串`。
 *    直接當成 JSON 解析一定失敗——Flutter 版的原生程式碼就踩過這個坑，
 *    導致使用者自訂的包名白名單在原生端完全失效。
 *
 * 這個物件不依賴任何 Android API，可直接用 JVM 單元測試驗證。
 */
object FlutterLegacyCodec {

    /** `base64("This is the prefix for a list.")` */
    const val LIST_PREFIX: String = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"

    /** 較新的 shared_preferences 會用這個前綴 + JSON 編碼。 */
    const val JSON_LIST_PREFIX: String = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!"

    /** `base64("This is the prefix for Double.")` */
    const val DOUBLE_PREFIX: String = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"

    /**
     * 解碼 Flutter 儲存的 StringList。
     *
     * @return 解碼後的字串清單；格式不符或損毀時回傳 null（不拋例外）。
     */
    @JvmStatic
    fun decodeStringList(raw: String?): List<String>? {
        if (raw.isNullOrEmpty()) return null

        return when {
            raw.startsWith(JSON_LIST_PREFIX) -> decodeJsonList(raw.substring(JSON_LIST_PREFIX.length))
            raw.startsWith(LIST_PREFIX) -> decodePlatformList(raw.substring(LIST_PREFIX.length))
            else -> null
        }
    }

    /** 解碼 Flutter 儲存的 Double。 */
    @JvmStatic
    fun decodeDouble(raw: String?): Double? {
        if (raw.isNullOrEmpty()) return null
        if (!raw.startsWith(DOUBLE_PREFIX)) return null
        return raw.substring(DOUBLE_PREFIX.length).toDoubleOrNull()
    }

    /**
     * 解析記錄時間。
     *
     * Flutter 版寫入的是 `DateTime.toIso8601String()`：
     *   - UTC 時間會是 `2026-09-10T03:45:39.000Z`
     *   - 本地時間會是 `2026-09-10T03:45:39.000`
     * 原生端舊版另外用 SimpleDateFormat 產生帶 Z 的字串，
     * 兩種格式都必須能讀。
     */
    @JvmStatic
    fun parseLegacyDate(value: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (value.isNullOrEmpty()) return null

        return try {
            Instant.parse(value).toEpochMilli()
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * 把舊版的記錄 JSON 轉成 [Record]。
     *
     * 舊格式的 `date` / `createdAt` 是 ISO 字串（新版是 epoch millis），
     * 因此這裡兩種都接受。
     */
    @JvmStatic
    fun parseLegacyRecord(json: String?, zone: ZoneId = ZoneId.systemDefault()): Record? {
        if (json.isNullOrEmpty()) return null

        return try {
            val obj = org.json.JSONObject(json)
            val category = obj.optString("category")
            if (category.isEmpty()) return null

            val dateValue = obj.opt("date")
            val createdValue = obj.opt("createdAt")

            val dateMillis = when (dateValue) {
                is Number -> dateValue.toLong()
                else -> parseLegacyDate(dateValue as? String, zone)
            } ?: System.currentTimeMillis()

            val createdMillis = when (createdValue) {
                is Number -> createdValue.toLong()
                else -> parseLegacyDate(createdValue as? String, zone)
            } ?: dateMillis

            Record(
                amount = obj.optDouble("amount", 0.0),
                category = category,
                note = obj.optString("note"),
                dateMillis = dateMillis,
                createdAtMillis = createdMillis,
                id = obj.optString("id").ifEmpty { "legacy_$dateMillis" },
            )
        } catch (e: Exception) {
            null
        }
    }

    // ========== 內部 ==========

    private fun decodeJsonList(body: String): List<String>? = try {
        val array = JSONArray(body)
        buildList {
            for (i in 0 until array.length()) {
                add(array.optString(i))
            }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * 解碼 Java 序列化的 ArrayList<String>。
     *
     * ⚠️ 這裡會對 SharedPreferences 的內容做 ObjectInputStream 反序列化。
     *    資料來源是本 App 自己的私有儲存空間（其他 App 無法寫入），
     *    且 Flutter 版就是用它寫入的，因此這是唯一可行的讀法。
     *    轉檔完成後就不會再呼叫到這裡。
     */
    private fun decodePlatformList(body: String): List<String>? = try {
        val bytes = Base64.getDecoder().decode(body)
        ObjectInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val obj = stream.readObject()
            (obj as? List<*>)?.filterIsInstance<String>()
        }
    } catch (e: Exception) {
        null
    }
}
