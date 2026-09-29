package com.example.countapp.data

import android.content.SharedPreferences
import com.example.countapp.domain.ClassificationRules
import org.json.JSONArray
import org.json.JSONObject

/**
 * 所有使用者設定的集中地。
 *
 * 鍵名與 Flutter 版一致（不含 Flutter 端的 `flutter.` 前綴），
 * 讓資料移轉可以一對一對照。
 */
class SettingsStore(
    private val prefs: SharedPreferences,
) {

    // ========== 自動記帳 ==========

    /** 自動記錄開關。預設關閉（與 Flutter 版一致，需使用者主動開啟）。 */
    var autoRecordEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECORD, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RECORD, value).apply()

    /** 是否使用 AI 分類（關閉時走內建規則表，不消耗 AI 額度）。 */
    var useAiClassification: Boolean
        get() = prefs.getBoolean(KEY_USE_AI, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_AI, value).apply()

    /** 背景常駐通知（讓服務在被滑掉後仍存活）。 */
    var backgroundNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_NOTIFICATION, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_NOTIFICATION, value).apply()

    /** 預算提醒通知。 */
    var budgetNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_BUDGET_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_BUDGET_NOTIFICATION, value).apply()

    // ========== 支付 APP 白名單 ==========

    /**
     * 允許讀取的支付 App 包名。
     *
     * 未設定時回傳 [DEFAULT_ALLOWED_PACKAGES]（永遠回傳可修改的副本）。
     */
    var allowedPackages: List<String>
        get() {
            val raw = prefs.getString(KEY_ALLOWED_PACKAGES, null)
            if (raw.isNullOrEmpty()) return DEFAULT_ALLOWED_PACKAGES.toList()

            val parsed = try {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        val pkg = array.optString(i).trim()
                        if (pkg.isNotEmpty()) add(pkg)
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
            return parsed.ifEmpty { DEFAULT_ALLOWED_PACKAGES.toList() }
        }
        set(value) {
            val array = JSONArray()
            value.forEach { array.put(it) }
            prefs.edit().putString(KEY_ALLOWED_PACKAGES, array.toString()).apply()
        }

    /** 是否為允許的包名（系統與本 App 一律排除）。 */
    fun isPackageAllowed(packageName: String, ownPackage: String): Boolean {
        if (packageName.isEmpty()) return false
        if (packageName == "com.android.systemui") return false
        if (packageName == "com.android.settings") return false
        if (packageName == ownPackage) return false

        return allowedPackages.any { pkg ->
            packageName.contains(pkg, ignoreCase = true) ||
                pkg.contains(packageName, ignoreCase = true)
        }
    }

    // ========== AI 設定 ==========

    var aiApiUrl: String
        get() = prefs.getString(KEY_AI_API_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_AI_API_URL, value.trim()).apply()

    var aiApiKey: String
        get() = prefs.getString(KEY_AI_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_AI_API_KEY, value.trim()).apply()

    var aiModel: String
        get() = prefs.getString(KEY_AI_MODEL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_AI_MODEL, value.trim()).apply()

    /** AI 設定是否完整（三個欄位都填了才能用）。 */
    val isAiConfigured: Boolean
        get() = aiApiUrl.isNotEmpty() && aiApiKey.isNotEmpty() && aiModel.isNotEmpty()

    /** 遮蔽後的 API Key，供設定頁顯示。 */
    val maskedApiKey: String
        get() {
            val key = aiApiKey
            if (key.isEmpty()) return "未設定"
            if (key.length <= 8) return "****"
            return key.take(4) + "****" + key.takeLast(4)
        }

    // ========== 自訂分類規則 ==========

    /** 使用者自訂的分類規則（只存有編輯過的分類）。 */
    var customRules: Map<String, List<String>>
        get() {
            val raw = prefs.getString(KEY_CUSTOM_RULES, null) ?: return emptyMap()
            if (raw.isEmpty()) return emptyMap()

            return try {
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
        }
        set(value) {
            val json = JSONObject()
            value.forEach { (category, words) ->
                json.put(category, JSONArray(words))
            }
            prefs.edit().putString(KEY_CUSTOM_RULES, json.toString()).apply()
        }

    /** 自訂 + 內建合併後的有效規則表。 */
    fun effectiveRules(): Map<String, List<String>> =
        ClassificationRules.mergeRules(customRules)

    /** 恢復內建規則。 */
    fun resetRules() {
        prefs.edit().remove(KEY_CUSTOM_RULES).apply()
    }

    // ========== 資料移轉 ==========

    /** 是否已完成 Flutter 版資料移轉。 */
    var migrationCompleted: Boolean
        get() = prefs.getBoolean(KEY_MIGRATION_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_MIGRATION_DONE, value).apply()

    companion object {
        const val KEY_AUTO_RECORD: String = "auto_record_enabled"
        const val KEY_USE_AI: String = "use_ai_classification"
        const val KEY_BACKGROUND_NOTIFICATION: String = "background_notification_enabled"
        const val KEY_BUDGET_NOTIFICATION: String = "budget_notification_enabled"
        const val KEY_ALLOWED_PACKAGES: String = "allowed_package_names"
        const val KEY_AI_API_URL: String = "ai_api_url"
        const val KEY_AI_API_KEY: String = "ai_api_key"
        const val KEY_AI_MODEL: String = "ai_model"
        const val KEY_CUSTOM_RULES: String = "custom_rules"
        const val KEY_MIGRATION_DONE: String = "flutter_migration_completed"

        /**
         * 預設支援的支付 App 包名。
         *
         * ⚠️ 這裡是唯一的來源。Flutter 版因為分成 Dart 與 Kotlin 兩份，
         *    曾經漏掉支付寶本體（只放了 SDK 套件 com.alipay.android.app），
         *    導致支付寶預設完全記不到帳。原生版不會再有這個問題。
         */
        val DEFAULT_ALLOWED_PACKAGES: List<String> = listOf(
            "com.tencent.mm",                 // 微信／微信支付
            "com.eg.android.AlipayGphone",    // 支付寶（中國本體）
            "hk.alipay.wallet",               // AlipayHK
            "com.alipay.android.app",         // 支付寶 SDK／安全支付（部分交易只有這裡會發通知）
            "com.macaupass.rechargeEasy",     // MPay / Macau Pass 澳門通
            "com.google.android.apps.wallet", // Google Pay / Google Wallet
            "com.apple.wallet",               // Apple Wallet
            "com.octopus.nfc",                // 八達通
            "hk.com.boc.bocmobilebanking",    // 中銀香港
            "com.icbc.imobile",               // 工銀亞洲
        )
    }
}
