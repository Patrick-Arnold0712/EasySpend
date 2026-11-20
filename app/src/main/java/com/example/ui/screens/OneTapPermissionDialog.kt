package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.NotificationKeepAliveService
import com.example.service.PaymentNotificationListenerService
import com.example.service.PermissionSetupManager
import com.example.service.SpecialPermissionStep
import com.example.ui.motion.LiveStatusDot
import com.example.ui.theme.NumericSmallStyle
import com.example.ui.theme.semanticColors
import kotlinx.coroutines.launch

@Composable
fun OneTapPermissionDialog(
    keepAliveEnabled: Boolean,
    onAllPermissionsReady: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val semantic = MaterialTheme.semanticColors

    var snapshot by remember { mutableStateOf(PermissionSetupManager.snapshot(context)) }
    var autoChainActive by remember { mutableStateOf(false) }
    var pendingSpecialStep by remember { mutableStateOf<SpecialPermissionStep?>(null) }
    var statusHint by remember { mutableStateOf<String?>(null) }

    fun finishAndSyncServices(message: String) {
        autoChainActive = false
        pendingSpecialStep = null
        statusHint = message
        NotificationKeepAliveService.sync(context, keepAliveEnabled)
        coroutineScope.launch {
            PaymentNotificationListenerService.ensureConnectedAndBackfillSilently(
                context = context,
                reason = "一键权限配置完成巡检"
            )
        }
        onAllPermissionsReady()
    }

    fun continueSpecialStep(currentSnap: com.example.service.AppPermissionSnapshot) {
        val nextStep = PermissionSetupManager.nextMissingSpecialStep(currentSnap)
        when (nextStep) {
            SpecialPermissionStep.NOTIFICATION_LISTENER -> {
                pendingSpecialStep = SpecialPermissionStep.NOTIFICATION_LISTENER
                statusHint = "正在引导开启「支付通知读取权限」，开启后返回即可自动进入下一步..."
                PermissionSetupManager.openNotificationListenerSettings(context)
            }
            SpecialPermissionStep.BATTERY_OPTIMIZATION -> {
                pendingSpecialStep = SpecialPermissionStep.BATTERY_OPTIMIZATION
                statusHint = "正在引导开启「后台省电无限制」，设为无限制后返回即完成配置..."
                PermissionSetupManager.openBatteryOptimizationSettings(context)
            }
            null -> {
                if (currentSnap.allGranted) {
                    finishAndSyncServices("全部 4 项核心权限已就绪，自动抓账与定位辅助分类已生效！")
                } else {
                    autoChainActive = false
                    pendingSpecialStep = null
                    statusHint = "系统设置项已完成，如需补齐弹窗权限可点击上方对应条目。"
                }
            }
        }
    }

    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val updated = PermissionSetupManager.snapshot(context)
        snapshot = updated
        if (autoChainActive) {
            continueSpecialStep(updated)
        } else if (updated.allGranted) {
            finishAndSyncServices("全部 4 项核心权限已就绪！")
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val latest = PermissionSetupManager.snapshot(context)
                snapshot = latest
                val awaiting = pendingSpecialStep
                if (autoChainActive && awaiting != null) {
                    val stepGranted = when (awaiting) {
                        SpecialPermissionStep.NOTIFICATION_LISTENER -> latest.notificationListenerGranted
                        SpecialPermissionStep.BATTERY_OPTIMIZATION -> latest.batteryOptimizationIgnored
                    }
                    pendingSpecialStep = null
                    if (stepGranted) {
                        continueSpecialStep(latest)
                    } else {
                        autoChainActive = false
                        statusHint = "已暂停自动引导（尚未开启「${awaiting.title}」），可点击该项或再次一键配置。"
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("one_tap_permission_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text("一键权限配置", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "已就绪 ${snapshot.grantedCount} / ${snapshot.totalCount} 项核心权限",
                        style = NumericSmallStyle,
                        color = if (snapshot.allGranted) semantic.safe else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "一次点击即可自动串行申请运行时权限，并按序引导完成系统通知读取与后台防掉线设置，免去逐个翻找系统菜单。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    PermissionChecklistRow(
                        icon = Icons.Default.Notifications,
                        title = "通知栏发送权限",
                        subtitle = "用于前台防掉线守护状态栏提示与每日核对提醒",
                        granted = snapshot.postNotificationsGranted,
                        actionLabel = "授权",
                        rowTag = "perm_row_post_notifications",
                        onAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                autoChainActive = false
                                runtimePermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            }
                        }
                    )

                    PermissionChecklistRow(
                        icon = Icons.Default.LocationOn,
                        title = "消费场景定位权限（粗略定位）",
                        subtitle = "读账时同步识别大致商圈/校区/医院等场景，提高个人收款码分类准度",
                        granted = snapshot.locationGranted,
                        actionLabel = "授权",
                        rowTag = "perm_row_location",
                        onAction = {
                            autoChainActive = false
                            runtimePermissionsLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    )

                    PermissionChecklistRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "支付通知读取权限",
                        subtitle = "自动捕获微信支付、校园轻松付与支付宝扣款通知进入待核对",
                        granted = snapshot.notificationListenerGranted,
                        actionLabel = "去开启",
                        rowTag = "perm_row_notification_listener",
                        onAction = {
                            autoChainActive = false
                            pendingSpecialStep = null
                            PermissionSetupManager.openNotificationListenerSettings(context)
                        }
                    )

                    PermissionChecklistRow(
                        icon = Icons.Default.BatteryChargingFull,
                        title = "后台省电无限制",
                        subtitle = "防止长时息屏或切后台后系统冻结监听进程，减少漏抓",
                        granted = snapshot.batteryOptimizationIgnored,
                        actionLabel = "去放行",
                        rowTag = "perm_row_battery_optimization",
                        onAction = {
                            autoChainActive = false
                            pendingSpecialStep = null
                            PermissionSetupManager.openBatteryOptimizationSettings(context)
                        }
                    )
                    if (!statusHint.isNullOrBlank()) {
                        Surface(
                            color = if (snapshot.allGranted) semantic.safeContainer else MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("one_tap_permission_status_hint")
                        ) {
                            Text(
                                text = statusHint.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = if (snapshot.allGranted) semantic.safe else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val remaining = snapshot.totalCount - snapshot.grantedCount
            Button(
                onClick = {
                    val latest = PermissionSetupManager.snapshot(context)
                    snapshot = latest
                    if (latest.allGranted) {
                        finishAndSyncServices("全部 4 项核心权限已就绪，已完成一次在线巡检补录！")
                    } else {
                        autoChainActive = true
                        val runtimeMissing = latest.missingRuntimePermissions
                        if (runtimeMissing.isNotEmpty()) {
                            statusHint = "正在批量申请系统弹窗权限..."
                            runtimePermissionsLauncher.launch(runtimeMissing.toTypedArray())
                        } else {
                            continueSpecialStep(latest)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("one_tap_permission_setup_button")
            ) {
                Icon(
                    imageVector = if (snapshot.allGranted) Icons.Default.CheckCircle else Icons.Default.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (snapshot.allGranted) {
                        "立即巡检"
                    } else {
                        "一键配置 ($remaining 项)"
                    },
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun PermissionChecklistRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    rowTag: String,
    onAction: () -> Unit
) {
    val semantic = MaterialTheme.semanticColors
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = semantic.cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(rowTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) semantic.safe else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LiveStatusDot(
                        color = if (granted) semantic.safe else semantic.warning,
                        size = 6.dp
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (granted) {
                Surface(
                    color = semantic.safeContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "已开启",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = semantic.safe,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(actionLabel, fontSize = 12.sp)
                }
            }
        }
    }
}
