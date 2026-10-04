package com.example.countapp.data

import android.content.SharedPreferences
import com.example.countapp.domain.CategoryCatalog
import com.example.countapp.domain.CategoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 分類名稱沿用舊版資料鍵；清單、順序及已刪分類的圖示一起保存。 */
class CategoryStore(private val prefs: SharedPreferences) {
    private val lock = Any()
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()

    fun categoriesFor(type: String): List<CategoryItem> {
        val defaults = CategoryCatalog.defaultsFor(type)
        val state = readState(type)
        if (state != null) {
            val known = state.optJSONArray("knownDefaults") ?: JSONArray()
            val knownNames = (0 until known.length()).map { known.optString(it) }.toSet()
            val items = decode(state.optJSONArray("items"))
            // 將來新增的預設項仍可加入；曾刪除或改名的內建項不會復活。
            return items + defaults.filter { it.name !in knownNames && items.none { item -> item.name == it.name } }
        }
        val custom = readLegacy(type)
        val overrides = custom.associateBy { it.name }
        val defaultNames = defaults.map { it.name }.toSet()
        return defaults.map { overrides[it.name] ?: it } + custom.filterNot { it.name in defaultNames }
    }

    fun find(type: String, name: String): CategoryItem? = categoriesFor(type).firstOrNull { it.name == name }

    fun findAny(name: String): CategoryItem? =
        find(CategoryCatalog.TYPE_EXPENSE, name) ?: find(CategoryCatalog.TYPE_INCOME, name)

    /** 歷史帳目保留已刪分類的圖示，但已刪分類不再出現在選擇清單。 */
    fun findForRecord(type: String, name: String): CategoryItem? =
        find(type, name) ?: decode(readState(type)?.optJSONArray("archived")).firstOrNull { it.name == name }

    fun displayCategories(type: String): List<CategoryItem> =
        (categoriesFor(type) + decode(readState(type)?.optJSONArray("archived"))).distinctBy { it.name }

    /** 列表一次建立查表，避免每筆帳目重複解析偏好設定。 */
    fun recordCategoryLookup(): Map<Pair<Boolean, String>, CategoryItem> = buildMap {
        CategoryCatalog.TYPES.forEach { type ->
            displayCategories(type).forEach { put((type == CategoryCatalog.TYPE_EXPENSE) to it.name, it) }
        }
    }

    /** 背景分類可能在改名前計算；在帳目寫入鎖內將舊名稱對應到當前名稱。 */
    fun resolveName(type: String, name: String): String {
        val aliases = readState(type)?.optJSONObject("aliases") ?: return name
        return aliases.optString(name, name)
    }

    fun customCategories(type: String): List<CategoryItem> =
        if (readState(type) == null) readLegacy(type) else categoriesFor(type).filter { it.isCustom }

    fun addCustomCategory(type: String, name: String, iconKey: String, colorArgb: Long): Boolean = synchronized(lock) {
        val trimmed = name.trim()
        val current = categoriesFor(type)
        if (trimmed.isEmpty() || current.any { it.name == trimmed }) return false
        val aliases = readState(type)?.optJSONObject("aliases") ?: JSONObject()
        aliases.remove(trimmed)
        save(type, current + CategoryItem(trimmed, iconKey, colorArgb, isCustom = true), aliases = aliases)
        true
    }

    fun editCategory(type: String, oldName: String, name: String, iconKey: String, colorArgb: Long): Boolean = synchronized(lock) {
        val trimmed = name.trim()
        val current = categoriesFor(type)
        if (trimmed.isEmpty() || current.none { it.name == oldName } ||
            current.any { it.name == trimmed && it.name != oldName }) return false
        val aliases = readState(type)?.optJSONObject("aliases") ?: JSONObject()
        if (oldName != trimmed) {
            // 對應表保持扁平，改回舊名稱也不會形成 A→B→A 循環。
            aliases.keys().asSequence().toList().forEach { key ->
                if (aliases.optString(key) == oldName) aliases.put(key, trimmed)
            }
            aliases.remove(trimmed)
            aliases.put(oldName, trimmed)
        }
        save(type, current.map { if (it.name == oldName) it.copy(name = trimmed, iconKey = iconKey, colorArgb = colorArgb) else it }, aliases = aliases)
        true
    }

    fun deleteCategory(type: String, name: String): Boolean = synchronized(lock) {
        val current = categoriesFor(type)
        val removed = current.firstOrNull { it.name == name } ?: return false
        val archived = decode(readState(type)?.optJSONArray("archived")).filterNot { it.name == name } + removed
        save(type, current.filterNot { it.name == name }, archived)
        true
    }

    /** 相容舊版移轉及呼叫端；分類設定頁使用可處理預設項的 deleteCategory。 */
    fun deleteCustomCategory(type: String, name: String): Boolean =
        if (find(type, name)?.isCustom == true) deleteCategory(type, name) else false

    fun reorder(type: String, names: List<String>): Boolean = synchronized(lock) {
        val current = categoriesFor(type)
        val byName = current.associateBy { it.name }
        if (names.size != current.size || names.toSet() != byName.keys) return false
        save(type, names.map { byName.getValue(it) })
        true
    }

    private fun save(
        type: String,
        items: List<CategoryItem>,
        archived: List<CategoryItem> = decode(readState(type)?.optJSONArray("archived")),
        aliases: JSONObject = readState(type)?.optJSONObject("aliases") ?: JSONObject(),
    ) {
        val oldKnown = readState(type)?.optJSONArray("knownDefaults") ?: JSONArray()
        val known = (0 until oldKnown.length()).map { oldKnown.optString(it) } + CategoryCatalog.defaultsFor(type).map { it.name }
        val state = JSONObject().apply {
            put("items", encode(items))
            put("archived", encode(archived))
            put("knownDefaults", JSONArray(known.distinct()))
            put("aliases", aliases)
        }
        prefs.edit().putString(stateKey(type), state.toString()).apply()
        _revision.value++
    }

    private fun readState(type: String): JSONObject? = try {
        prefs.getString(stateKey(type), null)?.let { JSONObject(it) }?.takeIf { it.optJSONArray("items") != null }
    } catch (_: Exception) { null }

    private fun readLegacy(type: String): List<CategoryItem> = try {
        val key = if (type == CategoryCatalog.TYPE_EXPENSE) KEY_CUSTOM_EXPENSE else KEY_CUSTOM_INCOME
        decode(prefs.getString(key, null)?.let { JSONArray(it) }, legacy = true)
    } catch (_: Exception) { emptyList() }

    private fun decode(array: JSONArray?, legacy: Boolean = false): List<CategoryItem> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isEmpty()) continue
            add(CategoryItem(name, obj.optString("iconKey", "label"), obj.optLong("colorArgb", 0xFF9E9E9E),
                if (legacy) true else obj.optBoolean("isCustom", false)))
        }
    }.distinctBy { it.name }

    private fun encode(items: List<CategoryItem>) = JSONArray().apply {
        items.forEach { item -> put(JSONObject().apply {
            put("name", item.name)
            put("iconKey", item.iconKey)
            put("colorArgb", item.colorArgb)
            put("isCustom", item.isCustom)
        }) }
    }

    private fun stateKey(type: String) = if (type == CategoryCatalog.TYPE_EXPENSE) "category_state_expense_v2" else "category_state_income_v2"

    companion object {
        const val KEY_CUSTOM_EXPENSE = "custom_expense_categories"
        const val KEY_CUSTOM_INCOME = "custom_income_categories"
    }
}
