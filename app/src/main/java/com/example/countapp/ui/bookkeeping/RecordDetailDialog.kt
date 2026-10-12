package com.example.countapp.ui.bookkeeping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.data.Record
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.ui.theme.MikuColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 唯讀詳情置中顯示；交易身分不隨使用者修改日期或金額而改變。 */
@Composable
internal fun RecordDetailDialog(record: Record, onDismiss: () -> Unit) {
    val format = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    fun time(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(format)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("記錄詳情") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text((if (record.isExpense) "−" else "+") + AmountFormatter.format(record.absoluteAmount),
                    color = if (record.isExpense) MikuColors.Expense else MikuColors.Income,
                    fontSize = 26.sp, fontWeight = FontWeight.Bold)
                DetailField("類別", record.category)
                DetailField("備註", record.note.ifBlank { "未填寫" })
                DetailField("記錄時間", time(record.dateMillis))
                DetailField("建立時間", time(record.createdAtMillis))
                DetailField("記帳來源", record.sourceLabel)
                DetailField(if (record.sourceLabel == "MPay") "MPay 帳單編號" else "帳單編號",
                    record.transactionId ?: "未提供")
                record.transactionTimeMillis?.let { DetailField("原交易時間", time(it)) }
                Text("左滑記錄後按「編輯」可修改內容", color = MikuColors.TextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
    )
}

@Composable
private fun DetailField(label: String, value: String) {
    Column {
        Text(label, color = MikuColors.TextSecondary, fontSize = 12.sp)
        Text(value, color = MikuColors.Text, fontSize = 15.sp)
    }
}
