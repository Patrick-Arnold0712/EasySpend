package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.finance.CategoryBudgetStatus
import com.example.domain.finance.MoneyFormatter
import com.example.ui.AiInsightState
import com.example.ui.EasySpendUiState
import com.example.ui.components.BudgetProgressBar
import com.example.ui.components.MonthlyCategoryBreakdownCard
import com.example.ui.components.riskLevelColor
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
    modifier: Modifier = Modifier
) {
    val summary = state.summary
    val semantic = MaterialTheme.semanticColors
    var editingCategory by remember { mutableStateOf<CategoryBudgetStatus?>(null) }
    var showGoalDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Monthly Savings Challenge Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("本月存钱小目标", style = MaterialTheme.typography.titleMedium)
                            }
                            OutlinedButton(
                                onClick = { showGoalDialog = true },
                                modifier = Modifier.testTag("edit_savings_goal_button")
                            ) {
                                Text("修改目标")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("挑战存下", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    MoneyFormatter.formatFen(summary?.savingsTargetFen ?: 100000L),
                                    style = NumericMediumStyle
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1.3f),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    "预计月底结余（仅计已到账）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End
                                )
                                val proj = summary?.projectedMonthEndSavingsFen ?: 0L
                                Text(
                                    MoneyFormatter.formatFen(proj),
                                    style = NumericMediumStyle,
                                    color = if (summary?.isSavingsTargetFeasible == true) semantic.safe else semantic.warning
                                )
                            }
                        }

                        if (summary != null) {
                            Surface(
                                color = if (summary.isSavingsTargetFeasible) semantic.safeContainer else semantic.warningContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = summary.savingsFeasibilityNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Monthly Spending Category Visualization Card
            item {
                MonthlyCategoryBreakdownCard(
                    monthKey = state.settings.activeMonthKey,
                    summary = summary
                )
            }

            // 3. AI Spending Analysis Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = semantic.cardBorder,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("AI 消费分析与省钱建议", style = MaterialTheme.typography.titleMedium)
                            }
                            Button(
                                onClick = onGenerateAiInsights,
                                enabled = state.aiInsightState !is AiInsightState.Loading,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("generate_ai_insights_button")
                            ) {
                                if (state.aiInsightState is AiInsightState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("生成分析", fontSize = 13.sp, maxLines = 1)
                                }
                            }
                        }

                        when (val insight = state.aiInsightState) {
                            AiInsightState.Idle -> {
                                Text(
                                    text = "点击「生成分析」，AI 会根据你本月的实际花销给出消费趋势小结、冲动提醒和三条省钱小建议。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!state.settings.isApiConfigured) {
                                    TextButton(
                                        onClick = onOpenApiSettings,
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("先配置大模型 API Key")
                                    }
                                }
                            }
                            AiInsightState.Loading -> {
                                Text(
                                    text = "正在分析你的本月账本与花销节奏...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is AiInsightState.Error -> {
                                Surface(
                                    color = semantic.dangerContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                Text(
                                    text = "存钱评估：${r.savingsVerdict}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "${idx + 1}. $rec",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Budget Adjustment Suggestions
            if (summary != null && summary.suggestedAdjustments.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        border = semantic.cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null)
                                Text("温和预算调整建议（需你点应用才生效）", style = MaterialTheme.typography.titleSmall)
                            }
                            summary.suggestedAdjustments.forEach { prop ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
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
                                                text = "${prop.categoryName}：${MoneyFormatter.formatFen(prop.currentLimitFen)} → ${MoneyFormatter.formatFen(prop.proposedLimitFen)}",
                                                style = MaterialTheme.typography.titleSmall
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

            // 4. Category Budgets & Custom Categories
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "分类月度预算（共 ${summary?.categoryStatuses?.size ?: 0} 项）",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    FilledTonalButton(
                        onClick = onOpenCategoryManager,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("open_category_manager_button")
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("自定义分类", fontSize = 13.sp, maxLines = 1)
                    }
                }
            }

            items(summary?.categoryStatuses.orEmpty(), key = { it.categoryId }) { status ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = semantic.cardBorder,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    status.categoryName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    color = if (status.isEssential) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (status.isEssential) "必要保障" else "可调弹性",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (status.isEssential) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${MoneyFormatter.formatFen(status.spentFen)} / ${MoneyFormatter.formatFen(status.limitFen)}",
                                    style = NumericSmallStyle,
                                    maxLines = 1
                                )
                                IconButton(
                                    onClick = { editingCategory = status },
                                    modifier = Modifier.testTag("edit_budget_${status.categoryId}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "修改${status.categoryName}预算", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        BudgetProgressBar(status = status)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "还剩 ${MoneyFormatter.formatFen(status.remainingFen)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (status.remainingFen < 0) semantic.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "月底预计 ${MoneyFormatter.formatFen(status.projectedEndOfMonthFen)} · ${status.riskLevel.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = riskLevelColor(status.riskLevel),
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.4f)
                            )
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
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = allowanceStr,
                        onValueChange = { allowanceStr = it },
                        label = { Text("每月家庭生活费（元）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = partTimeStr,
                        onValueChange = { partTimeStr = it },
                        label = { Text("预计兼职参考（到账后才计入结余）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSavingsGoal(targetStr, allowanceStr, partTimeStr)
                        showGoalDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) { Text("取消") }
            }
        )
    }
}
