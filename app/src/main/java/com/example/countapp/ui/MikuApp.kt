package com.example.countapp.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.AppContainer
import com.example.countapp.data.Record
import com.example.countapp.ui.analysis.AnalysisScreen
import com.example.countapp.ui.bookkeeping.BookkeepingScreen
import com.example.countapp.ui.bookkeeping.RecordEditorScreen
import com.example.countapp.ui.bookkeeping.SearchScreen
import com.example.countapp.ui.budget.BudgetScreen
import com.example.countapp.ui.profile.ProfileScreen
import com.example.countapp.ui.theme.MikuColors
import java.time.YearMonth

/** Material3 主題：沿用 Flutter 版的配色。 */
@Composable
fun MikuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MikuColors.Primary,
            secondary = MikuColors.Secondary,
            background = MikuColors.Background,
            surface = MikuColors.Surface,
            onPrimary = MikuColors.Text,
            onBackground = MikuColors.Text,
            onSurface = MikuColors.Text,
        ),
        content = content,
    )
}

/**
 * App 根畫面：底部四個頁籤 + 中央記帳按鈕。
 *
 * 對應 Flutter 版 `lib/main.dart` 的 `MyHomePage`。因為不再需要
 * 跨頁面刷新（記錄是 StateFlow，寫入後所有畫面自動更新），
 * 這裡比 Flutter 版的 GlobalKey 刷新機制單純很多。
 */
