package com.example.countapp.data

import android.content.SharedPreferences
import android.util.Log
import com.example.countapp.domain.CategoryItem
import org.json.JSONObject

/**
 * 把 Flutter 版（`FlutterSharedPreferences`）的資料搬到原生版。
 *
 * 因為新舊版使用相同的 `applicationId`，兩者共用同一個私有儲存空間，
 * 所以原生版可以直接讀取舊檔案的內容，使用者不需要手動匯出／匯入。
 *
 * 只會執行一次（以 [SettingsStore.migrationCompleted] 記錄）。
 * 舊資料**不會被刪除**，萬一轉檔有問題還能退回 Flutter 版。
 */
class FlutterPreferencesMigrator(
    private val legacyPrefs: SharedPreferences,
    private val recordRepository: RecordRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryStore: CategoryStore,
    private val settingsStore: SettingsStore,
) {

    /** 轉檔結果，供 UI 顯示一次性提示。 */
    data class MigrationResult(
        val ran: Boolean = false,
        val recordCount: Int = 0,
        val budgetCount: Int = 0,
        val customCategoryCount: Int = 0,
        val copiedSettings: Boolean = false,
        val error: String? = null,
    )

    /**
     * 若尚未轉檔且偵測到舊資料，就執行轉檔。
     *
     * 這個方法會做磁碟 I/O 與 JSON 解析，請在背景執行緒呼叫。
     */
    fun migrateIfNeeded(): MigrationResult {
        if (settingsStore.migrationCompleted) {
            return MigrationResult(ran = false)
        }

        val legacyKeys = legacyPrefs.all.keys
        if (legacyKeys.isEmpty()) {
            // 全新安裝，沒有舊資料可搬
            settingsStore.migrationCompleted = true
            return MigrationResult(ran = false)
        }

        return try {
            val records = migrateRecords()
            val budgets = migrateBudgets()
            val categories = migrateCustomCategories()
            val settings = migrateSettings()

            settingsStore.migrationCompleted = true
            Log.i(
                TAG,
                "✅ Flutter 資料移轉完成：記錄 $records 筆、預算 $budgets 筆、" +
                    "自訂分類 $categories 個、設定 ${if (settings) "已複製" else "無"}",
            )

            MigrationResult(
                ran = true,
                recordCount = records,
                budgetCount = budgets,
                customCategoryCount = categories,
                copiedSettings = settings,
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Flutter 資料移轉失敗: ${e.message}")
            // 不標記完成，下次啟動可以再試
            MigrationResult(ran = false, error = e.message)
        }
    }

    // ========== 記錄 ==========

    private fun migrateRecords(): Int {
        var count = 0

        // 1) 主要清單：flutter.records（StringList，平台編碼）
        val raw = legacyPrefs.getString(key("records"), null)
        FlutterLegacyCodec.decodeStringList(raw)?.forEach { json ->
            val record = FlutterLegacyCodec.parseLegacyRecord(json) ?: return@forEach
            // add() 會依 id 去重，重複執行也安全
            if (recordRepository.add(record)) count++
        }

        // 2) 原生端待匯入的記錄：flutter.native_record_<eventId>（單筆 JSON 字串）
        legacyPrefs.all.forEach { (k, value) ->
            if (!k.startsWith(key(NATIVE_RECORD_PREFIX))) return@forEach
            val json = value as? String ?: return@forEach
            val record = FlutterLegacyCodec.parseLegacyRecord(json) ?: return@forEach
            if (recordRepository.add(record)) count++
        }

        return count
    }

    // ========== 預算 ==========

    private fun migrateBudgets(): Int {
        var count = 0
        legacyPrefs.all.forEach { (k, value) ->
            if (!k.startsWith(key("budget_"))) return@forEach
            val month = BudgetRepository.parseKey(k.removePrefix("flutter.")) ?: return@forEach

            val amount = when (value) {
                // 舊版 Flutter 的 Double 會帶 DOUBLE_PREFIX 存成字串
                is String -> FlutterLegacyCodec.decodeDouble(value)
                is Number -> value.toDouble()
                else -> null
            } ?: return@forEach

            if (amount > 0) {
                budgetRepository.save(month, amount)
                count++
            }
        }
        return count
    }

    // ========== 自訂分類 ==========

    private fun migrateCustomCategories(): Int {
        var count = 0
        count += migrateCustomCategoryType("custom_expense_categories", "支出")
        count += migrateCustomCategoryType("custom_income_categories", "收入")
        return count
    }

    private fun migrateCustomCategoryType(legacyKey: String, type: String): Int {
        val raw = legacyPrefs.getString(key(legacyKey), null)
        val items = FlutterLegacyCodec.decodeStringList(raw) ?: return 0

        var count = 0
        items.forEach { json ->
            try {
                val obj = JSONObject(json)
                val name = obj.optString("name")
                if (name.isEmpty()) return@forEach

                val added = categoryStore.addCustomCategory(
                    type = type,
                    name = name,
                    iconKey = obj.optString("iconName", "label"),
                    colorArgb = obj.optLong("colorValue", 0xFF9E9E9E),
                )
                if (added) count++
            } catch (e: Exception) {
                Log.w(TAG, "跳過無法解析的自訂分類: $json")
            }
        }
        return count
    }

    // ========== 設定 ==========

    private fun migrateSettings(): Boolean {
        var copied = false

        legacyPrefs.all.forEach { (k, value) ->
            when (k.removePrefix(LEGACY_KEY_PREFIX)) {
                "auto_record_enabled" -> (value as? Boolean)?.let {
                    settingsStore.autoRecordEnabled = it; copied = true
                }
                "use_ai_classification" -> (value as? Boolean)?.let {
                    settingsStore.useAiClassification = it; copied = true
                }
                "background_notification_enabled" -> (value as? Boolean)?.let {
                    settingsStore.backgroundNotificationEnabled = it; copied = true
                }
                "budget_notification_enabled" -> (value as? Boolean)?.let {
                    settingsStore.budgetNotificationEnabled = it; copied = true
                }
                "ai_api_url" -> (value as? String)?.let {
                    settingsStore.aiApiUrl = it; copied = true
                }
                "ai_api_key" -> (value as? String)?.let {
                    settingsStore.aiApiKey = it; copied = true
                }
                "ai_model" -> (value as? String)?.let {
                    settingsStore.aiModel = it; copied = true
                }
                "custom_rules" -> (value as? String)?.let { raw ->
                    val rules = parseCustomRules(raw)
                    if (rules.isNotEmpty()) {
                        settingsStore.customRules = rules
                        copied = true
                    }
                }
                "allowed_package_names" -> (value as? String)?.let { raw ->
                    val packages = FlutterLegacyCodec.decodeStringList(raw)
                    if (!packages.isNullOrEmpty()) {
                        settingsStore.allowedPackages = packages
                        copied = true
                    }
                }
            }
        }
        return copied
    }

    private fun parseCustomRules(raw: String): Map<String, List<String>> = try {
        val json = JSONObject(raw)
        buildMap {
            json.keys().forEach { category ->
                val array = json.optJSONArray(category) ?: return@forEach
                val words = buildList {
                    for (i in 0 until array.length()) {
                        val word = array.optString(i).trim()
                        if (word.isNotEmpty()) add(word)
                    }
                }
                if (words.isNotEmpty()) put(category, words)
            }
        }
    } catch (e: Exception) {
        emptyMap()
    }

    private fun key(name: String): String = LEGACY_KEY_PREFIX + name

    companion object {
        private const val TAG = "FlutterMigration"

        /** Flutter shared_preferences 的檔案名稱。 */
        const val LEGACY_PREFS_NAME: String = "FlutterSharedPreferences"

        /** Flutter 端會為所有鍵加上這個前綴。 */
        const val LEGACY_KEY_PREFIX: String = "flutter."

        /** 原生端在 App 未執行時暫存的記錄鍵前綴。 */
        private const val NATIVE_RECORD_PREFIX = "native_record_"
    }
}
