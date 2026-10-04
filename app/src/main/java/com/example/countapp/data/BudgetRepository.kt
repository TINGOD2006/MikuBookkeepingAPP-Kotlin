package com.example.countapp.data

import android.content.SharedPreferences
import com.example.countapp.domain.AmountFormatter
import org.json.JSONObject
import java.time.YearMonth

/**
 * 每月預算儲存庫。
 *
 * 鍵名沿用 Flutter 版的 `budget_YYYY_MM` 格式，方便資料移轉對照。
 *
 * 另外支援「各分類」的每月預算（[loadCategoryBudgets] 等）：
 * 分類預算可以完全不設定，未設定時讀出來就是 null／空 map，
 * 呼叫端不可自行補 0 當預設值（那會在預算頁變成 0% 的假進度）。
 */
class BudgetRepository(
    private val prefs: SharedPreferences,
) {

    fun save(year: Int, month: Int, amount: Double) {
        prefs.edit().putFloat(key(year, month), amount.toFloat()).apply()
    }

    fun load(year: Int, month: Int): Double? {
        val k = key(year, month)
        if (!prefs.contains(k)) return null
        // 舊版 Flutter 存的是 Double；原生端一开始用 Float。
        // 兩種都要能讀，否則使用者的預算會在升級後消失。
        return try {
            prefs.getFloat(k, 0f).toDouble()
        } catch (e: ClassCastException) {
            null
        }
    }

    fun load(month: YearMonth): Double? = load(month.year, month.monthValue)

    fun save(month: YearMonth, amount: Double) =
        save(month.year, month.monthValue, amount)

    fun clear(year: Int, month: Int) {
        prefs.edit().remove(key(year, month)).apply()
    }

    /** 所有已設定預算的月份。 */
    fun allMonths(): List<YearMonth> =
        prefs.all.keys
            .filter { it.startsWith(PREFIX) }
            .mapNotNull { parseKey(it) }
            .sorted()

    // ========== 各分類每月預算 ==========

    /**
     * 某個月的各分類預算。
     *
     * 只回傳「有設定且金額有效」的分類；完全沒設定時回傳空 map。
     */
    fun loadCategoryBudgets(month: YearMonth): Map<String, Double> {
        val forMonth = readCategoryBudgets()
            .optJSONObject(categoryMonthKey(month))
            ?: return emptyMap()

        val result = linkedMapOf<String, Double>()
        // 依 JSON 內的順序回傳，讓預算頁的清單順序穩定
        for (name in forMonth.keys()) {
            val amount = forMonth.optDouble(name, Double.NaN)
            // 0 或負數算不出使用率，一律視為未設定（不顯示進度）
            if (amount.isFinite() && amount > 0) result[name] = amount
        }
        return result
    }

    /** 某分類的每月預算；未設定時回傳 null（不是 0）。 */
    fun loadCategoryBudget(month: YearMonth, category: String): Double? =
        loadCategoryBudgets(month)[category]

    /**
     * 設定某分類的每月預算。
     *
     * 金額不是有限正數時直接視為「清除」，避免留下一筆永遠算不出使用率的預算。
     * 分類名稱空白時不寫入。
     */
    fun saveCategoryBudget(month: YearMonth, category: String, amount: Double) {
        val name = category.trim()
        if (name.isEmpty()) return
        if (!amount.isFinite() || amount <= 0) {
            clearCategoryBudget(month, name)
            return
        }

        val root = readCategoryBudgets()
        val key = categoryMonthKey(month)
        val forMonth = root.optJSONObject(key) ?: JSONObject()
        forMonth.put(name, AmountFormatter.round(amount))
        root.put(key, forMonth)
        writeCategoryBudgets(root)
    }

    /** 清除某分類的每月預算（只影響該分類，其他分類與整月預算都不動）。 */
    fun clearCategoryBudget(month: YearMonth, category: String) {
        val name = category.trim()
        val key = categoryMonthKey(month)
        val root = readCategoryBudgets()
        val forMonth = root.optJSONObject(key) ?: return
        if (!forMonth.has(name)) return

        forMonth.remove(name)
        // 整個月都沒有分類預算了就把該月的物件一起移除，不留下空殼
        if (forMonth.length() == 0) root.remove(key) else root.put(key, forMonth)
        writeCategoryBudgets(root)
    }

    /**
     * 讀取整份分類預算 JSON。
     *
     * 內容不是 JSON 或型別不對（例如被別的版本寫成非字串）時都當成「尚未設定」：
     * 分類預算是附加功能，壞掉也不能讓預算頁或整月預算跟著失效。
     */
    /** 已刪分類的歷史預算仍佔用名稱；改名前檢查所有月份，禁止隱式合併。 */
    fun canRenameCategory(oldName: String, newName: String): Boolean {
        if (oldName == newName) return true
        val root = readCategoryBudgets()
        return root.keys().asSequence().none { root.optJSONObject(it)?.has(newName) == true }
    }

    /** 分類名稱變更涵蓋所有月份，目標仍有歷史預算時不修改任何資料。 */
    fun renameCategory(oldName: String, newName: String) {
        if (oldName == newName) return
        if (!canRenameCategory(oldName, newName)) return
        val root = readCategoryBudgets()
        root.keys().forEach { month ->
            val values = root.optJSONObject(month) ?: return@forEach
            if (values.has(oldName)) {
                values.put(newName, values.get(oldName))
                values.remove(oldName)
            }
        }
        writeCategoryBudgets(root)
    }

    private fun readCategoryBudgets(): JSONObject {
        val raw = try {
            prefs.getString(KEY_CATEGORY_BUDGETS, null)
        } catch (e: ClassCastException) {
            null
        }
        if (raw.isNullOrEmpty()) return JSONObject()

        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun writeCategoryBudgets(root: JSONObject) {
        prefs.edit().putString(KEY_CATEGORY_BUDGETS, root.toString()).apply()
    }

    companion object {
        const val PREFIX: String = "budget_"

        /**
         * 各分類預算的儲存鍵：一份 JSON，例如
         * `{"2026-09":{"食物":3000.0,"交通":500.0}}`。
         *
         * 刻意不共用 `budget_` 前綴：`allMonths()` / [parseKey] 只認「整月預算」的
         * 鍵，混在同一個前綴下會讓 `allMonths()` 出現解析不了的鍵，
         * 也讓「分類預算」與「整月預算」有機會互相覆蓋。
         */
        const val KEY_CATEGORY_BUDGETS: String = "category_budgets"

        fun key(year: Int, month: Int): String =
            "$PREFIX${year}_${month.toString().padStart(2, '0')}"

        /** 分類預算的月份鍵（`YYYY-MM`），與整月預算的 `budget_YYYY_MM` 不同。 */
        fun categoryMonthKey(month: YearMonth): String =
            "${month.year}-${month.monthValue.toString().padStart(2, '0')}"

        fun parseKey(key: String): YearMonth? {
            val body = key.removePrefix(PREFIX)
            val parts = body.split('_')
            if (parts.size != 2) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            if (month !in 1..12) return null
            return YearMonth.of(year, month)
        }
    }
}
