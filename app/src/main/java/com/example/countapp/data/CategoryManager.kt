package com.example.countapp.data

import com.example.countapp.domain.CategoryCatalog

/** 編輯名稱時同步既有的名稱型關聯；收支同名分類的規則仍供另一方向使用。 */
class CategoryManager(
    private val categories: CategoryStore,
    private val records: RecordRepository,
    private val budgets: BudgetRepository,
    private val settings: SettingsStore,
) {
    @Synchronized
    fun edit(type: String, oldName: String, name: String, iconKey: String, colorArgb: Long): Boolean {
        val trimmed = name.trim()
        if (type == CategoryCatalog.TYPE_EXPENSE && !budgets.canRenameCategory(oldName, trimmed)) return false
        val words = settings.effectiveRules()[oldName].orEmpty()
        if (!categories.editCategory(type, oldName, trimmed, iconKey, colorArgb)) return false
        if (oldName != trimmed) {
            records.renameCategory(type, oldName, trimmed)
            if (type == CategoryCatalog.TYPE_EXPENSE) budgets.renameCategory(oldName, trimmed)
            val rules = settings.customRules.toMutableMap()
            rules[trimmed] = (words + rules[trimmed].orEmpty()).distinct()
            if (categories.findAny(oldName) == null) rules.remove(oldName)
            settings.customRules = rules
        }
        return true
    }
}
