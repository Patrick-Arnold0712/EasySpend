package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ApiProviderType
import com.example.data.local.ThemeMode
import com.example.domain.finance.MoneyFormatter
import com.example.ui.ApiTestFeedback
import com.example.ui.EasySpendUiState
import com.example.ui.theme.semanticColors

@Composable
fun ApiConfigDialog(
    state: EasySpendUiState,
    onSaveApiConfig: (ApiProviderType, String, String, String) -> Unit,
    onTestApiConnection: (ApiProviderType, String, String, String) -> Unit,
    onClearFeedback: () -> Unit,
    onDismiss: () -> Unit
) {
    var provider by remember(state.settings.apiProvider) { mutableStateOf(state.settings.apiProvider) }
    var baseUrl by remember(state.settings.apiBaseUrl) { mutableStateOf(state.settings.apiBaseUrl) }
    var apiKey by remember(state.settings.apiKey) { mutableStateOf(state.settings.apiKey) }
    var model by remember(state.settings.apiModel) { mutableStateOf(state.settings.apiModel) }
    var keyVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            onClearFeedback()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("大模型 API 配置", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ApiConfigFormContent(
                    provider = provider,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    keyVisible = keyVisible,
                    feedback = state.apiTestFeedback,
                    onSelectProvider = { chosen ->
                        provider = chosen
                        baseUrl = chosen.defaultBaseUrl
                        model = chosen.defaultModel
                        onClearFeedback()
                    },
                    onBaseUrlChange = { baseUrl = it },
                    onApiKeyChange = { apiKey = it },
                    onModelChange = { model = it },
                    onToggleKeyVisible = { keyVisible = !keyVisible },
                    onTestConnection = { onTestApiConnection(provider, baseUrl, apiKey, model) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveApiConfig(provider, baseUrl, apiKey, model)
                    onClearFeedback()
                    onDismiss()
                },
                enabled = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank(),
                modifier = Modifier.testTag("save_api_config_button")
            ) {
                Text("保存配置")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onClearFeedback()
                    onDismiss()
                }
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
fun SettingsAndAuditDialog(
    state: EasySpendUiState,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleAiCloud: (Boolean) -> Unit,
    onSetDailyReviewTime: (Int, Int) -> Unit,
    onSaveApiConfig: (ApiProviderType, String, String, String) -> Unit,
    onTestApiConnection: (ApiProviderType, String, String, String) -> Unit,
    onClearApiTestFeedback: () -> Unit,
    onToggleBeginnerGuide: (Boolean) -> Unit = {},
    onOpenCategoryManager: () -> Unit = {},
    onOpenAccountManager: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    var provider by remember(state.settings.apiProvider) { mutableStateOf(state.settings.apiProvider) }
    var baseUrl by remember(state.settings.apiBaseUrl) { mutableStateOf(state.settings.apiBaseUrl) }
    var apiKey by remember(state.settings.apiKey) { mutableStateOf(state.settings.apiKey) }
    var model by remember(state.settings.apiModel) { mutableStateOf(state.settings.apiModel) }
    var keyVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            onClearApiTestFeedback()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("设置与大模型 API") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 0. Quick Entries: Local Account & Custom Categories
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenAccountManager,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_open_account_button")
                    ) {
                        Text(
                            text = "账号：${state.accountState.currentAccount?.displayLabel ?: "管理"}",
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenCategoryManager,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_open_categories_button")
                    ) {
                        Text(
                            text = "分类管理 (${state.categories.size})",
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

                // 1. User API Configuration
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("大模型 API 配置（直连调用）", style = MaterialTheme.typography.titleSmall)
                    ApiConfigFormContent(
                        provider = provider,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = model,
                        keyVisible = keyVisible,
                        feedback = state.apiTestFeedback,
                        onSelectProvider = { chosen ->
                            provider = chosen
                            baseUrl = chosen.defaultBaseUrl
                            model = chosen.defaultModel
                            onClearApiTestFeedback()
                        },
                        onBaseUrlChange = { baseUrl = it },
                        onApiKeyChange = { apiKey = it },
                        onModelChange = { model = it },
                        onToggleKeyVisible = { keyVisible = !keyVisible },
                        onTestConnection = { onTestApiConnection(provider, baseUrl, apiKey, model) }
                    )
                    Button(
                        onClick = { onSaveApiConfig(provider, baseUrl, apiKey, model) },
                        enabled = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_save_api_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text("保存 API 配置")
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

                // 2. Appearance Mode Selector (System / Light / Dark)
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
                                Text(shortLabel, maxLines = 1, fontSize = 13.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

                // 3. Privacy Gate for Cloud AI
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

                // Beginner Guide Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("显示「新手快速体验」卡片", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "关闭总览页新手体验卡片后，可在此重新开启。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = !state.settings.hideBeginnerGuide,
                        onCheckedChange = onToggleBeginnerGuide,
                        modifier = Modifier.testTag("show_beginner_guide_switch")
                    )
                }

                HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

                // 4. Daily Review Reminder Time
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

                // 5. Tool Audit Logs
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
                        state.toolLogs.take(15).forEach { log ->
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onClearApiTestFeedback()
                    onDismiss()
                }
            ) { Text("完成") }
        }
    )
}

@Composable
private fun ApiConfigFormContent(
    provider: ApiProviderType,
    baseUrl: String,
    apiKey: String,
    model: String,
    keyVisible: Boolean,
    feedback: ApiTestFeedback,
    onSelectProvider: (ApiProviderType) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onToggleKeyVisible: () -> Unit,
    onTestConnection: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors

    Text(
        text = "选择常用模板（手机直连，支持国内接口与中转站）：",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ApiProviderType.entries.forEach { item ->
            FilterChip(
                selected = provider == item,
                onClick = { onSelectProvider(item) },
                label = { Text(item.label, maxLines = 1) },
                modifier = Modifier.testTag("api_provider_${item.name.lowercase()}")
            )
        }
    }

    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text("API Key（仅保存在本机）") },
        placeholder = { Text("sk-... 或 Gemini API Key") },
        singleLine = true,
        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleKeyVisible) {
                Icon(
                    imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (keyVisible) "隐藏密钥" else "显示密钥"
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("api_key_input")
    )

    OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        label = { Text("Base URL 接口地址") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("api_base_url_input")
    )

    OutlinedTextField(
        value = model,
        onValueChange = onModelChange,
        label = { Text("模型名称 Model") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("api_model_input")
    )

    OutlinedButton(
        onClick = onTestConnection,
        enabled = !feedback.isTesting && apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("test_api_connection_button"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (feedback.isTesting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("正在测试连接...")
        } else {
            Text("测试并保存接口")
        }
    }

    if (!feedback.message.isNullOrBlank()) {
        Surface(
            color = if (feedback.isSuccess) semantic.safeContainer else semantic.dangerContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("api_test_feedback_banner")
        ) {
            Text(
                text = feedback.message,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = if (feedback.isSuccess) semantic.safe else semantic.danger,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}
