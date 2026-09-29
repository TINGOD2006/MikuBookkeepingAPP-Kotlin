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
