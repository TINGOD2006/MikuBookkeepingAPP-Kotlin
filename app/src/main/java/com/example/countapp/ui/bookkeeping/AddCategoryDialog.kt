package com.example.countapp.ui.bookkeeping

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.domain.CategoryCatalog
import com.example.countapp.domain.CategoryItem
import com.example.countapp.ui.CategoryIcons
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors
import com.example.countapp.ui.theme.toComposeColor

/** 新增與編輯共用選擇器；名稱衝突時保留輸入並顯示錯誤。 */
@Composable
internal fun AddCategoryDialog(
    type: String,
    initial: CategoryItem? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, Long) -> Boolean,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var iconKey by remember { mutableStateOf(initial?.iconKey ?: CategoryCatalog.customIconKeys.first()) }
    var colorArgb by remember { mutableStateOf(initial?.colorArgb ?: CategoryCatalog.customColorArgb.first()) }
    var error by remember { mutableStateOf(false) }
    val icons = remember { (listOfNotNull(initial?.iconKey) + CategoryCatalog.TYPES.flatMap { CategoryCatalog.defaultsFor(it) }.map { it.iconKey } + CategoryCatalog.customIconKeys).distinct() }
    val colors = remember { (listOfNotNull(initial?.colorArgb) + CategoryCatalog.customColorArgb).distinct() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val dismiss = { keyboard?.hide(); focus.clearFocus(); onDismiss() }

    Dialog(onDismissRequest = dismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false,
    )) {
        // 明確扣除 IME 高度：Samsung 的浮動對話框可能在語意樹仍有按鈕，但被鍵盤遮住。
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.fillMaxWidth().heightIn(max = maxHeight), color = MikuColors.Surface, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text(if (initial == null) "新增$type 類別" else "編輯$type 類別", color = MikuColors.Text, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; error = false },
                            singleLine = true,
                            isError = error,
                            supportingText = if (error) ({ Text("名稱已存在或仍有歷史預算，請使用其他名稱") }) else null,
                            label = { Text("分類名稱", color = MikuColors.TextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MikuColors.Text,
                                unfocusedTextColor = MikuColors.Text,
                            ),
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("圖示", color = MikuColors.TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            modifier = Modifier.height(120.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(icons) { key ->
                                val selected = key == iconKey
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (selected) MikuColors.Primary else MikuColors.SurfaceVariant,
                                            CircleShape,
                                        )
                                        .clickableNoRipple { iconKey = key },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = CategoryIcons.forKey(key),
                                        contentDescription = key,
                                        tint = MikuColors.Text,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("顏色", color = MikuColors.TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            modifier = Modifier.height(80.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(colors) { argb ->
                                val selected = argb == colorArgb
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(argb.toComposeColor(), CircleShape)
                                        .clickableNoRipple { colorArgb = argb },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) {
                                        Text("✓", color = MikuColors.Text, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = dismiss) { Text("取消", color = MikuColors.TextSecondary) }
                        TextButton(
                            onClick = {
                                if (onSave(name.trim(), iconKey, colorArgb)) dismiss() else error = true
                            },
                            enabled = name.isNotBlank(),
                        ) { Text(if (initial == null) "新增" else "儲存", color = MikuColors.Primary) }
                    }
                }
            }
        }
    }
}
