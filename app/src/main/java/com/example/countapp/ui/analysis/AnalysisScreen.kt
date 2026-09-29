package com.example.countapp.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countapp.AppContainer
import com.example.countapp.domain.AmountFormatter
import com.example.countapp.domain.CategoryCatalog
import com.example.countapp.domain.CategoryItem
import com.example.countapp.ui.CategoryIcons
import com.example.countapp.ui.EmptyState
import com.example.countapp.ui.MinTouchTarget
import com.example.countapp.ui.clickableNoRipple
import com.example.countapp.ui.theme.MikuColors
import com.example.countapp.ui.theme.toComposeColor
import kotlin.math.cos
import kotlin.math.sin

/** 單一分類的統計結果。 */
private data class CategoryStat(
    val name: String,
    val amount: Double,
    val percentage: Double,
    val color: Color,
    val iconKey: String,
)

/**
 * 分析頁。
 *
 * 對應 Flutter 版的 `analysis_page.dart`：
 * 支出／收入切換、圓環圖、分類佔比清單。
 */
@Composable
fun AnalysisScreen(container: AppContainer) {
    val records by container.recordRepository.records.collectAsState()
    var selectedType by remember { mutableStateOf(CategoryCatalog.TYPE_EXPENSE) }

    val filtered = remember(records, selectedType) {
        if (selectedType == CategoryCatalog.TYPE_EXPENSE) {
            records.filter { it.amount < 0 }
        } else {
            records.filter { it.amount > 0 }
        }
    }

    // 分類查表（預設 + 使用者自訂）。
    //
    // CategoryStore.categoriesFor() 每次呼叫都會重新解析 SharedPreferences 裡的 JSON，
    // 若在圖表／清單迴圈中逐筆呼叫會很浪費效能；這裡先用 remember 建立一次
    // 「名稱 → 分類」的快取，迴圈內只做 Map 查表。
    // 同名分類的優先順序與 CategoryStore.findAny() 一致：先支出、後收入。
    val categoryLookup = remember(records) {
        val lookup = linkedMapOf<String, CategoryItem>()
        CategoryCatalog.TYPES.forEach { type ->
            container.categoryStore.categoriesFor(type).forEach { item ->
                if (!lookup.containsKey(item.name)) lookup[item.name] = item
            }
        }
        lookup
    }

    val stats = remember(filtered, categoryLookup) { buildStats(filtered, categoryLookup) }
    val total = stats.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "分析",
            color = MikuColors.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.statusBarsPadding().padding(16.dp),
        )

        // ===== 支出 / 收入 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(MinTouchTarget)
                .background(MikuColors.Surface, RoundedCornerShape(8.dp)),
        ) {
            CategoryCatalog.TYPES.forEach { type ->
                val selected = type == selectedType
                // 可點範圍是整個 48dp 高的半格（原本只有 32dp）；
                // 視覺藥丸再用 2dp 內縮畫出來，外觀比例與原本一致。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickableNoRipple { selectedType = type },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .background(
                                if (selected) MikuColors.Primary else Color.Transparent,
                                RoundedCornerShape(6.dp),
                            ),
                    )
                    Text(
                        text = type,
                        color = if (selected) MikuColors.Text else MikuColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (stats.isEmpty()) {
            EmptyState(
                message = "尚無$selectedType 記錄",
                hint = "新增記錄後即可看到分類分析",
            )
            return@Column
        }

        // ===== 總計 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$selectedType 總計：", color = MikuColors.TextSecondary, fontSize = 13.sp)
            Text(
                text = "\$${AmountFormatter.format(total)}",
                color = MikuColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ===== 圓環圖 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            DonutChart(stats = stats)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("總計", color = MikuColors.TextSecondary, fontSize = 12.sp)
                Text(
                    text = "\$${AmountFormatter.format(total)}",
                    color = MikuColors.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 分類明細 =====
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            stats.forEach { stat -> StatRow(stat) }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
private fun DonutChart(stats: List<CategoryStat>) {
    val colors = stats.map { it.color }
    val sweeps = stats.map { (it.percentage / 100.0 * 360.0).toFloat() }

    // 文字量測器必須在 composition 階段建立（rememberTextMeasurer），
    // 不能在 draw lambda 內建立，否則每一帧都會重建一次。
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = size.minDimension * 0.22f
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f,
        )
        val arcSize = Size(diameter, diameter)

        // ===== 圓環本體 =====
        // 每個扇形左右各讓出一半的縫隙角，讓相鄰分類看起來是獨立區塊；
        // 角度累計（startAngle）仍然用「未扣縫隙」的原始 sweep，
        // 這樣後續標籤用原始累計角度算中線時，位置才不會偏移。
        val gapAngle = 1.5f

        var startAngle = -90f
        sweeps.forEachIndexed { index, sweep ->
            val hasGap = sweep > gapAngle
            drawArc(
                color = colors[index],
                startAngle = if (hasGap) startAngle + gapAngle / 2f else startAngle,
                sweepAngle = if (hasGap) sweep - gapAngle else sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }

        // ===== 各扇形佔比標註 =====
        // drawArc + Stroke 是沿著橢圓「路徑」置中描邊，因此圓環色帶的
        // 徑向中心就是路徑半徑（diameter / 2），標籤放在這裡剛好落在色帶上。
        val bandRadius = diameter / 2f
        val center = Offset(topLeft.x + bandRadius, topLeft.y + bandRadius)

        // 佔比太小的扇形不放標籤，否則只會擠成一團看不懂的雜訊。
        val minLabelPercentage = 4.0

        var cumulativeAngle = -90f
        stats.forEachIndexed { index, stat ->
            val sweep = sweeps[index]
            // 用「未扣縫隙」的原始角度計算扇形中線。
            val midAngle = cumulativeAngle + sweep / 2f
            cumulativeAngle += sweep

            if (stat.percentage < minLabelPercentage) return@forEachIndexed

            val text = String.format(java.util.Locale.US, "%.1f%%", stat.percentage)
            // 依色帶亮度決定文字顏色。
            // 門檻用 ≈0.18 而不是 0.5：那是「白字與黑字對比度相等」的 crossover，
            // 用 0.5 會讓中間亮度的分類色（例如靛藍、紫、灰色）配到偏淡的白字。
            val style = TextStyle(
                color = if (stat.color.luminance() > 0.18f) Color.Black else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val measured = textMeasurer.measure(text = text, style = style)

            // 扇形在色帶中心處的弦長（近似可用的水平空間）；
            // 文字比弦長還寬就代表這個扇形塞不下，直接略過避免互相重疊。
            val halfSweepRadians = Math.toRadians((sweep / 2f).toDouble())
            val chord = (2.0 * bandRadius * sin(halfSweepRadians)).toFloat()
            if (measured.size.width > chord) return@forEachIndexed

            val midRadians = Math.toRadians(midAngle.toDouble())
            val labelCenter = Offset(
                x = center.x + (cos(midRadians) * bandRadius).toFloat(),
                y = center.y + (sin(midRadians) * bandRadius).toFloat(),
            )

            drawText(
                textMeasurer = textMeasurer,
                text = text,
                topLeft = Offset(
                    x = labelCenter.x - measured.size.width / 2f,
                    y = labelCenter.y - measured.size.height / 2f,
                ),
                style = style,
            )
        }
    }
}

@Composable
private fun StatRow(stat: CategoryStat) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(stat.color.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CategoryIcons.forKey(stat.iconKey),
                    contentDescription = null,
                    tint = stat.color,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = stat.name,
                color = MikuColors.Text,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = "\$${AmountFormatter.format(stat.amount)}",
                color = MikuColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = String.format(java.util.Locale.US, "%.1f%%", stat.percentage),
                color = MikuColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.width(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MikuColors.Surface, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((stat.percentage / 100.0).toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(stat.color, RoundedCornerShape(3.dp)),
            )
        }
    }
}

/**
 * 依分類彙總並計算百分比（由大到小排序）。
 *
 * [categoryLookup] 是呼叫端用 remember 建好的「名稱 → 分類」快取（含自訂分類），
 * 避免在這裡逐筆重新解析 SharedPreferences。
 */
private fun buildStats(
    records: List<com.example.countapp.data.Record>,
    categoryLookup: Map<String, CategoryItem>,
): List<CategoryStat> {
    if (records.isEmpty()) return emptyList()

    val sums = linkedMapOf<String, Double>()
    records.forEach { record ->
        sums[record.category] = (sums[record.category] ?: 0.0) + record.absoluteAmount
    }

    val total = sums.values.sum()
    if (total <= 0) return emptyList()

    return sums.entries
        .map { (name, amount) ->
            // 改查快取表而不是 CategoryCatalog.find()：後者只看預設分類，
            // 會讓使用者自建的分類退成灰色 label 圖示。
            val info = categoryLookup[name]
            CategoryStat(
                name = name,
                amount = amount,
                percentage = amount / total * 100.0,
                color = (info?.colorArgb ?: 0xFF9E9E9E).toComposeColor(),
                iconKey = info?.iconKey ?: "label",
            )
        }
        .sortedByDescending { it.amount }
}
