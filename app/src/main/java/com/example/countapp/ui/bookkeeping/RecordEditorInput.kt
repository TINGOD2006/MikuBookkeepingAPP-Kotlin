package com.example.countapp.ui.bookkeeping

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.domain.CategoryItem
import com.example.countapp.ui.CategoryIcons
import com.example.countapp.ui.MinTouchTarget
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors
import java.time.LocalDate

@Composable
internal fun RecordEditorInput(
    category: CategoryItem?,
    categoryName: String,
    showNumberPad: Boolean,
    compactNumberPad: Boolean,
    amountInput: String,
    note: String,
    date: LocalDate,
    isEditing: Boolean,
    onKey: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onPickDate: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
) {
    // 備註使用系統鍵盤，數字鍵盤僅在系統鍵盤收起後顯示。
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MikuColors.Surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CategoryIcons.forKey(category?.iconKey ?: "label"),
                contentDescription = null,
                tint = MikuColors.Primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                categoryName, color = MikuColors.Text, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = AmountFormatter.formatInput(amountInput),
                color = MikuColors.Text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Note,
                contentDescription = null,
                tint = MikuColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                // 備註只是輔助欄位：按「完成」就收鍵盤，讓自製數字鍵盤回到可見範圍
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }),
                placeholder = { Text("備註（選填）", color = MikuColors.TextSecondary, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MikuColors.Text,
                    unfocusedTextColor = MikuColors.Text,
                ),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                modifier = Modifier
                    .height(MinTouchTarget)
                    .background(MikuColors.SurfaceVariant, RoundedCornerShape(6.dp))
                    .clickableNoRipple(onPickDate)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = "選擇日期",
                    tint = MikuColors.Text,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${date.monthValue}/${date.dayOfMonth}",
                    color = MikuColors.Text,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 編寫備註時由系統鍵盤接替數字鍵盤，避免兩個鍵盤擠掉分類區。
        if (showNumberPad) {
            NumberPad(compactNumberPad) { key ->
                focusManager.clearFocus()
                onKey(key)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // 編輯模式才提供刪除：會進垃圾桶，不是直接消失
            if (isEditing) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(MinTouchTarget)
                        .background(MikuColors.SurfaceVariant, RoundedCornerShape(6.dp))
                        .clickableNoRipple(onDelete),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MikuColors.Expense,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("刪除", color = MikuColors.Expense, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .weight(3f)
                    .height(MinTouchTarget)
                    .background(MikuColors.Primary, RoundedCornerShape(6.dp))
                    .clickableNoRipple(onSave),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isEditing) "儲存變更" else "保存記帳",
                    color = MikuColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 自製數字鍵盤：最後一列是加寬的 0，並提供小數點與清空。 */
@Composable
private fun NumberPad(compact: Boolean, onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "清空"),
        listOf("1", "2", "3", "."),
    )

    Column {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    KeyButton(key = key, flex = 1, compact = compact, onKey = onKey)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            KeyButton(key = "0", flex = 4, compact = compact, onKey = onKey)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.KeyButton(
    key: String,
    flex: Int,
    compact: Boolean,
    onKey: (String) -> Unit,
) {
    val isSpecial = key == "清空" || key == "⌫"

    Box(
        modifier = Modifier
            .weight(flex.toFloat())
            .padding(horizontal = 3.dp)
            .height(if (compact) 32.dp else 40.dp)
            .background(
                if (isSpecial) MikuColors.SurfaceVariant else MikuColors.Background,
                RoundedCornerShape(6.dp),
            )
            .clickableNoRipple { onKey(key) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key,
            color = if (isSpecial) MikuColors.Secondary else MikuColors.Text,
            fontSize = if (isSpecial) 12.sp else 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 數字鍵盤的輸入規則（與 Flutter 版一致）：
 *   - 小數點只能有一個
 *   - 小數最多兩位
 *   - 整數最多九位
 */
internal fun applyAmountKey(current: String, key: String): String = when (key) {
    "清空" -> "0"
    "⌫" -> if (current.length > 1) current.dropLast(1) else "0"
    "." -> if (current.contains('.')) current else "$current."
    else -> appendDigit(current, key)
}

private fun appendDigit(current: String, digit: String): String {
    val dotIndex = current.indexOf('.')

    if (dotIndex >= 0) {
        val decimals = current.length - dotIndex - 1
        if (decimals >= AmountFormatter.DECIMAL_PLACES) return current
        return current + digit
    }

    if (current.length >= MAX_INTEGER_DIGITS) return current
    return if (current == "0") digit else current + digit
}

private const val MAX_INTEGER_DIGITS = 9

