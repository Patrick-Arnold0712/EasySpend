package com.example.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ApiProviderType
import com.example.data.local.ThemeMode
import com.example.domain.finance.MoneyFormatter
import com.example.service.PermissionSetupManager
import com.example.ui.ApiTestFeedback
import com.example.ui.EasySpendUiState
import com.example.ui.components.AnimatedStatusPill
import com.example.ui.components.ModularActionTile
import com.example.ui.components.SilkyModuleTransition
import com.example.ui.motion.EasySpendMotion
import com.example.ui.motion.FluidSegmentOption
import com.example.ui.motion.FluidSegmentedBar
import com.example.ui.motion.TonalIconBadge
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
    onReplayOnboarding: () -> Unit = {},
    onOpenCategoryManager: () -> Unit = {},
    onOpenAccountManager: () -> Unit = {},
    onOpenPermissionWizard: () -> Unit = {},
    onToggleDetailedMode: (Boolean) -> Unit = {},
    onToggleLocationClassification: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val permSnapshot = remember(context) { PermissionSetupManager.snapshot(context) }
    val semantic = MaterialTheme.semanticColors
    var provider by remember(state.settings.apiProvider) { mutableStateOf(state.settings.apiProvider) }
    var baseUrl by remember(state.settings.apiBaseUrl) { mutableStateOf(state.settings.apiBaseUrl) }
    var apiKey by remember(state.settings.apiKey) { mutableStateOf(state.settings.apiKey) }
    var model by remember(state.settings.apiModel) { mutableStateOf(state.settings.apiModel) }
    var keyVisible by remember { mutableStateOf(false) }

    // 0 = 偏好模式, 1 = 账本权限, 2 = AI 模型, 3 = 审计
    var activeModule by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = {
            onClearApiTestFeedback()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("控制中心", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                AnimatedStatusPill(
                    text = if (permSnapshot.allGranted) "权限就绪" else "权限 ${permSnapshot.grantedCount}/${permSnapshot.totalCount}",
                    containerColor = if (permSnapshot.allGranted) semantic.safeContainer else semantic.warningContainer,
                    contentColor = if (permSnapshot.allGranted) semantic.safe else semantic.warning
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 510.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FluidSegmentedBar(
                    options = listOf(
                        FluidSegmentOption("偏好", "settings_module_general"),
                        FluidSegmentOption("账本", "settings_module_ledger"),
                        FluidSegmentOption("AI", "settings_module_ai"),
                        FluidSegmentOption("审计", "settings_module_audit")
                    ),
                    selectedIndex = activeModule,
                    onSelect = { activeModule = it }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SilkyModuleTransition(targetIndex = activeModule) { moduleIdx ->
                        when (moduleIdx) {
                            // Module 0: Appearance, Detailed Mode, Reminders, Onboarding
                            0 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SettingsModuleBlock(
                                    title = "外观主题",
                                    icon = Icons.Default.Palette
                                ) {
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
                                                Text(shortLabel, maxLines = 1, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }

                                SettingsSwitchCard(
                                    title = "详细版（资金库：零花 / 储蓄）",
                                    subtitle = "显示零花库与储蓄库，支持为每笔收支归属资金库并独立统计出入数据。",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    checked = state.settings.detailedModeEnabled,
                                    onCheckedChange = onToggleDetailedMode,
                                    switchTestTag = "detailed_mode_switch"
                                )

                                SettingsModuleBlock(
                                    title = "每日集中核对提醒",
                                    icon = Icons.Default.NotificationsActive
                                ) {
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

                                ModularActionTile(
                                    title = "重温新手引导与目标向导",
                                    caption = "随时回顾功能介绍、修改月生活费与存钱目标",
                                    icon = Icons.Default.School,
                                    onClick = onReplayOnboarding,
                                    testTag = "settings_replay_onboarding_button",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Module 1: Account, Categories, One-Tap Permissions, Location Classification
                            1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ModularActionTile(
                                        title = state.accountState.currentAccount?.displayLabel ?: "账号管理",
                                        caption = "切换/修改账号",
                                        icon = Icons.Default.AccountCircle,
                                        onClick = onOpenAccountManager,
                                        testTag = "settings_open_account_button",
                                        modifier = Modifier.weight(1f)
                                    )
                                    ModularActionTile(
                                        title = "分类管理",
                                        caption = "共 ${state.categories.size} 个分类",
                                        icon = Icons.Default.Category,
                                        onClick = onOpenCategoryManager,
                                        testTag = "settings_open_categories_button",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                ModularActionTile(
                                    title = "一键权限配置向导",
                                    caption = if (permSnapshot.allGranted) {
                                        "全部 4/4 核心权限已就绪"
                                    } else {
                                        "已就绪 ${permSnapshot.grantedCount}/${permSnapshot.totalCount}，一键补齐通知/定位/防掉线"
                                    },
                                    icon = Icons.Default.VerifiedUser,
                                    highlighted = !permSnapshot.allGranted,
                                    accentContainerColor = if (permSnapshot.allGranted) semantic.safeContainer else MaterialTheme.colorScheme.primaryContainer,
                                    accentIconTint = if (permSnapshot.allGranted) semantic.safe else MaterialTheme.colorScheme.primary,
                                    onClick = onOpenPermissionWizard,
                                    testTag = "settings_one_tap_permissions_button",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                SettingsSwitchCard(
                                    title = "读账同步定位精准分类",
                                    subtitle = "读取支付通知或记账时同步获取粗略位置，结合校园/医院/影院/商圈提升分类准度。",
                                    icon = Icons.Default.LocationOn,
                                    checked = state.settings.locationClassificationEnabled,
                                    onCheckedChange = onToggleLocationClassification,
                                    switchTestTag = "location_classification_switch"
                                )
                            }

                            // Module 2: AI Privacy Gate + Direct API Config
                            2 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SettingsSwitchCard(
                                    title = "允许 AI 分析脱敏账单",
                                    subtitle = "关闭后仅保留本地记账与预算计算，不向云端发送任何数据。",
                                    icon = Icons.Default.Security,
                                    checked = state.settings.aiCloudAnalysisEnabled,
                                    onCheckedChange = onToggleAiCloud,
                                    switchTestTag = "privacy_ai_switch"
                                )

                                SettingsModuleBlock(
                                    title = "大模型 API 直连配置",
                                    icon = Icons.Default.AutoAwesome
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
                            }

                            // Module 3: Tool Audit Logs
                            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "助手查账审计记录",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                    AnimatedStatusPill(text = "${state.toolLogs.size} 条")
                                }

                                if (state.toolLogs.isEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = RoundedCornerShape(14.dp),
                                        border = semantic.cardBorder,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "还没有调用过财务助手查账工具。每次 AI 查询账本都会在这里留下可审计记录。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                } else {
                                    state.toolLogs.take(15).forEach { log ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            shape = RoundedCornerShape(12.dp),
                                            border = semantic.cardBorder,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = "${log.toolName} · ${MoneyFormatter.formatTimestamp(log.executedAtMillis)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.primary
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
private fun SettingsModuleBlock(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        border = semantic.cardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TonalIconBadge(
                    icon = icon,
                    size = 28.dp,
                    iconSize = 15.dp
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String
) {
    val semantic = MaterialTheme.semanticColors
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        border = semantic.cardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TonalIconBadge(
                icon = icon,
                size = 32.dp,
                iconSize = 16.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(switchTestTag)
            )
        }
    }
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
