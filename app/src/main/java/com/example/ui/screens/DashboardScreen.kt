package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DonutSmall
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FundVault
import com.example.domain.finance.BudgetAndForecastEngine
import com.example.domain.finance.CashflowAnalyticsEngine
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.ReportPeriod
import com.example.domain.finance.VaultPortfolioSummary
import com.example.ui.EasySpendUiState
import com.example.ui.components.CashflowCalendarCard
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.FundVaultSummaryCard
import com.example.ui.components.ModularActionTile
import com.example.ui.components.ModularSectionHeader
import com.example.ui.components.SilkyModuleTransition
import com.example.ui.motion.AnimatedMoneyText
import com.example.ui.motion.AtmosphericHeroCard
import com.example.ui.motion.EasySpendMotion
import com.example.ui.motion.FluidSegmentOption
import com.example.ui.motion.FluidSegmentedBar
import com.example.ui.motion.LiveStatusDot
import com.example.ui.motion.SpringAnimatedVisibility
import com.example.ui.motion.StaggeredEntrance
import com.example.ui.motion.TonalIconBadge
import com.example.ui.motion.springClickable
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
    onOpenDigest: (ReportPeriod, Long) -> Unit = { _, _ -> },
    onToggleDetailedMode: (Boolean) -> Unit = {},
    onNavigateToLedgerVault: (FundVault) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val summary = state.summary
    val semantic = MaterialTheme.semanticColors
    var selectedChartCategory by remember { mutableStateOf<String?>(null) }
    // 0 = 支出结构 (default), 1 = 收支日历, 2 = 财务小报
    var activeInsightModule by rememberSaveable { mutableIntStateOf(0) }

    val allTimeVaultSummary = remember(state.allTransactions) {
        BudgetAndForecastEngine.evaluateVaultPortfolio(state.allTransactions)
    }
    val digestAnchor = remember(state.settings.activeMonthKey, state.allTransactions.size) {
        CashflowAnalyticsEngine.defaultAnchorMillis(state.settings.activeMonthKey, state.allTransactions)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .testTag("dashboard_lazy_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Module 1: Sculptural Atmospheric Balance & Cashflow Cockpit
            item(key = "hero_cockpit") {
                StaggeredEntrance(index = 0) {
                    AtmosphericHeroCard {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${state.settings.activeMonthKey} 本月结余",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = semantic.heroTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Surface(
                                    color = semantic.heroPillBackground,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.springClickable(pressedScale = 0.95f) {
                                        onNavigateToBudget()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Savings,
                                            contentDescription = null,
                                            tint = semantic.heroTextPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (summary?.isSavingsTargetFeasible == true) "存钱稳步推进" else "存钱目标需留意",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = semantic.heroTextPrimary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            AnimatedMoneyText(
                                amountFen = summary?.currentCashBalanceFen ?: 0L,
                                style = NumericLargeStyle,
                                color = semantic.heroTextPrimary,
                                autoShrink = true,
                                minFontSize = 22.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("hero_balance_text")
                            )

                            HorizontalDivider(color = semantic.heroDivider, thickness = 0.75.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HeroMetricItem(
                                    label = "净收入",
                                    amountFen = summary?.totalIncomeFen ?: 0L,
                                    labelColor = semantic.heroTextSecondary,
                                    valueColor = semantic.heroTextPrimary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(0.75.dp)
                                        .fillMaxHeight()
                                        .padding(vertical = 3.dp)
                                        .background(semantic.heroDivider)
                                )
                                HeroMetricItem(
                                    label = "净支出",
                                    amountFen = summary?.netExpenseFen ?: 0L,
                                    labelColor = semantic.heroTextSecondary,
                                    valueColor = semantic.heroTextPrimary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 14.dp)
                                )
                            }

                            if (summary != null) {
                                val targetFen = summary.savingsTargetFen.coerceAtLeast(1L)
                                val projectedRatio = (summary.projectedMonthEndSavingsFen.toFloat() / targetFen.toFloat()).coerceIn(0f, 1f)
                                val animatedGoalProgress by animateFloatAsState(
                                    targetValue = projectedRatio,
                                    animationSpec = EasySpendMotion.GentleSpring,
                                    label = "heroSavingsProgress"
                                )
                                Surface(
                                    color = semantic.heroPillBackground,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .springClickable(pressedScale = 0.985f) { onNavigateToBudget() }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "存钱进度",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = semantic.heroTextSecondary
                                            )
                                            Text(
                                                text = "${(projectedRatio * 100).toInt()}% · 目标 ${MoneyFormatter.formatFen(summary.savingsTargetFen)}",
                                                style = NumericSmallStyle.copy(fontSize = 12.sp),
                                                color = semantic.heroTextPrimary
                                            )
                                        }
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                        ) {
                                            val r = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                            drawRoundRect(
                                                color = Color.White.copy(alpha = 0.22f),
                                                cornerRadius = r
                                            )
                                            if (animatedGoalProgress > 0f) {
                                                drawRoundRect(
                                                    color = Color.White.copy(alpha = 0.94f),
                                                    size = Size((size.width * animatedGoalProgress).coerceAtLeast(6.dp.toPx()), size.height),
                                                    cornerRadius = r
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

            // Module 2: Detailed Mode Fund Vaults (silky spring expand/collapse)
            item(key = "fund_vault_summary") {
                SpringAnimatedVisibility(visible = state.settings.detailedModeEnabled) {
                    FundVaultSummaryCard(
                        monthlySummary = summary?.monthlyVaultSummary ?: VaultPortfolioSummary(),
                        allTimeSummary = allTimeVaultSummary,
                        onHideDetailedMode = { onToggleDetailedMode(false) },
                        onSelectVault = onNavigateToLedgerVault
                    )
                }
            }

            // Module 3: Tactile Pending Review Action Banner (silky spring enter/exit)
            item(key = "pending_review_banner") {
                SpringAnimatedVisibility(visible = state.pendingCandidates.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .testTag("pending_review_banner")
                            .springClickable(pressedScale = 0.98f) { onNavigateToImportQueue() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        border = semantic.cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TonalIconBadge(
                                    icon = Icons.AutoMirrored.Filled.FactCheck,
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    size = 38.dp,
                                    iconSize = 20.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        LiveStatusDot(color = MaterialTheme.colorScheme.secondary, size = 6.dp)
                                        Text(
                                            text = "有 ${state.pendingCandidates.size} 笔账单等你核对",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Text(
                                        text = "点击进入核对队列一键入账",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Module 4: Unified Modular Analytics Hub (Structure / Calendar / Digest)
            item(key = "dashboard_analytics_hub") {
                StaggeredEntrance(index = 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModularSectionHeader(
                            title = "数据看板",
                            trailing = {
                                val detailedOn = state.settings.detailedModeEnabled
                                val pillBg by animateColorAsState(
                                    targetValue = if (detailedOn) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    animationSpec = tween(180),
                                    label = "detailedPillBg"
                                )
                                val pillText by animateColorAsState(
                                    targetValue = if (detailedOn) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    animationSpec = tween(180),
                                    label = "detailedPillText"
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = pillBg,
                                    border = BorderStroke(
                                        0.75.dp,
                                        if (detailedOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else semantic.hairline
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .springClickable(pressedScale = 0.95f) {
                                            onToggleDetailedMode(!detailedOn)
                                        }
                                        .testTag("dashboard_detailed_mode_toggle")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = pillText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (detailedOn) "详细版 · 资金库开" else "开启资金库",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = pillText,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        )

                        FluidSegmentedBar(
                            options = listOf(
                                FluidSegmentOption(
                                    label = "支出结构",
                                    testTag = "dashboard_module_structure",
                                    icon = Icons.Outlined.DonutSmall
                                ),
                                FluidSegmentOption(
                                    label = "收支日历",
                                    testTag = "dashboard_module_calendar",
                                    icon = Icons.Outlined.CalendarMonth
                                ),
                                FluidSegmentOption(
                                    label = "财务小报",
                                    testTag = "dashboard_module_digest",
                                    icon = Icons.AutoMirrored.Filled.Article
                                )
                            ),
                            selectedIndex = activeInsightModule,
                            onSelect = { activeInsightModule = it }
                        )

                        SilkyModuleTransition(targetIndex = activeInsightModule) { moduleIdx ->
                            when (moduleIdx) {
                                0 -> DashboardStructureModule(
                                    summary = summary,
                                    selectedChartCategory = selectedChartCategory,
                                    onSelectCategory = { selectedChartCategory = it },
                                    onNavigateToBudget = onNavigateToBudget
                                )

                                1 -> CashflowCalendarCard(
                                    initialMonthKey = state.settings.activeMonthKey,
                                    allTransactions = state.allTransactions,
                                    categories = state.categories
                                )

                                else -> DashboardDigestModule(
                                    digestAnchor = digestAnchor,
                                    onOpenDigest = onOpenDigest,
                                    onNavigateToBudget = onNavigateToBudget
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
private fun DashboardStructureModule(
    summary: com.example.domain.finance.MonthlyFinancialSummary?,
    selectedChartCategory: String?,
    onSelectCategory: (String?) -> Unit,
    onNavigateToBudget: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("spending_donut_card"),
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
                    text = "本月花在哪儿",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                TextButton(
                    onClick = onNavigateToBudget,
                    modifier = Modifier.testTag("dashboard_to_budget_button")
                ) {
                    Text("管理预算")
                }
            }
            CategoryDonutChart(
                statuses = summary?.categoryStatuses.orEmpty(),
                netExpenseFen = summary?.netExpenseFen ?: 0L,
                selectedCategoryId = selectedChartCategory,
                onSelectCategory = onSelectCategory
            )
        }
    }
}

@Composable
private fun DashboardDigestModule(
    digestAnchor: Long,
    onOpenDigest: (ReportPeriod, Long) -> Unit,
    onNavigateToBudget: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_digest_entry_card"),
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "财务小报中心",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "按周期生成收支回顾与储蓄诊断",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onNavigateToBudget,
                    modifier = Modifier.testTag("dashboard_to_trend_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("折线趋势")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModularActionTile(
                    title = "周报",
                    caption = "近7日节奏",
                    icon = Icons.AutoMirrored.Filled.Article,
                    onClick = { onOpenDigest(ReportPeriod.WEEK, digestAnchor) },
                    testTag = "dashboard_open_digest_week",
                    modifier = Modifier.weight(1f)
                )
                ModularActionTile(
                    title = "月报",
                    caption = "本月结余复盘",
                    icon = Icons.AutoMirrored.Filled.Article,
                    onClick = { onOpenDigest(ReportPeriod.MONTH, digestAnchor) },
                    testTag = "dashboard_open_digest_month",
                    highlighted = true,
                    modifier = Modifier.weight(1f)
                )
            }
            ModularActionTile(
                title = "年度财务小报",
                caption = "全年收支趋势、分类排行与储蓄达成率",
                icon = Icons.AutoMirrored.Filled.ShowChart,
                onClick = { onOpenDigest(ReportPeriod.YEAR, digestAnchor) },
                testTag = "dashboard_open_digest_year",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HeroMetricItem(
    label: String,
    amountFen: Long,
    labelColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AnimatedMoneyText(
            amountFen = amountFen,
            style = NumericMediumStyle.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = valueColor,
            maxLines = 1,
            autoShrink = true,
            minFontSize = 11.sp
        )
    }
}
