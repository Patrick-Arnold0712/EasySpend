package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionEntity
import com.example.domain.finance.CashflowAnalyticsEngine
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.ReportPeriod
import com.example.ui.components.CashflowTrendLineCanvas
import com.example.ui.motion.FluidSegmentOption
import com.example.ui.motion.FluidSegmentedBar
import com.example.ui.theme.NumericMediumStyle
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialDigestDialog(
    initialPeriod: ReportPeriod,
    initialAnchorMillis: Long,
    allTransactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    savingsTargetFen: Long,
    onAskAgent: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var period by remember(initialPeriod) { mutableStateOf(initialPeriod) }
    var anchorMillis by remember(initialAnchorMillis) { mutableStateOf(initialAnchorMillis) }
    var selectedChartIdx by remember(period, anchorMillis) { mutableStateOf<Int?>(null) }

    val digest = remember(period, anchorMillis, allTransactions, categories, savingsTargetFen) {
        CashflowAnalyticsEngine.buildDigestReport(
            period = period,
            anchorMillis = anchorMillis,
            allTransactions = allTransactions,
            categories = categories,
            savingsTargetFen = savingsTargetFen
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("financial_digest_dialog"),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = "财务小报",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(digest.sharePlainText))
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已复制文字小报")
                                    }
                                },
                                modifier = Modifier.testTag("digest_copy_button")
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "复制小报")
                            }
                            IconButton(
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, digest.title)
                                        putExtra(Intent.EXTRA_TEXT, digest.sharePlainText)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(sendIntent, "分享财务小报"))
                                    }
                                },
                                modifier = Modifier.testTag("digest_share_button")
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = "分享小报")
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("digest_close_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "关闭")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                    HorizontalDivider(thickness = 0.75.dp, color = semantic.hairline)
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize()
                        .testTag("digest_lazy_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Period Switcher & Stepper
                    item(key = "digest_controls") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FluidSegmentedBar(
                                options = listOf(
                                    FluidSegmentOption("周报", "digest_period_week"),
                                    FluidSegmentOption("月报", "digest_period_month"),
                                    FluidSegmentOption("年报", "digest_period_year")
                                ),
                                selectedIndex = period.ordinal,
                                onSelect = { idx ->
                                    period = ReportPeriod.entries[idx]
                                    selectedChartIdx = null
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        anchorMillis = CashflowAnalyticsEngine.shiftAnchor(anchorMillis, period, -1)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("digest_prev_period")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一期")
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = digest.trend.periodTitle,
                                        style = NumericSmallStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.testTag("digest_period_label")
                                    )
                                    if (period == ReportPeriod.WEEK) {
                                        Text(
                                            text = digest.rangeSubtitle,
                                            style = NumericSmallStyle.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        anchorMillis = CashflowAnalyticsEngine.shiftAnchor(anchorMillis, period, 1)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("digest_next_period")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一期")
                                }
                            }
                        }
                    }

                    // 2. Clean Summary Card
                    item(key = "digest_masthead") {
                        val (gradeBg, gradeFg) = when (digest.gradeCode) {
                            "S", "A" -> semantic.safeContainer to semantic.safe
                            "B" -> semantic.warningContainer to semantic.warning
                            "C" -> semantic.dangerContainer to semantic.danger
                            else -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("digest_masthead_card"),
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
                                        text = digest.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 10.dp)
                                            .testTag("digest_title_text")
                                    )
                                    Surface(
                                        color = gradeBg,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("digest_grade_badge")
                                    ) {
                                        Text(
                                            text = "${digest.gradeCode} · ${digest.gradeLabel}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = gradeFg,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(thickness = 0.75.dp, color = semantic.hairline)

                                // Clean 2x2 Metrics Grid
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                    ) {
                                        DigestMetricCell(
                                            label = "收入",
                                            value = MoneyFormatter.formatFen(digest.trend.totalIncomeFen),
                                            color = semantic.safe,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 10.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(0.75.dp)
                                                .fillMaxHeight()
                                                .background(semantic.hairline)
                                        )
                                        DigestMetricCell(
                                            label = "支出",
                                            value = MoneyFormatter.formatFen(digest.trend.totalExpenseFen),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 12.dp)
                                        )
                                    }

                                    HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                    ) {
                                        val netSign = if (digest.trend.netSavedFen > 0L) "+" else ""
                                        DigestMetricCell(
                                            label = digest.trend.savingsRatePercent?.let { "净攒 · ${it}%" } ?: "净攒",
                                            value = "$netSign${MoneyFormatter.formatFen(digest.trend.netSavedFen)}",
                                            color = if (digest.trend.netSavedFen >= 0L) semantic.safe else semantic.danger,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 10.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(0.75.dp)
                                                .fillMaxHeight()
                                                .background(semantic.hairline)
                                        )
                                        DigestMetricCell(
                                            label = "日均支出",
                                            value = MoneyFormatter.formatFen(digest.avgDailyExpenseFen),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 12.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "${digest.txCount} 笔交易  ·  零消费 ${digest.zeroExpenseDaysCount} 天",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 3. Clean Trend Line Card
                    if (digest.txCount > 0) {
                        item(key = "digest_trend_curve") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                border = semantic.cardBorder,
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "收支走势",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            LegendDotLabel("收入", semantic.safe)
                                            LegendDotLabel("支出", semantic.danger)
                                            LegendDotLabel("净攒", MaterialTheme.colorScheme.secondary)
                                        }
                                    }

                                    CashflowTrendLineCanvas(
                                        buckets = digest.trend.buckets,
                                        period = period,
                                        showIncome = true,
                                        showExpense = true,
                                        showCumulativeNet = true,
                                        incomeColor = semantic.safe,
                                        expenseColor = semantic.danger,
                                        netColor = MaterialTheme.colorScheme.secondary,
                                        selectedIndex = selectedChartIdx,
                                        onSelectIndex = { selectedChartIdx = it },
                                        chartHeight = 128.dp,
                                        interactive = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    selectedChartIdx?.let { idx ->
                                        digest.trend.buckets.getOrNull(idx)?.let { pt ->
                                            Text(
                                                text = "${pt.fullLabel}  ·  收 ${MoneyFormatter.formatFen(pt.incomeFen)}  ·  支 ${MoneyFormatter.formatFen(pt.expenseFen)}",
                                                style = NumericSmallStyle.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Clean Top Categories Card
                    if (digest.topCategories.isNotEmpty()) {
                        item(key = "digest_top_categories") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("digest_top_categories_card"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                border = semantic.cardBorder,
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "支出排行",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )

                                    digest.topCategories.forEachIndexed { idx, cat ->
                                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = cat.categoryName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "${MoneyFormatter.formatFen(cat.amountFen)}  ${cat.sharePercent}%",
                                                    style = NumericSmallStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(semantic.trackBackground)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth((cat.sharePercent / 100f).coerceIn(0.04f, 1f))
                                                        .fillMaxHeight()
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(if (cat.isEssential) MaterialTheme.colorScheme.primary else semantic.warning)
                                                )
                                            }
                                        }
                                        if (idx < digest.topCategories.lastIndex) {
                                            HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline.copy(alpha = 0.6f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Key Moments (Max Single + Most Frequent)
                    if (digest.topSingleExpense != null || digest.topFrequentMerchant != null) {
                        item(key = "digest_moments") {
                            val dateFmt = remember { SimpleDateFormat("M/d", Locale.US) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                digest.topSingleExpense?.let { maxTx ->
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                        border = semantic.cardBorder,
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "最大单笔",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = MoneyFormatter.formatFen(maxTx.amountFen),
                                                style = NumericMediumStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                                color = semantic.danger
                                            )
                                            Text(
                                                text = "${maxTx.merchant} · ${dateFmt.format(Date(maxTx.occurredAtMillis))}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                digest.topFrequentMerchant?.let { freq ->
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                        border = semantic.cardBorder,
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "最常消费",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${freq.count} 次",
                                                style = NumericMediumStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${freq.merchant} · ${MoneyFormatter.formatFen(freq.totalFen)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 6. Concise Key Takeaways (max 3 short items, no walls of text)
                    val concisePoints = (digest.highlights.take(2) + digest.actionTips.take(1)).distinct().take(3)
                    if (concisePoints.isNotEmpty()) {
                        item(key = "digest_highlights_and_tips") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("digest_highlights_card"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                border = semantic.cardBorder,
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "本期要点",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    concisePoints.forEach { line ->
                                        Text(
                                            text = "· $line",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 7. Deep-dive CTA
                    item(key = "digest_ask_agent_cta") {
                        Button(
                            onClick = {
                                val prompt = CashflowAnalyticsEngine.buildAgentDeepDivePrompt(digest)
                                onDismiss()
                                onAskAgent(prompt)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("digest_ask_agent_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("助手解读本期${period.digestLabel}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DigestMetricCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = NumericMediumStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LegendDotLabel(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
