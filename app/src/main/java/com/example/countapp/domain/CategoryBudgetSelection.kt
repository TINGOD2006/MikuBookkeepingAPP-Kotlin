package com.example.countapp.domain

/**
 * 決定預算頁「各分類預算」要列出哪些分類（純函式，可直接 JVM 單元測試）。
 *
 * 預算頁不再預設所有分類，只列出使用者自己新增（＝想管控）的分類。
 * 規則：
 *   1. 只有「已設定預算」（[configured]）與「剛按新增、還沒填金額」（[drafts]）
 *      的分類會出現；其餘分類一律不出現，也就不會畫出 0% 的假進度。
 *   2. 順序沿用分類表（[orderedCategories]）的順序，畫面重組時穩定，
 *      不會因為 JSON 鍵的順序或使用者新增的先後而跳動。
 *   3. 已設定預算、但分類表裡找不到的分類（例如分類被刪掉）仍然要列出來，
 *      否則那筆預算會變成看不見、也清不掉的孤兒。
 *   4. 這些「找不到的分類」接在後面並依名稱排序，結果才可預期。
 *
 * @param orderedCategories 分類表的完整順序（通常是支出分類清單）
 * @param configured 已設定預算的分類名稱
 * @param drafts 已按「新增分類預算」但還沒存金額的分類名稱
 */
fun visibleCategoryBudgetNames(
    orderedCategories: List<String>,
    configured: Collection<String>,
    drafts: Collection<String>,
): List<String> {
    val shown = LinkedHashSet<String>()
    shown.addAll(configured)
    shown.addAll(drafts)
    if (shown.isEmpty()) return emptyList()

    val known = orderedCategories.filter { it in shown }
    val knownSet = known.toHashSet()
    val extra = shown.filterNot { it in knownSet }.sorted()
    return known + extra
}
