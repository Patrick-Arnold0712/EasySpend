package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.finance.CategoryBudgetStatus
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.OverspendRiskLevel
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors
import kotlin.math.atan2
import kotlin.math.hypot

private val lightChartPalette = listOf(
    Color(0xFF1F7A63),
    Color(0xFF3A6EA5),
    Color(0xFFD4822A),
    Color(0xFF5D737E),
    Color(0xFFC94A4A),
    Color(0xFF7C6B9E),
    Color(0xFF2B8A9C),
    Color(0xFFB85D7A),
    Color(0xFF6B8E23),
    Color(0xFF8C6239),
    Color(0xFF4B6CB7),
    Color(0xFF4F9D88)
)

private val darkChartPalette = listOf(
    Color(0xFF4CC9A4),
    Color(0xFF70A9EA),
    Color(0xFFFFB347),
    Color(0xFF8FA7B5),
    Color(0xFFFF7A7A),
    Color(0xFFA794CE),
    Color(0xFF5AC8D8),
    Color(0xFFE88AA8),
    Color(0xFF9CCB5C),
    Color(0xFFD4A373),
    Color(0xFF7C9CE6),
    Color(0xFF6CD6BA)
)

@Composable
fun chartCategoryPalette(): List<Color> {
    return if (MaterialTheme.semanticColors.isDark) darkChartPalette else lightChartPalette
}

