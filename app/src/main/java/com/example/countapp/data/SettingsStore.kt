package com.example.countapp.data

import android.content.SharedPreferences
import com.example.countapp.domain.ClassificationRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _autoRecordEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_RECORD, false))
    val autoRecordEnabledFlow: StateFlow<Boolean> = _autoRecordEnabled.asStateFlow()
    private val _useAiClassification = MutableStateFlow(prefs.getBoolean(KEY_USE_AI, true))
    val useAiClassificationFlow: StateFlow<Boolean> = _useAiClassification.asStateFlow()
    private val _backgroundNotificationEnabled = MutableStateFlow(prefs.getBoolean(KEY_BACKGROUND_NOTIFICATION, false))
    val backgroundNotificationEnabledFlow: StateFlow<Boolean> = _backgroundNotificationEnabled.asStateFlow()
    private val _budgetNotificationEnabled = MutableStateFlow(prefs.getBoolean(KEY_BUDGET_NOTIFICATION, true))
    val budgetNotificationEnabledFlow: StateFlow<Boolean> = _budgetNotificationEnabled.asStateFlow()

    /** 同一份狀態供 UI 與服務使用，先持久保存再通知觀察者。 */
    private fun setBoolean(key: String, value: Boolean, state: MutableStateFlow<Boolean>) {
        prefs.edit().putBoolean(key, value).apply()
        state.value = value
    }

    /** 自動記錄開關。預設關閉（與 Flutter 版一致，需使用者主動開啟）。 */
    var autoRecordEnabled: Boolean
        get() = _autoRecordEnabled.value
        set(value) = setBoolean(KEY_AUTO_RECORD, value, _autoRecordEnabled)

    /** 是否使用 AI 分類（關閉時走內建規則表，不消耗 AI 額度）。 */
    var useAiClassification: Boolean
        get() = _useAiClassification.value
        set(value) = setBoolean(KEY_USE_AI, value, _useAiClassification)

    /** 背景常駐通知（讓服務在被滑掉後仍存活）。 */
    var backgroundNotificationEnabled: Boolean
        get() = _backgroundNotificationEnabled.value
        set(value) = setBoolean(KEY_BACKGROUND_NOTIFICATION, value, _backgroundNotificationEnabled)

    /** 預算提醒通知。 */
    var budgetNotificationEnabled: Boolean
        get() = _budgetNotificationEnabled.value
        set(value) = setBoolean(KEY_BUDGET_NOTIFICATION, value, _budgetNotificationEnabled)

    /** 僅保存尚待確認的記錄 id；帳目內容仍由記錄庫管理。 */
    var pendingPromptIds: List<String>
        get() = runCatching {
            val array = JSONArray(prefs.getString("pending_auto_record_ids", "[]"))
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                .distinct().takeLast(10)
        }.getOrDefault(emptyList())
        set(value) {
            prefs.edit().putString("pending_auto_record_ids", JSONArray(value.filter(String::isNotBlank).distinct().takeLast(10)).toString()).apply()
        }

    /**
     * 自動記帳浮球（同時控制付款 App 上的系統浮球與 App 內右下角浮球）。
     *
     * 為什麼要有這個開關：浮球是「提醒使用者補分類」的入口，但蓋在別人的
     * App 上，對不想被干擾的人來說就是一種打擾。關掉之後**記錄照樣會寫入**，
     * 只是不再有任何浮球，需要時仍可從通知點進來確認。
     *
     * ⚠️ 用 StateFlow 而不是像其他設定一樣每次讀 prefs：
     *    系統浮球由無障礙服務持有（見 PaymentAccessibilityService），
     *    App 內浮球由 Compose 持有；兩者都要在「使用者當下切換開關」時
     *    立刻收起已經顯示的浮球，所以需要一個可觀察的來源。
     *    prefs 仍是最終儲存位置（關掉 App 再開、服務被重啟都要記得），
     *    這裡的 StateFlow 只是「同一份設定的可觀察鏡像」，不可反向當成唯一來源。
     */
    private val _floatingBallEnabled = MutableStateFlow(prefs.getBoolean(KEY_FLOATING_BALL, true))

    /** 浮球開關的可觀察版本（UI 與無障礙服務訂閱它）。 */
    val floatingBallEnabledFlow: StateFlow<Boolean> = _floatingBallEnabled.asStateFlow()

    /** 是否顯示自動記帳浮球（預設開啟）。 */
    var floatingBallEnabled: Boolean
        get() = _floatingBallEnabled.value
        set(value) {
            prefs.edit().putBoolean(KEY_FLOATING_BALL, value).apply()
            _floatingBallEnabled.value = value
        }

    // ========== 支付 APP 白名單 ==========

    /**
     * 允許讀取的支付 App 包名。
     *
     * 未設定才使用預設；已儲存的空清單代表停用所有來源，不會恢復預設。
     */
    private fun readAllowedPackages(): List<String> {
        val raw = prefs.getString(KEY_ALLOWED_PACKAGES, null)
        if (raw.isNullOrEmpty()) return DEFAULT_ALLOWED_PACKAGES.toList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val pkg = array.optString(index).trim()
                    if (pkg.isNotEmpty()) add(pkg)
                }
            }.distinct()
        } catch (_: Exception) {
            DEFAULT_ALLOWED_PACKAGES.toList()
        }
    }

    private val _allowedPackages = MutableStateFlow(readAllowedPackages())
    val allowedPackagesFlow: StateFlow<List<String>> = _allowedPackages.asStateFlow()

    var allowedPackages: List<String>
        get() = _allowedPackages.value.toList()
        set(value) {
            val packages = value.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val array = JSONArray()
            packages.forEach { array.put(it) }
            prefs.edit().putString(KEY_ALLOWED_PACKAGES, array.toString()).apply()
            _allowedPackages.value = packages
        }

    /** 是否為允許的包名（系統與本 App 一律排除）。 */
    fun isPackageAllowed(packageName: String, ownPackage: String): Boolean {
        if (packageName.isEmpty()) return false
        if (packageName == "com.android.systemui") return false
        if (packageName == "com.android.settings") return false
        if (packageName == ownPackage) return false

        return packageName in allowedPackages
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
                        // 空陣列代表用戶明確停用此分類，不應在重開時恢復內建詞條。
                        put(category, words)
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
    fun effectiveRules(): Map<String, List<String>> {
        val categories = CategoryStore(prefs)
        val visible = com.example.countapp.domain.CategoryCatalog.TYPES.flatMap { categories.categoriesFor(it) }.map { it.name }.toSet()
        return ClassificationRules.mergeRules(customRules).filterKeys { it in visible }
    }

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
        const val KEY_FLOATING_BALL: String = "auto_record_floating_ball_enabled"
        const val KEY_ALLOWED_PACKAGES: String = "allowed_package_names"
        const val KEY_AI_API_URL: String = "ai_api_url"
        const val KEY_AI_API_KEY: String = "ai_api_key"
        const val KEY_AI_MODEL: String = "ai_model"
        const val KEY_CUSTOM_RULES: String = "custom_rules"
        const val KEY_MIGRATION_DONE: String = "flutter_migration_completed"

        /** 唯一的預設來源；新增支援 App 不應自動擴大用戶的允許清單。 */
        val DEFAULT_ALLOWED_PACKAGES: List<String> = listOf(
            "com.tencent.mm",                 // 微信／微信支付
            "com.eg.android.AlipayGphone",    // 支付寶（中國本體）
            "com.macaupass.rechargeEasy",     // MPay / Macau Pass 澳門通
        )
    }
}
