package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionEntity
import com.example.domain.finance.CashflowAnalyticsEngine
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.ReportPeriod
import com.example.domain.finance.TrendBucketPoint
import com.example.ui.motion.FluidSegmentOption
import com.example.ui.motion.FluidSegmentedBar
import com.example.ui.motion.SpringAnimatedVisibility
import com.example.ui.motion.springClickable
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CashflowTrendChartCard(
    activeMonthKey: String,
    allTransactions: List<TransactionEntity>,
    onOpenDigest: (ReportPeriod, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.semanticColors
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.MONTH) }
    val initialAnchor = remember(activeMonthKey, allTransactions.size) {
        CashflowAnalyticsEngine.defaultAnchorMillis(activeMonthKey, allTransactions)
    }
    var anchorMillis by remember(activeMonthKey) { mutableStateOf(initialAnchor) }
    var selectedBucketIndex by remember(selectedPeriod, anchorMillis) { mutableStateOf<Int?>(null) }

    var showIncomeLine by remember { mutableStateOf(true) }
    var showExpenseLine by remember { mutableStateOf(true) }
    var showCumulativeNetLine by remember { mutableStateOf(true) }

    val report = remember(selectedPeriod, anchorMillis, allTransactions) {
        CashflowAnalyticsEngine.buildTrendReport(
            period = selectedPeriod,
            anchorMillis = anchorMillis,
            allTransactions = allTransactions
        )
    }

    val incomeColor = semantic.safe
    val expenseColor = semantic.danger
    val netColor = MaterialTheme.colorScheme.secondary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cashflow_trend_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Clean Header + Digest Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "收支趋势",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                FilledTonalButton(
                    onClick = { onOpenDigest(selectedPeriod, anchorMillis) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("trend_open_digest_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Article,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "查看${selectedPeriod.digestLabel}",
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            // 2. Crisp 周 / 月 / 年 Switcher
            FluidSegmentedBar(
                options = listOf(
                    FluidSegmentOption("周", "trend_period_week"),
                    FluidSegmentOption("月", "trend_period_month"),
                    FluidSegmentOption("年", "trend_period_year")
                ),
                selectedIndex = selectedPeriod.ordinal,
                onSelect = { idx ->
                    selectedPeriod = ReportPeriod.entries[idx]
                    selectedBucketIndex = null
                }
            )

            // 3. Clean Period Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        anchorMillis = CashflowAnalyticsEngine.shiftAnchor(anchorMillis, selectedPeriod, -1)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("trend_prev_period")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "上一周期",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = report.periodTitle,
                        style = NumericSmallStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("trend_period_label")
                    )
                    if (selectedPeriod == ReportPeriod.WEEK) {
                        Text(
                            text = report.rangeSubtitle,
                            style = NumericSmallStyle.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        anchorMillis = CashflowAnalyticsEngine.shiftAnchor(anchorMillis, selectedPeriod, 1)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("trend_next_period")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "下一周期",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Clean 3-Column Metrics Strip
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, semantic.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrendKpiColumn(
                        label = "收入",
                        value = MoneyFormatter.formatFen(report.totalIncomeFen),
                        color = incomeColor,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(0.75.dp)
                            .fillMaxHeight()
                            .background(semantic.hairline)
                    )
                    TrendKpiColumn(
                        label = "支出",
                        value = MoneyFormatter.formatFen(report.totalExpenseFen),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(0.75.dp)
                            .fillMaxHeight()
                            .background(semantic.hairline)
                    )
                    val netSign = if (report.netSavedFen > 0L) "+" else ""
                    TrendKpiColumn(
                        label = "净攒",
                        value = "$netSign${MoneyFormatter.formatFen(report.netSavedFen)}",
                        color = if (report.netSavedFen >= 0L) incomeColor else expenseColor,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    )
                }
            }

            // 5. Minimal Legend Toggles + Compact Delta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MinimalSeriesPill(
                        label = "收入",
                        dotColor = incomeColor,
                        selected = showIncomeLine,
                        onClick = {
                            if (showIncomeLine && !showExpenseLine && !showCumulativeNetLine) return@MinimalSeriesPill
                            showIncomeLine = !showIncomeLine
                        },
                        testTag = "trend_toggle_income"
                    )
                    MinimalSeriesPill(
                        label = "支出",
                        dotColor = expenseColor,
                        selected = showExpenseLine,
                        onClick = {
                            if (!showIncomeLine && showExpenseLine && !showCumulativeNetLine) return@MinimalSeriesPill
                            showExpenseLine = !showExpenseLine
                        },
                        testTag = "trend_toggle_expense"
                    )
                    MinimalSeriesPill(
                        label = "净攒",
                        dotColor = netColor,
                        selected = showCumulativeNetLine,
                        onClick = {
                            if (!showIncomeLine && !showExpenseLine && showCumulativeNetLine) return@MinimalSeriesPill
                            showCumulativeNetLine = !showCumulativeNetLine
                        },
                        testTag = "trend_toggle_net"
                    )
                }

                report.expenseDeltaPercent?.let { delta ->
                    val down = delta <= 0
                    Text(
                        text = "环比 ${if (delta > 0) "+$delta" else "$delta"}%",
                        style = NumericSmallStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = if (down) semantic.safe else semantic.warning
                    )
                }
            }

            // 6. Clean Smooth Multi-Series Line Chart
            if (report.txCount == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无收支记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                CashflowTrendLineCanvas(
                    buckets = report.buckets,
                    period = selectedPeriod,
                    showIncome = showIncomeLine,
                    showExpense = showExpenseLine,
                    showCumulativeNet = showCumulativeNetLine,
                    incomeColor = incomeColor,
                    expenseColor = expenseColor,
                    netColor = netColor,
                    selectedIndex = selectedBucketIndex,
                    onSelectIndex = { selectedBucketIndex = it },
                    chartHeight = 168.dp,
                    interactive = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cashflow_trend_canvas")
                )
            }

            // 7. Single-line Point Readout on Tap / Drag
            val activePoint = selectedBucketIndex?.let { report.buckets.getOrNull(it) }
            SpringAnimatedVisibility(visible = activePoint != null) {
                activePoint?.let { pt ->
                    val cumSign = if (pt.cumulativeNetFen > 0L) "+" else ""
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trend_selected_point_banner")
                    ) {
                        Text(
                            text = "${pt.fullLabel}  ·  收 ${MoneyFormatter.formatFen(pt.incomeFen)}  ·  支 ${MoneyFormatter.formatFen(pt.expenseFen)}  ·  净攒 $cumSign${MoneyFormatter.formatFen(pt.cumulativeNetFen)}",
                            style = NumericSmallStyle.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CashflowTrendLineCanvas(
    buckets: List<TrendBucketPoint>,
    period: ReportPeriod,
    showIncome: Boolean,
    showExpense: Boolean,
    showCumulativeNet: Boolean,
    incomeColor: Color,
    expenseColor: Color,
    netColor: Color,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    chartHeight: Dp,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    if (buckets.isEmpty()) return

    val semantic = MaterialTheme.semanticColors
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val haptic = LocalHapticFeedback.current

    val reveal = remember(buckets.size, period, buckets.firstOrNull()?.fullLabel) { Animatable(0f) }
    LaunchedEffect(buckets.size, period, buckets.firstOrNull()?.fullLabel) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing))
    }

    val allValues = buildList {
        add(0L)
        buckets.forEach { pt ->
            if (showIncome) add(pt.incomeFen)
            if (showExpense) add(pt.expenseFen)
            if (showCumulativeNet) add(pt.cumulativeNetFen)
        }
    }
    val rawMax = allValues.maxOrNull() ?: 100L
    val rawMin = allValues.minOrNull() ?: 0L
    val span = (rawMax - rawMin).coerceAtLeast(100L)
    val paddedMax = rawMax + (span * 0.12).toLong()
    val paddedMin = if (rawMin < 0L) rawMin - (span * 0.10).toLong() else 0L
    val valueRange = (paddedMax - paddedMin).coerceAtLeast(100L).toFloat()

    var lastHapticIdx by remember { mutableIntStateOf(-1) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val pointerMod = if (interactive) {
            Modifier
                .pointerInput(buckets) {
                    detectTapGestures { offset ->
                        val count = buckets.size
                        if (count <= 1) {
                            onSelectIndex(0)
                            return@detectTapGestures
                        }
                        val padX = 10.dp.toPx()
                        val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
                        val fraction = ((offset.x - padX) / usableW).coerceIn(0f, 1f)
                        val idx = (fraction * (count - 1)).roundToInt().coerceIn(0, count - 1)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectIndex(if (selectedIndex == idx) null else idx)
                    }
                }
                .pointerInput(buckets) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val count = buckets.size
                        if (count <= 1) return@detectHorizontalDragGestures
                        val padX = 10.dp.toPx()
                        val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
                        val fraction = ((change.position.x - padX) / usableW).coerceIn(0f, 1f)
                        val idx = (fraction * (count - 1)).roundToInt().coerceIn(0, count - 1)
                        if (idx != lastHapticIdx) {
                            lastHapticIdx = idx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSelectIndex(idx)
                    }
                }
        } else {
            Modifier
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .then(pointerMod)
        ) {
            val padX = 10.dp.toPx()
            val padTop = 8.dp.toPx()
            val padBottom = 8.dp.toPx()
            val plotW = (size.width - padX * 2f).coerceAtLeast(1f)
            val plotH = (size.height - padTop - padBottom).coerceAtLeast(1f)

            fun xOf(index: Int): Float {
                return if (buckets.size <= 1) {
                    padX + plotW / 2f
                } else {
                    padX + plotW * (index.toFloat() / (buckets.size - 1).toFloat())
                }
            }

            fun yOf(valueFen: Long): Float {
                val ratio = ((valueFen - paddedMin).toFloat() / valueRange).coerceIn(0f, 1f)
                return padTop + plotH * (1f - ratio)
            }

            // Minimal horizontal hairlines
            listOf(0f, 0.5f, 1f).forEach { frac ->
                val y = padTop + plotH * frac
                drawLine(
                    color = semantic.hairline.copy(alpha = if (frac == 1f) 0.8f else 0.45f),
                    start = Offset(padX, y),
                    end = Offset(size.width - padX, y),
                    strokeWidth = 0.75.dp.toPx()
                )
            }

            if (paddedMin < 0L) {
                val zeroY = yOf(0L)
                drawLine(
                    color = labelColor.copy(alpha = 0.35f),
                    start = Offset(padX, zeroY),
                    end = Offset(size.width - padX, zeroY),
                    strokeWidth = 0.75.dp.toPx()
                )
            }

            val zeroY = yOf(0L).coerceIn(padTop, padTop + plotH)

            fun drawSmoothSeries(
                values: List<Long>,
                strokeColor: Color,
                fillArea: Boolean,
                dashed: Boolean = false,
                strokeWidthDp: Dp = 2.dp
            ) {
                val pts = values.mapIndexed { i, v -> Offset(xOf(i), yOf(v)) }
                if (pts.isEmpty()) return
                val linePath = buildSmoothLinePath(pts)

                clipRect(right = padX + plotW * reveal.value + 4.dp.toPx()) {
                    if (fillArea && pts.size >= 2) {
                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(pts.last().x, zeroY)
                            lineTo(pts.first().x, zeroY)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    strokeColor.copy(alpha = 0.14f),
                                    strokeColor.copy(alpha = 0.0f)
                                ),
                                startY = padTop,
                                endY = zeroY
                            )
                        )
                    }
                    drawPath(
                        path = linePath,
                        color = strokeColor,
                        style = Stroke(
                            width = strokeWidthDp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                        )
                    )

                    if (pts.size <= 12) {
                        pts.forEach { p ->
                            drawCircle(color = surfaceColor, radius = 3.dp.toPx(), center = p)
                            drawCircle(color = strokeColor, radius = 2.dp.toPx(), center = p)
                        }
                    }
                }
            }

            if (showIncome) {
                drawSmoothSeries(
                    values = buckets.map { it.incomeFen },
                    strokeColor = incomeColor,
                    fillArea = true
                )
            }
            if (showExpense) {
                drawSmoothSeries(
                    values = buckets.map { it.expenseFen },
                    strokeColor = expenseColor,
                    fillArea = true
                )
            }
            if (showCumulativeNet) {
                drawSmoothSeries(
                    values = buckets.map { it.cumulativeNetFen },
                    strokeColor = netColor,
                    fillArea = false,
                    dashed = true,
                    strokeWidthDp = 1.75.dp
                )
            }

            // Clean Crosshair on Selection
            selectedIndex?.let { idx ->
                val pt = buckets.getOrNull(idx) ?: return@let
                val x = xOf(idx)
                drawLine(
                    color = labelColor.copy(alpha = 0.35f),
                    start = Offset(x, padTop),
                    end = Offset(x, padTop + plotH),
                    strokeWidth = 0.75.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                )
                if (showIncome) {
                    val cy = yOf(pt.incomeFen)
                    drawCircle(color = surfaceColor, radius = 4.5.dp.toPx(), center = Offset(x, cy))
                    drawCircle(color = incomeColor, radius = 3.dp.toPx(), center = Offset(x, cy))
                }
                if (showExpense) {
                    val cy = yOf(pt.expenseFen)
                    drawCircle(color = surfaceColor, radius = 4.5.dp.toPx(), center = Offset(x, cy))
                    drawCircle(color = expenseColor, radius = 3.dp.toPx(), center = Offset(x, cy))
                }
                if (showCumulativeNet) {
                    val cy = yOf(pt.cumulativeNetFen)
                    drawCircle(color = surfaceColor, radius = 4.5.dp.toPx(), center = Offset(x, cy))
                    drawCircle(color = netColor, radius = 3.dp.toPx(), center = Offset(x, cy))
                }
            }
        }

        val xTicks: List<String> = when (period) {
            ReportPeriod.WEEK -> buckets.map { it.label }
            ReportPeriod.MONTH -> listOf("1日", "8日", "15日", "22日", "${buckets.size}日")
            ReportPeriod.YEAR -> listOf("1月", "3月", "6月", "9月", "12月")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            xTicks.forEach { tick ->
                Text(
                    text = tick,
                    style = NumericSmallStyle.copy(fontSize = 10.sp),
                    color = labelColor
                )
            }
        }
    }
}

private fun buildSmoothLinePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) return path
    for (i in 0 until points.lastIndex) {
        val p0 = points[max(0, i - 1)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[min(points.lastIndex, i + 2)]

        val c1x = p1.x + (p2.x - p0.x) / 6f
        val c1y = p1.y + (p2.y - p0.y) / 6f
        val c2x = p2.x - (p3.x - p1.x) / 6f
        val c2y = p2.y - (p3.y - p1.y) / 6f

        val minY = min(p1.y, p2.y)
        val maxY = max(p1.y, p2.y)
        path.cubicTo(
            c1x,
            c1y.coerceIn(minY, maxY),
            c2x,
            c2y.coerceIn(minY, maxY),
            p2.x,
            p2.y
        )
    }
    return path
}

@Composable
private fun TrendKpiColumn(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = NumericSmallStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MinimalSeriesPill(
    label: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (selected) dotColor.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            0.75.dp,
            if (selected) dotColor.copy(alpha = 0.35f) else MaterialTheme.semanticColors.hairline
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .springClickable(pressedScale = 0.95f, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (selected) dotColor else dotColor.copy(alpha = 0.3f))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
