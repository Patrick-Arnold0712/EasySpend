package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

data class AppPermissionSnapshot(
    val postNotificationsGranted: Boolean,
    val locationGranted: Boolean,
    val notificationListenerGranted: Boolean,
    val batteryOptimizationIgnored: Boolean
) {
    val grantedCount: Int
        get() = listOf(
            postNotificationsGranted,
            locationGranted,
            notificationListenerGranted,
            batteryOptimizationIgnored
        ).count { it }

    val totalCount: Int get() = 4

    val allGranted: Boolean get() = grantedCount == totalCount

    val missingRuntimePermissions: List<String>
        get() = buildList {
            if (!postNotificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!locationGranted) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
}

enum class SpecialPermissionStep(val title: String) {
    NOTIFICATION_LISTENER("支付通知读取权限"),
    BATTERY_OPTIMIZATION("后台省电无限制")
}

object PermissionSetupManager {

    fun snapshot(context: Context): AppPermissionSnapshot {
        val appCtx = context.applicationContext
        val postNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appCtx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val location = LocationContextResolver.hasLocationPermission(appCtx)
        val listener = PaymentNotificationListenerService.isNotificationAccessEnabled(appCtx)
        val battery = NotificationKeepAliveService.isIgnoringBatteryOptimizations(appCtx)

        return AppPermissionSnapshot(
            postNotificationsGranted = postNotif,
            locationGranted = location,
            notificationListenerGranted = listener,
            batteryOptimizationIgnored = battery
        )
    }

    fun nextMissingSpecialStep(snapshot: AppPermissionSnapshot): SpecialPermissionStep? {
        return when {
            !snapshot.notificationListenerGranted -> SpecialPermissionStep.NOTIFICATION_LISTENER
            !snapshot.batteryOptimizationIgnored -> SpecialPermissionStep.BATTERY_OPTIMIZATION
            else -> null
        }
    }

    fun openNotificationListenerSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        NotificationKeepAliveService.openBatteryOptimizationSettings(context)
    }
}
