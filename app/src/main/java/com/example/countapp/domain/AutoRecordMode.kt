package com.example.countapp.domain

/** 高層保留下層來源；系統授權與使用者選擇分開管理。 */
enum class AutoRecordMode(val notificationsEnabled: Boolean, val accessibilityEnabled: Boolean) {
    MANUAL(false, false),
    NOTIFICATION(true, false),
    ACCESSIBILITY(true, true);

    /** MPay 無編號的兩種來源不能憑同額猜測身份，統一由通知建立帳目。 */
    fun canCreateFromScreen(packageName: String): Boolean =
        accessibilityEnabled && packageName != PaymentNotificationAnalyzer.MPAY_PACKAGE
}
