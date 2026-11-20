package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import com.example.domain.finance.MoneyFormatter
import com.example.ui.motion.SpringAnimatedVisibility
import com.example.ui.motion.springClickable
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class DailyCashflowSummary(
    val dayOfMonth: Int,
    val incomeFen: Long,
    val expenseFen: Long,
    val transactions: List<TransactionEntity>
) {
    val netSavedFen: Long get() = incomeFen - expenseFen
    val hasActivity: Boolean get() = transactions.isNotEmpty()
}

data class CalendarMonthGrid(
    val monthKey: String,
    val year: Int,
    val month: Int, // 1..12
    val daysInMonth: Int,
    val leadingEmptyDays: Int, // 0 = Monday .. 6 = Sunday
    val dailyMap: Map<Int, DailyCashflowSummary>,
    val monthIncomeFen: Long,
    val monthExpenseFen: Long
) {
    val monthNetSavedFen: Long get() = monthIncomeFen - monthExpenseFen
}

object CashflowCalendarCalculator {

    fun shiftMonthKey(monthKey: String, deltaMonths: Int): String {
        val (year, month) = parseMonthKey(monthKey)
        val cal = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, deltaMonths)
        }
        return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun buildMonthGrid(monthKey: String, allTransactions: List<TransactionEntity>): CalendarMonthGrid {
        val (year, month) = parseMonthKey(monthKey)
        val cal = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        // Calendar.MONDAY == 2 -> 0, ..., Calendar.SUNDAY == 1 -> 6
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val leadingEmpty = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY

        val monthTxs = allTransactions.filter { it.monthKey == monthKey }
        val byDay = mutableMapOf<Int, MutableList<TransactionEntity>>()
        val dayCal = Calendar.getInstance()
        for (tx in monthTxs) {
            dayCal.timeInMillis = tx.occurredAtMillis
            val d = dayCal.get(Calendar.DAY_OF_MONTH).coerceIn(1, daysInMonth)
            byDay.getOrPut(d) { mutableListOf() }.add(tx)
        }

        val dailyMap = mutableMapOf<Int, DailyCashflowSummary>()
        var totalIncome = 0L
        var totalExpense = 0L

        for ((day, txs) in byDay) {
            val income = txs.filter { it.direction == TransactionDirection.INCOME }.sumOf { it.amountFen }
            val grossExpense = txs.filter { it.direction == TransactionDirection.EXPENSE }.sumOf { it.amountFen }
            val refund = txs.filter { it.direction == TransactionDirection.REFUND }.sumOf { it.amountFen }
            val netExpense = (grossExpense - refund).coerceAtLeast(0L)
            totalIncome += income
            totalExpense += netExpense
            dailyMap[day] = DailyCashflowSummary(
                dayOfMonth = day,
                incomeFen = income,
                expenseFen = netExpense,
                transactions = txs.sortedByDescending { it.occurredAtMillis }
            )
        }

        return CalendarMonthGrid(
            monthKey = monthKey,
            year = year,
            month = month,
            daysInMonth = daysInMonth,
            leadingEmptyDays = leadingEmpty,
            dailyMap = dailyMap,
            monthIncomeFen = totalIncome,
            monthExpenseFen = totalExpense
        )
    }

    fun formatCompactSignedYuan(netFen: Long): String {
        val sign = if (netFen > 0L) "+" else if (netFen < 0L) "-" else ""
        val absYuan = abs(netFen) / 100.0
        val body = when {
            absYuan >= 10000.0 -> String.format(Locale.US, "%.1fw", absYuan / 10000.0)
            absYuan >= 100.0 -> String.format(Locale.US, "%.0f", absYuan)
            abs(netFen) % 100L == 0L -> String.format(Locale.US, "%.0f", absYuan)
            else -> String.format(Locale.US, "%.1f", absYuan)
        }
        return "$sign$body"
    }

    private fun parseMonthKey(monthKey: String): Pair<Int, Int> {
        val parts = monthKey.split("-")
        val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val m = (parts.getOrNull(1)?.toIntOrNull() ?: 11).coerceIn(1, 12)
        return y to m
    }
}

