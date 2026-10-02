package com.example.countapp.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.countapp.data.InstalledApp
import com.example.countapp.data.InstalledAppRepository
import com.example.countapp.data.SettingsStore
import com.example.countapp.ui.theme.MikuColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 編輯中的勾選只保留在此視窗，按儲存才同步通知來源與無障礙來源。 */
@Composable
internal fun AutoRecordAppsDialog(settings: SettingsStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val repository = remember(context) { InstalledAppRepository(context) }
    val initialSelection = remember { settings.allowedPackages.toSet() }
    var selected by remember { mutableStateOf(initialSelection) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        error = false
        try {
            apps = withContext(Dispatchers.IO) { repository.load() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = true
        } finally {
            loading = false
        }
    }
    val choices = remember(apps, initialSelection) {
        val installed = apps.map { it.packageName }.toSet()
        val defaults = mapOf(
            "com.tencent.mm" to "微信",
            "com.eg.android.AlipayGphone" to "支付寶",
            "com.macaupass.rechargeEasy" to "MPay",
        )
        val missing = (initialSelection + SettingsStore.DEFAULT_ALLOWED_PACKAGES).filterNot { it in installed }
            .map { InstalledApp(it, defaults[it] ?: it, installed = false) }
        // 勾選期間不重排清單，避免用戶下一次點擊落到另一個 App。
        (apps + missing).sortedBy { it.packageName !in initialSelection }
    }
    val visible = remember(choices, query, showSystem, selected) {
        val search = query.trim()
        choices.filter { app ->
            (showSystem || !app.isSystem || app.packageName in selected) &&
                (app.name.contains(search, ignoreCase = true) || app.packageName.contains(search, ignoreCase = true))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(0.95f),
                color = MikuColors.Surface,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("自動記錄應用", color = MikuColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("勾選要自動記錄的 App，通知和讀屏都依此清單篩選。", color = MikuColors.TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        label = { Text("搜尋 App 名稱或包名") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.weight(1f).toggleable(showSystem, role = Role.Checkbox, onValueChange = { showSystem = it }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = showSystem, onCheckedChange = null)
                            Text("顯示系統 App", fontSize = 12.sp)
                        }
                        Text("已選 ${selected.size} 個", color = MikuColors.Primary, fontSize = 12.sp)
                        TextButton(onClick = { refresh++ }, enabled = !loading) { Text("重新掃描") }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                            error -> Text("無法取得應用清單，請重新掃描。", modifier = Modifier.align(Alignment.Center))
                            visible.isEmpty() -> Text("沒有符合的應用", modifier = Modifier.align(Alignment.Center))
                            else -> LazyColumn(Modifier.fillMaxSize()) {
                                items(visible, key = { it.packageName }) { app ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().toggleable(
                                            value = app.packageName in selected,
                                            role = Role.Checkbox,
                                            onValueChange = { checked ->
                                                selected = if (checked) selected + app.packageName else selected - app.packageName
                                            },
                                        ).padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(checked = app.packageName in selected, onCheckedChange = null)
                                        Column(Modifier.weight(1f)) {
                                            Text(app.name, color = MikuColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            Text(app.packageName, color = MikuColors.TextSecondary, fontSize = 11.sp)
                                            if (!app.installed || app.isSystem) {
                                                Text(if (app.installed) "系統 App" else "目前未安裝", color = MikuColors.TextSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = MikuColors.SurfaceVariant)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selected = SettingsStore.DEFAULT_ALLOWED_PACKAGES.toSet() }) { Text("恢復預設") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("取消") }
                        TextButton(onClick = { settings.allowedPackages = selected.toList(); onDismiss() }, enabled = !loading && !error) { Text("儲存") }
                    }
                }
            }
        }
    }
}
