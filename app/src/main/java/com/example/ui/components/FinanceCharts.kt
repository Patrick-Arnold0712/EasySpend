package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.finance.CategoryBudgetStatus
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.OverspendRiskLevel
import com.example.ui.motion.EasySpendMotion
import com.example.ui.motion.TonalIconBadge
import com.example.ui.motion.springClickable
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val lightChartPalette = listOf(
    Color(0xFF0F6E56),
    Color(0xFF2D5F9A),
    Color(0xFFD17A22),
    Color(0xFF56707D),
    Color(0xFFC84545),
    Color(0xFF7461A0),
    Color(0xFF21869A),
    Color(0xFFB45576),
    Color(0xFF638A22),
    Color(0xFF8C6239),
    Color(0xFF4769B8),
    Color(0xFF3E9A84)
)

private val darkChartPalette = listOf(
    Color(0xFF3DE0A8),
    Color(0xFF72AEF2),
    Color(0xFFFFB54C),
    Color(0xFF8FA9B8),
    Color(0xFFFF7575),
    Color(0xFFAE9AD6),
    Color(0xFF59CCE0),
    Color(0xFFEB8DAD),
    Color(0xFFA0D15E),
    Color(0xFFD8A776),
    Color(0xFF7FA0EC),
    Color(0xFF6ADBBF)
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
    val haptic = LocalHapticFeedback.current

    val sweepProgress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        sweepProgress.snapTo(0f)
        sweepProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 185f)
        )
    }

    val selectedPop by animateFloatAsState(
        targetValue = if (selectedCategoryId != null) 1f else 0f,
        animationSpec = EasySpendMotion.SnappySpring,
        label = "donutSelectedPop"
    )

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
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(124.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(118.dp)
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
                                    val innerR = outerR * 0.40f
                                    if (dist in innerR..outerR) {
                                        var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                        if (deg < 0f) deg += 360f
                                        val hitIdx = sliceAngles.indexOfFirst { (start, end) -> deg in start..end }
                                        if (hitIdx >= 0) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                val baseStroke = 13.dp.toPx()
                val activeStroke = 17.dp.toPx()
                val popMargin = 4.dp.toPx()
                val inset = activeStroke / 2f + popMargin
                val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                val baseTopLeft = Offset(inset, inset)

                // Subtle background ring track
                drawArc(
                    color = semantic.trackBackground.copy(alpha = 0.55f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = baseTopLeft,
                    size = arcSize,
                    style = Stroke(width = baseStroke)
                )

                if (active.isNotEmpty()) {
                    var startAngle = -90f
                    active.forEachIndexed { idx, item ->
                        val rawSweep = (item.spentFen.toFloat() / totalSpent.toFloat()) * 360f
                        val isSelected = selectedCategoryId == item.categoryId
                        val dimOthers = selectedCategoryId != null && !isSelected
                        val baseColor = palette[idx % palette.size]
                        val sliceColor = if (dimOthers) baseColor.copy(alpha = 0.26f) else baseColor
                        val animatedSweep = ((rawSweep - 3f).coerceAtLeast(2.5f)) * sweepProgress.value

                        val midAngleRad = ((startAngle + rawSweep / 2f) * PI / 180.0)
                        val popOffset = if (isSelected) popMargin * 0.85f * selectedPop else 0f
                        val sliceTopLeft = Offset(
                            x = baseTopLeft.x + (cos(midAngleRad) * popOffset).toFloat(),
                            y = baseTopLeft.y + (sin(midAngleRad) * popOffset).toFloat()
                        )
                        val strokeW = if (isSelected) {
                            baseStroke + (activeStroke - baseStroke) * selectedPop
                        } else {
                            baseStroke
                        }

                        drawArc(
                            color = sliceColor,
                            startAngle = startAngle,
                            sweepAngle = animatedSweep,
                            useCenter = false,
                            topLeft = sliceTopLeft,
                            size = arcSize,
                            style = Stroke(
                                width = strokeW,
                                cap = StrokeCap.Round
                            )
                        )
                        startAngle += rawSweep
                    }
                }
            }

            AnimatedContent(
                targetState = selectedItem,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(initialScale = 0.92f, animationSpec = tween(180))) togetherWith
                        (fadeOut(tween(120)) + scaleOut(targetScale = 0.92f, animationSpec = tween(120)))
                },
                label = "donutCenterReadout"
            ) { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    if (item != null) {
                        val pct = ((item.spentFen * 100) / totalSpent).toInt()
                        Text(
                            text = item.categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = MoneyFormatter.formatFen(item.spentFen),
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
                    val rowBg by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            Color.Transparent
                        },
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        label = "donutLegendBg"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(rowBg)
                            .then(
                                if (onSelectCategory != null) {
                                    Modifier
                                        .springClickable(pressedScale = 0.98f) {
                                            onSelectCategory(if (isSelected) null else item.categoryId)
                                        }
                                        .testTag("donut_legend_${item.categoryId}")
                                } else Modifier
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
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
                                        .size(if (isSelected) 9.dp else 7.dp)
                                        .clip(CircleShape)
                                        .background(palette[idx % palette.size])
                                )
                                Spacer(Modifier.width(7.dp))
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
                            modifier = Modifier.padding(start = 15.dp)
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
            animationSpec = EasySpendMotion.FluidSpring
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
            val rowSurfaceColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    Color.Transparent
                },
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                label = "barRowBg"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(rowSurfaceColor)
                    .springClickable(pressedScale = 0.985f) {
                        onSelectCategory(if (isSelected) null else status.categoryId)
                    }
                    .testTag("bar_row_${status.categoryId}")
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            shape = RoundedCornerShape(6.dp)
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

                    drawRoundRect(
                        color = semantic.trackBackground,
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width, trackHeight),
                        cornerRadius = corner
                    )

                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    listOf(0.25f, 0.5f, 0.75f).forEach { grid ->
                        val gx = size.width * grid
                        drawLine(
                            color = semantic.hairline.copy(alpha = 0.65f),
                            start = Offset(gx, trackTop),
                            end = Offset(gx, trackTop + trackHeight),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }

                    val budgetRatio = (status.limitFen.toFloat() / maxScaleFen.toFloat()).coerceIn(0f, 1f)
                    if (budgetRatio > 0f) {
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.16f),
                            topLeft = Offset(0f, trackTop),
                            size = Size(size.width * budgetRatio, trackHeight),
                            cornerRadius = corner
                        )
                    }

                    val spentRatio = (status.spentFen.toFloat() / maxScaleFen.toFloat()).coerceIn(0f, 1f) * fillProgress.value
                    if (spentRatio > 0f) {
                        val barAlpha = if (selectedCategoryId != null && !isSelected) 0.35f else 1f
                        drawRoundRect(
                            color = barColor.copy(alpha = barAlpha),
                            topLeft = Offset(0f, trackTop),
                            size = Size((size.width * spentRatio).coerceAtLeast(4.dp.toPx()), trackHeight),
                            cornerRadius = corner
                        )
                    }

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
    val targetRatio = if (essentialFen + flexibleFen == 0L) 0.5f else essentialFen.toFloat() / total.toFloat()
    val essentialRatio by animateFloatAsState(
        targetValue = targetRatio.coerceIn(0f, 1f),
        animationSpec = EasySpendMotion.FluidSpring,
        label = "essentialFlexibleRatio"
    )
    val essentialPct = if (essentialFen + flexibleFen == 0L) 0 else (targetRatio * 100).toInt()
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
                val gap = if (essentialFen > 0L && flexibleFen > 0L) 3.dp.toPx() else 0f
                val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                if (essWidth > 0f) {
                    drawRoundRect(
                        color = primaryColor,
                        size = Size((essWidth - gap / 2f).coerceAtLeast(0f), size.height),
                        cornerRadius = radius
                    )
                }
                if (size.width - essWidth > 0f) {
                    drawRoundRect(
                        color = secondaryColor,
                        topLeft = Offset(essWidth + gap / 2f, 0f),
                        size = Size((size.width - essWidth - gap / 2f).coerceAtLeast(0f), size.height),
                        cornerRadius = radius
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
    val barColor by animateColorAsState(
        targetValue = riskLevelColor(status.riskLevel),
        animationSpec = tween(220),
        label = "budgetBarColor"
    )
    val animatedActual by animateFloatAsState(
        targetValue = status.usageRatio.coerceIn(0f, 1f),
        animationSpec = EasySpendMotion.FluidSpring,
        label = "budgetBarActual"
    )
    val targetProjected = if (status.limitFen > 0L) {
        (status.projectedEndOfMonthFen.toFloat() / status.limitFen.toFloat()).coerceIn(0f, 1.25f)
    } else 0f
    val animatedProjected by animateFloatAsState(
        targetValue = targetProjected.coerceAtMost(1f),
        animationSpec = EasySpendMotion.GentleSpring,
        label = "budgetBarProjected"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            drawRoundRect(color = semantic.trackBackground, cornerRadius = radius)
            if (animatedProjected > animatedActual) {
                val projWidth = size.width * animatedProjected
                drawRoundRect(
                    color = barColor.copy(alpha = 0.24f),
                    size = Size(projWidth, size.height),
                    cornerRadius = radius
                )
            }
            val actWidth = size.width * animatedActual
            if (actWidth > 0f) {
                drawRoundRect(
                    color = barColor,
                    size = Size(actWidth.coerceAtLeast(4.dp.toPx()), size.height),
                    cornerRadius = radius
                )
            }
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
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TonalIconBadge(
                icon = icon,
                size = 52.dp,
                iconSize = 26.dp
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
