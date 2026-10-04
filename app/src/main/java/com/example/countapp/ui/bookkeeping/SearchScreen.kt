package com.example.countapp.ui.bookkeeping

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.AppContainer
import com.example.countapp.data.Record
import com.example.countapp.domain.RecordSearch
import com.example.countapp.ui.EmptyState
import com.example.countapp.ui.MinTouchTarget
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors

/** 沒輸入關鍵字時，先顯示最近幾筆讓使用者有個起點。 */
private const val RECENT_PREVIEW_COUNT = 20

/**
 * 尋找頁（獨立頁面）。
 *
 * 明細頁上方 bar 的放大鏡會切到這一頁：這裡**不分月份**搜尋使用者的全部記錄
 * （分類、備註、金額、日期都可比對），點結果直接開編輯畫面。
 */
@Composable
fun SearchScreen(
    container: AppContainer,
    onDismiss: () -> Unit,
    onEditRecord: (Record) -> Unit,
) {
    val allRecords by container.recordRepository.records.collectAsState()
    val categoryRevision by container.categoryStore.revision.collectAsState()
    val categoryLookup = remember(categoryRevision) { container.categoryStore.recordCategoryLookup() }

    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val keyword = query.trim()
    val results = remember(allRecords, keyword) { RecordSearch.query(allRecords, keyword) }
    val previewing = keyword.isEmpty()

    // 返回鍵＝離開尋找頁（回到明細）
    BackHandler { onDismiss() }

    // 進頁面就把焦點放到輸入框，直接打字
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MikuColors.Background)
            // ⚠️ 這一頁是「獨立頁面」，蓋住底部導覽與中央 FAB。
            //    Compose 只會在上層節點真的命中 pointerInput 時停止往下層兄弟搜尋，
            //    純 background 不算命中——空白處（例如沒有結果時）的點擊會穿透到
            //    底下的 FAB。加了這行才是真正的獨立頁面。
            .pointerInput(Unit) {}
            .imePadding(),
    ) {
        // ===== 搜尋列 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 返回（48dp 觸控）
            Box(
                modifier = Modifier
                    .size(MinTouchTarget)
                    .clickableNoRipple(onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MikuColors.Text,
                    modifier = Modifier.size(24.dp),
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                placeholder = {
                    Text("搜尋分類、備註、金額或日期", color = MikuColors.TextSecondary, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MikuColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MikuColors.Text,
                    unfocusedTextColor = MikuColors.Text,
                ),
            )

            // 清除（有輸入才出現，48dp 觸控）
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(MinTouchTarget)
                        .clickableNoRipple { query = "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清除",
                        tint = MikuColors.Text,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // ===== 結果摘要 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (previewing) {
                    "最近的記錄"
                } else {
                    "找到 ${results.size} 筆"
                },
                color = MikuColors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (previewing) "輸入關鍵字可搜尋全部記錄" else "「$keyword」",
                color = MikuColors.TextSecondary,
                fontSize = 11.sp,
            )
        }

        // ===== 結果清單 =====
        val shown = if (previewing) results.take(RECENT_PREVIEW_COUNT) else results

        if (shown.isEmpty()) {
            EmptyState(
                message = if (previewing) "還沒有任何記錄" else "找不到「$keyword」相關的記錄",
                hint = if (previewing) "回到明細頁按 ＋ 新增記錄" else "試試分類名稱、備註、金額或日期",
            )
        } else {
            LazyColumn(
                // 最後一筆結果不要落在系統導覽列底下（edge-to-edge）
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            ) {
                items(shown, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        category = categoryLookup[record.isExpense to record.category],
                        showDate = true,
                        onClick = { onEditRecord(record) },
                    )
                }
            }
        }
    }
}
