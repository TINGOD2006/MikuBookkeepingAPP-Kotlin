package com.example.countapp.ui.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.AppContainer
import com.example.countapp.data.Record
import com.example.countapp.data.RecordRepository
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.domain.CategoryItem
import com.example.countapp.ui.CategoryIcons
import com.example.countapp.ui.ConfirmDialog
import com.example.countapp.ui.theme.MikuColors
import com.example.countapp.ui.theme.toComposeColor

/**
 * 垃圾桶。
 *
 * 使用者刪除的記錄不會立刻消失，而是移到這裡保留
 * [RecordRepository.TRASH_RETENTION_DAYS] 天：
 *   - 「還原」把記錄放回原本的日期分組
 *   - 「永久刪除」直接移除單筆
 *   - 「清空垃圾桶」一次清掉全部
 *
 * 逾期記錄在儲存庫初始化或開啟這個對話框時自動清除。
 */
@Composable
fun TrashDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val trashed by container.recordRepository.trashedRecords.collectAsState()
    val categoryStore = container.categoryStore

    var pendingDelete by remember { mutableStateOf<Record?>(null) }
    var showEmptyConfirm by remember { mutableStateOf(false) }

    // 開啟時順手清掉超過 30 天的記錄
    LaunchedEffect(Unit) {
        container.recordRepository.purgeExpiredTrash()
    }

    AlertDialog(
        containerColor = MikuColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.RestoreFromTrash,
                    contentDescription = null,
                    tint = MikuColors.Primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("垃圾桶", color = MikuColors.Text, fontSize = 18.sp)
            }
        },
        text = {
            Column {
                Text(
                    "刪除的記錄會保留 ${RecordRepository.TRASH_RETENTION_DAYS} 天，" +
                        "逾期自動清除；期間內都可以還原。",
                    color = MikuColors.TextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (trashed.isEmpty()) {
                    Text(
                        "垃圾桶是空的",
                        color = MikuColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else {
                    val now = System.currentTimeMillis()
                    Column(
                        modifier = Modifier
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        trashed.forEach { record ->
                            TrashRow(
                                record = record,
                                remainingDays = record.trashRemainingDays(now),
                                category = categoryStore.findAny(record.category),
                                onRestore = { container.recordRepository.restore(record.id) },
                                onDeleteForever = { pendingDelete = record },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉", color = MikuColors.Primary) }
        },
        dismissButton = {
            if (trashed.isNotEmpty()) {
                TextButton(onClick = { showEmptyConfirm = true }) {
                    Text("清空垃圾桶", color = MikuColors.Expense)
                }
            }
        },
        onDismissRequest = onDismiss,
    )

    pendingDelete?.let { record ->
        ConfirmDialog(
            title = "永久刪除？",
            message = "「${record.category} ${AmountFormatter.format(record.absoluteAmount)}」" +
                "將會立刻從垃圾桶移除，無法復原。",
            confirmText = "永久刪除",
            onConfirm = {
                container.recordRepository.deleteById(record.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (showEmptyConfirm) {
        ConfirmDialog(
            title = "清空垃圾桶？",
            message = "垃圾桶內的 ${trashed.size} 筆記錄將被永久刪除，無法復原。",
            confirmText = "清空",
            onConfirm = {
                container.recordRepository.emptyTrash()
                showEmptyConfirm = false
            },
            onDismiss = { showEmptyConfirm = false },
        )
    }
}

@Composable
private fun TrashRow(
    record: Record,
    remainingDays: Int,
    category: CategoryItem?,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val color = category?.colorArgb?.toComposeColor() ?: MikuColors.TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(MikuColors.Background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategoryIcons.forKey(category?.iconKey ?: "label"),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.category,
                    color = MikuColors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (record.isExpense) "-" else "+",
                    color = if (record.isExpense) MikuColors.Expense else MikuColors.Income,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = AmountFormatter.format(record.absoluteAmount),
                    color = if (record.isExpense) MikuColors.Expense else MikuColors.Income,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(Record.formatDate(record.localDate()))
                    if (record.note.isNotEmpty()) append("・").append(record.note)
                },
                color = MikuColors.TextSecondary,
                fontSize = 10.sp,
            )
            Text(
                text = "剩餘 $remainingDays 天",
                color = if (remainingDays <= 3) MikuColors.Expense else MikuColors.TextSecondary,
                fontSize = 10.sp,
            )
        }

        TextButton(onClick = onRestore) {
            Icon(
                imageVector = Icons.Filled.Restore,
                contentDescription = null,
                tint = MikuColors.Primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text("還原", color = MikuColors.Primary, fontSize = 12.sp)
        }
        TextButton(onClick = onDeleteForever) {
            Icon(
                imageVector = Icons.Filled.DeleteForever,
                contentDescription = "永久刪除",
                tint = MikuColors.Expense,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