@Composable
fun CategoryDonutChart(
    statuses: List<CategoryBudgetStatus>,
    netExpenseFen: Long,
    modifier: Modifier = Modifier,
    selectedCategoryId: String? = null,
    onSelectCategory: ((String?) -> Unit)? = null
) {
    val active = remember(statuses) {
        statuses.filter { it.spentFen > 0L }.sortedByDescending { it.spentFen }
    }
    val totalSpent = remember(active) { active.sumOf { it.spentFen }.coerceAtLeast(1L) }
    val semantic = MaterialTheme.semanticColors
    val palette = chartCategoryPalette()

    val sweepProgress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        sweepProgress.snapTo(0f)
        sweepProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
        )
    }

    val selectedItem = remember(active, selectedCategoryId) {
        active.firstOrNull { it.categoryId == selectedCategoryId }
    }

    val sliceAngles = remember(active, totalSpent) {
        var cursor = 0f
        active.map { item ->
            val sweep = (item.spentFen.toFloat() / totalSpent.toFloat()) * 360f
            val range = cursor to (cursor + sweep)
            cursor += sweep
            range
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(116.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(110.dp)
                    .testTag("donut_canvas")
                    .then(
                        if (onSelectCategory != null && active.isNotEmpty()) {
                            Modifier.pointerInput(active, selectedCategoryId) {
                                detectTapGestures { offset ->
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val dx = offset.x - cx
                                    val dy = offset.y - cy
                                    val dist = hypot(dx, dy)
                                    val outerR = size.width / 2f
                                    val innerR = outerR * 0.42f
                                    if (dist in innerR..outerR) {
                                        // Convert to degrees starting from top (-90 deg) clockwise [0..360)
                                        var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                        if (deg < 0f) deg += 360f
                                        val hitIdx = sliceAngles.indexOfFirst { (start, end) -> deg in start..end }
                                        if (hitIdx >= 0) {
                                            val hitId = active[hitIdx].categoryId
                                            onSelectCategory(if (selectedCategoryId == hitId) null else hitId)
                                        }
                                    } else {
                                        onSelectCategory(null)
                                    }
                                }
                            }
                        } else Modifier
                    )
            ) {
                val baseStroke = 14.dp.toPx()
                val activeStroke = 18.dp.toPx()
                val maxStroke = activeStroke
                val arcSize = Size(size.width - maxStroke, size.height - maxStroke)
                val topLeft = Offset(maxStroke / 2f, maxStroke / 2f)

                if (active.isEmpty()) {
                    drawArc(
                        color = semantic.trackBackground,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = baseStroke)
                    )
                } else {
                    var startAngle = -90f
                    active.forEachIndexed { idx, item ->
                        val rawSweep = (item.spentFen.toFloat() / totalSpent.toFloat()) * 360f
                        val isSelected = selectedCategoryId == item.categoryId
                        val dimOthers = selectedCategoryId != null && !isSelected
                        val baseColor = palette[idx % palette.size]
                        val sliceColor = if (dimOthers) baseColor.copy(alpha = 0.30f) else baseColor
                        val animatedSweep = ((rawSweep - 2.5f).coerceAtLeast(2.5f)) * sweepProgress.value

                        drawArc(
                            color = sliceColor,
                            startAngle = startAngle,
                            sweepAngle = animatedSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = if (isSelected) activeStroke else baseStroke,
                                cap = StrokeCap.Round
                            )
                        )
                        startAngle += rawSweep
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 10.dp)
            ) {
                if (selectedItem != null) {
                    val pct = ((selectedItem.spentFen * 100) / totalSpent).toInt()
                    Text(
                        text = selectedItem.categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = MoneyFormatter.formatFen(selectedItem.spentFen),
                        style = NumericSmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = "占 $pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "本月已花",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = MoneyFormatter.formatFen(netExpenseFen),
                        style = NumericSmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (active.isEmpty()) {
                Text(
                    text = "还没有消费记录。点「载入体验账单」或手动记一笔，就能看到花销分布。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                active.take(6).forEachIndexed { idx, item ->
                    val pct = ((item.spentFen * 100) / totalSpent).toInt()
                    val isSelected = selectedCategoryId == item.categoryId
                    val rowBg = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else Color.Transparent

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(rowBg)
                            .then(
                                if (onSelectCategory != null) {
                                    Modifier
                                        .clickable {
                                            onSelectCategory(if (isSelected) null else item.categoryId)
                                        }
                                        .testTag("donut_legend_${item.categoryId}")
                                } else Modifier
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(palette[idx % palette.size])
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = item.categoryName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = MoneyFormatter.formatFen(item.spentFen),
                            style = NumericSmallStyle.copy(fontSize = 12.sp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySpendingBarChart(
    statuses: List<CategoryBudgetStatus>,
    selectedCategoryId: String?,
    onSelectCategory: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(statuses) {
        statuses.sortedByDescending { maxOf(it.spentFen, 0L) }
    }
    val totalSpent = remember(items) { items.sumOf { it.spentFen }.coerceAtLeast(1L) }
    val maxScaleFen = remember(items) {
        items.maxOfOrNull { maxOf(it.spentFen, it.limitFen) }?.coerceAtLeast(10000L) ?: 10000L
    }
    val semantic = MaterialTheme.semanticColors
    val palette = chartCategoryPalette()

    val fillProgress = remember { Animatable(0f) }
    LaunchedEffect(items) {
        fillProgress.snapTo(0f)
        fillProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
        )
    }

    if (items.isEmpty()) {
        Text(
            text = "当前筛选条件下暂无消费分类数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bar_chart_container"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Scale Legend Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp, 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text("已花费", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(2.dp, 10.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    )
                    Text("预算参考线", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Text(
                text = "上限 ${MoneyFormatter.formatFen(maxScaleFen)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        items.forEachIndexed { idx, status ->
            val isSelected = selectedCategoryId == status.categoryId
            val barColor = if (status.spentFen > status.limitFen && status.limitFen > 0L) {
                semantic.danger
            } else {
                palette[idx % palette.size]
            }
            val pct = ((status.spentFen * 100) / totalSpent).toInt()
            val rowSurfaceColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                Color.Transparent
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(rowSurfaceColor)
                    .clickable {
                        onSelectCategory(if (isSelected) null else status.categoryId)
                    }
                    .testTag("bar_row_${status.categoryId}")
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(barColor)
                        )
                        Text(
                            text = status.categoryName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            color = if (status.isEssential) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (status.isEssential) "必要" else "弹性",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (status.isEssential) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(
                        text = "${MoneyFormatter.formatFen(status.spentFen)} ($pct%)",
                        style = NumericSmallStyle.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (status.spentFen > status.limitFen && status.limitFen > 0L) {
                            semantic.danger
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1
                    )
                }

                // Custom Canvas Horizontal Bar with Budget Reference Tick & Dashed Grid
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                ) {
                    val corner = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    val trackHeight = 10.dp.toPx()
                    val trackTop = (size.height - trackHeight) / 2f

                    // 1. Track Background
                    drawRoundRect(
                        color = semantic.trackBackground,
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width, trackHeight),
                        cornerRadius = corner
                    )

                    // 2. Dashed grid markers at 25%, 50%, 75%
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    listOf(0.25f, 0.5f, 0.75f).forEach { grid ->
                        val gx = size.width * grid
                        drawLine(
                            color = semantic.hairline.copy(alpha = 0.7f),
                            start = Offset(gx, trackTop),
                            end = Offset(gx, trackTop + trackHeight),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }

                    // 3. Soft Budget Allowance Band
                    val budgetRatio = (status.limitFen.toFloat() / maxScaleFen.toFloat()).coerceIn(0f, 1f)
                    if (budgetRatio > 0f) {
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.16f),
                            topLeft = Offset(0f, trackTop),
                            size = Size(size.width * budgetRatio, trackHeight),
                            cornerRadius = corner
                        )
                    }

                    // 4. Animated Actual Spending Fill
                    val spentRatio = (status.spentFen.toFloat() / maxScaleFen.toFloat()).coerceIn(0f, 1f) * fillProgress.value
                    if (spentRatio > 0f) {
                        val barAlpha = if (selectedCategoryId != null && !isSelected) 0.4f else 1f
                        drawRoundRect(
                            color = barColor.copy(alpha = barAlpha),
                            topLeft = Offset(0f, trackTop),
                            size = Size((size.width * spentRatio).coerceAtLeast(4.dp.toPx()), trackHeight),
                            cornerRadius = corner
                        )
                    }

                    // 5. Crisp Vertical Budget Cap Marker Line
                    if (budgetRatio > 0f) {
                        val markerX = (size.width * budgetRatio).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
                        drawLine(
                            color = if (status.spentFen > status.limitFen) semantic.danger else semantic.hairline.copy(alpha = 0.95f),
                            start = Offset(markerX, 0f),
                            end = Offset(markerX, size.height),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EssentialVsFlexibleRatioBar(
    essentialFen: Long,
    flexibleFen: Long,
    modifier: Modifier = Modifier
) {
    val total = (essentialFen + flexibleFen).coerceAtLeast(1L)
    val essentialRatio = if (essentialFen + flexibleFen == 0L) 0.5f else essentialFen.toFloat() / total.toFloat()
    val essentialPct = if (essentialFen + flexibleFen == 0L) 0 else (essentialRatio * 100).toInt()
    val flexiblePct = if (essentialFen + flexibleFen == 0L) 0 else (100 - essentialPct)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.semanticColors.trackBackground

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "必要支出 ${MoneyFormatter.formatFen(essentialFen)} ($essentialPct%)",
                style = MaterialTheme.typography.labelMedium,
                color = primaryColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "弹性支出 ${MoneyFormatter.formatFen(flexibleFen)} ($flexiblePct%)",
                style = MaterialTheme.typography.labelMedium,
                color = secondaryColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .testTag("essential_flexible_ratio_bar")
        ) {
            if (essentialFen + flexibleFen == 0L) {
                drawRect(color = trackColor)
            } else {
                val essWidth = size.width * essentialRatio.coerceIn(0f, 1f)
                val gap = if (essentialFen > 0L && flexibleFen > 0L) 2.dp.toPx() else 0f
                if (essWidth > 0f) {
                    drawRect(
                        color = primaryColor,
                        size = Size((essWidth - gap / 2f).coerceAtLeast(0f), size.height)
                    )
                }
                if (size.width - essWidth > 0f) {
                    drawRect(
                        color = secondaryColor,
                        topLeft = Offset(essWidth + gap / 2f, 0f),
                        size = Size((size.width - essWidth - gap / 2f).coerceAtLeast(0f), size.height)
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetProgressBar(
    status: CategoryBudgetStatus,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.semanticColors
    val barColor = riskLevelColor(status.riskLevel)
    val actualRatio = status.usageRatio.coerceIn(0f, 1f)
    val projectedRatio = if (status.limitFen > 0L) {
        (status.projectedEndOfMonthFen.toFloat() / status.limitFen.toFloat()).coerceIn(0f, 1.25f)
    } else 0f

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            drawRect(color = semantic.trackBackground)
            if (projectedRatio > actualRatio) {
                val projWidth = size.width * projectedRatio.coerceAtMost(1f)
                drawRect(
                    color = barColor.copy(alpha = 0.25f),
                    size = Size(projWidth, size.height)
                )
            }
            val actWidth = size.width * actualRatio
            drawRect(
                color = barColor,
                size = Size(actWidth, size.height)
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.semanticColors
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun riskLevelColor(risk: OverspendRiskLevel): Color {
    val semantic = MaterialTheme.semanticColors
    return when (risk) {
        OverspendRiskLevel.SAFE -> semantic.safe
        OverspendRiskLevel.WARNING -> semantic.warning
        OverspendRiskLevel.HIGH_RISK -> if (semantic.isDark) Color(0xFFFF8C42) else Color(0xFFD95D14)
        OverspendRiskLevel.OVER_BUDGET -> semantic.danger
    }
}
