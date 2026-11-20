package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.CandidateResolution
import com.example.data.model.CategoryEntity
import com.example.data.model.DiscrepancyType
import com.example.data.model.FundVault
import com.example.data.model.FundingSource
import com.example.data.model.ImportCandidateEntity
import com.example.data.model.PaymentChannel
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import com.example.domain.finance.MoneyFormatter
import com.example.service.LocationContextResolver
import com.example.service.NotificationKeepAliveService
import com.example.service.PaymentNotificationListenerService
import com.example.service.PermissionSetupManager
import com.example.ui.EasySpendUiState
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.ui.components.AnimatedStatusPill
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ModularAccordionCard
import com.example.ui.components.ModularActionTile
import com.example.ui.components.ModularSectionHeader
import com.example.ui.components.SilkyModuleTransition
import com.example.ui.motion.FluidSegmentOption
import com.example.ui.motion.FluidSegmentedBar
import com.example.ui.motion.LiveStatusDot
import com.example.ui.motion.SpringAnimatedVisibility
import com.example.ui.motion.TonalIconBadge
import com.example.ui.motion.springClickable
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsAndImportScreen(
    state: EasySpendUiState,
    initialTab: Int = 0,
    onImportCsvUri: (Uri, PaymentChannel?) -> Unit,
    onLoadDemoQueue: () -> Unit,
    onSimulateNotification: (Boolean) -> Unit,
    onRunAiBatchClassify: () -> Unit,
    onConfirmAllClean: () -> Unit,
    onConfirmCandidate: (Long, String, FundingSource, Boolean, String) -> Unit,
    onSkipOrDismissCandidate: (Long, CandidateResolution) -> Unit,
    onAddManualTransaction: (String, TransactionDirection, PaymentChannel, FundingSource, String, String, String) -> Unit,
    onUpdateTxCategory: (Long, String, Boolean) -> Unit,
    onDeleteTx: (Long) -> Unit,
    onUndoLastLedgerAction: () -> Unit = {},
    onExportRecordsUri: (Uri) -> Unit = {},
    onAutoImportRecordsUri: (Uri) -> Unit = {},
    onOpenCategoryManager: () -> Unit = {},
    onToggleBackgroundKeepAlive: (Boolean) -> Unit = {},
    initialVaultFilter: FundVault? = null,
    onOpenPermissionWizard: () -> Unit = {},
    onToggleLocationClassification: (Boolean) -> Unit = {},
    onConfirmCandidateWithVault: ((Long, String, FundingSource, Boolean, String, FundVault) -> Unit)? = null,
    onAddManualTransactionDetailed: ((String, TransactionDirection, PaymentChannel, FundingSource, String, String, String, FundVault, String) -> Unit)? = null,
    onUpdateTxCategoryAndVault: ((Long, String, FundVault, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val semantic = MaterialTheme.semanticColors
    val detailedMode = state.settings.detailedModeEnabled
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var pendingChannelPicker by remember { mutableStateOf<PaymentChannel?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var directionFilter by remember { mutableStateOf<TransactionDirection?>(null) }
    var vaultFilter by remember(initialVaultFilter, detailedMode) {
        mutableStateOf(if (detailedMode) initialVaultFilter else null)
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportCsvUri(uri, pendingChannelPicker)
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            onExportRecordsUri(uri)
        }
    }

    val autoImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onAutoImportRecordsUri(uri)
        }
    }

    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var permSnapshot by remember {
        mutableStateOf(PermissionSetupManager.snapshot(context))
    }
    var listenerEnabled by remember {
        mutableStateOf(permSnapshot.notificationListenerGranted)
    }
    var batteryIgnored by remember {
        mutableStateOf(permSnapshot.batteryOptimizationIgnored)
    }
    var showCaptureDiagnostics by remember { mutableStateOf(false) }
    var rebindFeedback by remember { mutableStateOf<String?>(null) }
    var isRebinding by remember { mutableStateOf(false) }
    val listenerBound by PaymentNotificationListenerService.isListenerBoundFlow.collectAsStateWithLifecycle()
    val keepAliveRunning by NotificationKeepAliveService.isRunningFlow.collectAsStateWithLifecycle()
    val captureHealth by PaymentNotificationListenerService.captureHealthFlow.collectAsStateWithLifecycle()
    val recentDiagnostics by PaymentNotificationListenerService.recentDiagnosticsFlow.collectAsStateWithLifecycle()

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permSnapshot = PermissionSetupManager.snapshot(context)
        onToggleBackgroundKeepAlive(true)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permSnapshot = PermissionSetupManager.snapshot(context)
        onToggleLocationClassification(true)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val updated = PermissionSetupManager.snapshot(context)
                permSnapshot = updated
                listenerEnabled = updated.notificationListenerGranted
                batteryIgnored = updated.batteryOptimizationIgnored
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showImportToolbox by rememberSaveable { mutableStateOf(true) }
    var showLedgerFilters by rememberSaveable(detailedMode, initialVaultFilter) {
        mutableStateOf(detailedMode || initialVaultFilter != null)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            FluidSegmentedBar(
                options = listOf(
                    FluidSegmentOption("待核对 (${state.pendingCandidates.size})", "tab_import_review"),
                    FluidSegmentOption("已入账 (${state.transactions.size})", "tab_formal_ledger")
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SilkyModuleTransition(
                targetIndex = selectedTab,
                modifier = Modifier.weight(1f)
            ) { activeTab ->
            if (activeTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("import_review_lazy_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Module 1: Auto-Capture & Anti-Kill Guard Status Card
                    item(key = "capture_guard_module") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.large,
                            border = semantic.cardBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notification_listener_status_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LiveStatusDot(
                                        color = when {
                                            !listenerEnabled -> MaterialTheme.colorScheme.outline
                                            listenerBound -> semantic.safe
                                            else -> semantic.warning
                                        },
                                        size = 8.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "自动抓账与防掉线守护",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        val statusText = when {
                                            !listenerEnabled -> "未授权（可先使用下方模拟通知体验）"
                                            listenerBound && keepAliveRunning -> "后台守护在线 · 息屏/切后台持续监听"
                                            listenerBound -> "已在线 · 建议开启后台守护与电池放行"
                                            else -> "连接暂停 · 点「防掉线」重连并补录"
                                        }
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = { showCaptureDiagnostics = !showCaptureDiagnostics },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("toggle_notification_diagnostics_button")
                                    ) {
                                        Text(if (showCaptureDiagnostics) "收起" else "防掉线", fontSize = 12.sp)
                                    }
                                    TextButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("open_notification_settings_button")
                                    ) {
                                        Text(if (listenerEnabled) "管理" else "去开启", fontSize = 12.sp)
                                    }
                                }

                                if (!permSnapshot.allGranted) {
                                    FilledTonalButton(
                                        onClick = onOpenPermissionWizard,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("open_one_tap_permissions_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "一键配置全部权限（已就绪 ${permSnapshot.grantedCount}/${permSnapshot.totalCount}）",
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                SpringAnimatedVisibility(visible = showCaptureDiagnostics) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        HorizontalDivider(color = semantic.hairline)

                                        // 0. Location-assisted Classification Switch
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "读账同步定位精准分类",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = if (permSnapshot.locationGranted) {
                                                        "已获粗略定位权限，抓账时自动结合消费地点辅助分类"
                                                    } else {
                                                        "开启后将在读账时同步获取粗略位置，提升个人收款码分类准度"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Switch(
                                                checked = state.settings.locationClassificationEnabled,
                                                onCheckedChange = { wantEnabled ->
                                                    if (wantEnabled && !permSnapshot.locationGranted) {
                                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                                        return@Switch
                                                    }
                                                    onToggleLocationClassification(wantEnabled)
                                                },
                                                modifier = Modifier.testTag("ledger_location_classification_switch")
                                            )
                                        }

                                        // 1. Background Keep-Alive Foreground Monitor Switch
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "后台常驻防掉线守护",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "通过静默守护通知防止息屏/划掉后台后抓账失效，亮屏解锁自动巡检补录",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Switch(
                                                checked = state.settings.backgroundKeepAliveEnabled,
                                                onCheckedChange = { wantEnabled ->
                                                    if (wantEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                        val granted = ContextCompat.checkSelfPermission(
                                                            context,
                                                            Manifest.permission.POST_NOTIFICATIONS
                                                        ) == PackageManager.PERMISSION_GRANTED
                                                        if (!granted) {
                                                            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                            return@Switch
                                                        }
                                                    }
                                                    onToggleBackgroundKeepAlive(wantEnabled)
                                                },
                                                modifier = Modifier.testTag("background_keep_alive_switch")
                                            )
                                        }

                                        // 2. Battery Optimization Status + One-Tap Settings Shortcut
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (batteryIgnored) {
                                                        "电池策略：已设为不受省电限制 ✓"
                                                    } else {
                                                        "电池策略：受系统省电限制（息屏易断连）"
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (batteryIgnored) semantic.safe else semantic.warning
                                                )
                                                Text(
                                                    text = "将易支道设为「无限制 / 允许后台运行」可显著降低长时息屏断连",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    NotificationKeepAliveService.openBatteryOptimizationSettings(context)
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.testTag("open_battery_optimization_button")
                                            ) {
                                                Text(if (batteryIgnored) "查看" else "去放行", fontSize = 12.sp)
                                            }
                                        }

                                        HorizontalDivider(color = semantic.hairline)

                                        // 3. Real Force-Reconnect + Active/Snoozed Backfill Button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val displayFeedback = rebindFeedback
                                                ?: captureHealth.lastScanSummary.takeIf { it.isNotBlank() }
                                                ?: "最近通知诊断 (${recentDiagnostics.size} 条)"
                                            Text(
                                                text = displayFeedback,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(
                                                enabled = !isRebinding,
                                                onClick = {
                                                    isRebinding = true
                                                    rebindFeedback = "正在重连并扫描状态栏补录..."
                                                    coroutineScope.launch {
                                                        NotificationKeepAliveService.sync(
                                                            context = context,
                                                            keepAliveEnabled = state.settings.backgroundKeepAliveEnabled
                                                        )
                                                        val report = PaymentNotificationListenerService.reconnectAndBackfill(context)
                                                        listenerEnabled = !report.permissionMissing
                                                        rebindFeedback = report.message
                                                        isRebinding = false
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.testTag("rebind_notification_listener_button")
                                            ) {
                                                if (isRebinding) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                }
                                                Text("重连并补录", fontSize = 12.sp)
                                            }
                                        }

                                        Text(
                                            text = "补录与防漏说明：\n" +
                                                "1. 点击「重连并补录」会强制重置监听连接并扫描状态栏/延后队列中未入账的支付通知；已被手动划掉的通知系统不保留，可用下方「一键导入并分类」导入账单补全。\n" +
                                                "2. 微信 → 设置 → 新消息通知 → 开启「通知显示消息详情」，并关闭微信支付公众号「消息免打扰」。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (recentDiagnostics.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                recentDiagnostics.take(5).forEach { entry ->
                                                    Text(
                                                        text = "${if (entry.captured) "✓" else "!"} [${entry.channelLabel}] ${entry.preview} · ${entry.reason}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (entry.captured) {
                                                            MaterialTheme.colorScheme.onSurface
                                                        } else {
                                                            MaterialTheme.colorScheme.error
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Module 2: Collapsible Bill Import & Simulation Toolbox
                    item(key = "import_toolbox_module") {
                        ModularAccordionCard(
                            title = "账单导入与演练工坊",
                            subtitle = "微信 / 支付宝文件导入、备份导出与模拟记账",
                            icon = Icons.Default.Inventory2,
                            expanded = showImportToolbox,
                            onToggle = { showImportToolbox = !showImportToolbox },
                            headerTestTag = "toggle_import_toolbox_button",
                            statusBadge = if (showImportToolbox) "收起工具" else "6 项工具"
                        ) {
                            ModularSectionHeader(
                                title = "账单文件导入与备份",
                                trailing = {
                                    TextButton(
                                        onClick = onOpenCategoryManager,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.testTag("ledger_manage_categories_button")
                                    ) {
                                        Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("自定义分类", fontSize = 12.sp)
                                    }
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ModularActionTile(
                                    title = "微信账单",
                                    caption = ".xlsx / .csv",
                                    icon = Icons.Default.FileUpload,
                                    onClick = {
                                        pendingChannelPicker = PaymentChannel.WECHAT_PAY
                                        csvLauncher.launch(arrayOf("*/*"))
                                    },
                                    testTag = "import_wechat_csv_button",
                                    modifier = Modifier.weight(1f)
                                )
                                ModularActionTile(
                                    title = "支付宝账单",
                                    caption = ".csv 账单",
                                    icon = Icons.Default.FileUpload,
                                    onClick = {
                                        pendingChannelPicker = PaymentChannel.ALIPAY
                                        csvLauncher.launch(arrayOf("*/*"))
                                    },
                                    testTag = "import_alipay_csv_button",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ModularActionTile(
                                    title = "一键导入分类",
                                    caption = "自动查重与归类",
                                    icon = Icons.Default.AutoAwesome,
                                    highlighted = true,
                                    onClick = { autoImportLauncher.launch(arrayOf("*/*")) },
                                    testTag = "auto_import_records_button",
                                    modifier = Modifier.weight(1f)
                                )
                                ModularActionTile(
                                    title = "一键导出记录",
                                    caption = "备份为 CSV",
                                    icon = Icons.Default.FileDownload,
                                    onClick = {
                                        exportCsvLauncher.launch("EasySpend_${state.settings.activeMonthKey}.csv")
                                    },
                                    testTag = "export_records_button",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            ModularSectionHeader(title = "快速体验与演练")

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ModularActionTile(
                                    title = "载入演示账单",
                                    caption = "体验核对流程",
                                    icon = Icons.Default.PlayArrow,
                                    onClick = onLoadDemoQueue,
                                    testTag = "load_demo_csv_queue_button",
                                    modifier = Modifier.weight(1f)
                                )
                                ModularActionTile(
                                    title = "模拟通知记账",
                                    caption = "测试自动抓账",
                                    icon = Icons.Default.NotificationsActive,
                                    onClick = { onSimulateNotification(true) },
                                    testTag = "simulate_wechat_notif_button",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Module 3: Pending Review Queue Header & Batch Actions
                    if (state.pendingCandidates.isNotEmpty()) {
                        item(key = "pending_queue_actions") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ModularSectionHeader(
                                    title = "待核对流水队列",
                                    badge = "${state.pendingCandidates.size} 笔"
                                )
                                val batchBtnPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = onRunAiBatchClassify,
                                        enabled = !state.isAiClassifying,
                                        contentPadding = batchBtnPadding,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("ai_batch_classify_button")
                                    ) {
                                        if (state.isAiClassifying) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(6.dp))
                                            Text("AI 分类中...", fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
                                        } else {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("AI 帮我预分类", fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
                                        }
                                    }
                                    Button(
                                        onClick = onConfirmAllClean,
                                        contentPadding = batchBtnPadding,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("confirm_all_clean_button")
                                    ) {
                                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("一键确认清晰项", fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }

                    if (state.pendingCandidates.isEmpty()) {
                        item(key = "pending_empty_state") {
                            EmptyStateCard(
                                icon = Icons.Default.Check,
                                title = "待核对列表很干净",
                                subtitle = "展开上方「账单导入与演练工坊」，选择演示账单或导入微信/支付宝账单即可体验自动查重与预分类。"
                            )
                        }
                    } else {
                        items(state.pendingCandidates, key = { it.id }) { candidate ->
                            CandidateReviewCard(
                                candidate = candidate,
                                categories = state.categories,
                                categoryMap = categoryMap,
                                detailedModeEnabled = detailedMode,
                                onConfirm = { id, cat, fs, rem, note, vault ->
                                    if (onConfirmCandidateWithVault != null) {
                                        onConfirmCandidateWithVault(id, cat, fs, rem, note, vault)
                                    } else {
                                        onConfirmCandidate(id, cat, fs, rem, note)
                                    }
                                },
                                onSkipOrDismiss = onSkipOrDismissCandidate,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            } else {
                val filteredTxs = remember(state.transactions, searchQuery, directionFilter, vaultFilter, detailedMode) {
                    state.transactions.filter { tx ->
                        val matchQuery = searchQuery.isBlank() ||
                            tx.merchant.contains(searchQuery, true) ||
                            tx.rawDescription.contains(searchQuery, true) ||
                            tx.note.contains(searchQuery, true) ||
                            tx.locationLabel.contains(searchQuery, true)
                        val matchDir = directionFilter == null || tx.direction == directionFilter
                        val matchVault = !detailedMode || vaultFilter == null || tx.fundVault == vaultFilter
                        matchQuery && matchDir && matchVault
                    }
                }
                val activeFilterCount = (if (directionFilter != null) 1 else 0) +
                    (if (detailedMode && vaultFilter != null) 1 else 0)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("formal_ledger_lazy_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Undo bar in Committed Ledger
                    item(key = "ledger_undo_banner") {
                        SpringAnimatedVisibility(visible = state.lastUndoLabel != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(14.dp),
                                border = semantic.cardBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ledger_undo_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = state.lastUndoLabel.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = onUndoLastLedgerAction,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("ledger_undo_button")
                                    ) {
                                        Text("撤销", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Unified Modular Sync & Filter Control Deck
                    item(key = "ledger_export_import_row") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.large,
                            border = semantic.cardBorder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ModularActionTile(
                                        title = "一键导入分类",
                                        caption = "自动归类入账",
                                        icon = Icons.Default.FileUpload,
                                        highlighted = true,
                                        onClick = { autoImportLauncher.launch(arrayOf("*/*")) },
                                        testTag = "ledger_tab_auto_import_button",
                                        modifier = Modifier.weight(1f)
                                    )
                                    ModularActionTile(
                                        title = "一键导出CSV",
                                        caption = "备份本月账本",
                                        icon = Icons.Default.FileDownload,
                                        onClick = {
                                            exportCsvLauncher.launch("EasySpend_${state.settings.activeMonthKey}.csv")
                                        },
                                        testTag = "ledger_tab_export_button",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("搜索商家、备注或消费地点...", fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("ledger_search_input")
                                    )
                                    FilterChip(
                                        selected = showLedgerFilters || activeFilterCount > 0,
                                        onClick = { showLedgerFilters = !showLedgerFilters },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.FilterList,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = if (activeFilterCount > 0) "筛选($activeFilterCount)" else "筛选",
                                                fontSize = 12.sp
                                            )
                                        },
                                        modifier = Modifier.testTag("toggle_ledger_filters_button")
                                    )
                                }

                                SpringAnimatedVisibility(visible = showLedgerFilters) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilterChip(
                                                selected = directionFilter == null,
                                                onClick = { directionFilter = null },
                                                label = { Text("全部") }
                                            )
                                            TransactionDirection.entries.forEach { dir ->
                                                FilterChip(
                                                    selected = directionFilter == dir,
                                                    onClick = { directionFilter = if (directionFilter == dir) null else dir },
                                                    label = { Text(dir.displayName) }
                                                )
                                            }
                                        }

                                        if (detailedMode) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilterChip(
                                                    selected = vaultFilter == null,
                                                    onClick = { vaultFilter = null },
                                                    label = { Text("全部资金库") },
                                                    modifier = Modifier.testTag("ledger_vault_filter_all")
                                                )
                                                FilterChip(
                                                    selected = vaultFilter == FundVault.POCKET,
                                                    onClick = {
                                                        vaultFilter = if (vaultFilter == FundVault.POCKET) null else FundVault.POCKET
                                                    },
                                                    label = { Text(FundVault.POCKET.displayName) },
                                                    modifier = Modifier.testTag("ledger_vault_filter_pocket")
                                                )
                                                FilterChip(
                                                    selected = vaultFilter == FundVault.SAVINGS,
                                                    onClick = {
                                                        vaultFilter = if (vaultFilter == FundVault.SAVINGS) null else FundVault.SAVINGS
                                                    },
                                                    label = { Text(FundVault.SAVINGS.displayName) },
                                                    modifier = Modifier.testTag("ledger_vault_filter_savings")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filteredTxs.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                title = "还没有已入账记录",
                                subtitle = "你可以点右下角「+」手动记一笔，或先在「待核对」页签确认导入的账单。"
                            )
                        }
                    } else {
                        items(filteredTxs, key = { it.id }) { tx ->
                            val catName = categoryMap[tx.categoryId]?.name ?: tx.categoryId
                            TransactionLedgerCard(
                                tx = tx,
                                categoryName = catName,
                                detailedModeEnabled = detailedMode,
                                onEdit = { editingTx = tx },
                                onDelete = { onDeleteTx(tx.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
            }
        }

        FloatingActionButton(
            onClick = { showManualDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_transaction_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_transaction))
        }
    }

    if (showManualDialog) {
        ManualTransactionDialog(
            categories = state.categories,
            detailedModeEnabled = detailedMode,
            locationClassificationEnabled = state.settings.locationClassificationEnabled,
            onDismiss = { showManualDialog = false },
            onOpenCategoryManager = {
                showManualDialog = false
                onOpenCategoryManager()
            },
            onSave = { amt, dir, ch, fs, catId, merchant, note, vault, locLabel ->
                if (onAddManualTransactionDetailed != null) {
                    onAddManualTransactionDetailed(amt, dir, ch, fs, catId, merchant, note, vault, locLabel)
                } else {
                    onAddManualTransaction(amt, dir, ch, fs, catId, merchant, note)
                }
                showManualDialog = false
            }
        )
    }

    editingTx?.let { tx ->
        EditTxCategoryDialog(
            tx = tx,
            categories = state.categories,
            detailedModeEnabled = detailedMode,
            onDismiss = { editingTx = null },
            onSave = { newCatId, newVault, rememberPref ->
                if (onUpdateTxCategoryAndVault != null) {
                    onUpdateTxCategoryAndVault(tx.id, newCatId, newVault, rememberPref)
                } else {
                    onUpdateTxCategory(tx.id, newCatId, rememberPref)
                }
                editingTx = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateReviewCard(
    candidate: ImportCandidateEntity,
    categories: List<CategoryEntity>,
    categoryMap: Map<String, CategoryEntity>,
    detailedModeEnabled: Boolean,
    onConfirm: (Long, String, FundingSource, Boolean, String, FundVault) -> Unit,
    onSkipOrDismiss: (Long, CandidateResolution) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.semanticColors
    var selectedCategoryId by remember(candidate.id, candidate.suggestedCategoryId) {
        mutableStateOf(candidate.suggestedCategoryId)
    }
    var selectedFunding by remember(candidate.id) { mutableStateOf(candidate.fundingSource) }
    var selectedVault by remember(candidate.id, candidate.fundVault) { mutableStateOf(candidate.fundVault) }
    var rememberPref by remember(candidate.id) { mutableStateOf(false) }
    var catMenuExpanded by remember { mutableStateOf(false) }

    val badgeColor = when (candidate.discrepancyType) {
        DiscrepancyType.NONE -> semantic.safe
        DiscrepancyType.POTENTIAL_DUPLICATE -> semantic.danger
        DiscrepancyType.AMOUNT_MISMATCH -> semantic.danger
        DiscrepancyType.REFUND_MATCH -> MaterialTheme.colorScheme.secondary
        DiscrepancyType.UNCERTAIN_CATEGORY -> semantic.warning
    }
    val badgeBg = when (candidate.discrepancyType) {
        DiscrepancyType.NONE -> semantic.safeContainer
        DiscrepancyType.POTENTIAL_DUPLICATE -> semantic.dangerContainer
        DiscrepancyType.AMOUNT_MISMATCH -> semantic.dangerContainer
        DiscrepancyType.REFUND_MATCH -> MaterialTheme.colorScheme.secondaryContainer
        DiscrepancyType.UNCERTAIN_CATEGORY -> semantic.warningContainer
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("candidate_card_${candidate.id}"),
        shape = MaterialTheme.shapes.large,
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
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (candidate.discrepancyType != DiscrepancyType.NONE) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = candidate.discrepancyType.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor
                        )
                    }
                }
                Text(
                    text = "${candidate.channel.displayName} · ${MoneyFormatter.formatTimestamp(candidate.occurredAtMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.merchant,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (candidate.rawDescription.isNotBlank()) {
                        Text(
                            text = candidate.rawDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (candidate.locationLabel.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.testTag("candidate_location_${candidate.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = candidate.locationLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Text(
                    text = "${if (candidate.direction == TransactionDirection.INCOME || candidate.direction == TransactionDirection.REFUND) "+" else "-"}${MoneyFormatter.formatFen(candidate.amountFen)}",
                    style = NumericSmallStyle.copy(fontWeight = FontWeight.Bold),
                    color = if (candidate.direction == TransactionDirection.EXPENSE) MaterialTheme.colorScheme.onSurface else semantic.safe
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${candidate.classificationSource.displayName} · ${candidate.discrepancyNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp)
                )
            }

            ExposedDropdownMenuBox(
                expanded = catMenuExpanded,
                onExpandedChange = { catMenuExpanded = !catMenuExpanded }
            ) {
                OutlinedTextField(
                    value = categoryMap[selectedCategoryId]?.name ?: selectedCategoryId,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("分类") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = catMenuExpanded,
                    onDismissRequest = { catMenuExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text("${cat.name} (${cat.group.displayName})") },
                            onClick = {
                                selectedCategoryId = cat.id
                                if (cat.id == "allowance") selectedFunding = FundingSource.LIVING_ALLOWANCE
                                if (cat.id == "part_time") selectedFunding = FundingSource.PART_TIME_INCOME
                                catMenuExpanded = false
                            }
                        )
                    }
                }
            }

            if (detailedModeEnabled) {
                val isInflow = candidate.direction == TransactionDirection.INCOME || candidate.direction == TransactionDirection.REFUND
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "资金库",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilterChip(
                        selected = selectedVault == FundVault.POCKET,
                        onClick = { selectedVault = FundVault.POCKET },
                        label = { Text(if (isInflow) "存入零花库" else "花零花钱", fontSize = 12.sp) },
                        modifier = Modifier.testTag("candidate_vault_pocket_${candidate.id}")
                    )
                    FilterChip(
                        selected = selectedVault == FundVault.SAVINGS,
                        onClick = { selectedVault = FundVault.SAVINGS },
                        label = { Text(if (isInflow) "存入储蓄库" else "花储蓄钱", fontSize = 12.sp) },
                        modifier = Modifier.testTag("candidate_vault_savings_${candidate.id}")
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = rememberPref,
                    onCheckedChange = { rememberPref = it }
                )
                Text(
                    text = "记住「${candidate.merchant}」的常用分类",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (candidate.discrepancyType == DiscrepancyType.POTENTIAL_DUPLICATE) {
                    Button(
                        onClick = { onSkipOrDismiss(candidate.id, CandidateResolution.MERGED_SKIP) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("merge_skip_button_${candidate.id}")
                    ) {
                        Text("跳过重复", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            onConfirm(candidate.id, selectedCategoryId, selectedFunding, rememberPref, "", selectedVault)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("仍要入账", maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSkipOrDismiss(candidate.id, CandidateResolution.DISMISSED) },
                        modifier = Modifier.testTag("dismiss_candidate_${candidate.id}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("忽略")
                    }
                    Button(
                        onClick = {
                            onConfirm(candidate.id, selectedCategoryId, selectedFunding, rememberPref, "", selectedVault)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_candidate_${candidate.id}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("确认入账")
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionLedgerCard(
    tx: TransactionEntity,
    categoryName: String,
    detailedModeEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = MaterialTheme.semanticColors
    val isInflow = tx.direction == TransactionDirection.INCOME || tx.direction == TransactionDirection.REFUND

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tx.merchant,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (detailedModeEnabled) {
                        val isSavings = tx.fundVault == FundVault.SAVINGS
                        Surface(
                            color = if (isSavings) semantic.safeContainer else MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("tx_vault_badge_${tx.id}")
                        ) {
                            Text(
                                text = tx.fundVault.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSavings) semantic.safe else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${tx.channel.displayName} · ${MoneyFormatter.formatTimestamp(tx.occurredAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (tx.locationLabel.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.testTag("tx_location_${tx.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = tx.locationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (tx.note.isNotBlank() || tx.rawDescription.isNotBlank()) {
                    Text(
                        text = tx.note.ifBlank { tx.rawDescription },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isInflow) "+" else "-"}${MoneyFormatter.formatFen(tx.amountFen)}",
                    style = NumericSmallStyle.copy(fontWeight = FontWeight.Bold),
                    color = if (isInflow) semantic.safe else MaterialTheme.colorScheme.onSurface
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "修改分类", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除记录", tint = semantic.danger, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTransactionDialog(
    categories: List<CategoryEntity>,
    detailedModeEnabled: Boolean,
    locationClassificationEnabled: Boolean,
    onDismiss: () -> Unit,
    onOpenCategoryManager: () -> Unit = {},
    onSave: (String, TransactionDirection, PaymentChannel, FundingSource, String, String, String, FundVault, String) -> Unit
) {
    val context = LocalContext.current
    var amountStr by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(TransactionDirection.EXPENSE) }
    var channel by remember { mutableStateOf(PaymentChannel.WECHAT_PAY) }
    var fundingSource by remember { mutableStateOf(FundingSource.LIVING_ALLOWANCE) }
    var fundVault by remember { mutableStateOf(FundVault.POCKET) }
    var categoryId by remember { mutableStateOf("food") }
    var catExpanded by remember { mutableStateOf(false) }
    var capturedLocation by remember { mutableStateOf("") }

    LaunchedEffect(locationClassificationEnabled) {
        if (locationClassificationEnabled && LocationContextResolver.hasLocationPermission(context)) {
            capturedLocation = runCatching {
                LocationContextResolver.captureCurrentLocation(context)?.label.orEmpty()
            }.getOrDefault("")
        }
    }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("手动记一笔") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("金额（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_amount_input")
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TransactionDirection.entries.forEach { d ->
                        FilterChip(
                            selected = direction == d,
                            onClick = {
                                direction = d
                                if (d == TransactionDirection.INCOME) categoryId = "part_time"
                                else if (d == TransactionDirection.EXPENSE) categoryId = "food"
                            },
                            label = { Text(d.displayName) }
                        )
                    }
                }
                if (detailedModeEnabled) {
                    val isInflow = direction == TransactionDirection.INCOME || direction == TransactionDirection.REFUND
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "资金库",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilterChip(
                            selected = fundVault == FundVault.POCKET,
                            onClick = { fundVault = FundVault.POCKET },
                            label = { Text(if (isInflow) "收入零花库" else "花零花钱") },
                            modifier = Modifier.testTag("manual_vault_pocket")
                        )
                        FilterChip(
                            selected = fundVault == FundVault.SAVINGS,
                            onClick = { fundVault = FundVault.SAVINGS },
                            label = { Text(if (isInflow) "收入储蓄库" else "花储蓄钱") },
                            modifier = Modifier.testTag("manual_vault_savings")
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(PaymentChannel.WECHAT_PAY, PaymentChannel.ALIPAY, PaymentChannel.BANK_CARD).forEach { ch ->
                        FilterChip(
                            selected = channel == ch,
                            onClick = { channel = ch },
                            label = { Text(ch.displayName) }
                        )
                    }
                }
                if (direction == TransactionDirection.INCOME) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = fundingSource == FundingSource.LIVING_ALLOWANCE,
                            onClick = {
                                fundingSource = FundingSource.LIVING_ALLOWANCE
                                categoryId = "allowance"
                            },
                            label = { Text("家庭生活费") }
                        )
                        FilterChip(
                            selected = fundingSource == FundingSource.PART_TIME_INCOME,
                            onClick = {
                                fundingSource = FundingSource.PART_TIME_INCOME
                                categoryId = "part_time"
                            },
                            label = { Text("兼职收入") }
                        )
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = !catExpanded }
                ) {
                    OutlinedTextField(
                        value = categoryMap[categoryId]?.name ?: categoryId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(if (c.isCustom) "${c.name}（自定义）" else c.name) },
                                onClick = {
                                    categoryId = c.id
                                    catExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "+ 新建自定义分类...",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                catExpanded = false
                                onOpenCategoryManager()
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("商家或对方名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (capturedLocation.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_location_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "同步定位：$capturedLocation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            TextButton(
                                onClick = { capturedLocation = "" },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("清除", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(amountStr, direction, channel, fundingSource, categoryId, merchant, note, fundVault, capturedLocation)
                },
                modifier = Modifier.testTag("manual_save_button")
            ) {
                Text("保存入账")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EditTxCategoryDialog(
    tx: TransactionEntity,
    categories: List<CategoryEntity>,
    detailedModeEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, FundVault, Boolean) -> Unit
) {
    var selectedCat by remember(tx.id) { mutableStateOf(tx.categoryId) }
    var selectedVault by remember(tx.id) { mutableStateOf(tx.fundVault) }
    var rememberPref by remember(tx.id) { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("修改分类：${tx.merchant}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detailedModeEnabled) {
                    val isInflow = tx.direction == TransactionDirection.INCOME || tx.direction == TransactionDirection.REFUND
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "资金库",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilterChip(
                            selected = selectedVault == FundVault.POCKET,
                            onClick = { selectedVault = FundVault.POCKET },
                            label = { Text(if (isInflow) "零花库" else "零花钱") },
                            modifier = Modifier.testTag("edit_vault_pocket")
                        )
                        FilterChip(
                            selected = selectedVault == FundVault.SAVINGS,
                            onClick = { selectedVault = FundVault.SAVINGS },
                            label = { Text(if (isInflow) "储蓄库" else "储蓄钱") },
                            modifier = Modifier.testTag("edit_vault_savings")
                        )
                    }
                }
                categories.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pair.forEach { c ->
                            FilterChip(
                                selected = selectedCat == c.id,
                                onClick = { selectedCat = c.id },
                                label = { Text(c.name) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberPref, onCheckedChange = { rememberPref = it })
                    Text("记住此商家分类，下次自动匹配", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedCat, selectedVault, rememberPref) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
