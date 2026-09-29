package com.example.countapp.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.domain.BudgetAlertLevel
import com.example.countapp.domain.BudgetStatus
import com.example.countapp.domain.CategoryCatalog
import com.example.countapp.domain.CategoryItem
import com.example.countapp.domain.calculateBudgetStatus
import com.example.countapp.domain.visibleCategoryBudgetNames
import com.example.countapp.ui.CategoryIcons
import com.example.countapp.ui.MinTouchTarget
import com.example.countapp.ui.MonthPickerDialog
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors
import com.example.countapp.ui.theme.toComposeColor
import java.time.YearMonth

/**
 * 預算頁。
 *
 * 對應 Flutter 版的 `budget_page.dart`：
 * 設定當月預算（整月）＋ 各分類每月預算、顯示已支出／剩餘、進度條與超支警示。
 *
 * 兩個關鍵設計：
 *   - 「各分類預算」是**選用**且**自己挑**的：畫面不預設任何分類，
 *     只列出使用者按「新增分類預算」加進來的分類（已設定金額的，或剛新增還沒填的）；
 *     沒有設定的分類根本不畫進度條（0% 的進度條會讓人誤以為設了預算卻沒花到）。
 *   - 使用率／超支金額／警示等級全部由 `domain/BudgetStatus.kt` 的純函式算出，
 *     月份進度條與分類進度條共用同一套規則，不會出現兩邊說法不一致。
 */
