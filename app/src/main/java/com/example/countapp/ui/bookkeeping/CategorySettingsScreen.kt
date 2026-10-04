package com.example.countapp.ui.bookkeeping

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.countapp.AppContainer
import com.example.countapp.domain.CategoryCatalog
import com.example.countapp.domain.CategoryItem
import com.example.countapp.ui.CategoryIcons
import com.example.countapp.ui.theme.MikuColors
import com.example.countapp.ui.theme.toComposeColor
import kotlinx.coroutines.delay

/** 清單即時預覽拖曳順序，放手才存檔；取消拖曳還原，避免寫入半途狀態。 */
@Composable
internal fun CategorySettingsScreen(
    container: AppContainer,
    initialType: String,
    onDismiss: () -> Unit,
    onRenamed: (String, String, String) -> Unit,
    onDeleted: (String, String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false,
    )) {
        var type by remember { mutableStateOf(initialType) }
        val revision by container.categoryStore.revision.collectAsState()
        var categories by remember(type, revision) { mutableStateOf(container.categoryStore.categoriesFor(type)) }
        val list = rememberLazyListState()
        var editItem by remember { mutableStateOf<CategoryItem?>(null) }
        var adding by remember { mutableStateOf(false) }
        var deleting by remember { mutableStateOf<CategoryItem?>(null) }
        var draggedName by remember { mutableStateOf<String?>(null) }
        var dragTop by remember { mutableFloatStateOf(0f) }
        var dragHeight by remember { mutableIntStateOf(0) }
        val finishDrag = {
            if (draggedName != null) container.categoryStore.reorder(type, categories.map { it.name })
            draggedName = null
        }
        val dismiss = { finishDrag(); onDismiss() }
        BackHandler(onBack = dismiss)

        fun moveTo(index: Int) {
            val from = categories.indexOfFirst { it.name == draggedName }
            if (from < 0 || from == index || index !in categories.indices) return
            // 保持原本的捲動位置，避免第一個可見項目移位時 LazyColumn 跟著跳動。
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
            categories = categories.toMutableList().apply { add(index, removeAt(from)) }
        }
        fun matchDragPosition() {
            val center = dragTop + dragHeight / 2f
            val target = list.layoutInfo.visibleItemsInfo.firstOrNull {
                it.key != draggedName && center >= it.offset && center < it.offset + it.size
            }
            if (target != null) moveTo(target.index)
        }
        // 手指停在上下邊緣仍持續捲動，支援跨越整份清單的排序。
        LaunchedEffect(draggedName) {
            if (draggedName == null) return@LaunchedEffect
            while (draggedName != null) {
                val info = list.layoutInfo
                val edge = dragHeight.toFloat().coerceAtLeast(1f)
                val speed = when {
                    dragTop < info.viewportStartOffset + edge -> -18f
                    dragTop + dragHeight > info.viewportEndOffset - edge -> 18f
                    else -> 0f
                }
                if (speed != 0f) { list.scrollBy(speed); matchDragPosition() }
                delay(16)
            }
        }
        val dragMove by rememberUpdatedState<(Float) -> Unit>({ dy -> dragTop += dy; matchDragPosition() })
        val dragEnd by rememberUpdatedState(finishDrag)
        val dragCancel by rememberUpdatedState<() -> Unit>({
            draggedName = null
            categories = container.categoryStore.categoriesFor(type)
        })
        val dragStart by rememberUpdatedState<(String) -> Unit>({ name ->
            list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == name }?.let {
                draggedName = name; dragTop = it.offset.toFloat(); dragHeight = it.size
            }
        })

        Column(Modifier.fillMaxSize().background(MikuColors.Background).statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                IconButton(onClick = dismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回記帳", tint = MikuColors.Text)
                }
                Text("類別設定", Modifier.align(Alignment.Center), color = MikuColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryCatalog.TYPES.forEach { option ->
                    val selected = option == type
                    FilledTonalButton(
                        onClick = { type = option }, enabled = draggedName == null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (selected) MikuColors.Primary else MikuColors.SurfaceVariant,
                            contentColor = MikuColors.Text,
                        ),
                    ) { Text(option, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            LaunchedEffect(type) { list.scrollToItem(0) }
            Text("拖動右側排序圖示調整順序", color = MikuColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(16.dp, 8.dp))
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(categories, key = { it.name }) { item ->
                    val dragged = item.name == draggedName
                    Row(
                        Modifier.fillMaxWidth().zIndex(if (dragged) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragged) dragTop - (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.name }?.offset ?: dragTop.toInt()) else 0f
                            }
                            .background(if (dragged) MikuColors.SurfaceVariant else MikuColors.Surface)
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(CategoryIcons.forKey(item.iconKey), null, tint = item.colorArgb.toComposeColor(), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(item.name, color = MikuColors.Text, modifier = Modifier.weight(1f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { editItem = item }, enabled = draggedName == null) {
                            Icon(Icons.Filled.Edit, "編輯${item.name}", tint = MikuColors.TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { deleting = item }, enabled = draggedName == null) {
                            Icon(Icons.Filled.DeleteOutline, "刪除${item.name}", tint = MikuColors.Expense, modifier = Modifier.size(20.dp))
                        }
                        Box(
                            Modifier.size(48.dp).semantics {
                                contentDescription = "排序${item.name}"
                                customActions = listOf(-1 to "向上移動", 1 to "向下移動").map { (delta, label) ->
                                    CustomAccessibilityAction(label) {
                                        val from = categories.indexOfFirst { it.name == item.name }
                                        val to = from + delta
                                        if (from >= 0 && to in categories.indices) {
                                            val moved = categories.toMutableList().apply { add(to, removeAt(from)) }
                                            container.categoryStore.reorder(type, moved.map { it.name })
                                        } else false
                                    }
                                }
                            }.pointerInput(type, item.name) {
                                detectDragGestures(
                                    onDragStart = { dragStart(item.name) },
                                    onDragEnd = { dragEnd() }, onDragCancel = { dragCancel() },
                                    onDrag = { change, amount -> change.consume(); dragMove(amount.y) },
                                )
                            }, contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.DragHandle, null, tint = MikuColors.TextSecondary) }
                    }
                    HorizontalDivider(color = MikuColors.SurfaceVariant)
                }
                if (categories.isEmpty()) item { Text("尚未建立類別，按下方按鈕新增", color = MikuColors.TextSecondary, modifier = Modifier.padding(24.dp)) }
            }
            Button(onClick = { adding = true }, enabled = draggedName == null, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MikuColors.Primary, contentColor = MikuColors.Text)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("新增類別")
            }
        }
        if (adding || editItem != null) {
            val original = editItem
            AddCategoryDialog(type = type, initial = original, onDismiss = { adding = false; editItem = null }, onSave = { name, icon, color ->
                if (original == null) container.categoryStore.addCustomCategory(type, name, icon, color)
                else container.categoryManager.edit(type, original.name, name, icon, color).also { saved ->
                    if (saved) onRenamed(type, original.name, name)
                }
            })
        }
        deleting?.let { item ->
            AlertDialog(onDismissRequest = { deleting = null }, containerColor = MikuColors.Surface,
                title = { Text("刪除「${item.name}」？", color = MikuColors.Text) },
                text = { Text("此類別將從選擇清單移除，歷史帳目與預算會保留。", color = MikuColors.TextSecondary) },
                confirmButton = { TextButton(onClick = {
                    if (container.categoryStore.deleteCategory(type, item.name)) onDeleted(type, item.name)
                    deleting = null
                }) { Text("刪除", color = MikuColors.Expense) } },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
            )
        }
    }
}
