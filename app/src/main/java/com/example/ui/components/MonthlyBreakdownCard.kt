package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.MonthlyFinancialSummary
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors

enum class SpendingGroupFilter(val label: String) {
    ALL("全部"),
    ESSENTIAL("必要支出"),
    FLEXIBLE("弹性支出")
}

@Composable
fun MonthlyCategoryBreakdownCard(
    monthKey: String,
    summary: MonthlyFinancialSummary?,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.semanticColors
    var chartMode by remember { mutableIntStateOf(0) } // 0 = Donut, 1 = Bar
    var groupFilter by remember { mutableStateOf(SpendingGroupFilter.ALL) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }

    val allStatuses = summary?.categoryStatuses.orEmpty()
    val filteredStatuses = remember(allStatuses, groupFilter) {
        when (groupFilter) {
            SpendingGroupFilter.ALL -> allStatuses
            SpendingGroupFilter.ESSENTIAL -> allStatuses.filter { it.isEssential }
            SpendingGroupFilter.FLEXIBLE -> allStatuses.filter { !it.isEssential }
        }
    }
    val filteredTotalFen = remember(filteredStatuses) {
        filteredStatuses.sumOf { it.spentFen }
    }
    val selectedStatus = remember(filteredStatuses, selectedCategoryId) {
        filteredStatuses.firstOrNull { it.categoryId == selectedCategoryId }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_breakdown_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "$monthKey 月度分类消费分布",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "点击图表分区或分类行可查看详情",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = MoneyFormatter.formatFen(filteredTotalFen),
                        style = NumericSmallStyle.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (summary != null) {
                EssentialVsFlexibleRatioBar(
                    essentialFen = summary.essentialSpentFen,
                    flexibleFen = summary.flexibleSpentFen
                )
            }

            // Chart Mode Switcher: Donut vs Bar Comparison
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = chartMode == 0,
                    onClick = { chartMode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            Icons.Default.DonutLarge,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("chart_mode_donut")
                ) {
                    Text("占比环形图", maxLines = 1, fontSize = 13.sp)
                }
                SegmentedButton(
                    selected = chartMode == 1,
                    onClick = { chartMode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("chart_mode_bar")
                ) {
                    Text("预算对比柱图", maxLines = 1, fontSize = 13.sp)
                }
            }

            // Spending Group Filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpendingGroupFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = groupFilter == filter,
                        onClick = {
                            groupFilter = filter
                            selectedCategoryId = null
                        },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("chart_filter_${filter.name.lowercase()}")
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

            if (chartMode == 0) {
                CategoryDonutChart(
                    statuses = filteredStatuses,
                    netExpenseFen = filteredTotalFen,
                    selectedCategoryId = selectedCategoryId,
                    onSelectCategory = { selectedCategoryId = it }
                )
            } else {
                CategorySpendingBarChart(
                    statuses = filteredStatuses,
                    selectedCategoryId = selectedCategoryId,
                    onSelectCategory = { selectedCategoryId = it }
                )
            }

            AnimatedVisibility(visible = selectedStatus != null) {
                selectedStatus?.let { st ->
                    val sharePct = if (filteredTotalFen > 0L) ((st.spentFen * 100) / filteredTotalFen).toInt() else 0
                    val diffLabel = if (st.remainingFen >= 0L) {
                        "剩余可用 ${MoneyFormatter.formatFen(st.remainingFen)}"
                    } else {
                        "已超预算 ${MoneyFormatter.formatFen(-st.remainingFen)}"
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chart_selected_detail")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${st.categoryName} · 占当前视图 $sharePct%（${if (st.isEssential) "必要支出" else "弹性支出"}）",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "已花 ${MoneyFormatter.formatFen(st.spentFen)} / 预算 ${MoneyFormatter.formatFen(st.limitFen)} · $diffLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (st.remainingFen < 0L) semantic.danger else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { selectedCategoryId = null }) {
                                Text("重置")
                            }
                        }
                    }
                }
            }
        }
    }
}
