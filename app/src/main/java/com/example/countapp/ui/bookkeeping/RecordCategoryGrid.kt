package com.example.countapp.ui.bookkeeping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.domain.CategoryItem
import com.example.countapp.ui.CategoryIcons
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors
import com.example.countapp.ui.theme.toComposeColor

/** 分類網格只佔剩餘高度，避免與固定鍵盤共用捲動。 */
@Composable
internal fun RecordCategoryGrid(
    categories: List<CategoryItem>,
    selectedName: String?,
    modifier: Modifier,
    onSelect: (String) -> Unit,
    onCategorySettings: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        items(categories, key = { "category:${it.name}" }) { category ->
            CategoryCell(category, category.name == selectedName, onClick = { onSelect(category.name) })
        }
        item(key = "action:category_settings") { CategorySettingsCell(onClick = onCategorySettings) }
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
private fun CategorySettingsCell(
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
                imageVector = Icons.Filled.Settings,
                contentDescription = "類別設定",
                tint = MikuColors.Text,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text("設定", color = MikuColors.Text, fontSize = 10.sp)
    }
}

