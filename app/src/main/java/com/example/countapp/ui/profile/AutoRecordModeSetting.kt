package com.example.countapp.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.data.SettingsStore
import com.example.countapp.domain.AutoRecordMode
import com.example.countapp.ui.SettingRow
import com.example.countapp.ui.theme.MikuColors

private val AutoRecordMode.label: String get() = when (this) {
    AutoRecordMode.MANUAL -> "不使用（手動記錄）"
    AutoRecordMode.NOTIFICATION -> "通知讀取記錄"
    AutoRecordMode.ACCESSIBILITY -> "通知＋無障礙讀取"
}

/** 三層互斥選擇；最高層包含通知來源，開關停用後仍保留最後選擇。 */
@Composable
internal fun AutoRecordModeSetting(settings: SettingsStore) {
    val mode by settings.autoRecordModeFlow.collectAsState()
    var choosing by remember { mutableStateOf(false) }
    SettingRow(Icons.Filled.NotificationsActive, "記錄方式") {
        TextButton(onClick = { choosing = true }) {
            Text(mode.label, color = MikuColors.Primary, fontSize = 12.sp)
        }
    }
    if (choosing) AlertDialog(
        onDismissRequest = { choosing = false },
        containerColor = MikuColors.Surface,
        title = { Text("自動記錄方式", color = MikuColors.Text) },
        text = {
            Column(Modifier.selectableGroup()) {
                AutoRecordMode.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth().selectable(
                        selected = option == mode,
                        role = Role.RadioButton,
                        onClick = { settings.autoRecordMode = option },
                    ).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = option == mode, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text(option.label, color = MikuColors.Text, fontSize = 14.sp)
                            Text(when (option) {
                                AutoRecordMode.MANUAL -> "不讀取通知或付款畫面，由你自行記帳。"
                                AutoRecordMode.NOTIFICATION -> "讀取所選 App 的交易通知；不讀取付款畫面。"
                                AutoRecordMode.ACCESSIBILITY -> "保留通知記錄，另讀取付款畫面。MPay 由通知入帳，畫面僅補充資料。"
                            }, color = MikuColors.TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
                Text("MPay 付款、收款與轉帳通知無須交易編號。需授予通知使用權限，並允許 MPay 發送交易通知。",
                    color = MikuColors.TextSecondary, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text("完成") } },
    )
}
