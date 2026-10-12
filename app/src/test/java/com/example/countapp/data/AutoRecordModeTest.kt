package com.example.countapp.data

import com.example.countapp.domain.AutoRecordMode
import com.example.countapp.domain.PaymentNotificationAnalyzer
import org.junit.Assert.*
import org.junit.Test

class AutoRecordModeTest {
    @Test fun `預設手動而舊版開啟設定恢復通知層不自動擴大讀屏授權`() {
        assertEquals(AutoRecordMode.MANUAL, SettingsStore(FakeSharedPreferences()).autoRecordMode)
        val prefs = FakeSharedPreferences()
        prefs.edit().putBoolean(SettingsStore.KEY_AUTO_RECORD, true).apply()
        val settings = SettingsStore(prefs)
        assertEquals(AutoRecordMode.NOTIFICATION, settings.autoRecordMode)
        assertTrue(settings.autoRecordMode.notificationsEnabled)
        assertFalse(settings.autoRecordMode.accessibilityEnabled)
    }

    @Test fun `三層切换即時通知服務並持久化兼容主開關`() {
        val prefs = FakeSharedPreferences()
        val settings = SettingsStore(prefs)
        AutoRecordMode.entries.forEach { mode ->
            settings.autoRecordMode = mode
            assertEquals(mode, settings.autoRecordModeFlow.value)
            assertEquals(mode != AutoRecordMode.MANUAL, settings.autoRecordEnabledFlow.value)
            assertEquals(mode, SettingsStore(prefs).autoRecordMode)
        }
        settings.autoRecordEnabled = false
        assertEquals(AutoRecordMode.MANUAL, settings.autoRecordMode)
        settings.autoRecordEnabled = true
        assertEquals(AutoRecordMode.ACCESSIBILITY, settings.autoRecordMode)
    }

    @Test fun `通知層不讀屏最高層仍讀通知且MPay只有通知新增帳目`() {
        assertFalse(AutoRecordMode.MANUAL.notificationsEnabled)
        assertFalse(AutoRecordMode.MANUAL.accessibilityEnabled)
        assertTrue(AutoRecordMode.NOTIFICATION.notificationsEnabled)
        assertFalse(AutoRecordMode.NOTIFICATION.accessibilityEnabled)
        assertTrue(AutoRecordMode.ACCESSIBILITY.notificationsEnabled)
        assertTrue(AutoRecordMode.ACCESSIBILITY.accessibilityEnabled)
        assertFalse(AutoRecordMode.ACCESSIBILITY.canCreateFromScreen(PaymentNotificationAnalyzer.MPAY_PACKAGE))
        assertTrue(AutoRecordMode.ACCESSIBILITY.canCreateFromScreen("com.tencent.mm"))
    }
}
