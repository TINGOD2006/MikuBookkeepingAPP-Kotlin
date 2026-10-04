package com.example.countapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分類規則的單元測試。
 *
 * 與 Flutter 版 `test/mpay_notification_test.dart` 的「自訂規則表」群組對應。
 */
class ClassificationRulesTest {

    @Test
    fun `AI 完整分類名稱不會被較短名稱搶先匹配`() {
        val known = listOf("保險", "其他", "保險理賠", "其他收入")
        assertEquals("保險理賠", ClassificationRules.resolveAiCategory("保險理賠", known))
        assertEquals("其他收入", ClassificationRules.resolveAiCategory("其他收入", known))
    }

    @Test
    fun `訂閱費不被商家名稱搶成購物`() {
        assertEquals("訂閱", ClassificationRules.classify("Apple iCloud subscription MOP8"))
    }

    @Test
    fun `手續費不被轉帳文字搶成轉帳`() {
        assertEquals("手續費", ClassificationRules.classify("跨行轉帳手續費 MOP3"))
    }

    @Test
    fun `日用品具有可用的內建分類詞條`() {
        assertEquals("日用品", ClassificationRules.classify("洗衣液 MOP32"))
    }

    @Test
    fun `明確清空內建分類不會自動恢復詞條`() {
        val merged = ClassificationRules.mergeRules(mapOf("食物" to emptyList()))
        assertEquals("其他", ClassificationRules.classifyWith("星巴克 MOP38", merged))
    }

    @Test
    fun `常見固定支出分類必須能在新增頁選取`() {
        listOf("房租", "水電", "通訊", "保險", "稅務", "訂閱", "手續費", "日用品", "其他").forEach { name ->
            org.junit.Assert.assertNotNull("無法選取 $name", CategoryCatalog.defaultExpenseCategories.firstOrNull { it.name == name })
        }
    }

    @Test
    fun `內建規則 麥當勞 歸入食物`() {
        assertEquals("食物", ClassificationRules.classify("麥當勞 MOP45"))
    }

    @Test
    fun `內建規則 星巴克 歸入食物`() {
        assertEquals("食物", ClassificationRules.classify("星巴克咖啡 MOP38"))
    }

    @Test
    fun `內建規則 捷運 歸入交通`() {
        assertEquals("交通", ClassificationRules.classify("捷運車費 MOP6"))
    }

    @Test
    fun `內建規則 轉賬 歸入轉帳`() {
        assertEquals("轉帳", ClassificationRules.classify("成功轉賬MOP1.00"))
    }

    @Test
    fun `無匹配時歸入其他`() {
        assertEquals("其他", ClassificationRules.classify("外星人入侵 MOP999"))
    }

    @Test
    fun `自訂規則會完全覆蓋該分類的內建詞條`() {
        // 使用者編輯「食物」時，整份詞條清單會被自訂內容取代
        val custom = mapOf("食物" to listOf("麥當勞"))

        val merged = ClassificationRules.mergeRules(custom)

        assertEquals("食物", ClassificationRules.classifyWith("麥當勞 MOP45", merged))
        // 星巴克原本是「食物」的內建詞條，被覆蓋後就不該再歸入食物
        assertEquals("其他", ClassificationRules.classifyWith("星巴克 MOP38", merged))
    }

    @Test
    fun `自訂分類可以搶走原本屬於內建分類的詞條`() {
        // Flutter 版的對應測試：把「麥當勞」改歸入「娛樂」
        val custom = mapOf("娛樂" to listOf("麥當勞"))

        val merged = ClassificationRules.mergeRules(custom)

        // 自訂規則排在前面，先命中者勝
        assertEquals("娛樂", ClassificationRules.classifyWith("麥當勞 MOP45", merged))
    }

    @Test
    fun `未編輯的分類仍沿用內建規則`() {
        val custom = mapOf("寵物" to listOf("狗糧"))

        val merged = ClassificationRules.mergeRules(custom)

        assertEquals("寵物", ClassificationRules.classifyWith("買狗糧 MOP120", merged))
        // 未涵蓋到的分類照樣用內建
        assertEquals("交通", ClassificationRules.classifyWith("捷運 MOP6", merged))
    }

    @Test
    fun `空白詞條會被忽略`() {
        val custom = mapOf("測試" to listOf("  ", ""))

        val merged = ClassificationRules.mergeRules(custom)

        // 整組都是空白 → 不納入，因此不會出現「測試」這個分類
        assertEquals(false, merged.containsKey("測試"))
    }
}
