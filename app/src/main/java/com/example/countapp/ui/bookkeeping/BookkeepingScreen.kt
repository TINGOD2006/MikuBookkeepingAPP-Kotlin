package com.example.countapp.ui.bookkeeping

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.AppContainer
import com.example.countapp.data.Record
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.domain.CategoryItem
import com.example.countapp.ui.EmptyState
import com.example.countapp.ui.MinTouchTarget
import com.example.countapp.ui.MonthPickerDialog
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * 滑動後露出的動作方塊尺寸。
 *
 * 左滑到底會停在 [SwipeActionRevealWidth]，也就是剛好露出兩個方塊的寬度
 * （不是把整列滑掉），使用者可以停在這個位置再決定要刪除還是編輯。
 */
private val SwipeActionWidth: Dp = 62.dp
private val SwipeActionGap: Dp = 6.dp
private val SwipeActionRevealWidth: Dp = SwipeActionWidth * 2 + SwipeActionGap

/** 記錄卡片左右留白（越大＝每一列越窄）。 */
private val AccountRowHorizontalMargin: Dp = 26.dp

/** 判定「快速滑動」的速度門檻（dp/s）。 */
private val SwipeFlingThreshold: Dp = 320.dp

/**
 * 明細頁（首頁）。
 *
 * 版面：上方 bar（標題 + 月份選擇器 + 收支統計）共用同一塊藍色底，中間沒有縫隙；
 * 下面是依日期分組的記錄清單，**日期由大到小、由上往下排**（30 號在最上面）。
 *
 * 操作：
 *   - **點一下**記錄 → 開編輯畫面
 *   - **左滑**記錄 → 停在開啟位置，右側露出【紅色刪除】與【藍色編輯】兩個方塊
 *   - 放大鏡 → 切到獨立的「尋找」頁（[SearchScreen]）
 *
 * 垃圾桶只在「我的」頁面開啟（明細頁上方 bar 不再放垃圾桶按鈕）。
 */
@Composable
fun BookkeepingScreen(
    container: AppContainer,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onEditRecord: (Record) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val allRecords by container.recordRepository.records.collectAsState()
    val categoryStore = container.categoryStore

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showMonthPicker by remember { mutableStateOf(false) }

    // 一次只允許一列停在開啟位置；開啟別列時原本那列會自動收回
    var openRecordId by remember { mutableStateOf<String?>(null) }

    // 由上到下「新到舊」：日期大的（30 號）在最上面，同一天內也是晚的在前。
    // 三個鍵都要是降冪，否則同一天內會被 createdAt 或 id 的升冪蓋回來。
    val monthRecords = remember(allRecords, selectedMonth) {
        allRecords.filter { YearMonth.from(it.localDate()) == selectedMonth }
            .sortedWith(
                compareByDescending<Record> { it.dateMillis }
                    .thenByDescending { it.createdAtMillis }
                    .thenByDescending { it.id },
            )
    }

    // 日期群組也必須跟著反向：groupBy 會保留 monthRecords 的順序，
    // 但 toSortedMap 預設是升冪（1 號在最上），所以這裡要自己給比較器。
    val grouped = remember(monthRecords) {
        monthRecords.groupBy { it.localDate() }.toSortedMap(compareByDescending { it })
    }

    val expense = container.recordRepository.totalExpense(monthRecords)
    val income = container.recordRepository.totalIncome(monthRecords)

    // 刪除 → 移到垃圾桶，並提供「復原」。回傳是否真的刪除，讓滑動列可以決定要不要收回。
    val moveToTrash: (Record) -> Boolean = { record ->
        val moved = container.recordRepository.moveToTrash(record.id)
        if (moved) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "「${record.category}」已移到垃圾桶",
                    actionLabel = "復原",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    container.recordRepository.restore(record.id)
                }
            }
        }
        moved
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 上方 bar 與金額區塊是同一塊藍色底，中間沒有黑色縫隙
            BookkeepingHeader(
                month = selectedMonth,
                expense = expense,
                income = income,
                onPickMonth = { showMonthPicker = true },
                onOpenSearch = onOpenSearch,
            )

            if (monthRecords.isEmpty()) {
                EmptyState(
                    message = "${selectedMonth.year}年${selectedMonth.monthValue}月 尚無記錄",
                    hint = "點擊下方 ＋ 按鈕新增記錄；點一下帳目可編輯，左滑可刪除",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        // 記錄卡片左右留白加大 → 每一列看起來更窄、更像卡片清單
                        start = AccountRowHorizontalMargin,
                        end = AccountRowHorizontalMargin,
                        top = 8.dp,
                        bottom = 120.dp,
                    ),
                ) {
                    grouped.forEach { (date, records) ->
                        item(key = "header_$date") {
                            DateHeader(
                                date = date,
                                expense = container.recordRepository.dailyExpense(records),
                                income = container.recordRepository.dailyIncome(records),
                                total = container.recordRepository.dailyTotal(records),
                            )
                        }
                        items(records, key = { it.id }) { record ->
                            SwipeActionRecord(
                                record = record,
                                category = categoryStore.findAny(record.category),
                                revealed = openRecordId == record.id,
                                onRevealChange = { revealed ->
                                    openRecordId = if (revealed) record.id else null
                                },
                                onEdit = { onEditRecord(record) },
                                onDelete = {
                                    val moved = moveToTrash(record)
                                    // 刪掉的那列不該留在「開啟中」的清單狀態裡
                                    if (moved) openRecordId = null
                                    moved
                                },
                            )
                        }
                    }
                }
            }
        }

        // 刪除後的「復原」提示
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            initial = selectedMonth,
            onDismiss = { showMonthPicker = false },
            onConfirm = {
                onMonthChange(it)
                showMonthPicker = false
            },
        )
    }
}

