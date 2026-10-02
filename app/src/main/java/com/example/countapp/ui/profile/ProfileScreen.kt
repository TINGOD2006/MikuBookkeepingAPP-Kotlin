package com.example.countapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.AppContainer
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.notification.PaymentAccessibilityService
import com.example.countapp.notification.PaymentNotificationListenerService
import com.example.countapp.ui.ConfirmDialog
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
    val context = LocalContext.current
    val records by container.recordRepository.records.collectAsState()
    val trashedRecords by container.recordRepository.trashedRecords.collectAsState()
    val settings = container.settingsStore

    // 設定不是 StateFlow，用版本號在使用者變更後重繪
    var version by remember { mutableStateOf(0) }

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

                SettingRow(Icons.Filled.PlayCircle, "自動記錄") {
                    Switch(
                        checked = settings.autoRecordEnabled,
                        onCheckedChange = { enabled ->
                            settings.autoRecordEnabled = enabled
                            version++

                            if (enabled &&
                                !PaymentNotificationListenerService.isNotificationAccessGranted(context)
                            ) {
                                // 沒授權的話服務永遠收不到通知，直接帶使用者去設定
                                PaymentNotificationListenerService.openNotificationAccessSettings(context)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = MikuColors.Primary),
                    )
                }

                // 浮球是自動記錄的提醒入口（付款 App 上的系統浮球 + App 內右下角浮球），
                // 關掉只影響「提醒」，記錄照樣寫入，不會漏帳。
                SettingRow(Icons.Filled.TouchApp, "自動記帳浮球") {
                    Switch(
                        checked = ballEnabled,
                        onCheckedChange = {
                            settings.floatingBallEnabled = it
                            version++
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = MikuColors.Primary),
                    )
                }

                SettingRow(Icons.Filled.Psychology, "AI 分類") {
                    Switch(
                        checked = settings.useAiClassification,
                        enabled = settings.autoRecordEnabled,
                        onCheckedChange = {
                            settings.useAiClassification = it
                            version++
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = MikuColors.Primary),
                    )
                }

                SettingRow(Icons.Filled.Info, "分類方式") {
                    StatusChip(
                        text = if (!settings.autoRecordEnabled) {
                            "⏸️ 已停用"
                        } else if (settings.useAiClassification) {
                            "🤖 AI 分類"
                        } else {
                            "📋 規則表分類"
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

                SettingRow(Icons.Filled.NotificationsActive, "後台常駐通知") {
                    Switch(
                        checked = settings.backgroundNotificationEnabled,
                        onCheckedChange = {
                            settings.backgroundNotificationEnabled = it
                            version++
                            // 讓已在執行的服務即時套用（否則要等下次重新綁定）
                            PaymentNotificationListenerService.requestRebindService(context)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = MikuColors.Primary),
                    )
                }

                SettingRow(Icons.Filled.Notifications, "預算提醒") {
                    Switch(
                        checked = settings.budgetNotificationEnabled,
                        onCheckedChange = {
                            settings.budgetNotificationEnabled = it
                            version++
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = MikuColors.Primary),
                    )
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
        AiSettingsDialog(container, onDismiss = { showAiDialog = false; version++ })
    }
    if (showAppsDialog) {
        AutoRecordAppsDialog(container.settingsStore, onDismiss = { showAppsDialog = false; version++ })
    }
    if (showRulesDialog) {
        RulesDialog(container, onDismiss = { showRulesDialog = false; version++ })
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
// ✅ 分類規則表
// ============================================================

/**
 * 解析使用者輸入的詞條字串。
 *
 * 支援半形逗號、全形逗號、半形空白（含 Tab／換行）與全形空白分隔，
 * 逐項 trim 後丟掉空字串。
 */
private fun parseRuleWords(raw: String): List<String> =
    raw.split(Regex("[,，\\s　]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

@Composable
private fun RulesDialog(container: AppContainer, onDismiss: () -> Unit) {
    val settings = container.settingsStore
    // 進場時取「自訂 + 內建」合併後的有效規則；
    // 順序與原本一致（自訂分類在前、內建在後），之後只在使用者操作時改動。
    var rules by remember { mutableStateOf(settings.effectiveRules()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var newWord by remember { mutableStateOf("") }

    AlertDialog(
        containerColor = MikuColors.Surface,
        title = { Text("分類規則表", color = MikuColors.Text, fontSize = 18.sp) },
        text = {
            Column {
                val category = selectedCategory
                if (category == null) {
                    Text(
                        "選擇要編輯的分類。通知文字包含任一詞條即歸入該分類。",
                        color = MikuColors.TextSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.height(280.dp).verticalScroll(rememberScrollState())) {
                        rules.forEach { (name, words) ->
                            RuleCategoryRow(
                                name = name,
                                words = words,
                                onClick = {
                                    selectedCategory = name
                                    newWord = ""
                                },
                            )
                        }
                    }
                } else {
                    val words = rules[category].orEmpty()

                    Text(
                        "編輯「$category」的詞條：點字條右側的 ✕ 可單獨刪除，或在下方輸入後按「＋ 新增」。",
                        color = MikuColors.TextSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 字條區：每個詞條一張可辨識的字條（chip），各自可刪除
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp, max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (words.isEmpty()) {
                            Text(
                                "目前沒有詞條；儲存後這個分類會沿用內建規則。",
                                color = MikuColors.TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        } else {
                            RuleWordChips(
                                words = words,
                                onDeleteAt = { index ->
                                    // 只改記憶體中的規則，按「儲存」才會寫回設定
                                    rules = rules + (category to words.filterIndexed { i, _ -> i != index })
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "目前 ${words.size} 個詞條",
                        color = MikuColors.TextSecondary,
                        fontSize = 11.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // 輸入框 +「＋ 新增」逐條加入
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DialogTextField(
                            value = newWord,
                            onValueChange = { newWord = it },
                            label = "輸入詞條（可用逗號分隔多個）",
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            val current = rules[category].orEmpty()
                            val added = parseRuleWords(newWord)
                                .filter { word -> current.none { it.equals(word, ignoreCase = true) } }
                            if (added.isNotEmpty()) {
                                // 新詞條一律附加在尾端，既有詞條順序不變
                                rules = rules + (category to (current + added))
                            }
                            newWord = ""
                        }) { Text("＋ 新增", color = MikuColors.Primary) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        selectedCategory = null
                        newWord = ""
                    }) {
                        Text("← 回到分類列表", color = MikuColors.Primary)
                    }
                }
            }
        },
        confirmButton = {
            // 只有按「儲存」才寫回 settings.customRules；
            // 「取消」與點擊對話框外部都只走 onDismiss，不寫入。
            TextButton(onClick = {
                settings.customRules = rules
                onDismiss()
            }) { Text("儲存", color = MikuColors.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = MikuColors.TextSecondary) }
        },
        onDismissRequest = onDismiss,
    )
}

/** 規則表第一層的單一分類列：名稱 + 詞條預覽 + 右側詞條數徽章。 */
@Composable
private fun RuleCategoryRow(name: String, words: List<String>, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Category,
            contentDescription = null,
            tint = MikuColors.Primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = MikuColors.Text, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (words.isEmpty()) {
                    "尚無詞條（沿用內建規則）"
                } else {
                    words.take(3).joinToString("、") + if (words.size > 3) "…" else ""
                },
                color = MikuColors.TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 詞條數徽章：比純文字更容易對齊，也能一眼看出數量
        Box(
            modifier = Modifier
                .background(MikuColors.Primary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = "${words.size} 個詞條",
                color = MikuColors.Primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MikuColors.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * 詞條字條區：每列 2 張字條，最後一列不足時用等寬空白補齊以維持對齊。
 *
 * 刻意不使用 FlowRow：本專案解析到的 Compose 版本（foundation-layout 1.9.1）
 * 中，含 `overflow` 參數的 FlowRow 多載仍標記為 @ExperimentalLayoutApi，
 * 會逼出實驗性 API 的 opt-in（甚至有多載解析的模糊風險）。改用
 * `chunked` + Row 的版面完全等價、沒有實驗性 API，也不會有編譯風險。
 */
@Composable
private fun RuleWordChips(words: List<String>, onDeleteAt: (Int) -> Unit) {
    val columns = 2
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        words.chunked(columns).forEachIndexed { rowIndex, rowWords ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowWords.forEachIndexed { columnIndex, word ->
                    RuleWordChip(
                        word = word,
                        onDelete = { onDeleteAt(rowIndex * columns + columnIndex) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowWords.size < columns) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 單一詞條字條（chip）：文字過長以省略號呈現，右側 ✕ 可單獨刪除。 */
@Composable
private fun RuleWordChip(word: String, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(MikuColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = word,
            color = MikuColors.Text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(2.dp))
        // 刪除鈕：40dp 觸控範圍，好按又不容易誤觸
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickableNoRipple(onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = MikuColors.Expense, fontSize = 13.sp)
        }
    }
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
    val logs = remember(refreshToken) { PaymentAccessibilityService.readLogs(context) }

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
                        "同一筆消費若同時收到付款通知，兩個來源只會記一次；" +
                        "記完會直接在支付介面浮出可拖動圓球，點一下就能補分類與備註" +
                        "（不理它也不會漏記）。圓球可以在「我的」頁用「自動記帳浮球」開關關掉，" +
                        "關掉後仍然會照常記錄。\n" +
                        "記錄日期優先採用畫面顯示的交易時間（畫面沒寫才用讀到的當下時間），" +
                        "因此同一筆交易重複讀到也只會落在同一天。\n" +
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

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notificationGranted = PaymentNotificationListenerService.isNotificationAccessGranted(context)
    val accessibilityEnabled = PaymentAccessibilityService.isEnabled(context)

    AlertDialog(
        containerColor = MikuColors.Surface,
        title = { Text("關於", color = MikuColors.Text, fontSize = 18.sp) },
        text = {
            Column {
                Text("Miku 記帳（Android 原生版）", color = MikuColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "由 Flutter 版改寫為 Kotlin + Jetpack Compose，" +
                        "功能包含記帳、預算、分析、通知自動記帳與廣告過濾。",
                    color = MikuColors.TextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "通知使用權限：" + if (notificationGranted) "✅ 已授予" else "❌ 未授予",
                    color = MikuColors.TextSecondary,
                    fontSize = 12.sp,
                )
                Text(
                    "無障礙服務：" + if (accessibilityEnabled) "✅ 已啟用" else "❌ 未啟用",
                    color = MikuColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉", color = MikuColors.Primary) }
        },
        onDismissRequest = onDismiss,
    )
}

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
