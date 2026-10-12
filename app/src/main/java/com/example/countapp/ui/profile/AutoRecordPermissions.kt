package com.example.countapp.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.countapp.notification.PaymentAccessibilityService
import com.example.countapp.notification.PaymentNotificationListenerService
import com.example.countapp.ui.theme.MikuColors
import com.example.countapp.data.SettingsStore
import androidx.compose.runtime.collectAsState

/** 設定可先選好；從系統授權頁返回時重新核對實際權限。 */
@Composable
internal fun AutoRecordPermissions(settings: SettingsStore) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var notifications by remember { mutableStateOf(PaymentNotificationListenerService.isNotificationAccessGranted(context)) }
    var accessibility by remember { mutableStateOf(PaymentAccessibilityService.isEnabled(context)) }
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var batteryExempt by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    val mode by settings.autoRecordModeFlow.collectAsState()
    val ballEnabled by settings.floatingBallEnabledFlow.collectAsState()
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifications = PaymentNotificationListenerService.isNotificationAccessGranted(context)
                accessibility = PaymentAccessibilityService.isEnabled(context)
                batteryExempt = power.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column {
        Text(
            "通知記錄：${if (!mode.notificationsEnabled) "已停用" else if (notifications) "已授權" else "待授權，尚不能記錄"} · " +
                "畫面讀取：${if (!mode.accessibilityEnabled) "已停用" else if (accessibility) "已授權" else "待授權"}",
            color = MikuColors.TextSecondary, fontSize = 11.sp,
        )
        Row {
            if (mode.notificationsEnabled && !notifications) TextButton(onClick = { PaymentNotificationListenerService.openNotificationAccessSettings(context) }) {
                Text("通知使用權限", fontSize = 12.sp)
            }
            if (!accessibility && (mode.accessibilityEnabled || ballEnabled)) TextButton(onClick = { PaymentAccessibilityService.openSettings(context) }) {
                Text(if (mode.accessibilityEnabled) "無障礙服務" else "浮球所需無障礙權限", fontSize = 12.sp)
            }
        }
        if (mode.notificationsEnabled) Text("請同時允許 MPay 發送交易通知；系統通知使用權限與 MPay 的通知開關都需要開啟。",
            color = MikuColors.TextSecondary, fontSize = 11.sp)
        Text("電池最佳化：${if (batteryExempt) "已豁免" else "系統預設"}。若背景記錄中斷，請在 App 資訊允許背景電池使用。",
            color = MikuColors.TextSecondary, fontSize = 11.sp)
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("背景運作設定", fontSize = 12.sp) }
    }
}