@Composable
fun BudgetScreen(container: AppContainer) {
    val records by container.recordRepository.records.collectAsState()

    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    // 分類預算的編輯狀態：記錄「正在編輯哪個分類」（null 表示沒有）
    var editingCategory by remember { mutableStateOf<String?>(null) }
    var categoryInputText by remember { mutableStateOf("") }

    // 使用者按了「新增分類預算」、但還沒儲存金額的分類。
    // 分類預算改成一張白紙：畫面只列「已設定的」＋「剛新增還沒填金額的」，
    // 其餘分類完全不出現，所以不會再有「每個分類都預設了預算」的錯覺。
    var draftCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    // 預算不是 StateFlow，改用一個版本號在使用者儲存後重算
    var version by remember { mutableStateOf(0) }

    val budget = remember(selectedMonth, version) {
        container.budgetRepository.load(selectedMonth)
    }
    // 各分類預算：只包含「有設定」的分類，缺值不會出現在這個 map
    val categoryBudgets = remember(selectedMonth, version) {
        container.budgetRepository.loadCategoryBudgets(selectedMonth)
    }
    val monthRecords = remember(records, selectedMonth) {
        container.recordRepository.forMonth(selectedMonth)
    }
    val monthlyExpense = remember(monthRecords) {
        container.recordRepository.totalExpense(monthRecords)
    }
    // 各分類支出：只算支出（負數），依分類加總絕對值
    val categoryExpenses = remember(monthRecords) {
        monthRecords.filter { it.isExpense }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.absoluteAmount } }
    }
    // 支出分類清單（含自訂分類）；即使一個分類預算都沒設定也能正常運作
    val expenseCategories = remember(version) {
        container.categoryStore.categoriesFor(CategoryCatalog.TYPE_EXPENSE)
    }

    // 這張清單只放「已設定預算的」與「剛新增還沒填金額的」，其他分類完全不出現。
    // 排序、去重與「分類被刪掉」的處理都在純函式裡（有單元測試）。
    val visibleCategories = remember(expenseCategories, categoryBudgets, draftCategories) {
        visibleCategoryBudgetNames(
            orderedCategories = expenseCategories.map { it.name },
            configured = categoryBudgets.keys,
            drafts = draftCategories,
        )
    }

    // 還可以新增的分類＝還沒設預算、也不在草稿中的分類
    val availableCategories = remember(expenseCategories, categoryBudgets, draftCategories) {
        expenseCategories.filterNot { it.name in categoryBudgets || it.name in draftCategories }
    }

    // 月份的使用狀態（警示等級／超支金額都在這裡算好）
    val monthStatus = calculateBudgetStatus(monthlyExpense, budget)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ===== 標題 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("預算", color = MikuColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            // 月份切換：外層是 48dp 的透明外框（可點範圍），內層膠囊維持原本的視覺與字級；
            // 高度只是把 header 撐高，不會與下方（不可點的）預算卡片重疊。
            Box(
                modifier = Modifier
                    .height(MinTouchTarget)
                    .clickableNoRipple { showMonthPicker = true },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .background(MikuColors.Surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${selectedMonth.year} 年 ${selectedMonth.monthValue} 月",
                        color = MikuColors.Text,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // ===== 整月預算設定 =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(MikuColors.Surface, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            if (editing) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    singleLine = true,
                    label = { Text("每月預算金額", color = MikuColors.TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MikuColors.Text,
                        unfocusedTextColor = MikuColors.Text,
                    ),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        editing = false
                        inputText = ""
                    }) { Text("取消", color = MikuColors.TextSecondary) }

                    TextButton(onClick = {
                        val amount = AmountFormatter.round(inputText.toDoubleOrNull() ?: 0.0)
                        if (amount > 0) {
                            container.budgetRepository.save(selectedMonth, amount)
                            version++
                            editing = false
                            inputText = ""
                        }
                    }) { Text("儲存", color = MikuColors.Primary) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = budget?.let { "\$${AmountFormatter.format(it)}" } ?: "未設定",
                        color = if (budget != null) MikuColors.Text else MikuColors.TextSecondary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        // 「編輯」原本的可點高度只有文字行高（約 20dp），
                        // 用 48dp 高的外框撐開；內容仍垂直置中，不會與相鄰列重疊
                        // （卡片下方是 Spacer 與另一張不可點的卡片）。
                        modifier = Modifier
                            .height(MinTouchTarget)
                            .clickableNoRipple {
                                inputText = budget?.let { AmountFormatter.format(it) } ?: ""
                                editing = true
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "編輯",
                            tint = MikuColors.Primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("編輯", color = MikuColors.Primary, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 預算資訊、警示與進度共用一張卡片。
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .background(MikuColors.Surface, RoundedCornerShape(12.dp)).padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("已支出", color = MikuColors.TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\$${AmountFormatter.format(monthlyExpense)}",
                        color = MikuColors.Expense,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(MikuColors.SurfaceVariant),
                )
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (monthStatus.isOver) "超出" else "剩餘", color = MikuColors.TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (monthStatus.isConfigured) {
                            "\$${AmountFormatter.format(if (monthStatus.isOver) monthStatus.overspent else monthStatus.remaining)}"
                        } else {
                            "--"
                        },
                        color = if (monthStatus.isOver) MikuColors.Expense else MikuColors.Income,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            BudgetAlertRow(monthStatus)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("0", color = MikuColors.TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (monthStatus.isConfigured) {
                        "${monthStatus.usedPercent}%"
                    } else {
                        "尚未設定預算"
                    },
                    color = alertColor(monthStatus) ?: MikuColors.TextSecondary,
                    fontSize = 12.sp,
                    // 未設定預算時沒有等級可言，不要讓文字變成粗體警示
                    fontWeight = if (monthStatus.isConfigured &&
                        monthStatus.level != BudgetAlertLevel.NORMAL
                    ) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = monthStatus.budget?.let { AmountFormatter.format(it) } ?: "--",
                    color = MikuColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { monthStatus.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = progressColor(monthStatus),
                trackColor = MikuColors.SurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== 各分類預算 =====
        // 這裡刻意**不預設任何分類**：只列出使用者自己新增（＝要管控）的分類，
        // 想管控哪個分類就用下方的「新增分類預算」按鈕自己挑。
        Text(
            text = "各分類預算",
            color = MikuColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "不會預設所有分類；按下方按鈕新增你想管控預算的分類。",
            color = MikuColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(MikuColors.Surface, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (visibleCategories.isEmpty()) {
                Text(
                    text = "尚未新增分類預算",
                    color = MikuColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }

            visibleCategories.forEach { name ->
                val spent = categoryExpenses[name] ?: 0.0
                // 未設定時 categoryBudgets[name] 是 null → 等級 UNKNOWN → 不畫進度條
                val status = calculateBudgetStatus(spent, categoryBudgets[name])

                CategoryBudgetRow(
                    name = name,
                    iconKey = expenseCategories.firstOrNull { it.name == name }?.iconKey,
                    iconColorArgb = expenseCategories.firstOrNull { it.name == name }?.colorArgb,
                    status = status,
                    editing = editingCategory == name,
                    input = categoryInputText,
                    onInputChange = { categoryInputText = it },
                    onStartEdit = {
                        categoryInputText = status.budget
                            ?.let { AmountFormatter.format(it) }
                            ?: ""
                        editingCategory = name
                    },
                    onCancelEdit = {
                        editingCategory = null
                        categoryInputText = ""
                        // 還沒填金額就取消 → 這一列直接消失（不會留下一個空的分類）
                        if (name !in categoryBudgets) draftCategories = draftCategories - name
                    },
                    onSave = {
                        val amount = AmountFormatter.round(categoryInputText.toDoubleOrNull() ?: 0.0)
                        if (amount > 0) {
                            container.budgetRepository.saveCategoryBudget(selectedMonth, name, amount)
                            version++
                            draftCategories = draftCategories - name
                        }
                        editingCategory = null
                        categoryInputText = ""
                    },
                    onClear = {
                        container.budgetRepository.clearCategoryBudget(selectedMonth, name)
                        version++
                        // 清除後連草稿一起移除 → 這一列從清單消失（回到「未新增」）
                        draftCategories = draftCategories - name
                        editingCategory = null
                        categoryInputText = ""
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 新增分類預算：分類清單不再預設全開，由使用者自己挑要管控的分類
        if (availableCategories.isEmpty()) {
            Text(
                text = "所有支出分類都已設定預算",
                color = MikuColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(MinTouchTarget)
                    .background(MikuColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .clickableNoRipple { showCategoryPicker = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MikuColors.Primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "新增分類預算",
                    color = MikuColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            initial = selectedMonth,
            onDismiss = { showMonthPicker = false },
            onConfirm = {
                selectedMonth = it
                editing = false
                // 換月份時把分類預算的編輯狀態一起收掉，避免改到別的月份的預算
                editingCategory = null
                categoryInputText = ""
                draftCategories = emptySet()
                showMonthPicker = false
            },
        )
    }

    if (showCategoryPicker) {
        CategoryBudgetPickerDialog(
            categories = availableCategories,
            onPick = { name ->
                // 先把這一列加進清單並直接進入輸入狀態，使用者少按一次「設定」
                draftCategories = draftCategories + name
                editingCategory = name
                categoryInputText = ""
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }
}

/**
 * 單一分類的預算列。
 *
 * 已設定 → 顯示「已花費 / 預算」＋**使用百分比**＋自己的進度條與警示；
 * 未設定（剛新增還沒填金額）→ 只顯示「未設定」與「設定」按鈕
 * （完全不畫進度條，避免 0% 的假進度）。
 */
@Composable
private fun CategoryBudgetRow(
    name: String,
    iconKey: String?,
    iconColorArgb: Long?,
    status: BudgetStatus,
    editing: Boolean,
    input: String,
    onInputChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            iconKey?.let { key ->
                Icon(
                    imageVector = CategoryIcons.forKey(key),
                    contentDescription = null,
                    tint = (iconColorArgb ?: 0xFF9E9E9EL).toComposeColor(),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = name,
                color = MikuColors.Text,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (status.isConfigured) {
                // 使用百分比放在分類名稱同一列，一眼就能比較各分類的用量
                Text(
                    text = "${status.usedPercent}%",
                    color = progressColor(status),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    text = "未設定",
                    color = MikuColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // 設定／編輯：整列 48dp 高都是可點範圍（文字行高本身只有約 20dp）
            Box(
                modifier = Modifier
                    .height(MinTouchTarget)
                    .clickableNoRipple(onStartEdit),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = if (status.isConfigured) "編輯分類預算" else "設定分類預算",
                        tint = MikuColors.Primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (status.isConfigured) "編輯" else "設定",
                        color = MikuColors.Primary,
                        fontSize = 12.sp,
                    )
                }
            }
            // 只有已設定的分類才需要「清除」
            if (status.isConfigured) {
                Box(
                    modifier = Modifier
                        .height(MinTouchTarget)
                        .clickableNoRipple(onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "清除",
                        color = MikuColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }

        if (editing) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                singleLine = true,
                label = { Text("$name 每月預算", color = MikuColors.TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MikuColors.Text,
                    unfocusedTextColor = MikuColors.Text,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onCancelEdit) {
                    Text("取消", color = MikuColors.TextSecondary)
                }
                TextButton(onClick = onSave) {
                    Text("儲存", color = MikuColors.Primary)
                }
            }
        } else if (status.isConfigured) {
            // 「已花費 / 預算」放在進度條正上方（百分比已經在最上面的名稱列）
            status.budget?.let { budget ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "\$${AmountFormatter.format(status.spent)} / " +
                            "\$${AmountFormatter.format(budget)}",
                        color = if (status.isOver) MikuColors.Expense else MikuColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (status.isOver) {
                            "超出 \$${AmountFormatter.format(status.overspent)}"
                        } else {
                            "剩 \$${AmountFormatter.format(status.remaining)}"
                        },
                        color = if (status.isOver) MikuColors.Expense else MikuColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            BudgetAlertRow(status)
            // 已設定預算才有進度條
            LinearProgressIndicator(
                progress = { status.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(6.dp),
                color = progressColor(status),
                trackColor = MikuColors.SurfaceVariant,
            )
        }
    }
}

/**
 * 進度條上方的警示列。
 *
 * 只有 >=80%（警示）與 >100%（超支）才顯示；一律「顏色 ＋ 圖示 ＋ 文字」，
 * 確保不是只靠顏色傳達訊息。
 */
@Composable
private fun BudgetAlertRow(status: BudgetStatus) {
    val color = alertColor(status) ?: return
    val isOver = status.level == BudgetAlertLevel.OVER
    val text = if (isOver) {
        "已超過預算！超出 \$${AmountFormatter.format(status.overspent)}"
    } else {
        "即將超過預算！已使用 ${status.usedPercent}%"
    }

    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = if (isOver) "已超過預算" else "即將超過預算",
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 警示色：超支用支出紅、即將超支用次要藍；未達門檻或未設定回傳 null。 */
private fun alertColor(status: BudgetStatus) = when (status.level) {
    BudgetAlertLevel.OVER -> MikuColors.Expense
    BudgetAlertLevel.WARNING -> MikuColors.Secondary
    else -> null
}

/** 進度條顏色：超支紅、警示藍、其餘主色。 */
private fun progressColor(status: BudgetStatus) = when (status.level) {
    BudgetAlertLevel.OVER -> MikuColors.Expense
    BudgetAlertLevel.WARNING -> MikuColors.Secondary
    else -> MikuColors.Primary
}

/**
 * 「新增分類預算」的分類挑選對話框。
 *
 * 只列出還沒設定預算的分類（支出分類有 20 幾個，所以清單可捲動並限制高度），
 * 每列都是 48dp 的可點範圍；點一下就代表「這個分類要管控預算」。
 */
@Composable
private fun CategoryBudgetPickerDialog(
    categories: List<CategoryItem>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        containerColor = MikuColors.Surface,
        title = { Text("新增分類預算", color = MikuColors.Text, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "選擇要管控預算的分類：",
                    color = MikuColors.TextSecondary,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MinTouchTarget)
                            .clickableNoRipple { onPick(category.name) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = CategoryIcons.forKey(category.iconKey),
                            contentDescription = null,
                            tint = category.colorArgb.toComposeColor(),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = category.name, color = MikuColors.Text, fontSize = 14.sp)
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
