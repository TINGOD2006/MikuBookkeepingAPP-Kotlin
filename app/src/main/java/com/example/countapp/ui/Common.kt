package com.example.countapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.ui.theme.MikuColors
import java.time.YearMonth

/**
 * Material 建議的最小觸控邊長（48dp）。
 *
 * 圖示本身通常只有 24dp，若直接把 clickable 掛在圖示上，可點擊範圍就只有
 * 24dp（實機上很難按到）。所有純圖示按鈕都應該用這個尺寸當外框。
 */
val MinTouchTarget: Dp = 48.dp

/** 無漣漪的點擊（沿用 Flutter 版的簡潔手感）。 */
@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

/** 深色圓角容器，用於設定卡片、輸入區。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MikuColors.Surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) { content() }
}

/** 設定列：左圖示 + 標籤 + 右側控制項。 */
@Composable
fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MikuColors.Primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, color = MikuColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** 簡單的確認對話框。 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "確定",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        containerColor = MikuColors.Surface,
        title = { Text(title, color = MikuColors.Text, fontSize = 18.sp) },
        text = { Text(message, color = MikuColors.TextSecondary, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MikuColors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MikuColors.TextSecondary)
            }
        },
        onDismissRequest = onDismiss,
    )
}

/**
 * 年月選擇器。
 *
 * 對應 Flutter 版的 `month_picker_dialog.dart`：
 * 上方用箭頭切換年份，下方是 12 個月份按鈕。
 */
@Composable
fun MonthPickerDialog(
    initial: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
) {
    var year by remember { mutableIntStateOf(initial.year) }
    val selectedMonth = initial.monthValue

    AlertDialog(
        containerColor = MikuColors.Surface,
        title = { Text("選擇月份", color = MikuColors.Text, fontSize = 18.sp) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(MinTouchTarget)
                            .clickableNoRipple { year -= 1 },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "上一年",
                            tint = MikuColors.Primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        text = "$year 年",
                        color = MikuColors.Text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Box(
                        modifier = Modifier
                            .size(MinTouchTarget)
                            .clickableNoRipple { year += 1 },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "下一年",
                            tint = MikuColors.Primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 12 個月，每列 4 個
                for (row in 0 until 3) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (col in 0 until 4) {
                            val month = row * 4 + col + 1
                            val isSelected = month == selectedMonth
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(3.dp)
                                    .height(40.dp)
                                    .background(
                                        if (isSelected) MikuColors.Primary else MikuColors.SurfaceVariant,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickableNoRipple { onConfirm(YearMonth.of(year, month)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$month 月",
                                    color = if (isSelected) MikuColors.Text else MikuColors.TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MikuColors.TextSecondary)
            }
        },
        onDismissRequest = onDismiss,
    )
}

/** 空狀態提示。 */
@Composable
fun EmptyState(message: String, hint: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, color = MikuColors.Text, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = hint, color = MikuColors.TextSecondary, fontSize = 13.sp)
    }
}

/** 卡片內容用的次要文字色。 */
val CardSecondaryText: Color = MikuColors.OnCardSecondary