// ============================================================
// 上方 bar：標題 + 月份選擇器 + 收支統計（融合成一塊）
// ============================================================

@Composable
private fun BookkeepingHeader(
    month: YearMonth,
    expense: Double,
    income: Double,
    onPickMonth: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 「上方 bar + 金額卡片」融入一齊：同一塊底色、沒有內縮圓角卡片、沒有黑縫
            .background(MikuColors.Primary)
            .statusBarsPadding(),
    ) {
        // ===== 標題列（標題置中、右側放大鏡）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = "Miku 記帳",
                color = MikuColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(vertical = 10.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(MinTouchTarget)
                    .clickableNoRipple(onOpenSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "尋找",
                    tint = MikuColors.Text,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // ===== 四等分：月份選擇器 | 支出 | 收入 | 結餘 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthCell(month = month, onClick = onPickMonth)
            SummaryDivider()
            SummaryStat("支出", AmountFormatter.format(expense))
            SummaryDivider()
            SummaryStat("收入", AmountFormatter.format(income))
            SummaryDivider()
            SummaryStat("結餘", AmountFormatter.format(income - expense))
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

/**
 * 四等分最左邊的月份選擇器。
 *
 * 用半透明白膠囊讓它從藍底上跳出來（獨立突顯），但寬度與其他三格相同，
 * 所以整條 bar 看起來就是 月份 | 支出 | 收入 | 結餘 四等分。
 *
 * 年份與月份**各自給 lineHeight**（不能只給 fontSize，原因見下方註解）：
 * 兩行合計 15 + 21 = 36dp，加上內距仍在 48dp 的最小觸控高度內，
 * 使用者不會再看到被裁掉一半的「9月」。
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.MonthCell(
    month: YearMonth,
    onClick: () -> Unit,
) {
    Box(
        // 外框至少 48dp 是可點範圍；用 heightIn(min) 而不是 height()：
        // 字級放大（或使用者把系統字體調大）時膠囊可以把外框撐高，
        // 不會像舊版那樣被硬切成 48dp 而把文字裁掉。
        modifier = Modifier
            .weight(1f)
            .heightIn(min = MinTouchTarget)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(MikuColors.Text.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                // 水平 padding 由 8dp 收到 2dp：把省下的 12dp 讓給左側對稱佔位，
                // 這樣「文字置中」才不會讓膠囊變寬（推導見下方 Row 內註解）。
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ===== 為什麼一定要寫 lineHeight =====
            // Material3 的 LocalTextStyle 是 typography.bodyLarge（lineHeight 24sp），
            // 只給 fontSize 的文字會繼承 24sp 行高：舊版年份 9sp、月份 16sp
            // 兩行各佔 24dp，加內距就是 56dp，卻被外框硬限制在 48dp，
            // 量測時第二行（月份）只拿到 16dp → 文字上下被裁掉，使用者看不到。
            // 現在每個 Text 都給相稱的 lineHeight，行高不再被 bodyLarge 綁住，
            // 同時把年份 9→12sp、月份 16→17sp，放大到一眼可讀。
            Text(
                text = "${month.year}",
                color = MikuColors.Text.copy(alpha = 0.9f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ===== 為什麼這樣就置中 =====
                // Row 內容 = [左佔位 11dp｜N月（文字寬 T）｜下拉箭頭 11dp]，左右兩側等寬。
                // 文字左緣 = padding(2) + 佔位(11) = 13dp，
                // 文字中心 = 2 + 11 + T/2 = (T + 26)/2 = 膠囊總寬中心；
                // 又 Column 是 CenterHorizontally、外層 Box 是 contentAlignment = Center，
                // 故 文字中心 = 膠囊中心 = 48dp 格中心（三個中心重合）。
                // 舊版 Row 只有右側一顆箭頭、沒有左佔位：文字中心 = T/2，
                // 比 Row 中心 (T + 14)/2 少 7dp → 看起來被箭頭往左推。
                //
                // ===== 寬度上限推導（改 dp 前務必先讀，別改回偏心版） =====
                // 本格外框是 weight(1f)，可用寬度 W = (螢幕寬 - 16(bar padding) - 3(分隔線)) / 4；
                // 膠囊是 wrap-content，必須塞得進 W，否則文字會被壓縮、尾字被裁。
                //   舊版偏心：膠囊 = 8 + (T + 14) + 8 = T + 30
                //   天真對稱版：膠囊 = 8 + (14 + T + 14) + 8 = T + 44（比舊版多 14dp ✗；
                //     320dp 螢幕 W ≈ (320 - 16 - 3)/4 ≈ 75dp，T ≈ 34dp 時需 78dp > 75dp）
                //   本版：膠囊 = 2 + (11 + T + 11) + 2 = T + 26 ✓
                //     320dp + fontScale 1.0：T（17sp）≈ 36dp → 約 62dp < 75dp
                //     320dp + fontScale 1.3：T ≈ 47dp → 約 73dp < 75dp，仍安全
                // 約束式：2 × 水平 padding + 左佔位 + 箭頭 = 26dp，
                // 且「左佔位寬度 == 箭頭寬度」不可破壞，否則文字又會偏心。
                Spacer(modifier = Modifier.size(11.dp))
                Text(
                    text = "${month.monthValue}月",
                    color = MikuColors.Text,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "切換月份",
                    tint = MikuColors.Text,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(MikuColors.Text.copy(alpha = 0.3f)),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SummaryStat(label: String, value: String) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = MikuColors.Text.copy(alpha = 0.85f),
            fontSize = 11.sp,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        // 四等分後每格較窄：金額不換行（9 位數也不會把 bar 撐高），必要時省略
        Text(
            text = value,
            color = MikuColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate, expense: Double, income: Double, total: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.CalendarToday,
            contentDescription = null,
            tint = MikuColors.TextSecondary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = Record.formatDate(date),
            color = MikuColors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.weight(1f))

        if (expense > 0) {
            Text("⬇ ${AmountFormatter.format(expense)}", color = MikuColors.Expense, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(6.dp))
        }
        if (income > 0) {
            Text("⬆ ${AmountFormatter.format(income)}", color = MikuColors.Income, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = "淨額 ${AmountFormatter.format(total)}",
            color = if (total < 0) MikuColors.Expense else MikuColors.Income,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ============================================================
// 左滑停在定點：右側露出【刪除】【編輯】兩個方塊
// ============================================================

/**
 * 記錄列 + 左滑動作。
 *
 * 手勢行為：
 *   - 往左拖曳 → 卡片跟著手指移動，最多 [SwipeActionRevealWidth]
 *   - 放開時：拖超過 40% 或快速左滑 → **停在開啟位置**（不會滑掉整列）；
 *     否則自動收回原位
 *   - 停在開啟位置時，右側就是紅色【刪除】與藍色【編輯】兩個方塊
 *   - 開啟狀態下點卡片本身 = 先收回，避免誤觸編輯
 *   - 一次只允許一列打開（[revealed] 由外層控制，別列打開時這一列自動收回）
 *
 * 這裡刻意不使用 SwipeToDismissBox：它的定位點是「整列寬度」（滑掉就消失），
 * 沒辦法停在中間只露出兩個方塊。
 */
@Composable
private fun SwipeActionRecord(
    record: Record,
    category: CategoryItem?,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Boolean,
) {
    val density = LocalDensity.current
    val revealPx = with(density) { SwipeActionRevealWidth.toPx() }
    val flingThreshold = with(density) { SwipeFlingThreshold.toPx() }
    val scope = rememberCoroutineScope()

    // 0f = 收合；-revealPx = 停在打開位置
    var offsetPx by remember(record.id) { mutableFloatStateOf(0f) }

    // 動作方塊只有在真的滑開時才組裝（derivedStateOf 只會在「有沒有滑開」改變時
    // 才觸發重組，拖曳過程不會每一帧都重組）。
    // 收合時不組裝也順便解決兩件事：方塊不可能被誤點，也不會出現在無障礙順序裡
    // （否則 TalkBack 會在沒滑開的列上唸出「刪除」，啟用就刪掉看不見的記錄）。
    val blocksVisible by remember(record.id) { derivedStateOf { offsetPx < -0.5f } }

    // 放開後的定點動畫
    suspend fun settleTo(target: Float) {
        animate(
            initialValue = offsetPx,
            targetValue = target,
            animationSpec = tween(durationMillis = 180),
        ) { value, _ -> offsetPx = value }
    }

    // 別列被打開、或外層要求收回時，這一列自動回到原位
    LaunchedEffect(revealed) {
        val target = if (revealed) -revealPx else 0f
        if (offsetPx != target) settleTo(target)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // 背景：與卡片同高、同縮排的兩個動作方塊；收合時完全被卡片蓋住
        if (blocksVisible) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(vertical = RecordCardVerticalGap),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SwipeActionBlock(
                    label = "刪除",
                    icon = Icons.Filled.Delete,
                    color = MikuColors.Expense,
                    onClick = {
                        onRevealChange(false)
                        // 刪除失敗（例如記錄已不在）時把這一列收回，不要留在半開狀態
                        if (!onDelete()) scope.launch { settleTo(0f) }
                    },
                )
                Spacer(modifier = Modifier.width(SwipeActionGap))
                SwipeActionBlock(
                    label = "編輯",
                    icon = Icons.Filled.Edit,
                    color = MikuColors.Primary,
                    onClick = {
                        // 先把這一列收回原位，再開編輯畫面（不然回到明細時會停在半開狀態）
                        onRevealChange(false)
                        scope.launch { settleTo(0f) }
                        onEdit()
                    },
                )
            }
        }

        // 前景：可拖曳的記錄卡片
        RecordCard(
            record = record,
            category = category,
            onClick = {
                if (offsetPx < 0f) {
                    // 已經打開 → 點一下收回
                    onRevealChange(false)
                    scope.launch { settleTo(0f) }
                } else {
                    onEdit()
                }
            },
            modifier = Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetPx = (offsetPx + delta).coerceIn(-revealPx, 0f)
                    },
                    onDragStopped = { velocity ->
                        val target = when {
                            velocity <= -flingThreshold -> -revealPx
                            velocity >= flingThreshold -> 0f
                            offsetPx <= -revealPx * 0.4f -> -revealPx
                            else -> 0f
                        }
                        onRevealChange(target != 0f)
                        settleTo(target)
                    },
                ),
        )
    }
}

/** 滑動露出的動作方塊（紅＝刪除、藍＝編輯）。 */
@Composable
private fun SwipeActionBlock(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(SwipeActionWidth)
            .background(color, RoundedCornerShape(10.dp))
            .clickableNoRipple(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MikuColors.Text,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = MikuColors.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
