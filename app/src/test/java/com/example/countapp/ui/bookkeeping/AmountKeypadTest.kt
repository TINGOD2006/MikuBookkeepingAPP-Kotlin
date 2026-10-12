package com.example.countapp.ui.bookkeeping

import org.junit.Assert.*
import org.junit.Test

class AmountKeypadTest {
    @Test fun `預算空值從小數點開始仍是有效金額輸入`() {
        assertEquals("0.", applyAmountKey("", "."))
        assertEquals("0", applyAmountKey("", "⌫"))
    }
    @Test fun `數字鍵共用兩位小數限制並區分退格和清空`() {
        var input = "0"
        listOf("1", "2", ".", "3", "4", "5").forEach { input = applyAmountKey(input, it) }
        assertEquals("12.34", input)
        assertEquals("12.3", applyAmountKey(input, "⌫"))
        assertEquals("0", applyAmountKey(input, "清空"))
        assertEquals(input, applyAmountKey(input, "."))
    }
    @Test fun `非數字按鍵不能污染共用金額欄位`() {
        assertEquals("12", applyAmountKey("12", "保存"))
        assertEquals("12", applyAmountKey("12", "-"))
    }
}
