package com.example.countapp.ui.bookkeeping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.data.Record
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.domain.CategoryItem
import com.example.countapp.ui.CategoryIcons
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors
import com.example.countapp.ui.theme.toComposeColor

/**
 * 記錄列的垂直留白。
 *
 * 滑動時露出的動作方塊必須用同一個值，否則收合狀態下會在卡片上下露出底色。
 */
val RecordCardVerticalGap: Dp = 3.dp

/**
 * 單筆記錄卡片（明細頁與「尋找」頁共用）。
 *
 * @param showDate 尋找頁需要顯示日期（明細頁已經有日期分組標題，不用重複）。
 */
@Composable
fun RecordCard(
    record: Record,
    category: CategoryItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
) {
    val iconKey = category?.iconKey ?: "label"
    val color = (category?.colorArgb ?: 0xFF9E9E9E).toComposeColor()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = RecordCardVerticalGap)
            .clickableNoRipple(onClick)
            .background(MikuColors.Card, RoundedCornerShape(10.dp))
            // 垂直內距由 10dp 縮到 8dp（水平維持 10dp），讓卡片豎直方向更矮。
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                // 左側圖示由 36dp 縮到 32dp，同時決定卡片的最小高度（見下方高度說明）。
                .size(32.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategoryIcons.forKey(iconKey),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // 注意：MaterialTheme 的 LocalTextStyle 是 bodyLarge（lineHeight = 24.sp），
            // 只給 fontSize 會沿用 24.sp 的行高，卡片就會被撐高。
            // 這裡逐個指定 lineHeight，讓每一行的高度與字級相稱（卡片豎直方向才會縮小）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.category,
                    color = MikuColors.OnCard,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(MikuColors.Primary.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = if (record.isExpense) "支出" else "收入",
                        color = MikuColors.Primary,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (showDate) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Record.formatDate(record.localDate()),
                        color = MikuColors.OnCardSecondary,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    )
                }
            }
            if (record.note.isNotEmpty()) {
                // 備註最多一行：太長以「…」省略，卡片不會再因為備註變成兩行高。
                // lineHeight 14.sp（字級 11.sp）本身就含上下留白，取代原本的 Spacer 行距，
                // 因此「有備註」相對「無備註」只多出剛好一行的高度。
                Text(
                    text = record.note,
                    color = MikuColors.OnCardSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 右側只留金額：編輯筆與按鈕已移除（點卡片或左滑的【編輯】方塊都能編輯）
        Text(
            text = (if (record.isExpense) "-" else "") + AmountFormatter.format(record.absoluteAmount),
            color = if (record.isExpense) MikuColors.Expense else MikuColors.Income,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
