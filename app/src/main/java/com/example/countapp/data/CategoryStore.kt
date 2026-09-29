package com.example.countapp.data

import android.content.SharedPreferences
import com.example.countapp.domain.CategoryCatalog
import com.example.countapp.domain.CategoryItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * 分類清單（預設 + 使用者自訂）。
 *
 * 對應 Flutter 版的 `lib/constants/categories.dart`：
 * 自訂分類以 JSON 陣列存進 SharedPreferences，讀取時接在預設分類之後。
 */
class CategoryStore(
    private val prefs: SharedPreferences,
) {

    /** 取得某類型（支出／收入）的完整分類清單。 */
    fun categoriesFor(type: String): List<CategoryItem> =
        CategoryCatalog.defaultsFor(type) + customCategories(type)

    /**
     * 依名稱取得分類（含使用者自訂）。
     *
     * `CategoryCatalog.find()` 只看預設分類，這裡補上自訂分類，
     * 否則自訂分類的記錄在明細與編輯頁會顯示成灰色預設圖示。
     */
    fun find(type: String, name: String): CategoryItem? =
        categoriesFor(type).firstOrNull { it.name == name }

    /** 不分收支，依名稱取得分類（明細頁用：記錄不一定帶得出原本的收支類型）。 */
    fun findAny(name: String): CategoryItem? =
        find(CategoryCatalog.TYPE_EXPENSE, name)
            ?: find(CategoryCatalog.TYPE_INCOME, name)

    /** 只取自訂分類。 */
    fun customCategories(type: String): List<CategoryItem> {
        val raw = prefs.getString(keyFor(type), null) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()

        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val name = obj.optString("name")
                    if (name.isEmpty()) continue
                    add(
                        CategoryItem(
                            name = name,
                            iconKey = obj.optString("iconKey", "label"),
                            colorArgb = obj.optLong("colorArgb", 0xFF9E9E9E),
                            isCustom = true,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 新增自訂分類。
     *
     * @return false 表示同名分類已存在（預設或自訂皆算）。
     */
    fun addCustomCategory(
        type: String,
        name: String,
        iconKey: String,
        colorArgb: Long,
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (categoriesFor(type).any { it.name == trimmed }) return false

        val updated = customCategories(type) + CategoryItem(
            name = trimmed,
            iconKey = iconKey,
            colorArgb = colorArgb,
            isCustom = true,
        )
        writeCustom(type, updated)
        return true
    }

    /** 刪除自訂分類（預設分類不可刪）。 */
    fun deleteCustomCategory(type: String, name: String): Boolean {
        val current = customCategories(type)
        val updated = current.filterNot { it.name == name }
        if (updated.size == current.size) return false

        writeCustom(type, updated)
        return true
    }

    private fun writeCustom(type: String, items: List<CategoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("name", item.name)
                    put("iconKey", item.iconKey)
                    put("colorArgb", item.colorArgb)
                }
            )
        }
        prefs.edit().putString(keyFor(type), array.toString()).apply()
    }

    private fun keyFor(type: String): String =
        if (type == CategoryCatalog.TYPE_EXPENSE) KEY_CUSTOM_EXPENSE else KEY_CUSTOM_INCOME

    companion object {
        const val KEY_CUSTOM_EXPENSE: String = "custom_expense_categories"
        const val KEY_CUSTOM_INCOME: String = "custom_income_categories"
    }
}
