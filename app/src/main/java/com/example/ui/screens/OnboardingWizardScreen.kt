package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.VerifiedUser
import com.example.domain.finance.MoneyFormatter
import com.example.service.AppPermissionSnapshot
import com.example.service.PaymentNotificationListenerService
import com.example.service.PermissionSetupManager
import com.example.ui.EasySpendUiState
import com.example.ui.motion.EasySpendMotion
import com.example.ui.motion.LiveStatusDot
import com.example.ui.motion.StaggeredEntrance
import com.example.ui.motion.TonalIconBadge
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors

@Composable
fun OnboardingWizardScreen(
    state: EasySpendUiState,
    isReplay: Boolean,
    onOpenApiConfig: () -> Unit,
    onComplete: (
        monthlyAllowanceYuan: String,
        savingsTargetYuan: String,
        expectedPartTimeYuan: String,
        recordAllowanceIncomeNow: Boolean,
        loadDemoData: Boolean
    ) -> Unit,
    onSkip: () -> Unit,
    onOpenPermissionWizard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val semantic = MaterialTheme.semanticColors
    val accountLabel = state.accountState.currentAccount?.displayLabel ?: "同学"

    var currentPage by remember { mutableIntStateOf(0) }

    val initialAllowance = remember(state.savingsGoal) {
        val fen = state.savingsGoal?.livingAllowanceExpectedFen ?: 0L
        if (fen > 0L) formatYuanPlain(fen) else "1800"
    }
    val initialSavings = remember(state.savingsGoal) {
        val fen = state.savingsGoal?.targetFen ?: 0L
        if (fen > 0L) formatYuanPlain(fen) else "500"
    }
    val initialPartTime = remember(state.savingsGoal) {
        val fen = state.savingsGoal?.partTimeExpectedFen ?: 0L
        if (fen > 0L) formatYuanPlain(fen) else ""
    }

    var allowanceInput by remember(initialAllowance) { mutableStateOf(initialAllowance) }
    var savingsInput by remember(initialSavings) { mutableStateOf(initialSavings) }
    var partTimeInput by remember(initialPartTime) { mutableStateOf(initialPartTime) }
    var recordIncomeNow by remember(isReplay, state.transactions.size) {
        mutableStateOf(!isReplay && state.transactions.isEmpty())
    }
    var loadDemoData by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var permSnapshot by remember {
        mutableStateOf(PermissionSetupManager.snapshot(context))
    }
    val listenerEnabled = permSnapshot.notificationListenerGranted
    val listenerBound by PaymentNotificationListenerService.isListenerBoundFlow.collectAsStateWithLifecycle()
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permSnapshot = PermissionSetupManager.snapshot(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = currentPage > 0 || isReplay) {
        if (currentPage > 0) {
            currentPage -= 1
        } else if (isReplay) {
            onSkip()
        }
    }

    val stepTitles = listOf("认识易支道", "设定收支目标", "配置自动抓账")

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("onboarding_wizard_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${currentPage + 1} / 3",
                                style = NumericSmallStyle.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = stepTitles[currentPage],
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSkip()
                        },
                        modifier = Modifier.testTag("onboarding_skip_button")
                    ) {
                        Text(if (isReplay) "关闭" else "先跳过")
                    }
                }

                // Segmented spring step indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (idx in 0 until 3) {
                        val fill by animateFloatAsState(
                            targetValue = if (idx <= currentPage) 1f else 0f,
                            animationSpec = EasySpendMotion.FluidSpring,
                            label = "onboardingStepFill_$idx"
                        )
                        val trackColor = semantic.trackBackground
                        val activeColor = MaterialTheme.colorScheme.primary
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        ) {
                            val r = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            drawRoundRect(color = trackColor, cornerRadius = r)
                            if (fill > 0f) {
                                drawRoundRect(
                                    color = activeColor,
                                    size = Size(size.width * fill, size.height),
                                    cornerRadius = r
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(thickness = 0.75.dp, color = semantic.hairline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = currentPage > 0,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(120))
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentPage -= 1
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
                            modifier = Modifier.testTag("onboarding_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("上一步")
                        }
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (currentPage < 2) {
                                currentPage += 1
                            } else {
                                onComplete(
                                    allowanceInput,
                                    savingsInput,
                                    partTimeInput,
                                    recordIncomeNow,
                                    loadDemoData
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(if (currentPage < 2) "onboarding_next_button" else "onboarding_finish_button")
                    ) {
                        Text(
                            text = when (currentPage) {
                                0 -> "开始个性化配置"
                                1 -> "保存目标，下一步"
                                else -> if (isReplay) "保存并返回主页" else "完成引导，开启易支道"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = if (currentPage < 2) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    val dir = if (targetState >= initialState) 1 else -1
                    (slideInHorizontally(EasySpendMotion.SpatialOffsetSpring) { it / 3 * dir } +
                        fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(260))) togetherWith
                        (slideOutHorizontally(EasySpendMotion.SpatialOffsetSpring) { -it / 4 * dir } +
                            fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                            scaleOut(targetScale = 0.97f, animationSpec = tween(180)))
                },
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                label = "onboardingPageTransition"
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (page) {
                        0 -> IntroDialoguePage(accountLabel = accountLabel)
                        1 -> BudgetFillInBlankPage(
                            allowanceInput = allowanceInput,
                            onAllowanceChange = { allowanceInput = it },
                            savingsInput = savingsInput,
                            onSavingsChange = { savingsInput = it },
                            partTimeInput = partTimeInput,
                            onPartTimeChange = { partTimeInput = it },
                            recordIncomeNow = recordIncomeNow,
                            onRecordIncomeNowChange = { recordIncomeNow = it }
                        )
                        else -> CaptureSetupPage(
                            permSnapshot = permSnapshot,
                            listenerEnabled = listenerEnabled,
                            listenerBound = listenerBound,
                            isApiConfigured = state.settings.isApiConfigured,
                            loadDemoData = loadDemoData,
                            onLoadDemoDataChange = { loadDemoData = it },
                            onOpenPermissionWizard = onOpenPermissionWizard,
                            onOpenNotificationSettings = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            onOpenApiConfig = onOpenApiConfig
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AssistantDialogueBubble(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.semanticColors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        TonalIconBadge(
            icon = Icons.Default.AutoAwesome,
            size = 40.dp,
            iconSize = 20.dp
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(
                topStart = 6.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            border = semantic.cardBorder,
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LiveStatusDot(color = MaterialTheme.colorScheme.primary, size = 6.dp)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun IntroDialoguePage(accountLabel: String) {
    StaggeredEntrance(index = 0) {
        AssistantDialogueBubble(
            title = "易支道引导助手",
            body = "你好，$accountLabel！欢迎开启你的专属本地账本。接下来花 30 秒，通过几句轻松对话帮你填好生活费、存钱目标并开启自动抓账。"
        )
    }

    StaggeredEntrance(index = 1) {
        FeatureHighlightRow(
            icon = Icons.Default.NotificationsActive,
            title = "自动抓账 & 账单查重导入",
            subtitle = "自动识别微信/支付宝扣款通知（支持校园轻松付多窗口连刷），也支持一键导入微信 .xlsx 与支付宝 .csv 官方账单。"
        )
    }

    StaggeredEntrance(index = 2) {
        FeatureHighlightRow(
            icon = Icons.Default.Category,
            title = "金额感知智能分类 & 自定义类别",
            subtitle = "同一商户按金额精准区分（如美团 ¥1.8 单车 vs ¥22.5 外卖），支持自由新建个性化分类。"
        )
    }

    StaggeredEntrance(index = 3) {
        FeatureHighlightRow(
            icon = Icons.Default.Savings,
            title = "存钱进度守护 & AI 财务顾问",
            subtitle = "实时追踪本月结余、净收入与支出，接入你的个人大模型 API 即可随时测算存钱计划并一键调预算。"
        )
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val semantic = MaterialTheme.semanticColors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            TonalIconBadge(icon = icon, size = 40.dp, iconSize = 20.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BudgetFillInBlankPage(
    allowanceInput: String,
    onAllowanceChange: (String) -> Unit,
    savingsInput: String,
    onSavingsChange: (String) -> Unit,
    partTimeInput: String,
    onPartTimeChange: (String) -> Unit,
    recordIncomeNow: Boolean,
    onRecordIncomeNowChange: (Boolean) -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    val haptic = LocalHapticFeedback.current

    AssistantDialogueBubble(
        title = "来填一份本月资金卡片",
        body = "把你的每月生活费和想存下的目标填进下面空格，我会自动帮你算好每日可支配预算。"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Blank 1: Monthly living allowance
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "① 我每个月大约有 _____ 元生活费 / 固定收入",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = allowanceInput,
                    onValueChange = { onAllowanceChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
                    placeholder = { Text("例如 1800") },
                    prefix = { Text("¥", style = NumericSmallStyle) },
                    suffix = { Text("元 / 月", style = MaterialTheme.typography.labelMedium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_allowance_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("1500", "1800", "2500", "3000").forEach { preset ->
                        FilterChip(
                            selected = allowanceInput == preset,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAllowanceChange(preset)
                            },
                            label = { Text("¥$preset") }
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

            // Blank 2: Monthly savings target
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "② 这个月我希望至少存下 _____ 元",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = savingsInput,
                    onValueChange = { onSavingsChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
                    placeholder = { Text("例如 500") },
                    prefix = { Text("¥", style = NumericSmallStyle) },
                    suffix = { Text("元 / 月", style = MaterialTheme.typography.labelMedium) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_savings_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("300", "500", "800", "1000").forEach { preset ->
                        FilterChip(
                            selected = savingsInput == preset,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSavingsChange(preset)
                            },
                            label = { Text("¥$preset") }
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

            // Blank 3: Optional expected extra income
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "③ 本月预计其他 / 兼职收入（选填）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = partTimeInput,
                    onValueChange = { onPartTimeChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
                    placeholder = { Text("没有可留空") },
                    prefix = { Text("¥", style = NumericSmallStyle) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_parttime_input")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "同步记为本月生活费入账",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "开启后主页结余与存钱进度会立刻显示你的初始资金",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = recordIncomeNow,
                    onCheckedChange = onRecordIncomeNowChange,
                    modifier = Modifier.testTag("onboarding_record_income_switch")
                )
            }
        }
    }

    // Live interactive calculation reply bubble
    val allowanceFen = MoneyFormatter.parseYuanToFen(allowanceInput)?.coerceAtLeast(0L) ?: 0L
    val savingsFen = MoneyFormatter.parseYuanToFen(savingsInput)?.coerceAtLeast(0L) ?: 0L
    val partTimeFen = MoneyFormatter.parseYuanToFen(partTimeInput)?.coerceAtLeast(0L) ?: 0L
    val totalPlannedFen = allowanceFen + partTimeFen
    val spendableFen = totalPlannedFen - savingsFen

    Surface(
        color = if (spendableFen >= 0L) semantic.safeContainer else semantic.warningContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_live_plan_bubble")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val previewText = when {
                totalPlannedFen == 0L -> "填上生活费和存钱目标后，这里会实时帮你测算每月可消费额度。"
                spendableFen >= 0L -> {
                    val dailyFen = spendableFen / 30L
                    "计划很清晰！扣除目标存款后，本月可用消费额度约 ${MoneyFormatter.formatFen(spendableFen)}（日均约 ${MoneyFormatter.formatFen(dailyFen)}）。"
                }
                else -> "当前存钱目标高于预计总收入，建议适当下调存钱目标，优先保障日常饮食与必要开支。"
            }
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = if (spendableFen >= 0L) semantic.safe else semantic.warning
            )
        }
    }
}

@Composable
private fun CaptureSetupPage(
    permSnapshot: AppPermissionSnapshot,
    listenerEnabled: Boolean,
    listenerBound: Boolean,
    isApiConfigured: Boolean,
    loadDemoData: Boolean,
    onLoadDemoDataChange: (Boolean) -> Unit,
    onOpenPermissionWizard: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenApiConfig: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors

    AssistantDialogueBubble(
        title = "最后一步：一键配置抓账与定位权限",
        body = "点击下方「一键配置全部权限」，即可自动完成通知读取、定位精准分类与后台防掉线配置，省去手动翻找系统设置。"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiveStatusDot(
                        color = when {
                            permSnapshot.allGranted -> semantic.safe
                            listenerEnabled && listenerBound -> semantic.safe
                            listenerEnabled -> semantic.warning
                            else -> MaterialTheme.colorScheme.outline
                        },
                        size = 8.dp
                    )
                    Text(
                        text = when {
                            permSnapshot.allGranted -> "全部 4 项抓账权限已就绪"
                            listenerEnabled && listenerBound -> "通知抓账已在线（已就绪 ${permSnapshot.grantedCount}/${permSnapshot.totalCount}）"
                            listenerEnabled -> "通知抓账已授权（已就绪 ${permSnapshot.grantedCount}/${permSnapshot.totalCount}）"
                            else -> "自动抓账权限待配置（${permSnapshot.grantedCount}/${permSnapshot.totalCount}）"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Button(
                onClick = onOpenPermissionWizard,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_one_tap_permissions_button")
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (permSnapshot.allGranted) "查看全部权限状态 (4/4)" else "一键配置全部权限 (${permSnapshot.grantedCount}/${permSnapshot.totalCount})"
                )
            }

            OutlinedButton(
                onClick = onOpenNotificationSettings,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_open_notif_settings_button")
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (listenerEnabled) "单独管理通知读取权限" else "单独开启支付通知读取")
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "防漏抓小贴士（特别是微信自动扣款 / 饭堂连刷）",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "1. 微信 → 设置 → 新消息通知 → 打开「通知显示消息详情」\n" +
                            "2. 微信 → 微信支付公众号 → 关闭「消息免打扰」\n" +
                            "3. 手机电池管理将「易支道」设为允许后台运行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Companion configuration card: AI API + optional demo dataset
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = semantic.cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("配置大模型 API（可选）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (isApiConfigured) "已配置，财务助手随时可用" else "支持 DeepSeek / 通义千问 / OpenAI / Gemini",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onOpenApiConfig,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("onboarding_open_api_button")
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isApiConfigured) "修改" else "去配置", fontSize = 13.sp)
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("顺便载入一套体验示例账单", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "想先看看图表与预算预警效果？打开此开关即可体验。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = loadDemoData,
                    onCheckedChange = onLoadDemoDataChange,
                    modifier = Modifier.testTag("onboarding_demo_switch")
                )
            }
        }
    }
}

private fun formatYuanPlain(fen: Long): String {
    return if (fen % 100L == 0L) {
        (fen / 100L).toString()
    } else {
        String.format("%.2f", fen / 100.0)
    }
}