@Composable
fun CashflowCalendarCard(
    initialMonthKey: String,
    allTransactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.semanticColors
    var displayedMonthKey by remember(initialMonthKey) { mutableStateOf(initialMonthKey) }
    var selectedDay by remember(displayedMonthKey) { mutableStateOf<Int?>(null) }

    val grid = remember(displayedMonthKey, allTransactions) {
        CashflowCalendarCalculator.buildMonthGrid(displayedMonthKey, allTransactions)
    }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val selectedSummary = selectedDay?.let { grid.dailyMap[it] }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cashflow_calendar_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clean Header: Title on left, compact < YYYY-MM > stepper on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "收支日历",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            displayedMonthKey = CashflowCalendarCalculator.shiftMonthKey(displayedMonthKey, -1)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("calendar_prev_month")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一个月",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = displayedMonthKey,
                        style = NumericSmallStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("calendar_month_label")
                    )
                    IconButton(
                        onClick = {
                            displayedMonthKey = CashflowCalendarCalculator.shiftMonthKey(displayedMonthKey, 1)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("calendar_next_month")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "下一个月",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Swiss 3-Column Month KPI Strip (Income | Expense | Net Saved)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
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
                    CalendarKpiCell(
                        label = "月收入",
                        value = MoneyFormatter.formatFen(grid.monthIncomeFen),
                        valueColor = semantic.safe,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(0.75.dp)
                            .fillMaxHeight()
                            .background(semantic.hairline)
                    )
                    CalendarKpiCell(
                        label = "月支出",
                        value = MoneyFormatter.formatFen(grid.monthExpenseFen),
                        valueColor = MaterialTheme.colorScheme.onSurface,
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
                    val netPrefix = if (grid.monthNetSavedFen > 0L) "+" else ""
                    CalendarKpiCell(
                        label = "净攒",
                        value = "$netPrefix${MoneyFormatter.formatFen(grid.monthNetSavedFen)}",
                        valueColor = if (grid.monthNetSavedFen >= 0L) semantic.safe else semantic.danger,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    )
                }
            }

            // Weekday Header Row
            val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdays.forEach { wd ->
                    Text(
                        text = wd,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Calendar Grid Rows
            val totalCells = grid.leadingEmptyDays + grid.daysInMonth
            val rowsCount = (totalCells + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (rowIdx in 0 until rowsCount) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (colIdx in 0 until 7) {
                            val cellIndex = rowIdx * 7 + colIdx
                            val dayNum = cellIndex - grid.leadingEmptyDays + 1
                            if (dayNum < 1 || dayNum > grid.daysInMonth) {
                                Spacer(modifier = Modifier.weight(1f).height(48.dp))
                            } else {
                                val daySummary = grid.dailyMap[dayNum]
                                val isSelected = selectedDay == dayNum
                                CalendarDayCell(
                                    day = dayNum,
                                    summary = daySummary,
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedDay = if (selectedDay == dayNum) null else dayNum
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("calendar_day_${grid.monthKey}_$dayNum")
                                )
                            }
                        }
                    }
                }
            }

            // Inline Expandable Day Detail Panel
            SpringAnimatedVisibility(visible = selectedDay != null) {
                val activeDay = selectedDay ?: 1
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(thickness = 0.75.dp, color = semantic.hairline)
                    val incomeFen = selectedSummary?.incomeFen ?: 0L
                    val expenseFen = selectedSummary?.expenseFen ?: 0L
                    val netFen = incomeFen - expenseFen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calendar_selected_day_summary"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${grid.month}月${activeDay}日",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        val netSign = if (netFen > 0L) "+" else ""
                        Text(
                            text = "收 ${MoneyFormatter.formatFen(incomeFen)} · 支 ${MoneyFormatter.formatFen(expenseFen)} · 净攒 $netSign${MoneyFormatter.formatFen(netFen)}",
                            style = NumericSmallStyle.copy(fontSize = 11.sp),
                            color = if (netFen >= 0L) semantic.safe else semantic.danger,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val dayTxs = selectedSummary?.transactions.orEmpty()
                    if (dayTxs.isEmpty()) {
                        Text(
                            text = "当日暂无收支记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            dayTxs.forEach { tx ->
                                val isInflow = tx.direction == TransactionDirection.INCOME || tx.direction == TransactionDirection.REFUND
                                val catName = categoryMap[tx.categoryId]?.name ?: tx.categoryId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tx.merchant,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$catName · ${synchronized(timeFmt) { timeFmt.format(Date(tx.occurredAtMillis)) }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${if (isInflow) "+" else "-"}${MoneyFormatter.formatFen(tx.amountFen)}",
                                        style = NumericSmallStyle.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isInflow) semantic.safe else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarKpiCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = NumericSmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    summary: DailyCashflowSummary?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.semanticColors
    val hasActivity = summary != null && summary.hasActivity
    val shape = RoundedCornerShape(10.dp)
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        hasActivity && summary.netSavedFen > 0L -> semantic.safeContainer.copy(alpha = 0.52f)
        hasActivity && summary.netSavedFen < 0L -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
        else -> Color.Transparent
    }
    val borderMod = if (isSelected) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
    } else if (hasActivity) {
        Modifier.border(0.5.dp, semantic.hairline.copy(alpha = 0.65f), shape)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bgColor)
            .then(borderMod)
            .springClickable(pressedScale = 0.94f, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = if (isSelected || hasActivity) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else if (hasActivity) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            }
        )

        if (hasActivity) {
            val netText = CashflowCalendarCalculator.formatCompactSignedYuan(summary.netSavedFen)
            val netColor = when {
                summary.netSavedFen > 0L -> semantic.safe
                summary.netSavedFen < 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = netText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = netColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
            // Subtle dots for income & expense presence
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (summary.incomeFen > 0L) {
                    Box(
                        modifier = Modifier
                            .size(3.5.dp)
                            .clip(CircleShape)
                            .background(semantic.safe)
                    )
                }
                if (summary.expenseFen > 0L) {
                    Box(
                        modifier = Modifier
                            .size(3.5.dp)
                            .clip(CircleShape)
                            .background(semantic.danger.copy(alpha = 0.75f))
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
