package com.example.countapp.ui.bookkeeping

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.AppContainer
import com.example.countapp.data.Record
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.domain.CategoryCatalog
import com.example.countapp.ui.ConfirmDialog
import com.example.countapp.ui.MinTouchTarget
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 記帳／編輯記錄畫面。
 *
 * 對應 Flutter 版的 `add_record_dialog.dart`，並補上原生版的編輯功能：
 *   - [editing] 為 null：新增記錄（標題「記帳」）
 *   - [editing] 有值：編輯該筆記錄，可改收支類型、分類、金額、備註與日期
 *   - 自動記錄的確認頁也是用這個畫面（帶入 [title]／[hint] 覆寫標題與提示）
 *
 * 兩種模式共用同一套分類九宮格與自製數字鍵盤，儲存時只差在
 * [com.example.countapp.data.RecordRepository.add] 與
 * [com.example.countapp.data.RecordRepository.update]。
 */
@Composable
fun RecordEditorScreen(
    container: AppContainer,
    onDismiss: () -> Unit,
    editing: Record? = null,
    title: String? = null,
    hint: String? = null,
) {
    // 獨立的全螢幕視窗攔住底層點擊，系統返回只作用於當前編輯器。
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        RecordEditorContent(container, onDismiss, editing, title, hint)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecordEditorContent(
    container: AppContainer,
    onDismiss: () -> Unit,
    editing: Record?,
    title: String?,
    hint: String?,
) {
    val context = LocalContext.current
    val isEditing = editing != null
    val screenTitle = title ?: if (isEditing) "編輯記錄" else "記帳"

    // 切換編輯對象時重新初始化欄位。
    val editingKey = editing?.id

    var selectedType by remember(editingKey) {
        mutableStateOf(
            when {
                editing == null -> CategoryCatalog.TYPE_EXPENSE
                editing.isExpense -> CategoryCatalog.TYPE_EXPENSE
                else -> CategoryCatalog.TYPE_INCOME
            },
        )
    }
    var selectedCategory by remember(editingKey) { mutableStateOf(editing?.category) }
    var amountInput by remember(editingKey) {
        mutableStateOf(editing?.let { AmountFormatter.format(it.absoluteAmount) } ?: "0")
    }
    var note by remember(editingKey) { mutableStateOf(editing?.note.orEmpty()) }
    var date by remember(editingKey) { mutableStateOf(editing?.localDate() ?: LocalDate.now()) }
    var showDatePicker by remember(editingKey) { mutableStateOf(false) }
    var showCategorySettings by remember(editingKey) { mutableStateOf(false) }
    var showDeleteConfirm by remember(editingKey) { mutableStateOf(false) }
    var errorMessage by remember(editingKey) { mutableStateOf<String?>(null) }

    val categoryVersion by container.categoryStore.revision.collectAsState()
    val categories = remember(selectedType, categoryVersion) {
        container.categoryStore.categoriesFor(selectedType)
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dismiss = {
        keyboard?.hide()
        focusManager.clearFocus()
        onDismiss()
    }
    LaunchedEffect(editingKey) {
        keyboard?.hide()
        focusManager.clearFocus()
    }
    BackHandler(enabled = !showCategorySettings, onBack = dismiss)

    val categoryContent: @Composable ColumnScope.() -> Unit = {
        // ===== 標題列（標題置中，關閉鈕靠右，兩者不互相擠壓）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = screenTitle,
                color = MikuColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
            IconButton(
                onClick = dismiss,
                modifier = Modifier.align(Alignment.CenterEnd).size(MinTouchTarget),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "關閉", tint = MikuColors.Text)
            }
        }

        // 自動記錄確認頁的說明（只有帶 hint 時才顯示）
        hint?.let {
            Text(
                text = it,
                color = MikuColors.TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // ===== 支出 / 收入 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(36.dp)
                .background(MikuColors.Surface, RoundedCornerShape(8.dp)),
        ) {
            CategoryCatalog.TYPES.forEach { type ->
                val selected = type == selectedType
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .background(
                            if (selected) MikuColors.Primary else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .clickableNoRipple {
                            selectedType = type
                            selectedCategory = null
                            amountInput = "0"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = type,
                        color = if (selected) MikuColors.Text else MikuColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 分類區獨立捲動，鍵盤和儲存按鈕固定在下方。
        RecordCategoryGrid(
            categories = categories,
            selectedName = selectedCategory,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onSelect = { name ->
                selectedCategory = name
                keyboard?.hide()
                focusManager.clearFocus()
            },
            onCategorySettings = { keyboard?.hide(); focusManager.clearFocus(); showCategorySettings = true },
        )

    }
    val inputContent: @Composable (Boolean) -> Unit = { compact ->
        RecordEditorInput(
            category = categories.firstOrNull { it.name == selectedCategory }
                ?: container.categoryStore.findForRecord(selectedType, selectedCategory.orEmpty()),
            categoryName = selectedCategory ?: "請選擇分類",
            showNumberPad = !imeVisible,
            compactNumberPad = compact,
            amountInput = amountInput,
            note = note,
            date = date,
            isEditing = isEditing,
            onKey = { key -> amountInput = applyAmountKey(amountInput, key) },
            onNoteChange = { note = it },
            onPickDate = { showDatePicker = true },
            onDelete = { showDeleteConfirm = true },
            onSave = {
                // 儲存後這個畫面會關閉：先收合系統 IME，免得鍵盤殘留在明細頁上
                keyboard?.hide()
                if (selectedCategory == null) {
                    errorMessage = "請先選擇分類！"
                    return@RecordEditorInput
                }
                val amount = AmountFormatter.round(amountInput.toDoubleOrNull() ?: 0.0)
                if (amount <= 0) {
                    errorMessage = "請輸入有效金額！"
                    return@RecordEditorInput
                }

                val signed = if (selectedType == CategoryCatalog.TYPE_EXPENSE) -amount else amount
                val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                if (editing == null) {
                    val record = Record.create(
                        amount = signed,
                        category = selectedCategory.orEmpty(),
                        note = note.trim(),
                        dateMillis = millis,
                    )
                    container.recordRepository.add(record)
                    runCatching { container.notifier.showRecordAdded(record) }
                    dismiss()
                } else {
                    // 編輯：只換內容，id／建立時間／垃圾桶狀態都由儲存庫保留
                    val saved = container.recordRepository.update(
                        editing.edited(
                            amount = signed,
                            category = selectedCategory.orEmpty(),
                            note = note.trim(),
                            dateMillis = millis,
                        ),
                    )
                    if (saved) {
                        Toast.makeText(context, "已更新記錄", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } else {
                        // 記錄在編輯期間被刪掉了：不要假裝成功，也不要吞掉使用者的輸入
                        errorMessage = "這筆記錄已經不存在（可能已被刪除），請關閉後重新整理明細。"
                    }
                }
            },
        )
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(MikuColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // 橫向矮視窗分欄，避免固定鍵盤擠掉分類；直向保持上方分類、下方輸入。
        if (landscape && maxHeight < 500.dp) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxSize(), content = categoryContent)
                Column(Modifier.weight(1f).align(Alignment.Bottom)) { inputContent(true) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                categoryContent()
                inputContent(false)
            }
        }
    }

    // ===== 日期選擇 =====
    if (showDatePicker) {
        // ⚠️ Material3 的 DatePicker 以 UTC 解讀 initialSelectedDateMillis，
        //    這裡必須餵 UTC 的當日 0 點。若用系統時區的 0 點，UTC+8 的使用者
        //    打開時會看到少一天，只按「確定」就會把記錄日期往前推一天。
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("確定", color = MikuColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消", color = MikuColors.TextSecondary)
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showCategorySettings) {
        CategorySettingsScreen(container, initialType = selectedType,
            onDismiss = { showCategorySettings = false },
            onRenamed = { type, old, name -> if (type == selectedType && selectedCategory == old) selectedCategory = name },
            onDeleted = { type, name -> if (type == selectedType && selectedCategory == name) selectedCategory = null },
        )
    }

    // ===== 編輯時的刪除（移到垃圾桶）=====
    if (showDeleteConfirm && editing != null) {
        ConfirmDialog(
            title = "刪除這筆記錄？",
            message = "記錄會先移到垃圾桶保留 ${com.example.countapp.data.RecordRepository.TRASH_RETENTION_DAYS} 天，" +
                "期間內都可以還原。",
            confirmText = "刪除",
            onConfirm = {
                container.recordRepository.moveToTrash(editing.id)
                showDeleteConfirm = false
                Toast.makeText(context, "已移到垃圾桶", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            containerColor = MikuColors.Surface,
            title = { Text("無法儲存", color = MikuColors.Text, fontSize = 16.sp) },
            text = { Text(message, color = MikuColors.TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("好", color = MikuColors.Primary)
                }
            },
            onDismissRequest = { errorMessage = null },
        )
    }
}

