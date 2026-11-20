package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FundVault
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.VaultBreakdown
import com.example.domain.finance.VaultPortfolioSummary
import com.example.ui.motion.springClickable
import com.example.ui.theme.NumericMediumStyle
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors

@Composable
fun FundVaultSummaryCard(
    monthlySummary: VaultPortfolioSummary,
    allTimeSummary: VaultPortfolioSummary,
    onHideDetailedMode: () -> Unit,
    onSelectVault: ((FundVault) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.semanticColors
    var showAllTime by remember { mutableStateOf(false) }
    val active = if (showAllTime) allTimeSummary else monthlySummary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("fund_vault_summary_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "资金库 · 零花 / 储蓄",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (showAllTime) "累计双库独立收支结余" else "本月零花钱与储蓄资金流向",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onHideDetailedMode,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("vault_hide_detailed_button")
                ) {
                    Text("切回简洁版", fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !showAllTime,
                    onClick = { showAllTime = false },
                    label = { Text("本月", fontSize = 12.sp) },
                    modifier = Modifier.testTag("vault_scope_month")
                )
                FilterChip(
                    selected = showAllTime,
                    onClick = { showAllTime = true },
                    label = { Text("累计", fontSize = 12.sp) },
                    modifier = Modifier.testTag("vault_scope_all")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VaultTile(
                    breakdown = active.pocket,
                    icon = Icons.Default.AccountBalanceWallet,
                    accentColor = MaterialTheme.colorScheme.primary,
                    tileTag = "vault_card_pocket",
                    onClick = onSelectVault?.let { cb -> { cb(FundVault.POCKET) } },
                    modifier = Modifier.weight(1f)
                )
                VaultTile(
                    breakdown = active.savings,
                    icon = Icons.Default.AccountBalance,
                    accentColor = semantic.safe,
                    tileTag = "vault_card_savings",
                    onClick = onSelectVault?.let { cb -> { cb(FundVault.SAVINGS) } },
                    modifier = Modifier.weight(1f)
                )
            }

            // Dual-vault flow comparison bar
            val pocketFlowFen = (active.pocket.incomeFen + active.pocket.netExpenseFen).coerceAtLeast(0L)
            val savingsFlowFen = (active.savings.incomeFen + active.savings.netExpenseFen).coerceAtLeast(0L)
            val totalFlowFen = pocketFlowFen + savingsFlowFen
            if (totalFlowFen > 0L) {
                val pocketShare = (pocketFlowFen.toFloat() / totalFlowFen.toFloat()).coerceIn(0f, 1f)
                val pocketColor = MaterialTheme.colorScheme.primary
                val savingsColor = semantic.safe
                val trackColor = semantic.trackBackground

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "零花流水占比 ${(pocketShare * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "储蓄流水占比 ${100 - (pocketShare * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    ) {
                        val r = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        drawRoundRect(color = trackColor, cornerRadius = r)
                        val pocketWidth = size.width * pocketShare
                        if (pocketWidth > 0f) {
                            drawRoundRect(
                                color = pocketColor,
                                size = Size(pocketWidth, size.height),
                                cornerRadius = r
                            )
                        }
                        val savingsWidth = size.width - pocketWidth
                        if (savingsWidth > 0f) {
                            drawRoundRect(
                                color = savingsColor,
                                topLeft = Offset(pocketWidth, 0f),
                                size = Size(savingsWidth, size.height),
                                cornerRadius = r
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultTile(
    breakdown: VaultBreakdown,
    icon: ImageVector,
    accentColor: Color,
    tileTag: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.semanticColors
    val clickableMod = if (onClick != null) {
        Modifier.springClickable(pressedScale = 0.98f, onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = semantic.cardBorder,
        modifier = modifier
            .then(clickableMod)
            .testTag(tileTag)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = breakdown.vault.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "净结余",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = MoneyFormatter.formatFen(breakdown.netBalanceFen),
                    style = NumericMediumStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    color = if (breakdown.netBalanceFen >= 0L) accentColor else semantic.danger,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "入 (${breakdown.incomeCount})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "+${MoneyFormatter.formatFen(breakdown.incomeFen)}",
                    style = NumericSmallStyle.copy(fontSize = 11.sp),
                    color = semantic.safe,
                    maxLines = 1
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "出 (${breakdown.expenseCount})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "-${MoneyFormatter.formatFen(breakdown.netExpenseFen)}",
                    style = NumericSmallStyle.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}
