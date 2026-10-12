package com.example.countapp.ui.bookkeeping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors
/** 共用四欄鍵盤；清空、0、小數點各佔一格，保存跨最後兩列。 */
@Composable
internal fun AmountKeypad(onSave: () -> Unit, onKey: (String) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val rows = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf("清空", "0", "."),
    )

    val height = if (compact) 32.dp else 48.dp
    Row(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.weight(3f)) {
            rows.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        KeyButton(key, Modifier.weight(1f), height, onKey)
                    }
                }
                if (index < rows.lastIndex) Spacer(Modifier.height(4.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            KeyButton("⌫", Modifier.fillMaxWidth(), height * 2 + 4.dp, onKey)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 3.dp)
                    .height(height * 2 + 4.dp)
                    .background(MikuColors.Primary, RoundedCornerShape(6.dp))
                    .clickableNoRipple(onSave),
                contentAlignment = Alignment.Center,
            ) { Text("保存", color = MikuColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun KeyButton(
    key: String,
    modifier: Modifier,
    height: androidx.compose.ui.unit.Dp,
    onKey: (String) -> Unit,
) {
    val isSpecial = key == "清空" || key == "⌫"

    Box(
        modifier = modifier
            .padding(horizontal = 3.dp)
            .height(height)
            .background(
                if (isSpecial) MikuColors.SurfaceVariant else MikuColors.Background,
                RoundedCornerShape(6.dp),
            )
            .clickableNoRipple { onKey(key) },
        contentAlignment = Alignment.Center,
    ) {
        if (key == "清空") Icon(Icons.Filled.Delete, contentDescription = "清空金額",
            tint = MikuColors.Secondary, modifier = Modifier.size(20.dp)) else Text(
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
internal fun applyAmountKey(current: String, key: String): String {
    val input = current.ifBlank { "0" }
    return when (key) {
    "清空" -> "0"
    "⌫" -> if (input.length > 1) input.dropLast(1) else "0"
    "." -> if (input.contains('.')) input else "$input."
    else -> if (key.length == 1 && key[0] in '0'..'9') appendDigit(input, key) else input
    }
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