@Composable
fun MikuApp(container: AppContainer) {
    MikuTheme {
        var selectedTab by remember { mutableIntStateOf(0) }
        var showAddRecord by remember { mutableStateOf(false) }
        // 編輯中的記錄。刻意放在最上層（與新增畫面同級），
        // 這樣編輯畫面才能蓋住底部導覽與中央 FAB，不會被誤觸而丟掉未儲存的修改。
        var editingRecord by remember { mutableStateOf<Record?>(null) }
        // 明細頁正在看的月份。放在最上層（而不是明細頁內部）：切到別頁再回來、
        // 或轉螢幕時，使用者選的月份都不會被重置。
        var selectedMonthCode by rememberSaveable {
            mutableIntStateOf(YearMonth.now().let { it.year * 100 + it.monthValue })
        }
        val selectedMonth = remember(selectedMonthCode) {
            YearMonth.of(selectedMonthCode / 100, selectedMonthCode % 100)
        }
        // 獨立的「尋找」頁：明細頁的放大鏡會切到這個全螢幕頁面
        var showSearch by remember { mutableStateOf(false) }
        val snackbarHostState = remember { SnackbarHostState() }

        // 記錄本身是 StateFlow：任何一處寫入（包含通知自動記帳）都會自動重繪，
        // 各頁面自己 collect，這裡只需要資料移轉的結果與自動記錄的確認請求。
        val migration by container.migrationResult.collectAsState()

        // 自動記錄（MPay 等）完成後，等待使用者確認分類／備註的記錄（可能有多筆排隊）
        val pendingAutoRecords by container.pendingAutoRecordPrompts.collectAsState()

        // 「我的」頁的浮球開關：關掉後 App 內與付款 App 上的浮球都不再出現
        // （記錄仍然照常寫入，只是不再有提醒入口）。訂閱 StateFlow 才會在
        // 使用者切換開關的當下就收起已經顯示的圓球。
        val floatingBallEnabled by container.settingsStore.floatingBallEnabledFlow.collectAsState()

        // 圓球浮出後，使用者「按了圓球」才會打開編輯頁；沒按就只是安靜地待著，
        // 記錄本身已經寫入，不會因為使用者不理它而遺失。
        var showAutoRecordEditor by remember { mutableStateOf(false) }
        val autoRecordOpenRequested by container.autoRecordPromptOpenRequest.collectAsState()

        // 通知被點擊（MainActivity 帶著記錄 id 進來）＝使用者已明確要看那一筆，
        // 這時直接開編輯頁，不必讓他再點一次圓球。
        LaunchedEffect(autoRecordOpenRequested, pendingAutoRecords) {
            if (autoRecordOpenRequested && pendingAutoRecords.isNotEmpty()) {
                showAutoRecordEditor = true
                container.consumeAutoRecordPromptOpenRequest()
            }
        }

        // 佇列清空（最後一筆處理完）時把「編輯中」歸零：
        // 下一次自動記錄才會重新浮出圓球，而不是沿用上一輪的狀態。
        LaunchedEffect(pendingAutoRecords.isEmpty()) {
            if (pendingAutoRecords.isEmpty()) showAutoRecordEditor = false
        }

        // 資料移轉完成時提示一次
        LaunchedEffect(migration) {
            val result = migration ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(
                "已從 Flutter 版匯入 ${result.recordCount} 筆記錄、" +
                    "${result.budgetCount} 筆預算",
            )
            container.acknowledgeMigration()
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MikuColors.Background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = MikuColors.Background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        MikuBottomBar(
                            selectedIndex = selectedTab,
                            onSelect = { selectedTab = it },
                            onAddRecord = { editingRecord = null; showAddRecord = true },
                        )
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding()),
                    ) {
                        when (selectedTab) {
                            0 -> BookkeepingScreen(
                                container = container,
                                selectedMonth = selectedMonth,
                                onMonthChange = {
                                    selectedMonthCode = it.year * 100 + it.monthValue
                                },
                                onEditRecord = {
                                    // 兩個編輯器互斥，避免疊在一起
                                    showAddRecord = false
                                    editingRecord = it
                                },
                                onOpenSearch = { showSearch = true },
                            )
                            1 -> BudgetScreen(container)
                            2 -> AnalysisScreen(container)
                            else -> ProfileScreen(container)
                        }
                    }
                }

                // 自動記錄（MPay 等）完成後的圓球：浮在整個 App 的最上層，
                // 點一下＝開啟這筆記錄的編輯頁；不理它記錄也已經存好了。
                // 使用者正在編輯那筆時（showAutoRecordEditor）先收起來，避免重複入口。
                if (floatingBallEnabled && !showAutoRecordEditor && pendingAutoRecords.isNotEmpty()) {
                    AutoRecordBall(
                        pendingCount = pendingAutoRecords.size,
                        onClick = { showAutoRecordEditor = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            // 疊在底部導覽列（72dp）之上，不會壓到明細頁的清單項
                            .padding(end = 16.dp, bottom = BottomBarHeight + 12.dp),
                    )
                }
            }
        }

        // 獨立的「尋找」頁：渲染在底部導覽之上，是一頁自己的畫面
        if (showSearch) {
            SearchScreen(
                container = container,
                onDismiss = { showSearch = false },
                onEditRecord = {
                    // 編輯畫面會疊在尋找頁之上，存檔後回到搜尋結果
                    editingRecord = it
                },
            )
        }

        // 自動記錄後的編輯頁（最上層）：使用者點了圓球（或點了通知）才會出現。
        // 直接關閉＝保留自動分類，因此不需要額外的「略過」按鈕。
        // 一次處理一筆，關閉後自動換佇列中的下一筆（圓球會帶著新的筆數重新浮出）。
        // 一次只建立一個編輯視窗；返回後保留原本的搜尋或頁籤位置。
        val autoRecord = pendingAutoRecords.firstOrNull().takeIf { showAutoRecordEditor }
        when {
            autoRecord != null -> {
                val record = autoRecord
                val remaining = pendingAutoRecords.size - 1
                RecordEditorScreen(
                    container = container,
                    editing = record,
                    title = if (remaining > 0) "已自動記錄（還有 $remaining 筆）" else "已自動記錄",
                    hint = "請確認分類與備註；直接關閉即保留自動分類（${record.note}）",
                    onDismiss = {
                        showAutoRecordEditor = false
                        container.clearAutoRecordPrompt(record.id)
                    },
                )
            }
            editingRecord != null -> RecordEditorScreen(
                container = container,
                editing = editingRecord,
                onDismiss = { editingRecord = null },
            )
            showAddRecord -> RecordEditorScreen(
                container = container,
                onDismiss = { showAddRecord = false },
            )
        }
    }
}

