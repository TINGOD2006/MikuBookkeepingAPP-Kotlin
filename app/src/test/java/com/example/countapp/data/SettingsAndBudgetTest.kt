package com.example.countapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * 白名單預設值與預算鍵名的單元測試。
 *
 * 白名單預設值是 Flutter 版實際出過包的地方：當時預設清單只放了
 * `com.alipay.android.app`（那是支付寶 SDK，不是主程式），
 * 導致支付寶完全記不到帳。原生版把清單收斂成唯一一份，這裡釘住它。
 */
class SettingsAndBudgetTest {

    @Test
    fun `預設白名單包含微信與支付寶`() {
        val defaults = SettingsStore.DEFAULT_ALLOWED_PACKAGES

        assertTrue("缺少微信", defaults.contains("com.tencent.mm"))
        assertTrue("缺少支付寶本體", defaults.contains("com.eg.android.AlipayGphone"))
        assertTrue("缺少 AlipayHK", defaults.contains("hk.alipay.wallet"))
        assertTrue("缺少支付寶 SDK", defaults.contains("com.alipay.android.app"))
        assertTrue("缺少 MPay", defaults.contains("com.macaupass.rechargeEasy"))
    }

    @Test
    fun `預設白名單沒有重複`() {
        val defaults = SettingsStore.DEFAULT_ALLOWED_PACKAGES
        assertEquals(defaults.size, defaults.toSet().size)
    }

    @Test
    fun `預設白名單回傳的是可修改的副本`() {
        val copy = SettingsStore.DEFAULT_ALLOWED_PACKAGES.toMutableList()
        copy.add("com.example.pay")

        // 原始清單不受影響（避免「恢復預設」被污染）
        assertEquals(
            false,
            SettingsStore.DEFAULT_ALLOWED_PACKAGES.contains("com.example.pay"),
        )
    }

    // ========== 預算鍵名 ==========

    @Test
    fun `預算鍵名格式與 Flutter 版一致`() {
        assertEquals("budget_2026_09", BudgetRepository.key(2026, 9))
        assertEquals("budget_2026_12", BudgetRepository.key(2026, 12))
    }

    @Test
    fun `預算鍵名可以來回轉換`() {
        val month = YearMonth.of(2026, 3)

        val parsed = BudgetRepository.parseKey(BudgetRepository.key(month.year, month.monthValue))

        assertEquals(month, parsed)
    }

    @Test
    fun `非預算鍵回傳 null`() {
        assertNull(BudgetRepository.parseKey("records"))
        assertNull(BudgetRepository.parseKey("budget_2026"))
        assertNull(BudgetRepository.parseKey("budget_2026_13"))
        assertNull(BudgetRepository.parseKey("budget_abc_01"))
    }
}
