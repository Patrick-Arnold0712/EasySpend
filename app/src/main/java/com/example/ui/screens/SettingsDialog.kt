package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.ThemeMode
import com.example.domain.finance.MoneyFormatter
import com.example.ui.EasySpendUiState
import com.example.ui.theme.semanticColors

@Composable
fun SettingsAndAuditDialog(
    state: EasySpendUiState,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleAiCloud: (Boolean) -> Unit,
    onSetDailyReviewTime: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("偏好与隐私") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Appearance Mode Selector (System / Light / Dark)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("外观主题", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val modes = listOf(
                            ThemeMode.SYSTEM to "跟随系统",
                            ThemeMode.LIGHT to "浅色",
                            ThemeMode.DARK to "深色"
                        )
                        modes.forEachIndexed { idx, (mode, shortLabel) ->
                            SegmentedButton(
                                selected = state.settings.themeMode == mode,
                                onClick = { onSelectThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                                modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                            ) {
                                Text(shortLabel, maxLines = 1)
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

                // Privacy Gate for Cloud AI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("允许 AI 分析脱敏账单", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "关闭后只保留本地记账与预算计算，不向云端发送任何数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.settings.aiCloudAnalysisEnabled,
                        onCheckedChange = onToggleAiCloud,
                        modifier = Modifier.testTag("privacy_ai_switch")
                    )
                }

                HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

                // Daily Review Reminder Time
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "每日集中核对提醒时间",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "只在每天固定时间提醒一次，不逐笔弹窗打扰。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(20 to 0, 21 to 30, 22 to 30).forEach { (h, m) ->
                            val selected = state.settings.dailyReviewHour == h && state.settings.dailyReviewMinute == m
                            FilterChip(
                                selected = selected,
                                onClick = { onSetDailyReviewTime(h, m) },
                                label = { Text(String.format("%02d:%02d", h, m)) }
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

                // Tool Audit Logs
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "助手查账记录 (${state.toolLogs.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (state.toolLogs.isEmpty()) {
                        Text(
                            text = "还没有调用过助手查账工具。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(state.toolLogs, key = { it.id }) { log ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "${log.toolName} · ${MoneyFormatter.formatTimestamp(log.executedAtMillis)}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            text = log.resultSummary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}
