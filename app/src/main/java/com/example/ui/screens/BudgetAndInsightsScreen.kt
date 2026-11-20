package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.finance.CategoryBudgetStatus
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.ReportPeriod
import com.example.ui.AiInsightState
import com.example.ui.EasySpendUiState
import com.example.ui.components.BudgetProgressBar
import com.example.ui.components.CashflowTrendChartCard
import com.example.ui.components.MonthlyCategoryBreakdownCard
import com.example.ui.components.SilkyModuleTransition
import com.example.ui.components.riskLevelColor
import com.example.ui.motion.AgentThinkingPulse
import com.example.ui.motion.AnimatedMoneyText
import com.example.ui.motion.EasySpendMotion
import com.example.ui.motion.FluidSegmentOption
import com.example.ui.motion.FluidSegmentedBar
import com.example.ui.motion.StaggeredEntrance
import com.example.ui.motion.springClickable
import com.example.ui.theme.NumericMediumStyle
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors

@Composable
fun BudgetAndInsightsScreen(
    state: EasySpendUiState,
    onUpdateBudgetLimit: (String, String) -> Unit,
    onApplyProposal: (String, Long) -> Unit,
    onUpdateSavingsGoal: (String, String, String) -> Unit,
    onGenerateAiInsights: () -> Unit,
    onOpenApiSettings: () -> Unit = {},
    onOpenCategoryManager: () -> Unit = {},
    onOpenDigest: (ReportPeriod, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val summary = state.summary
    val semantic = MaterialTheme.semanticColors
    var editingCategory by remember { mutableStateOf<CategoryBudgetStatus?>(null) }
    var showGoalDialog by remember { mutableStateOf(false) }
    // 0 = 分类预算 (default), 1 = 趋势图表, 2 = AI 诊断
    var activeModule by rememberSaveable { mutableIntStateOf(0) }
    // 0 = 全部, 1 = 有消费, 2 = 必要, 3 = 弹性
    var budgetFilter by remember { mutableIntStateOf(0) }

    val allStatuses = summary?.categoryStatuses.orEmpty()
    val totalBudgetFen = remember(allStatuses) { allStatuses.sumOf { it.limitFen } }
    val totalSpentFen = summary?.netExpenseFen ?: 0L
    val totalRemainingFen = totalBudgetFen - totalSpentFen
    val overallUsageRatio = if (totalBudgetFen > 0L) (totalSpentFen.toFloat() / totalBudgetFen.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedUsage by animateFloatAsState(
        targetValue = overallUsageRatio,
        animationSpec = EasySpendMotion.GentleSpring,
        label = "swissBudgetUsage"
    )

    val filteredStatuses = remember(allStatuses, budgetFilter) {
        val base = when (budgetFilter) {
            1 -> allStatuses.filter { it.spentFen > 0L }
            2 -> allStatuses.filter { it.isEssential }
            3 -> allStatuses.filter { !it.isEssential }
            else -> allStatuses
        }
        base.sortedWith(
            compareByDescending<CategoryBudgetStatus> { it.spentFen > 0L }
                .thenByDescending { it.usageRatio }
        )
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .testTag("budget_lazy_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Pinned Swiss Typographic Budget & Savings Overview
            item(key = "swiss_budget_overview") {
                StaggeredEntrance(index = 0) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("swiss_budget_overview_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = semantic.cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${state.settings.activeMonthKey} 预算总览",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                OutlinedButton(
                                    onClick = { showGoalDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("edit_savings_goal_button")
                                ) {
                                    Text("存钱目标", fontSize = 12.sp)
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SwissMetricColumn(
                                    label = "总预算",
                                    amountFen = totalBudgetFen,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(0.75.dp)
                                        .fillMaxHeight()
                                        .background(semantic.hairline)
                                )
                                SwissMetricColumn(
                                    label = "已支出",
                                    amountFen = totalSpentFen,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(0.75.dp)
                                        .fillMaxHeight()
                                        .background(semantic.hairline)
                                )
                                SwissMetricColumn(
                                    label = "剩余可用",
                                    amountFen = totalRemainingFen,
                                    color = if (totalRemainingFen < 0L) semantic.danger else semantic.safe,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "预算执行率",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${(overallUsageRatio * 100).toInt()}%",
                                        style = NumericSmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                        color = if (overallUsageRatio > 0.9f) semantic.danger else MaterialTheme.colorScheme.primary
                                    )
                                }
                                val barColor = when {
                                    overallUsageRatio >= 0.95f -> semantic.danger
                                    overallUsageRatio >= 0.80f -> semantic.warning
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                ) {
                                    val r = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                    drawRoundRect(color = semantic.trackBackground, cornerRadius = r)
                                    if (animatedUsage > 0f) {
                                        drawRoundRect(
                                            color = barColor,
                                            size = Size((size.width * animatedUsage).coerceAtLeast(5.dp.toPx()), size.height),
                                            cornerRadius = r
                                        )
                                    }
                                }
                            }

                            if (summary != null) {
                                HorizontalDivider(thickness = 0.75.dp, color = semantic.hairline)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "目标存下 ${MoneyFormatter.formatFen(summary.savingsTargetFen)}",
                                        style = NumericSmallStyle.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val proj = summary.projectedMonthEndSavingsFen
                                    Surface(
                                        color = if (summary.isSavingsTargetFeasible) semantic.safeContainer else semantic.warningContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "预计结余 ${MoneyFormatter.formatFen(proj)}",
                                            style = NumericSmallStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                            color = if (summary.isSavingsTargetFeasible) semantic.safe else semantic.warning,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Categorized 3-Module Switcher (Budget Allocation / Visual Charts / AI Insights)
            item(key = "budget_module_switcher") {
                StaggeredEntrance(index = 1) {
                    FluidSegmentedBar(
                        options = listOf(
                            FluidSegmentOption(
                                label = "分类预算",
                                testTag = "budget_module_categories",
                                icon = Icons.Outlined.Tune
                            ),
                            FluidSegmentOption(
                                label = "趋势图表",
                                testTag = "budget_module_charts",
                                icon = Icons.AutoMirrored.Filled.ShowChart
                            ),
                            FluidSegmentOption(
                                label = "AI 诊断",
                                testTag = "budget_module_ai",
                                icon = Icons.Outlined.AutoAwesome
                            )
                        ),
                        selectedIndex = activeModule,
                        onSelect = { activeModule = it }
                    )
                }
            }

            // 2.5 Budget Optimization Suggestions (own item when activeModule == 0 so budget table key aligns directly)
            if (activeModule == 0 && summary != null && summary.suggestedAdjustments.isNotEmpty()) {
                item(key = "budget_suggestions") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        border = semantic.cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("预算优化建议", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            }
                            summary.suggestedAdjustments.forEach { prop ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = semantic.cardBorder,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${prop.categoryName}  ${MoneyFormatter.formatFen(prop.currentLimitFen)} → ${MoneyFormatter.formatFen(prop.proposedLimitFen)}",
                                                style = NumericSmallStyle.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = prop.reason,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        FilledTonalButton(
                                            onClick = { onApplyProposal(prop.categoryId, prop.proposedLimitFen) },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.testTag("apply_proposal_${prop.categoryId}")
                                        ) {
                                            Text("应用")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Silky Directional Module Content (keeps stable LazyColumn key "swiss_category_budget_table")
            item(key = "swiss_category_budget_table") {
                SilkyModuleTransition(targetIndex = activeModule) { moduleIdx ->
                    when (moduleIdx) {
                        0 -> Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(EasySpendMotion.SpatialSizeSpring)
                                .testTag("swiss_category_budget_table"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                border = semantic.cardBorder,
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "分类预算",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        FilledTonalButton(
                                            onClick = onOpenCategoryManager,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("open_category_manager_button")
                                        ) {
                                            Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("自定义分类", fontSize = 12.sp, maxLines = 1)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            0 to "全部 (${allStatuses.size})",
                                            1 to "有消费",
                                            2 to "必要",
                                            3 to "弹性"
                                        ).forEach { (idx, label) ->
                                            FilterChip(
                                                selected = budgetFilter == idx,
                                                onClick = { budgetFilter = idx },
                                                label = { Text(label, fontSize = 12.sp) },
                                                modifier = Modifier.testTag("budget_filter_$idx")
                                            )
                                        }
                                    }

                                    if (filteredStatuses.isEmpty()) {
                                        Text(
                                            text = "当前筛选下暂无分类记录",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    } else {
                                        Column {
                                            filteredStatuses.forEachIndexed { idx, status ->
                                                if (idx > 0) {
                                                    HorizontalDivider(
                                                        thickness = 0.5.dp,
                                                        color = semantic.hairline.copy(alpha = 0.85f),
                                                        modifier = Modifier.padding(vertical = 10.dp)
                                                    )
                                                }
                                                SwissCategoryBudgetRow(
                                                    status = status,
                                                    onEdit = { editingCategory = status }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                        1 -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            CashflowTrendChartCard(
                                activeMonthKey = state.settings.activeMonthKey,
                                allTransactions = state.allTransactions,
                                onOpenDigest = onOpenDigest
                            )
                            MonthlyCategoryBreakdownCard(
                                monthKey = state.settings.activeMonthKey,
                                summary = summary
                            )
                        }

                        else -> Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_insights_card"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = semantic.cardBorder,
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
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
                                            text = "AI 消费诊断",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "结合本月收支结构生成个性化节流建议",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = onGenerateAiInsights,
                                        enabled = state.aiInsightState !is AiInsightState.Loading,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier.testTag("generate_ai_insights_button")
                                    ) {
                                        if (state.aiInsightState is AiInsightState.Loading) {
                                            AgentThinkingPulse(color = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("生成诊断", fontSize = 12.sp, maxLines = 1)
                                        }
                                    }
                                }

                                AnimatedContent(
                                    targetState = state.aiInsightState,
                                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                                    label = "aiInsightContentState"
                                ) { insight ->
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        when (insight) {
                                            AiInsightState.Idle -> {
                                                Text(
                                                    text = if (state.settings.isApiConfigured) {
                                                        "点击上方「生成诊断」，获取本月收支节奏点评与可执行的控费建议。"
                                                    } else {
                                                        "配置好大模型 API Key 后即可一键生成 AI 消费诊断报告。"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (!state.settings.isApiConfigured) {
                                                    TextButton(
                                                        onClick = onOpenApiSettings,
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                                    ) {
                                                        Text("先配置 API Key")
                                                    }
                                                }
                                            }
                                            AiInsightState.Loading -> {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    AgentThinkingPulse(color = MaterialTheme.colorScheme.primary)
                                                    Text(
                                                        text = "正在分析本月收支节奏...",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            is AiInsightState.Error -> {
                                                Surface(
                                                    color = semantic.dangerContainer,
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text(
                                                            text = insight.message,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = semantic.danger
                                                        )
                                                        if (!state.settings.isApiConfigured) {
                                                            TextButton(
                                                                onClick = onOpenApiSettings,
                                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                                            ) {
                                                                Text("去填写 API Key")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            is AiInsightState.Success -> {
                                                val r = insight.report
                                                Text(
                                                    text = r.headline,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(text = r.trendSummary, style = MaterialTheme.typography.bodySmall)
                                                if (r.impulseAlert.isNotBlank()) {
                                                    Surface(
                                                        color = semantic.warningContainer,
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = "留意项：${r.impulseAlert}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            modifier = Modifier.padding(10.dp)
                                                        )
                                                    }
                                                }
                                                HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)
                                                r.recommendations.forEachIndexed { idx, rec ->
                                                    Row(
                                                        verticalAlignment = Alignment.Top,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Lightbulb,
                                                            contentDescription = null,
                                                            tint = semantic.warning,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        Text(text = "${idx + 1}. $rec", style = MaterialTheme.typography.bodySmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingCategory?.let { cat ->
        var limitText by remember(cat.categoryId) {
            mutableStateOf(MoneyFormatter.fenToYuanString(cat.limitFen))
        }
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("设置「${cat.categoryName}」每月预算") },
            text = {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("预算上限（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_limit_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateBudgetLimit(cat.categoryId, limitText)
                        editingCategory = null
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("save_budget_limit_button")
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) { Text("取消") }
            }
        )
    }

    if (showGoalDialog) {
        val goal = state.savingsGoal
        var targetStr by remember { mutableStateOf(MoneyFormatter.fenToYuanString(goal?.targetFen ?: 100000L)) }
        var allowanceStr by remember { mutableStateOf(MoneyFormatter.fenToYuanString(goal?.livingAllowanceExpectedFen ?: 180000L)) }
        var partTimeStr by remember { mutableStateOf(MoneyFormatter.fenToYuanString(goal?.partTimeExpectedFen ?: 50000L)) }

        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("存钱目标与每月收入参考") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = targetStr,
                        onValueChange = { targetStr = it },
                        label = { Text("每月想存下多少钱（元）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = allowanceStr,
                        onValueChange = { allowanceStr = it },
                        label = { Text("每月生活费参考（元）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = partTimeStr,
                        onValueChange = { partTimeStr = it },
                        label = { Text("预计其他/兼职收入（到账后计入）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSavingsGoal(targetStr, allowanceStr, partTimeStr)
                        showGoalDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SwissMetricColumn(
    label: String,
    amountFen: Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        AnimatedMoneyText(
            amountFen = amountFen,
            style = NumericMediumStyle.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = color,
            autoShrink = true,
            minFontSize = 11.sp
        )
    }
}

@Composable
private fun SwissCategoryBudgetRow(
    status: CategoryBudgetStatus,
    onEdit: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .springClickable(pressedScale = 0.99f, onClick = onEdit)
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = status.categoryName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = if (status.isEssential) "必要" else "弹性",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (status.isEssential) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${MoneyFormatter.formatFen(status.spentFen)} / ${MoneyFormatter.formatFen(status.limitFen)}",
                    style = NumericSmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("edit_budget_${status.categoryId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "修改${status.categoryName}预算",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        BudgetProgressBar(status = status)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "余 ${MoneyFormatter.formatFen(status.remainingFen)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (status.remainingFen < 0L) semantic.danger else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "预计 ${MoneyFormatter.formatFen(status.projectedEndOfMonthFen)} · ${status.riskLevel.label}",
                style = MaterialTheme.typography.labelSmall,
                color = riskLevelColor(status.riskLevel)
            )
        }
    }
}
