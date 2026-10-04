package com.example.countapp.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.notification.PaymentAccessibilityService
import com.example.countapp.notification.PaymentNotificationListenerService
import com.example.countapp.ui.theme.MikuColors

@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notifications = PaymentNotificationListenerService.isNotificationAccessGranted(context)
    val accessibility = PaymentAccessibilityService.isEnabled(context)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MikuColors.Surface,
        title = { Text("關於與使用說明", color = MikuColors.Text) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text("Miku 記帳", color = MikuColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                AboutSection("用途與記錄準確性", "協助整理收支、分類與預算。本程式不代為付款或轉帳，也不需要支付帳戶密碼。自動辨識可能漏記、重複記錄或分錯類，請自行核對付款結果及帳目；預算提醒僅供參考。")
                AboutSection("通知、讀屏與應用清單", "系統授權範圍可能涵蓋其他通知或畫面；程式以您選取的 App 包名篩選交易來源。應用列表在本機查詢已安裝的 App 名稱、圖示和包名。無障礙診斷可能保存交易文字，包含金額、收款方及備註，分享前請檢查內容。")
                AboutSection("AI 與資料傳輸", "開啟 AI 並完成 API 設定後，交易文字會傳送至您填入的 API 端點，由該服務處理，可能產生服務費用。關閉 AI 則使用本機規則分類。請只使用您信任的端點，並了解其資料處理方式。")
                AboutSection("浮球、通知與耗電", "背景監聽、讀屏及網路請求可能增加耗電與資源使用；浮球會顯示在其他 App 上，可能遮住部分內容。可在設定關閉浮球與常駐通知。記帳不依賴您是否點擊浮球。")
                AboutSection("背景運作限制", "常駐通知可降低背景程序被回收的機會，但 Android 和手機廠商仍可停止服務，用戶也可停止 App、撤銷授權或關閉通知。Android 14 起一般常駐通知可被滑除，通知消失不代表監聽必然停止。若需要持續自動記錄，請在系統電池設定允許本程式背景運作。")
                AboutSection("儲存與備份", "帳目、規則、設定與診斷主要存於手機。Android 可能依系統備份設定備份應用資料。卸載、清除資料或裝置故障可能造成資料遺失；請保留可用的備份。")
                AboutSection("如何停止", "關閉「自動記錄」會停止新增自動帳目；若要停止讀屏診斷，請到 Android 設定停用本程式的無障礙服務。撤銷通知使用權限可停止接收通知。這些操作不會刪除既有帳目。")
                AboutSection("目前權限", "通知使用權限：${if (notifications) "已授予" else "未授予"}\n無障礙服務：${if (accessibility) "已啟用" else "未啟用"}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
    )
}

@Composable
private fun AboutSection(title: String, body: String) {
    Spacer(Modifier.height(14.dp))
    Text(title, color = MikuColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(body, color = MikuColors.TextSecondary, fontSize = 12.sp)
}
