package com.example.countapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.AppContainer
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.notification.PaymentAccessibilityService
import com.example.countapp.ui.SectionCard
import com.example.countapp.ui.SettingRow
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors
import com.example.countapp.ui.trash.TrashDialog
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 我的（個人與設定）頁。
 *
 * 對應 Flutter 版的 `profile_page.dart`：
 * 統計概覽 + 自動記帳相關設定 + 白名單／規則表／無障礙探針管理。
 */
@Composable
fun ProfileScreen(container: AppContainer) {
    val records by container.recordRepository.records.collectAsState()
    val trashedRecords by container.recordRepository.trashedRecords.collectAsState()
    val settings = container.settingsStore

    val autoEnabled by settings.autoRecordEnabledFlow.collectAsState()
    val aiEnabled by settings.useAiClassificationFlow.collectAsState()
    val backgroundEnabled by settings.backgroundNotificationEnabledFlow.collectAsState()
    val budgetEnabled by settings.budgetNotificationEnabledFlow.collectAsState()

    // 浮球開關：訂閱可觀察版本，切換時（含無障礙服務持有的系統浮球）立即生效
    val ballEnabled by settings.floatingBallEnabledFlow.collectAsState()

    val totalExpense = remember(records) { container.recordRepository.totalExpense(records) }
    val totalIncome = remember(records) { container.recordRepository.totalIncome(records) }

    var showAiDialog by remember { mutableStateOf(false) }
    var showAppsDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showProbeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showTrashDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        // ===== 標題 =====
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MikuColors.Surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MikuColors.Primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Miku 記帳", color = MikuColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("原生 Android 版", color = MikuColors.TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 統計概覽 =====
        SectionCard {
            Column {
                Text("統計概覽", color = MikuColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    StatItem("總支出", "\$${AmountFormatter.format(totalExpense)}", MikuColors.Expense, Modifier.weight(1f))
                    StatItem("總收入", "\$${AmountFormatter.format(totalIncome)}", MikuColors.Income, Modifier.weight(1f))
                    StatItem("記錄筆數", "${records.size}", MikuColors.Primary, Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== 設定（依用途分組排序，垃圾桶已併入「資料」群組）=====
        SectionCard {
            Column {
                Text("設定", color = MikuColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                // ---------- 群組一：自動記錄與分類 ----------
                SettingGroupTitle("自動記錄與分類")

                SettingSwitchRow(Icons.Filled.PlayCircle, "自動記錄", autoEnabled) {
                    settings.autoRecordEnabled = it
                }
                if (autoEnabled || backgroundEnabled) AutoRecordPermissions()

                // 浮球是自動記錄的提醒入口（付款 App 上的系統浮球 + App 內右下角浮球），
                // 關掉只影響「提醒」，記錄照樣寫入，不會漏帳。
                SettingSwitchRow(Icons.Filled.TouchApp, "自動記帳浮球", ballEnabled) {
                    settings.floatingBallEnabled = it
                }

                SettingSwitchRow(Icons.Filled.Psychology, "AI 分類", aiEnabled) {
                    settings.useAiClassification = it
                }

                SettingRow(Icons.Filled.Info, "分類方式") {
                    StatusChip(
                        text = if (!autoEnabled) {
                            "⏸️ 已停用"
                        } else if (aiEnabled && settings.isAiConfigured) {
                            "🤖 AI 分類"
                        } else {
                            if (aiEnabled) "📋 規則表（AI 尚未設定）" else "📋 規則表分類"
                        },
                    )
                }

                SettingRow(Icons.Filled.Key, "AI API 設定") {
                    TextButton(onClick = { showAiDialog = true }) {
                        Text("設定", color = MikuColors.Primary, fontSize = 13.sp)
                    }
                }

                if (settings.isAiConfigured) {
                    SettingRow(Icons.Filled.Badge, "目前 AI 設定") {
                        Text(
                            text = "${settings.aiModel} ・ Key: ${settings.maskedApiKey}",
                            color = MikuColors.TextSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }

                SettingRow(Icons.Filled.Security, "自動記錄應用") {
                    TextButton(onClick = { showAppsDialog = true }) {
                        Text("管理", color = MikuColors.Primary, fontSize = 13.sp)
                    }
                }

                SettingRow(Icons.Filled.Visibility, "無障礙自動記錄") {
                    TextButton(onClick = { showProbeDialog = true }) {
                        Text("查看", color = MikuColors.Primary, fontSize = 13.sp)
                    }
                }

                SettingRow(Icons.AutoMirrored.Filled.Rule, "分類規則表") {
                    TextButton(onClick = { showRulesDialog = true }) {
                        Text("編輯", color = MikuColors.Primary, fontSize = 13.sp)
                    }
                }

                // ---------- 群組二：通知與提醒 ----------
                SettingGroupDivider()
                SettingGroupTitle("通知與提醒")

                SettingSwitchRow(Icons.Filled.NotificationsActive, "後台常駐通知", backgroundEnabled) {
                    settings.backgroundNotificationEnabled = it
                }

                SettingSwitchRow(Icons.Filled.Notifications, "預算提醒", budgetEnabled) {
                    settings.budgetNotificationEnabled = it
                }

                // ---------- 群組三：資料（垃圾桶已由獨立卡片移入這裡）----------
                SettingGroupDivider()
                SettingGroupTitle("資料")

                // 整列都可點（包含「垃圾桶」標籤本身），點擊即開啟垃圾桶對話框
                TrashSettingRow(
                    statusText = if (trashedRecords.isEmpty()) {
                        "目前沒有刪除的記錄"
                    } else {
                        "有 ${trashedRecords.size} 筆記錄，30 天內都可以還原"
                    },
                    actionText = if (trashedRecords.isEmpty()) "查看" else "還原",
                    onClick = { showTrashDialog = true },
                )

                // ---------- 群組四：關於 ----------
                SettingGroupDivider()

                SettingRow(Icons.Filled.Info, "關於") {
                    TextButton(onClick = { showAboutDialog = true }) {
                        Text("查看", color = MikuColors.Primary, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }

    if (showAiDialog) {
        AiSettingsDialog(container, onDismiss = { showAiDialog = false })
    }
    if (showAppsDialog) {
        AutoRecordAppsScreen(container.settingsStore, onDismiss = { showAppsDialog = false })
    }
    if (showRulesDialog) {
        RulesDialog(container, onDismiss = { showRulesDialog = false })
    }
    if (showProbeDialog) {
        ProbeDialog(onDismiss = { showProbeDialog = false })
    }
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
    if (showTrashDialog) {
        TrashDialog(container = container, onDismiss = { showTrashDialog = false })
    }
}

@Composable
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MikuColors.TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusChip(text: String) {
    Box(
        modifier = Modifier
            .background(MikuColors.Primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, color = MikuColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 設定清單的分組小標題（讓使用者一眼看出這一區在做什麼）。 */
@Composable
private fun SettingGroupTitle(text: String) {
    Text(
        text = text,
        color = MikuColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

/** 設定清單的分組分隔線。 */
@Composable
private fun SettingGroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
        thickness = 1.dp,
        color = MikuColors.SurfaceVariant,
    )
}

/**
 * 垃圾桶設定列：整列可點（含「垃圾桶」標籤本身），點擊即開啟垃圾桶對話框。
 *
 * 這裡不用 ui/Common.kt 的 [SettingRow]（它只有 icon/label/trailing，沒有
 * onClick 或 modifier 參數，而 Common.kt 不在本次改動範圍），因此用同樣的
 * 版型自己實作一列，並以 heightIn(min = 48.dp) 保證觸控高度。
 */
@Composable
private fun TrashSettingRow(statusText: String, actionText: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickableNoRipple(onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.RestoreFromTrash,
            contentDescription = null,
            tint = MikuColors.Primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "垃圾桶",
            color = MikuColors.Text,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        // 右側狀態文案 + 主要色動作提示（限寬，避免把左側標籤擠掉）
        Column(
            modifier = Modifier.widthIn(max = 180.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = statusText,
                color = MikuColors.TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.End,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = actionText, color = MikuColors.Primary, fontSize = 13.sp)
        }
    }
}

// ============================================================
// ✅ AI API 設定
// ============================================================

@Composable
private fun AiSettingsDialog(container: AppContainer, onDismiss: () -> Unit) {
    val settings = container.settingsStore
    var url by remember { mutableStateOf(settings.aiApiUrl) }
    var key by remember { mutableStateOf(settings.aiApiKey) }
    var model by remember { mutableStateOf(settings.aiModel) }

    AlertDialog(
        containerColor = MikuColors.Surface,
        title = { Text("AI API 設定", color = MikuColors.Text, fontSize = 18.sp) },
        text = {
            Column {
                Text(
                    "填入任何 OpenAI 相容的端點即可。留空則一律使用內建規則表分類。",
                    color = MikuColors.TextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                DialogTextField(url, { url = it }, "API URL")
                Spacer(modifier = Modifier.height(8.dp))
                DialogTextField(key, { key = it }, "API Key")
                Spacer(modifier = Modifier.height(8.dp))
                DialogTextField(model, { model = it }, "模型名稱")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.aiApiUrl = url
                settings.aiApiKey = key
                settings.aiModel = model
                onDismiss()
            }) { Text("儲存", color = MikuColors.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = MikuColors.TextSecondary) }
        },
        onDismissRequest = onDismiss,
    )
}

// ============================================================
// ✅ 無障礙自動記錄（含讀屏診斷紀錄）
// ============================================================

/**
 * 診斷紀錄的讀取時間（裝置時區）。
 *
 * 為什麼要顯示到秒：判斷「一次付款被讀了好幾次」時，光看日期分不出是同一次
 * 事件的風暴（同一秒內連續好幾個事件）還是使用者稍後回頭重看同一頁。
 */
private val ProbeLogTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")

private fun formatLogTime(millis: Long): String =
    if (millis <= 0L) {
        "時間不明"
    } else {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(ProbeLogTimeFormat)
    }

@Composable
private fun ProbeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var refreshToken by remember { mutableStateOf(0) }
    val enabled = remember(refreshToken) { PaymentAccessibilityService.isEnabled(context) }
    val logs = remember(refreshToken) {
        PaymentAccessibilityService.readLogs(context).filterNot { it.verdict.contains("略過：") }
    }

    AlertDialog(
        containerColor = MikuColors.Surface,
        title = { Text("無障礙自動記錄", color = MikuColors.Text, fontSize = 18.sp) },
        text = {
            Column {
                Text(
                    text = if (enabled) "✅ 服務已啟用" else "⚠️ 服務未啟用，請到系統設定的無障礙功能中開啟",
                    color = if (enabled) MikuColors.Income else MikuColors.Secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "啟用後會讀取微信／支付寶／AlipayHK／MPay 的付款結果畫面，" +
                        "判定為「已完成交易」時自動記一筆（需要「自動記錄」開關也是開啟的）。\n" +
                        "記完會直接在支付介面浮出可拖動圓球，點一下就能補分類與備註" +
                        "（不理它也不會漏記）。圓球可以在「我的」頁用「自動記帳浮球」開關關掉，" +
                        "關掉後仍然會照常記錄。\n" +
                        "記錄日期優先採用畫面顯示的交易時間（畫面沒寫才用讀到的當下時間）。\n" +
                        "下面的紀錄是讀屏診斷：每筆都標了讀取時間，" +
                        "🟡 代表看到類似交易但沒有明確成功字樣，因此不會被記錄。",
                    color = MikuColors.TextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row {
                    TextButton(onClick = { PaymentAccessibilityService.openSettings(context) }) {
                        Text("前往設定", color = MikuColors.Primary, fontSize = 12.sp)
                    }
                    TextButton(onClick = { refreshToken++ }) {
                        Text("重新整理", color = MikuColors.Primary, fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        PaymentAccessibilityService.clearLogs(context)
                        refreshToken++
                    }) {
                        Text("清除", color = MikuColors.Expense, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (logs.isEmpty()) {
                    Text(
                        "尚無紀錄。請先啟用服務，並操作一次付款或轉帳。",
                        color = MikuColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                } else {
                    Column(modifier = Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
                        // 最新的排在最上面
                        logs.reversed().forEach { log ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(MikuColors.Background, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                            ) {
                                Text(log.packageName, color = MikuColors.Text, fontSize = 11.sp)
                                // 讀取時間：用來核對「同一筆到底被讀了幾次、每次落在哪一天」
                                Text(
                                    text = formatLogTime(log.timeMillis),
                                    color = MikuColors.TextSecondary,
                                    fontSize = 10.sp,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    log.verdict,
                                    color = MikuColors.Text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "節點 ${log.nodeCount} 個／有文字 ${log.textCount} 個・${log.eventType}",
                                    color = MikuColors.TextSecondary,
                                    fontSize = 10.sp,
                                )
                                if (log.text.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(log.text, color = MikuColors.TextSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉", color = MikuColors.Primary) }
        },
        onDismissRequest = onDismiss,
    )
}

// ============================================================
// ✅ 關於
// ============================================================

/** 對話框內統一樣式的文字輸入框。 */
@Composable
private fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label, color = MikuColors.TextSecondary, fontSize = 12.sp) },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = MikuColors.Text,
            unfocusedTextColor = MikuColors.Text,
        ),
    )
}