/** 底部導覽列高度。圓球要疊在它正上方，兩處必須用同一個值。 */
private val BottomBarHeight: Dp = 72.dp

/** 自動記錄圓球的直徑；中央新增按鈕獨立放在底部欄內。 */
private val AutoRecordBallSize: Dp = 60.dp

/** 五個按鈕在同一列，中心距由內側 16% 寬向外增至 24% 寬。 */
@Composable
private fun MikuBottomBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAddRecord: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(MikuColors.Background)
            .navigationBarsPadding()
            .height(BottomBarHeight),
    ) {
        val itemWidth = maxWidth * 0.16f
        fun itemAt(center: Float) = Modifier.align(Alignment.CenterStart)
            .offset(x = maxWidth * center - itemWidth / 2).width(itemWidth)
        BottomBarItem(Icons.Filled.Home, "明細", selectedIndex == 0, itemAt(0.10f)) { onSelect(0) }
        BottomBarItem(Icons.AutoMirrored.Filled.MenuBook, "預算", selectedIndex == 1, itemAt(0.34f)) { onSelect(1) }
        MikuFab(onClick = onAddRecord, modifier = Modifier.align(Alignment.Center))
        BottomBarItem(Icons.Filled.Analytics, "分析", selectedIndex == 2, itemAt(0.66f)) { onSelect(2) }
        BottomBarItem(Icons.Filled.Person, "我的", selectedIndex == 3, itemAt(0.90f)) { onSelect(3) }
    }
}

@Composable
private fun BottomBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val tint = if (selected) MikuColors.Primary else MikuColors.TextSecondary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(56.dp)
            .clickableNoRipple(onClick),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MikuFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(44.dp).background(MikuColors.Primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "新增記錄",
                tint = MikuColors.Text,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * 自動記錄完成後浮出的圓球。
 *
 * 為什麼不是直接彈出確認頁：自動記帳是在使用者「正在用別的 App 付款」的當下發生，
 * 把整頁畫面蓋上來會打斷他手上的操作。圓球只在右下角靜靜地呼吸，使用者想補分類
 * 或備註時再點它：
 *   - 點一下 → 開啟這筆記錄的編輯頁（可改分類／備註／金額／日期）
 *   - 不理它 → 記錄已經寫入，不會遺失，圓球會在下次進入 App 時繼續等著
 *
 * 同時有多筆待處理時，右上角顯示筆數徽章（一次只開一筆，關掉後換下一筆）。
 */
@Composable
private fun AutoRecordBall(
    pendingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 輕輕的呼吸動畫（1.0 → 1.08）。用 draw 階段的縮放，佈局尺寸不變，
    // 所以可點範圍仍然是完整的 AutoRecordBallSize（不會隨動畫忽大忽小）。
    val transition = rememberInfiniteTransition(label = "autoRecordBall")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "autoRecordBallScale",
    )

    Box(modifier = modifier.size(AutoRecordBallSize)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .background(MikuColors.Primary, CircleShape)
                .clickableNoRipple(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.EditNote,
                contentDescription = if (pendingCount > 1) {
                    "已自動記錄 $pendingCount 筆，點一下編輯"
                } else {
                    "已自動記錄，點一下編輯這筆記錄"
                },
                tint = MikuColors.Text,
                modifier = Modifier.size(28.dp),
            )
        }

        if (pendingCount > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // 往內縮 2dp：徽章貼在圓球的右上角邊緣，不會看起來像飄在外面
                    .padding(2.dp)
                    .size(24.dp)
                    .background(MikuColors.Expense, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (pendingCount > 9) "9+" else pendingCount.toString(),
                    color = MikuColors.Text,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
