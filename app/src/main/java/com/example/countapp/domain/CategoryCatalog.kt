package com.example.countapp.domain

/**
 * 一個分類項目。
 *
 * [iconKey] 與 [colorArgb] 刻意不使用 Compose 的型別（ImageVector / Color），
 * 讓這個資料層能脫離 Android 框架、直接用 JVM 單元測試驗證；
 * UI 端再透過 `CategoryIcons.forKey()` 轉成實際圖示。
 */
data class CategoryItem(
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val isCustom: Boolean = false,
)

/**
 * 分類清單。
 *
 * 由 Flutter 版 `lib/constants/categories.dart` 移植，
 * 名稱、順序與顏色都保持一致（顏色為 Flutter `Colors.*` 的 ARGB 值）。
 */
object CategoryCatalog {

    const val TYPE_EXPENSE: String = "支出"
    const val TYPE_INCOME: String = "收入"

    val TYPES: List<String> = listOf(TYPE_EXPENSE, TYPE_INCOME)

    /** 常用支出在前，保留歷史名稱以免影響既有帳目及預算。 */
    val defaultExpenseCategories: List<CategoryItem> = listOf(
        CategoryItem("學費", "school", 0xFF2196F3),
        CategoryItem("購物", "shopping_bag", 0xFF9C27B0),
        CategoryItem("食物", "restaurant", 0xFFFF9800),
        CategoryItem("手機", "phone_android", 0xFF009688),
        CategoryItem("娛樂", "movie", 0xFFE91E63),
        CategoryItem("教育", "menu_book", 0xFF3F51B5),
        CategoryItem("美容", "spa", 0xFFFF4081),
        CategoryItem("運動", "fitness_center", 0xFF4CAF50),
        CategoryItem("社交", "people", 0xFF00BCD4),
        CategoryItem("交通", "directions_car", 0xFF607D8B),
        CategoryItem("衣服", "checkroom", 0xFF673AB7),
        CategoryItem("汽車", "car_repair", 0xFF9E9E9E),
        CategoryItem("酒", "local_bar", 0xFF795548),
        CategoryItem("香煙", "smoke_free", 0xFF9E9E9E),
        CategoryItem("電子", "computer", 0xFF00BCD4),
        CategoryItem("旅行", "flight_takeoff", 0xFF03A9F4),
        CategoryItem("醫療", "local_hospital", 0xFFF44336),
        CategoryItem("寵物", "pets", 0xFFFFC107),
        CategoryItem("维修", "build", 0xFF795548),
        CategoryItem("住房", "home", 0xFFFF9800),
        CategoryItem("居家", "chair", 0xFFCDDC39),
        CategoryItem("禮金", "card_giftcard", 0xFFE91E63),
        CategoryItem("捐款", "volunteer_activism", 0xFFFF5252),
        CategoryItem("彩票", "confirmation_number", 0xFF4CAF50),
        CategoryItem("零食", "icecream", 0xFFE91E63),
        CategoryItem("孩子", "child_care", 0xFF8BC34A),
        CategoryItem("蔬菜", "agriculture", 0xFF4CAF50),
        CategoryItem("轉帳", "swap_horiz", 0xFF2196F3),
        CategoryItem("房租", "home", 0xFFFF9800),
        CategoryItem("水電", "water_drop", 0xFF03A9F4),
        CategoryItem("通訊", "phone_android", 0xFF009688),
        CategoryItem("保險", "health_and_safety", 0xFF4CAF50),
        CategoryItem("稅務", "receipt_long", 0xFF607D8B),
        CategoryItem("訂閱", "music_note", 0xFFE91E63),
        CategoryItem("手續費", "account_balance", 0xFF795548),
        CategoryItem("日用品", "shopping_cart", 0xFF9C27B0),
        CategoryItem("紅包", "card_giftcard", 0xFFF44336),
        CategoryItem("其他", "more_horiz", 0xFF9E9E9E),
    ).let { categories ->
        val frequent = listOf("食物", "交通", "購物", "住房", "房租", "水電", "通訊", "日用品", "醫療", "教育", "保險", "訂閱")
        categories.sortedBy { frequent.indexOf(it.name).takeIf { index -> index >= 0 } ?: frequent.size }
    }

    /** 預設收入分類（順序與 Flutter 版相同）。 */
    val defaultIncomeCategories: List<CategoryItem> = listOf(
        CategoryItem("薪水", "payments", 0xFF4CAF50),
        CategoryItem("獎金", "emoji_events", 0xFFFFC107),
        CategoryItem("禮金", "card_giftcard", 0xFFE91E63),
        CategoryItem("福利", "health_and_safety", 0xFF009688),
        CategoryItem("投資收益", "trending_up", 0xFF4CAF50),
        CategoryItem("租金收入", "home_work", 0xFFFF9800),
        CategoryItem("兼職", "work_outline", 0xFF2196F3),
        CategoryItem("稿費", "edit_note", 0xFF9C27B0),
        CategoryItem("股息", "account_balance", 0xFF3F51B5),
        CategoryItem("利息", "savings", 0xFF00BCD4),
        CategoryItem("退稅", "receipt_long", 0xFF8BC34A),
        CategoryItem("保險理賠", "assignment_icon", 0xFFF44336),
        CategoryItem("賣出物品", "sell", 0xFFFF9800),
        CategoryItem("贈與", "favorite", 0xFFE91E63),
        CategoryItem("其他收入", "more_horiz", 0xFF9E9E9E),
    )

    /** 新增自訂分類時可選的圖示。 */
    val customIconKeys: List<String> = listOf(
        "label", "star", "favorite", "bolt", "water_drop", "cloud", "wb_sunny",
        "nightlight", "music_note", "movie", "book", "school", "work", "home",
        "shopping_cart", "restaurant", "directions_car", "flight", "pets",
        "sports_esports", "local_cafe", "fitness_center", "savings", "category",
    )

    /** 新增自訂分類時可選的顏色。 */
    val customColorArgb: List<Long> = listOf(
        0xFF9E9E9E, 0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7,
        0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688,
        0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFFFFC107, 0xFFFF9800,
        0xFFFF5722, 0xFF795548, 0xFF607D8B,
    )

    /** 預設分類（不含自訂）。 */
    fun defaultsFor(type: String): List<CategoryItem> =
        if (type == TYPE_EXPENSE) defaultExpenseCategories else defaultIncomeCategories

    /**
     * 依分類名稱取得圖示與顏色。
     *
     * 找不到時回傳 [fallback]（供 UI 顯示預設圖示）。
     */
    fun find(name: String): CategoryItem? =
        (defaultExpenseCategories + defaultIncomeCategories).firstOrNull { it.name == name }
}
