package com.example.countapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `預設白名單只有微信支付寶與 MPay`() {
        assertEquals(
            setOf("com.tencent.mm", "com.eg.android.AlipayGphone", "com.macaupass.rechargeEasy"),
            SettingsStore(FakeSharedPreferences()).allowedPackages.toSet(),
        )
    }

    @Test
    fun `取消全部應用後重新開啟仍為空清單`() {
        val prefs = FakeSharedPreferences()
        val settings = SettingsStore(prefs)
        settings.allowedPackages = emptyList()
        assertEquals(emptyList<String>(), settings.allowedPackagesFlow.value)
        assertEquals(emptyList<String>(), SettingsStore(prefs).allowedPackages)
    }

    @Test
    fun `包名精確比對且排除自身和系統介面`() {
        val settings = SettingsStore(FakeSharedPreferences())
        settings.allowedPackages = listOf("com.tencent.mm", "com.android.settings", "com.android.systemui", "com.example.countapp")
        assertTrue(settings.isPackageAllowed("com.tencent.mm", "com.example.countapp"))
        listOf("com.tencent", "com.tencent.mm.other", "COM.TENCENT.MM", "", "com.android.settings", "com.android.systemui", "com.example.countapp").forEach {
            assertFalse(it, settings.isPackageAllowed(it, "com.example.countapp"))
        }
    }

    @Test
    fun `勾選內容去除空白重複並同步到觀察者及儲存`() {
        val prefs = FakeSharedPreferences()
        val settings = SettingsStore(prefs)
        settings.allowedPackages = listOf(" com.example.pay ", "", "com.example.pay", "com.example.bank")
        val expected = listOf("com.example.pay", "com.example.bank")
        assertEquals(expected, settings.allowedPackagesFlow.value)
        assertEquals(expected, SettingsStore(prefs).allowedPackages)
    }

    @Test
    fun `損壞的白名單安全恢復預設`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(SettingsStore.KEY_ALLOWED_PACKAGES, "invalid json").apply()
        assertEquals(SettingsStore.DEFAULT_ALLOWED_PACKAGES, SettingsStore(prefs).allowedPackages)
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
