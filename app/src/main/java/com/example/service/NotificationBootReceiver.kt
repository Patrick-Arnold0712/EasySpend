package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restores the persisted watchdog and requests notification listener rebinding
 * after device reboot or app update so payment capture resumes without manually opening the app.
 */
class NotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val appCtx = context.applicationContext
        if (!PaymentNotificationListenerService.isNotificationAccessEnabled(appCtx)) return

        NotificationWatchdogJobService.schedulePeriodic(appCtx)
        PaymentNotificationListenerService.forceToggleAndRebind(appCtx)
        NotificationWatchdogJobService.scheduleImmediateRecovery(appCtx)
    }
}
