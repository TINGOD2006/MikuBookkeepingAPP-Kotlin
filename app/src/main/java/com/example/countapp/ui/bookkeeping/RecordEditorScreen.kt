package com.example.countapp.ui.bookkeeping

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.AppContainer
import com.example.countapp.data.Record
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.domain.CategoryCatalog
import com.example.countapp.domain.CategoryItem
import com.example.countapp.ui.CategoryIcons
import com.example.countapp.ui.ConfirmDialog
import com.example.countapp.ui.MinTouchTarget
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors
import com.example.countapp.ui.theme.toComposeColor
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditorScreen(
    container: AppContainer,
    onDismiss: () -> Unit,
    editing: Record? = null,
    title: String? = null,
    hint: String? = null,
) {
    val context = LocalContext.current
    val isEditing = editing != null
    val screenTitle = title ?: if (isEditing) "編輯記錄" else "記帳"

    // 編輯對象可能在使用者操作期間被換掉（見下方根節點的觸控阻擋說明）。
    // 所有欄位都以 id 當 remember key，換了對象就重新初始化，
    // 絕不會出現「顯示 A 的內容、卻存成 B」的資料覆寫。
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
    var showAddCategory by remember(editingKey) { mutableStateOf(false) }
    var showDeleteConfirm by remember(editingKey) { mutableStateOf(false) }
    var errorMessage by remember(editingKey) { mutableStateOf<String?>(null) }

    // 分類清單會因為新增自訂分類而變動，用一個版本號強制重算
    var categoryVersion by remember { mutableStateOf(0) }
    val categories = remember(selectedType, categoryVersion) {
        container.categoryStore.categoriesFor(selectedType)
    }

    val inputAreaVisible = selectedCategory != null

    // 自製數字鍵盤是這個畫面主要的輸入方式，系統 IME 只留給「備註」欄位用。
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 關閉畫面時一律先收合系統 IME 並清掉焦點：殘留的 IME 除了會蓋住下一頁，
    // 系統鍵盤顯示時「返回手勢」還會先被 IME 吃掉（使用者會覺得手勢沒反應）。
    val dismiss = {
        keyboard?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    // 內容比畫面高時（小螢幕、橫向、或系統鍵盤開啟）必須能捲動，否則數字鍵盤與保存鈕
    // 會被擠出畫面而且點不到。
    // ⚠️ 捲動容器是以「無限高度」量測內容，容器內不能有 weight(1f) 子項，
    //    因此下面的分類九宮格改成一般 Row/Column 的自動高度版本（見 CategoryGrid）。
    val scrollState = rememberScrollState()

    // 記住「上一次的輸入區可見狀態」。初次組合時就初始化成目前值，因此
    //   - 編輯既有記錄（一開始就有分類）
    //   - 「已自動記錄」確認頁（一開始就有自動分類）
    // 開場時 inputAreaVisible 已經是 true，但不等於「使用者剛選了分類」，
    // 所以不會被自動捲到底，使用者仍看得到標題與分類九宮格。
    var wasInputAreaVisible by remember(editingKey) { mutableStateOf(inputAreaVisible) }

    // 只有在「停留在這個畫面期間，使用者剛把分類選好、輸入區由不可見變成可見」的那一瞬間
    // 才自動捲到最下面，把數字鍵盤與保存鈕帶進視野
    // （小螢幕上「分類格＋輸入區」會超過一個畫面高，不捲就得自己找鍵盤）。
    LaunchedEffect(inputAreaVisible) {
        if (inputAreaVisible && !wasInputAreaVisible) {
            // 等這一 frame 完成量測，maxValue 才會反映新內容的高度
            withFrameNanos { }
            scrollState.animateScrollTo(scrollState.maxValue)
        }
        wasInputAreaVisible = inputAreaVisible
    }

    // 返回鍵＝關閉編輯畫面（而不是直接結束 App，讓未儲存的輸入白白消失）。
    // 日期選擇／新增分類等對話框自己會先吃掉返回鍵，所以不會互相搶。
    // ⚠️ 這一層必須維持「最上層」：OnBackPressedDispatcher 取的是最後註冊且 enabled 的
    //    callback，而 MikuApp 的覆蓋層是依「由下往上」的順序組合（搜尋頁 → 新增頁 →
    //    編輯頁 → 自動記錄確認頁），所以這裡註冊的 callback 一定比它底下的畫面晚，
    //    返回手勢才會先關掉最上面這一層，而不是底下的頁面。
    BackHandler { dismiss() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MikuColors.Background)
            // ⚠️ 這一層必須「吃掉」觸控事件。
            //    Compose 只會在上層節點真的命中 pointerInput 時才停止往下的兄弟搜尋，
            //    純 background 不算命中——少了這行，畫面空白處的點擊會穿透到底下的
            //    明細清單／底部導覽／FAB，導致正在編輯的對象被換掉或誤開其他畫面。
            .pointerInput(Unit) {}
            // edge-to-edge（MainActivity 的 enableEdgeToEdge）：把手勢導覽列與系統鍵盤讓出來。
            // 少了 navigationBarsPadding，最下面的保存鈕會落在導覽列底下（三鍵導覽列是
            // 另一個視窗，會把點擊吃掉）＝按不到；少了 imePadding，系統鍵盤會直接蓋住數字鍵盤。
            // navigationBarsPadding 先做，imePadding 會扣掉已消耗的導覽列高度
            // （InsetsPaddingModifier 會 exclude 已消耗的 insets），兩者不會變成雙重留白。
            .navigationBarsPadding()
            .imePadding()
            // 內容超高時可捲動（分類格＋輸入區），確保數字鍵盤與保存鈕一定到得了
            .verticalScroll(scrollState),
    ) {
        // ===== 標題列（標題置中，關閉鈕靠右，兩者不互相擠壓）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
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
            // 觸控範圍放大到 48dp（原本只有 24dp，很難按到）
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(MinTouchTarget)
                    .clickableNoRipple(dismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "關閉",
                    tint = MikuColors.Text,
                    modifier = Modifier.size(24.dp),
                )
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

        // ===== 分類九宮格 =====
        // ⚠️ 刻意不用 LazyVerticalGrid：外層是 verticalScroll（見根節點說明），Lazy 元件
        //    只能被「有界高度」量測，放進來會在執行時丟出 "Vertically scrollable component
        //    was measured with an infinity maximum height constraints"；而且同方向的巢狀
        //    捲動也會互搶手勢。分類數量少（十幾個），用一般 Row/Column 排出每列四格即可。
        CategoryGrid(
            categories = categories,
            selectedName = selectedCategory,
            onSelect = { name ->
                val firstSelection = !inputAreaVisible
                selectedCategory = name
                // 分類一選好就會出現自製數字鍵盤：主動收合系統 IME，避免它一直蓋住鍵盤
                if (firstSelection) keyboard?.hide()
            },
            onAddCategory = { showAddCategory = true },
        )

        // ===== 輸入區 =====
        if (inputAreaVisible) {
            InputArea(
                category = categories.firstOrNull { it.name == selectedCategory }
                    ?: container.categoryStore.findAny(selectedCategory.orEmpty()),
                categoryName = selectedCategory.orEmpty(),
                amountInput = amountInput,
                note = note,
                date = date,
                isEditing = isEditing,
                onKey = { key -> amountInput = applyKey(amountInput, key) },
                onNoteChange = { note = it },
                onPickDate = { showDatePicker = true },
                onDelete = { showDeleteConfirm = true },
                onSave = {
                    // 儲存後這個畫面會關閉：先收合系統 IME，免得鍵盤殘留在明細頁上
                    keyboard?.hide()
                    val amount = AmountFormatter.round(amountInput.toDoubleOrNull() ?: 0.0)
                    if (amount <= 0) {
                        errorMessage = "請輸入有效金額！"
                        return@InputArea
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
                        onDismiss()
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
                            onDismiss()
                        } else {
                            // 記錄在編輯期間被刪掉了：不要假裝成功，也不要吞掉使用者的輸入
                            errorMessage = "這筆記錄已經不存在（可能已被刪除），請關閉後重新整理明細。"
                        }
                    }
                },
            )
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

    // ===== 新增自訂分類 =====
    if (showAddCategory) {
        AddCategoryDialog(
            type = selectedType,
            onDismiss = { showAddCategory = false },
            onCreate = { name, iconKey, colorArgb ->
                if (container.categoryStore.addCustomCategory(selectedType, name, iconKey, colorArgb)) {
                    categoryVersion++
                    selectedCategory = name
                }
                showAddCategory = false
            },
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

/** 分類九宮格一列幾格（沿用原本 LazyVerticalGrid 的 GridCells.Fixed(4)）。 */
private const val CATEGORY_COLUMNS = 4

/**
 * 分類九宮格。
 *
 * 為什麼不用 [LazyVerticalGrid]：這個畫面外層是 `verticalScroll`，Lazy 元件只能被
 * 「有界高度」量測，塞進無限高度的父層會在執行時丟出
 * `Vertically scrollable component was measured with an infinity maximum height constraints`。
 * 分類數量少（十幾個），用一般 Row/Column 排出每列固定四格即可，順便避免同方向的
 * 巢狀捲動互相搶手勢。
 *
 * 排版：分類依序填滿每列四格，最後一個「新增分類」格接在分類之後，
 * 不足的格子補空白 Spacer，讓每一列的欄寬與位置都對齊。
 */
@Composable
private fun CategoryGrid(
    categories: List<CategoryItem>,
    selectedName: String?,
    onSelect: (String) -> Unit,
    onAddCategory: () -> Unit,
) {
    val cellCount = categories.size + 1 // 分類 + 「新增」
    val rows = (cellCount + CATEGORY_COLUMNS - 1) / CATEGORY_COLUMNS

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until CATEGORY_COLUMNS) {
                    val index = row * CATEGORY_COLUMNS + column
                    if (index < categories.size) {
                        CategoryCell(
                            category = categories[index],
                            selected = categories[index].name == selectedName,
                            onClick = { onSelect(categories[index].name) },
                            modifier = Modifier.weight(1f),
                        )
                    } else if (index == categories.size) {
                        AddCategoryCell(
                            onClick = onAddCategory,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        // 補滿空白格，維持格線對齊
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    category: CategoryItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = category.colorArgb.toComposeColor()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(2.dp)
            .clickableNoRipple(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (selected) MikuColors.Primary else MikuColors.Surface,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategoryIcons.forKey(category.iconKey),
                contentDescription = category.name,
                tint = if (selected) MikuColors.Text else color,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = category.name,
                color = if (selected) MikuColors.Text else MikuColors.TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (category.isCustom) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MikuColors.TextSecondary,
                    modifier = Modifier.size(8.dp),
                )
            }
        }
    }
}

@Composable
private fun AddCategoryCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(2.dp)
            .clickableNoRipple(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MikuColors.Surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "新增分類",
                tint = MikuColors.Text,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text("新增", color = MikuColors.Text, fontSize = 10.sp)
    }
}

@Composable
private fun InputArea(
    category: CategoryItem?,
    categoryName: String,
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
    // 自製鍵盤是這個畫面的主要輸入方式：按任一鍵就主動收合系統 IME，
    // 避免 IME 一直蓋住數字鍵盤（IME 顯示時，返回手勢也會先被它吃掉）。
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
            Text(categoryName, color = MikuColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
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

        NumberPad { key ->
            // 用自製鍵盤輸入時就把系統 IME 收起來（IME 沒開時是 no-op）
            keyboard?.hide()
            onKey(key)
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
private fun NumberPad(onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "清空"),
        listOf("1", "2", "3", "."),
    )

    Column {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    KeyButton(key = key, flex = 1, onKey = onKey)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            KeyButton(key = "0", flex = 4, onKey = onKey)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.KeyButton(
    key: String,
    flex: Int,
    onKey: (String) -> Unit,
) {
    val isSpecial = key == "清空" || key == "⌫"

    Box(
        modifier = Modifier
            .weight(flex.toFloat())
            .padding(horizontal = 3.dp)
            .height(40.dp)
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
private fun applyKey(current: String, key: String): String = when (key) {
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

/** 新增自訂分類的對話框。 */
@Composable
private fun AddCategoryDialog(
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
