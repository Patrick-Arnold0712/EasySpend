package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.PendingBudgetProposal
import com.example.ai.ProposalStatus
import com.example.ai.ToolExecutionResult
import com.example.domain.finance.MoneyFormatter
import com.example.ui.AgentChatMessage
import com.example.ui.EasySpendUiState
import com.example.ui.theme.semanticColors

@Composable
fun AgentScreen(
    state: EasySpendUiState,
    prefillPrompt: String?,
    onClearPrefill: () -> Unit,
    onSendMessage: (String) -> Unit,
    onConfirmProposal: (String, PendingBudgetProposal) -> Unit,
    onRejectProposal: (String, PendingBudgetProposal) -> Unit,
    onOpenApiSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val semantic = MaterialTheme.semanticColors

    LaunchedEffect(prefillPrompt) {
        if (!prefillPrompt.isNullOrBlank()) {
            onSendMessage(prefillPrompt)
            onClearPrefill()
        }
    }

    LaunchedEffect(state.agentMessages.size, state.streamingAgentText) {
        val lastIndex = state.agentMessages.lastIndex + if (state.isAgentResponding) 1 else 0
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    val quickPrompts = listOf(
        "这个月还能存下 1000 元吗？",
        "多花 300 元学习资料费，预算怎么调？",
        "帮我看看哪些支出最容易超支"
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            // API Key status / quick setup bar
            Surface(
                color = if (state.settings.isApiConfigured) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    semantic.warningContainer
                },
                shape = RoundedCornerShape(14.dp),
                border = semantic.cardBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("agent_api_status_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = if (state.settings.isApiConfigured) MaterialTheme.colorScheme.primary else semantic.warning,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = if (state.settings.isApiConfigured) {
                                    "已接入 ${state.settings.apiProvider.label} · ${state.settings.apiModel}"
                                } else {
                                    "请先配置大模型 API Key"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (state.settings.isApiConfigured) {
                                    "支持随时切换 DeepSeek / 通义千问 / Gemini / OpenAI"
                                } else {
                                    "填写个人 API Key 后即可在手机直接调用财务助手"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (state.settings.isApiConfigured) {
                        TextButton(
                            onClick = onOpenApiSettings,
                            modifier = Modifier.testTag("open_api_config_button")
                        ) {
                            Text("配置 API")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onOpenApiSettings,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("open_api_config_button")
                        ) {
                            Text("去配置")
                        }
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.agentMessages, key = { it.id }) { msg ->
                    AgentMessageBubble(
                        message = msg,
                        onConfirmProposal = { prop -> onConfirmProposal(msg.id, prop) },
                        onRejectProposal = { prop -> onRejectProposal(msg.id, prop) }
                    )
                }
                if (state.isAgentResponding) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.streamingAgentText.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = semantic.cardBorder,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.widthIn(max = 520.dp)
                                ) {
                                    Text(
                                        text = state.streamingAgentText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = state.agentProgressText ?: "助手正在核对账本与预算...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Quick Prompt Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEachIndexed { idx, prompt ->
                    AssistChip(
                        onClick = { onSendMessage(prompt) },
                        label = { Text(prompt) },
                        enabled = !state.isAgentResponding,
                        modifier = Modifier.testTag("agent_quick_prompt_$idx")
                    )
                }
            }

            // Input Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("问问预算、存钱计划或花销分析...") },
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("agent_input_field")
                )
                FilledIconButton(
                    onClick = {
                        val toSend = inputText.trim()
                        if (toSend.isNotEmpty()) {
                            inputText = ""
                            onSendMessage(toSend)
                        }
                    },
                    enabled = inputText.isNotBlank() && !state.isAgentResponding,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("agent_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send_message)
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentMessageBubble(
    message: AgentChatMessage,
    onConfirmProposal: (PendingBudgetProposal) -> Unit,
    onRejectProposal: (PendingBudgetProposal) -> Unit
) {
    val semantic = MaterialTheme.semanticColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = when {
                message.isUser -> MaterialTheme.colorScheme.primary
                message.isError -> semantic.dangerContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
            border = if (message.isUser || message.isError) null else semantic.cardBorder,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    message.isUser -> MaterialTheme.colorScheme.onPrimary
                    message.isError -> semantic.danger
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(14.dp)
            )
        }

        if (message.toolTraces.isNotEmpty()) {
            ToolTraceAccordion(traces = message.toolTraces)
        }

        message.proposals.forEach { proposal ->
            BudgetProposalConfirmationCard(
                proposal = proposal,
                onConfirm = { onConfirmProposal(proposal) },
                onReject = { onRejectProposal(proposal) }
            )
        }
    }
}

@Composable
private fun ToolTraceAccordion(traces: List<ToolExecutionResult>) {
    var expanded by remember { mutableStateOf(false) }
    val semantic = MaterialTheme.semanticColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "已核对 ${traces.size} 项账本数据（点击查看依据）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    traces.forEach { t ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "${t.toolName}(${t.argumentsJson})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = t.humanSummary,
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
}

@Composable
private fun BudgetProposalConfirmationCard(
    proposal: PendingBudgetProposal,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("proposal_card_${proposal.categoryId}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "建议调整「${proposal.categoryName}」预算",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${MoneyFormatter.formatFen(proposal.currentLimitFen)} → ${MoneyFormatter.formatFen(proposal.proposedLimitFen)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = proposal.reason,
                style = MaterialTheme.typography.bodySmall
            )

            when (proposal.status) {
                ProposalStatus.PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reject_proposal_${proposal.categoryId}")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("暂不调整")
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_proposal_${proposal.categoryId}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("确认调整")
                        }
                    }
                }
                ProposalStatus.CONFIRMED -> {
                    Text(
                        text = "✓ 已按你的确认更新此预算",
                        style = MaterialTheme.typography.labelLarge,
                        color = semantic.safe
                    )
                }
                ProposalStatus.REJECTED -> {
                    Text(
                        text = "已保留原有预算不变",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
