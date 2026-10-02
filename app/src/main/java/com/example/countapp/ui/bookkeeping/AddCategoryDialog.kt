package com.example.countapp.ui.bookkeeping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.domain.CategoryCatalog
import com.example.countapp.ui.CategoryIcons
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors
import com.example.countapp.ui.theme.toComposeColor

/** 新增自訂分類的對話框。 */
@Composable
internal fun AddCategoryDialog(
    type: String,
    onDismiss: () -> Unit,
    onCreate: (String, String, Long) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf(CategoryCatalog.customIconKeys.first()) }
    var colorArgb by remember { mutableStateOf(CategoryCatalog.customColorArgb.first()) }

    AlertDialog(
        containerColor = MikuColors.Surface,
        title = { Text("新增$type 分類", color = MikuColors.Text, fontSize = 18.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
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
                    items(CategoryCatalog.customIconKeys) { key ->
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
                    items(CategoryCatalog.customColorArgb) { argb ->
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
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), iconKey, colorArgb) },
                enabled = name.isNotBlank(),
            ) { Text("新增", color = MikuColors.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MikuColors.TextSecondary)
            }
        },
        onDismissRequest = onDismiss,
    )
}
