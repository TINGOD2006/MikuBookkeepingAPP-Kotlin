package com.example.countapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 金額格式化的單元測試。
 *
 * 與 Flutter 版 `test/float_amount_test.dart` 對應，規則必須完全一致。
 */
class AmountFormatterTest {

    // ========== format ==========

    @Test
    fun `整數金額不顯示小數`() {
        assertEquals("0", AmountFormatter.format(0.0))
        assertEquals("12", AmountFormatter.format(12.0))
        assertEquals("-12", AmountFormatter.format(-12.0))
    }

    @Test
    fun `保留有效的小數`() {
        assertEquals("12.5", AmountFormatter.format(12.5))
        assertEquals("-12.5", AmountFormatter.format(-12.5))
        assertEquals("0.75", AmountFormatter.format(0.75))
        assertEquals("-0.75", AmountFormatter.format(-0.75))
    }

    @Test
    fun `尾端多餘的 0 會被移除`() {
        assertEquals("12.5", AmountFormatter.format(12.50))
        assertEquals("12", AmountFormatter.format(12.00))
        assertEquals("12.1", AmountFormatter.format(12.10))
    }

    @Test
    fun `超過兩位小數會四捨五入`() {
        assertEquals("12.57", AmountFormatter.format(12.567))
        assertEquals("12.56", AmountFormatter.format(12.564))
        assertEquals("-12.57", AmountFormatter.format(-12.567))
    }

    @Test
    fun `浮點誤差不會顯示成一長串數字`() {
        assertEquals("0.3", AmountFormatter.format(0.1 + 0.2))
    }

    @Test
    fun `不會顯示 -0`() {
        assertEquals("0", AmountFormatter.format(-0.001))
        assertEquals("0", AmountFormatter.format(-0.0))
    }

    // ========== round ==========

    @Test
    fun `round 正規化到兩位小數`() {
        assertEquals(12.57, AmountFormatter.round(12.567), 1e-9)
        assertEquals(12.5, AmountFormatter.round(12.5), 1e-9)
        assertEquals(12.0, AmountFormatter.round(12.0), 1e-9)
    }

    @Test
    fun `round 消除浮點誤差累積`() {
        assertEquals(0.3, AmountFormatter.round(0.1 + 0.2), 1e-9)
        assertEquals(3.3, AmountFormatter.round(1.1 + 2.2), 1e-9)
    }

    // ========== 千分位 ==========

    @Test
    fun `千分位只加在整數部分`() {
        assertEquals("1,234.5", AmountFormatter.formatWithSeparator(1234.5))
        assertEquals("1,234,567", AmountFormatter.formatWithSeparator(1234567.0))
        assertEquals("-1,234.56", AmountFormatter.formatWithSeparator(-1234.56))
        assertEquals("999", AmountFormatter.formatWithSeparator(999.0))
        assertEquals("1,000", AmountFormatter.formatWithSeparator(1000.0))
    }

    // ========== 輸入中的字串 ==========

    @Test
    fun `輸入中的字串結尾帶小數點也能分組`() {
        assertEquals("1,234.", AmountFormatter.formatInput("1234."))
        assertEquals("1,234.5", AmountFormatter.formatInput("1234.5"))
    }

    @Test
    fun `輸入尚未完成時不會掉字`() {
        assertEquals("0", AmountFormatter.formatInput("0"))
        assertEquals("0.", AmountFormatter.formatInput("0."))
        assertEquals("0", AmountFormatter.formatInput(""))
        assertEquals("12", AmountFormatter.formatInput("12"))
    }
}
