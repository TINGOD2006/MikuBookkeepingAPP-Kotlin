package com.example.countapp.domain

/**
 * 自動記帳的分類規則引擎。
 *
 * 由 Flutter 版 `lib/services/ai_service.dart` 的 `defaultRules` 與
 * `_classifyWithRules` 移植。行為完全一致：
 *   1. 取得「有效規則表」＝ 使用者自訂規則（優先）＋ 內建規則（未被覆蓋者）
 *   2. 逐一分類比對詞條，文字（轉小寫）包含任一詞條即歸入該分類
 *   3. 全部未命中 → 「其他」
 *
 * 這是純 Kotlin，可直接用 JVM 單元測試驗證。
 */
object ClassificationRules {

    /** 未命中任何規則時的預設分類。 */
    const val FALLBACK_CATEGORY: String = "其他"

    /**
     * 內建規則表。
     *
     * ⚠️ 使用 [LinkedHashMap] 維持插入順序：比對順序會影響結果
     *    （先命中者勝），Flutter 版的 Map 也是有序的。
     */
    val defaultRules: Map<String, List<String>> = linkedMapOf(
        "食物" to listOf(
            "food", "餐", "吃", "restaurant", "午餐", "晚餐", "早餐", "下午茶",
            "餐廳", "便當", "外賣", "delivery", "eat", "meal", "cafe", "coffee",
            "麥當勞", "肯德基", "星巴克", "摩斯", "漢堡王", "subway", "路易莎", "cama",
        ),
        "交通" to listOf(
            "transport", "交通", "車", "taxi", "bus", "mtr", "地鐵", "火車", "uber",
            "grab", "高鐵", "台鐵", "捷運", "公車", "客運", "渡輪", "加油", "停車",
            "停車費", "過路費", "etag", "車票", "機票",
        ),
        "購物" to listOf(
            "shop", "購物", "store", "超市", "網購", "電商", "pchome", "momo", "蝦皮",
            "shopee", "淘寶", "京東", "天貓", "costco", "家樂福", "全聯", "美廉社",
            "屈臣氏", "康是美", "寶雅", "uniqlo", "zara", "h&m", "nike", "adidas",
            "apple", "小米", "3c", "家電",
        ),
        "娛樂" to listOf(
            "entertain", "娛樂", "movie", "電影", "game", "遊戲", "演唱會", "票",
            "netflix", "spotify", "youtube", "premium", "迪士尼", "環球", "遊樂園",
            "ktv", "唱歌", "酒吧", "夜店", "party",
        ),
        "醫療" to listOf(
            "medical", "醫療", "doctor", "醫生", "醫院", "診所", "藥局", "看病",
            "掛號", "健保", "牙醫", "眼科", "皮膚科", "復健", "藥費", "檢查",
            "體檢", "疫苗", "口罩",
        ),
        "教育" to listOf(
            "education", "教育", "學校", "課程", "補習", "學費", "書", "教材",
            "文具", "才藝", "語言", "英文", "日文", "家教", "大學", "研究所",
            "考試", "證照", "培訓",
        ),
        "房租" to listOf("rent", "房租", "租金", "租屋", "套房", "公寓", "押金", "管理費"),
        "水電" to listOf(
            "水費", "電費", "煤氣", "瓦斯", "utilities", "水電", "天然氣", "帳單",
            "繳費", "台電", "自來水",
        ),
        "通訊" to listOf(
            "phone", "手機", "網路", "寬頻", "電信", "話費", "月租", "中華電信",
            "台灣大哥大", "遠傳", "亞太", "台灣之星",
        ),
        "保險" to listOf(
            "保險", "insurance", "保費", "壽險", "醫療險", "意外險", "車險",
            "產險", "儲蓄險", "投資型",
        ),
        "稅務" to listOf("稅", "tax", "所得稅", "營業稅", "房屋稅", "地價稅", "牌照稅"),
        "捐款" to listOf("捐款", "捐贈", "慈善", "公益", "fund", "donate", "紅十字會"),
        "紅包" to listOf("紅包", "禮金", "包紅", "結婚", "喜宴", "生日禮物"),
        "轉帳" to listOf("轉賬", "轉帳", "transfer", "轉帳成功", "轉賬成功", "轉帳給", "轉賬給"),
    )

    /**
     * 合併自訂與內建規則。
     *
     * 與 Flutter 版相同：使用者自訂的規則**完全覆蓋**該分類的詞條
     * （使用者編輯的就是整個清單），未編輯過的分類則沿用內建。
     */
    fun mergeRules(customRules: Map<String, List<String>>): Map<String, List<String>> {
        val merged = linkedMapOf<String, List<String>>()
        customRules.forEach { (category, words) ->
            val cleaned = words.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isNotEmpty()) merged[category] = cleaned
        }
        defaultRules.forEach { (category, words) ->
            merged.putIfAbsent(category, words)
        }
        return merged
    }

    /**
     * 依規則表分類文字。
     *
     * @return 命中的分類名稱；全部未命中時回傳 [FALLBACK_CATEGORY]。
     */
    fun classifyWith(text: String, rules: Map<String, List<String>>): String {
        val lowerText = text.lowercase()
        for ((category, keywords) in rules) {
            for (keyword in keywords) {
                if (lowerText.contains(keyword.lowercase())) {
                    return category
                }
            }
        }
        return FALLBACK_CATEGORY
    }

    /** 使用內建規則分類。 */
    fun classify(text: String): String = classifyWith(text, defaultRules)
}
