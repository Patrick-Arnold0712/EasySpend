package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.OverspendRiskLevel
import com.example.ui.EasySpendUiState
import com.example.ui.components.BudgetProgressBar
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.riskLevelColor
import com.example.ui.theme.NumericLargeStyle
import com.example.ui.theme.NumericMediumStyle
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors

@Composable
fun DashboardScreen(
    state: EasySpendUiState,
    onLoadDemoQueue: () -> Unit,
    onLoadDemoBaseline: () -> Unit,
    onAdd300Essential: () -> Unit,
    onNavigateToImportQueue: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToAgentWithPrompt: (String) -> Unit,
    onDismissBeginnerGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val summary = state.summary
    val semantic = MaterialTheme.semanticColors
    var selectedChartCategory by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .testTag("dashboard_lazy_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Calm iOS Wallet-style Balance & Income Overview Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(semantic.heroStart, semantic.heroEnd)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${state.settings.activeMonthKey} 本月可用结余",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = semantic.heroTextSecondary
                                )
                                Text(
                                    text = MoneyFormatter.formatFen(summary?.currentCashBalanceFen ?: 0L),
                                    style = NumericLargeStyle,
                                    color = semantic.heroTextPrimary
                                )
                            }
                            Surface(
                                color = semantic.heroPillBackground,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Savings,
                                        contentDescription = null,
                                        tint = semantic.heroTextPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (summary?.isSavingsTargetFeasible == true) "存钱目标稳步达成" else "存钱目标需留意",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = semantic.heroTextPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = semantic.heroDivider, thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HeroMetricItem(
                                label = "家庭生活费",
                                value = MoneyFormatter.formatFen(summary?.livingAllowanceIncomeFen ?: 0L),
                                labelColor = semantic.heroTextSecondary,
                                valueColor = semantic.heroTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            HeroMetricItem(
                                label = "兼职已到账",
                                value = MoneyFormatter.formatFen(summary?.partTimeIncomeFen ?: 0L),
                                labelColor = semantic.heroTextSecondary,
                                valueColor = semantic.heroTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            HeroMetricItem(
                                label = "本月净支出",
                                value = MoneyFormatter.formatFen(summary?.netExpenseFen ?: 0L),
                                labelColor = semantic.heroTextSecondary,
                                valueColor = semantic.heroTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (summary != null) {
                            Surface(
                                color = semantic.heroPillBackground,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = summary.savingsFeasibilityNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.heroTextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Friendly Pending Review Banner
            if (state.pendingCandidates.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pending_review_banner")
                            .clickable { onNavigateToImportQueue() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        border = semantic.cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FactCheck,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Column {
                                    Text(
                                        text = "有 ${state.pendingCandidates.size} 笔账单等你核对",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "已标出疑似重复、退款和未确定分类，点此确认入账",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // 3. Beginner-Friendly Quick Experience Card (dismissible)
            if (!state.settings.hideBeginnerGuide) {
                item(key = "beginner_guide_card") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("beginner_guide_card"),
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "新手快速体验",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                IconButton(
                                    onClick = onDismissBeginnerGuide,
                                    modifier = Modifier.testTag("dismiss_beginner_guide_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "关闭新手快速体验",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = "第一次使用？点一下即可导入模拟大学生生活费账单，体验查重核对、存钱预测与 AI 预算规划。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        val compactButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onLoadDemoBaseline,
                                contentPadding = compactButtonPadding,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("demo_load_baseline_button")
                            ) {
                                Text("载入体验账单", fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
                            }
                            OutlinedButton(
                                onClick = onLoadDemoQueue,
                                contentPadding = compactButtonPadding,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("demo_load_queue_button")
                            ) {
                                Text("体验核对流程", fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onAdd300Essential,
                                contentPadding = compactButtonPadding,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("demo_add_300_button")
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("记¥300学习费", fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
                            }
                            Button(
                                onClick = {
                                    onNavigateToAgentWithPrompt("结合当前最新账本，我这个月还能存下1000元吗？如果需要调整预算，请给出具体方案。")
                                },
                                contentPadding = compactButtonPadding,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("demo_ask_agent_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("问问财务助手", fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            }

            // 4. Spending Structure Donut Card
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
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "本月花在哪儿",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "必要 ${MoneyFormatter.formatFen(summary?.essentialSpentFen ?: 0L)} · 弹性 ${MoneyFormatter.formatFen(summary?.flexibleSpentFen ?: 0L)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        CategoryDonutChart(
                            statuses = summary?.categoryStatuses.orEmpty(),
                            netExpenseFen = summary?.netExpenseFen ?: 0L,
                            selectedCategoryId = selectedChartCategory,
                            onSelectCategory = { selectedChartCategory = it }
                        )
                    }
                }
            }

            // 5. Budget Progress Snapshot
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = semantic.cardBorder,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "预算使用进度",
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(onClick = onNavigateToBudget) {
                                Text("自定义预算")
                            }
                        }

                        val statuses = summary?.categoryStatuses.orEmpty().take(5)
                        statuses.forEach { st ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                                        Text(
                                            text = st.categoryName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (st.isEssential) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "必要",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${MoneyFormatter.formatFen(st.spentFen)} / ${MoneyFormatter.formatFen(st.limitFen)}",
                                            style = NumericSmallStyle
                                        )
                                        Icon(
                                            imageVector = if (st.riskLevel == OverspendRiskLevel.SAFE) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                            contentDescription = st.riskLevel.label,
                                            tint = riskLevelColor(st.riskLevel),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                BudgetProgressBar(status = st)
                                Text(
                                    text = "按当前速度月底预计花费 ${MoneyFormatter.formatFen(st.projectedEndOfMonthFen)} · ${st.riskLevel.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = riskLevelColor(st.riskLevel)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetricItem(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1
        )
        Text(
            text = value,
            style = NumericMediumStyle,
            color = valueColor,
            maxLines = 1
        )
    }
}
