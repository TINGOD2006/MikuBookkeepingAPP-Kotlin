package com.example.countapp.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.countapp.AppContainer
import com.example.countapp.domain.CategoryCatalog
import com.example.countapp.domain.ClassificationRules
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors

/** 分類清單來自完整分類庫，規則只決定詞條；草稿儲存前不影響自動記帳。 */
@Composable
internal fun RulesDialog(container: AppContainer, onDismiss: () -> Unit) {
    var overrides by remember { mutableStateOf(container.settingsStore.customRules) }
    val effective = remember(overrides) { ClassificationRules.mergeRules(overrides) }
    val categoryRevision by container.categoryStore.revision.collectAsState()
    val expenses = remember(categoryRevision) { container.categoryStore.categoriesFor(CategoryCatalog.TYPE_EXPENSE).map { it.name } }
    val incomes = remember(categoryRevision) { container.categoryStore.categoriesFor(CategoryCatalog.TYPE_INCOME).map { it.name } }
    val allNames = remember(expenses, incomes) { (expenses + incomes).distinct() }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("全部") }
    var search by remember { mutableStateOf("") }
    var newWord by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val goBack = {
        keyboard?.hide()
        focus.clearFocus()
        if (selectedCategory != null) { selectedCategory = null; newWord = "" } else onDismiss()
    }
    val addWords = {
        selectedCategory?.let { name ->
            val current = effective[name].orEmpty()
            val added = parseRuleWords(newWord).filter { word -> current.none { it.equals(word, ignoreCase = true) } }
            if (added.isNotEmpty()) overrides = overrides + (name to (current + added))
        }
        newWord = ""
    }
    Dialog(onDismissRequest = goBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = goBack)
        Column(Modifier.fillMaxSize().background(MikuColors.Background).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MikuColors.Text) }
                Text(selectedCategory ?: "分類規則表", color = MikuColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = {
                    // 尚未按「新增」的輸入也一併儲存，不丟掉最後一條詞。
                    val name = selectedCategory
                    val current = name?.let { effective[it].orEmpty() }.orEmpty()
                    val added = parseRuleWords(newWord).filter { word -> current.none { it.equals(word, ignoreCase = true) } }
                    val saved = if (name != null && added.isNotEmpty()) overrides + (name to (current + added)) else overrides
                    container.settingsStore.customRules = saved
                    keyboard?.hide()
                    onDismiss()
                }) { Text("儲存") }
            }
            val category = selectedCategory
            if (category == null) {
                Text("所有支出、收入及自訂分類都能設定；同名分類共用詞條。", color = MikuColors.TextSecondary, fontSize = 12.sp)
                OutlinedTextField(search, { search = it }, label = { Text("搜尋分類") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }), modifier = Modifier.fillMaxWidth())
                Row {
                    listOf("全部", "支出", "收入").forEach { type ->
                        TextButton(onClick = { filter = type }, modifier = Modifier.weight(1f)) {
                            Text(type, color = if (filter == type) MikuColors.Primary else MikuColors.TextSecondary)
                        }
                    }
                }
                val names = allNames.filter { name -> name.contains(search.trim(), ignoreCase = true) &&
                    (filter == "全部" || (filter == "支出" && name in expenses) || (filter == "收入" && name in incomes)) }
                Text("${names.size} 個分類", color = MikuColors.TextSecondary, fontSize = 12.sp)
                LazyColumn(Modifier.weight(1f)) {
                    items(names, key = { it }) { name ->
                        Row(Modifier.fillMaxWidth().clickableNoRipple { selectedCategory = name; newWord = ""; keyboard?.hide(); focus.clearFocus() }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(name, color = MikuColors.Text, fontSize = 16.sp)
                                Text(effective[name].orEmpty().take(3).joinToString("、").ifEmpty { "尚無詞條" }, color = MikuColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${effective[name].orEmpty().size} 個詞條", color = MikuColors.Primary, fontSize = 12.sp)
                        }
                        HorizontalDivider(color = MikuColors.SurfaceVariant)
                    }
                }
            } else {
                val words = effective[category].orEmpty()
                Text("交易文字包含詞條時歸入此分類。清空後儲存可停用此分類；按恢復預設才會重新啟用內建詞條。", color = MikuColors.TextSecondary, fontSize = 12.sp)
                Row {
                    TextButton(onClick = { overrides = overrides + (category to emptyList()) }) { Text("清空詞條") }
                    TextButton(onClick = { overrides = overrides - category }) { Text("恢復本分類預設") }
                }
                Text("目前 ${words.size} 個詞條", color = MikuColors.TextSecondary, fontSize = 12.sp)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (words.isEmpty()) Text("目前沒有詞條", color = MikuColors.TextSecondary, modifier = Modifier.padding(vertical = 16.dp))
                    words.chunked(2).forEachIndexed { rowIndex, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEachIndexed { index, word ->
                                Row(Modifier.weight(1f).background(MikuColors.Surface, RoundedCornerShape(12.dp)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(word, color = MikuColors.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    IconButton(onClick = { overrides = overrides + (category to words.filterIndexed { i, _ -> i != rowIndex * 2 + index }) }) {
                                        Icon(Icons.Filled.Close, "刪除詞條：$word", tint = MikuColors.Expense, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newWord, { newWord = it }, label = { Text("輸入詞條（可用逗號分隔）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addWords(); keyboard?.hide(); focus.clearFocus() }), modifier = Modifier.weight(1f))
                    TextButton(onClick = addWords) { Text("新增") }
                }
            }
            TextButton(onClick = { keyboard?.hide(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("取消，不儲存") }
        }
    }
}

private fun parseRuleWords(raw: String): List<String> =
    raw.split(Regex("[,，\\s　]+")).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
