package com.example.countapp.ui.bookkeeping

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.ui.theme.MikuColors

/** 顯示金額而非系統輸入框；整月／分類預算共用編輯與驗證。 */
@Composable
internal fun AmountInputDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var input by remember(title, initial) { mutableStateOf(initial.ifBlank { "0" }) }
    var error by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { keyboard?.hide(); focus.clearFocus() }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = MikuColors.Surface,
        title = { Text(title, color = MikuColors.Text) },
        text = {
            Column {
                Text(AmountFormatter.formatInput(input), color = MikuColors.Text, fontSize = 28.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                if (error) Text("請輸入大於 0 的金額", color = MikuColors.Expense)
                AmountKeypad(onKey = { input = applyAmountKey(input, it); error = false }, onSave = {
                    val amount = AmountFormatter.round(input.toDoubleOrNull() ?: 0.0)
                    if (amount > 0 && amount.isFinite()) onSave(amount) else error = true
                })
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
