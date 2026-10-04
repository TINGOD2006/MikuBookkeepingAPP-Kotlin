package com.example.countapp.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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

/** 系統設定式列表，開關即時保存，返回不撤銷已切換的來源。 */
@Composable
internal fun AutoRecordAppsScreen(settings: SettingsStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val repository = remember(context) { InstalledAppRepository(context) }
    val allowed by settings.allowedPackagesFlow.collectAsState()
    val selected = allowed.toSet()
    val initialSelection = remember { selected }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    val close = { keyboard?.hide(); focus.clearFocus(); onDismiss() }

    LaunchedEffect(refresh) {
        loading = true
        error = false
        try { apps = withContext(Dispatchers.IO) { repository.load() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    val choices = remember(apps, initialSelection) {
        val installed = apps.map { it.packageName }.toSet()
        val names = mapOf("com.tencent.mm" to "微信", "com.eg.android.AlipayGphone" to "支付寶", "com.macaupass.rechargeEasy" to "MPay")
        val missing = (initialSelection + SettingsStore.DEFAULT_ALLOWED_PACKAGES).filterNot { it in installed }
            .map { InstalledApp(it, names[it] ?: it, installed = false) }
        // 不隨開關即時重新排序，下一次點擊不會落到不同 App。
        (apps + missing).sortedBy { it.packageName !in initialSelection }
    }
    val visible = choices.filter { app ->
        (showSystem || !app.isSystem || app.packageName in selected) &&
            (app.name.contains(query.trim(), true) || app.packageName.contains(query.trim(), true))
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MikuColors.Background).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MikuColors.Text) }
                Text("自動記錄應用", color = MikuColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { refresh++ }, enabled = !loading) { Icon(Icons.Filled.Refresh, "重新掃描", tint = MikuColors.Primary) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "更多選項", tint = MikuColors.Text) }
                    DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(if (showSystem) "隱藏系統 App" else "顯示系統 App") }, onClick = { showSystem = !showSystem; menuOpen = false })
                        DropdownMenuItem(text = { Text("恢復三個預設 App") }, onClick = { settings.allowedPackages = SettingsStore.DEFAULT_ALLOWED_PACKAGES; menuOpen = false })
                    }
                }
            }
            Text("切換即時保存 · 已選 ${selected.size} 個 · 已安裝 ${apps.size} 個", color = MikuColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            OutlinedTextField(query, { query = it }, label = { Text("搜尋 App 名稱或包名") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            Surface(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), shape = RoundedCornerShape(24.dp), color = MikuColors.Surface) {
                Box(Modifier.fillMaxSize()) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        error -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("無法取得應用清單", color = MikuColors.TextSecondary)
                            TextButton(onClick = { refresh++ }) { Text("重新掃描") }
                        }
                        visible.isEmpty() -> Text("沒有符合的應用", color = MikuColors.TextSecondary, modifier = Modifier.align(Alignment.Center))
                        else -> LazyColumn(Modifier.fillMaxSize()) {
                            items(visible, key = { it.packageName }) { app ->
                                AppSwitchRow(app, repository, app.packageName in selected) { checked ->
                                    settings.allowedPackages = (if (checked) selected + app.packageName else selected - app.packageName).toList()
                                }
                                HorizontalDivider(color = MikuColors.SurfaceVariant, modifier = Modifier.padding(start = 84.dp, end = 20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSwitchRow(app: InstalledApp, repository: InstalledAppRepository, checked: Boolean, onChange: (Boolean) -> Unit) {
    val icon by produceState<ImageBitmap?>(null, app.packageName, app.installed) {
        if (app.installed) value = withContext(Dispatchers.IO) { repository.loadIcon(app.packageName)?.asImageBitmap() }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 96.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            val bitmap = icon
            if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.Apps, null, tint = MikuColors.Primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, color = MikuColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, color = MikuColors.TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!app.installed || app.isSystem) Text(if (app.installed) "系統 App" else "目前未安裝", color = MikuColors.TextSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = MikuColors.Primary))
    }
}
