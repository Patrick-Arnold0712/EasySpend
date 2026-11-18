package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.local.EasySpendDatabase
import com.example.data.repository.EasySpendRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class CapturedNotificationPayload(
    val packageName: String,
    val title: String,
    val body: String,
    val postedAtMillis: Long
)

class PaymentNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val payload = extractPayload(sbn ?: return) ?: return

        scope.launch {
            try {
                val repo = EasySpendRepository(EasySpendDatabase.getInstance(applicationContext))
                repo.stagePaymentNotification(
                    packageName = payload.packageName,
                    title = payload.title,
                    text = payload.body,
                    occurredAtMillis = payload.postedAtMillis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stage payment notification", e)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PaymentNotifListener"
        private val supportedPackages = setOf("com.tencent.mm", "com.eg.android.AlipayGphone")

        fun extractPayload(sbn: StatusBarNotification): CapturedNotificationPayload? {
            val pkg = sbn.packageName.orEmpty()
            if (pkg !in supportedPackages) return null

            val notification = sbn.notification ?: return null
            val flags = notification.flags
            if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null
            if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) return null

            val extras = notification.extras ?: return null
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

            val body = listOf(bigText, text, subText)
                .filter { it.isNotBlank() }
                .distinct()
                .fold(StringBuilder()) { acc, part ->
                    if (!acc.contains(part)) {
                        if (acc.isNotEmpty()) acc.append(' ')
                        acc.append(part)
                    }
                    acc
                }
                .toString()
                .trim()

            if (body.isBlank()) return null

            val postedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
            return CapturedNotificationPayload(
                packageName = pkg,
                title = title,
                body = body,
                postedAtMillis = postedAt
            )
        }

        fun isNotificationAccessEnabled(context: Context): Boolean {
            return try {
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ) ?: return false
                val cn = ComponentName(context, PaymentNotificationListenerService::class.java)
                flat.contains(cn.flattenToString()) || flat.contains(cn.shortClassName)
            } catch (_: Exception) {
                false
            }
        }
    }
}
