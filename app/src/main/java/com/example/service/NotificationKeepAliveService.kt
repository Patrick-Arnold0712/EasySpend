package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lightweight silent foreground service that keeps EasySpend's process and
 * [PaymentNotificationListenerService] alive during screen-off / Doze / Recents-swipe,
 * and triggers automatic reconnection + status-bar backfill when the user unlocks the phone.
 */
class NotificationKeepAliveService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var screenReceiverRegistered = false

    private val screenWakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action ?: return
            if (
                action == Intent.ACTION_USER_PRESENT ||
                action == Intent.ACTION_SCREEN_ON ||
                action == PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED
            ) {
                serviceScope.launch {
                    PaymentNotificationListenerService.ensureConnectedAndBackfillSilently(
                        context = applicationContext,
                        reason = when (action) {
                            Intent.ACTION_USER_PRESENT -> "解锁自动巡检"
                            PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> "退出省电自动巡检"
                            else -> "亮屏自动巡检"
                        }
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        registerScreenWakeReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            _isRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!PaymentNotificationListenerService.isNotificationAccessEnabled(applicationContext)) {
            _isRunning.value = false
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification()
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        }.onFailure { err ->
            Log.w(TAG, "Unable to enter foreground keep-alive right now", err)
        }.getOrDefault(false)

        if (!started) {
            _isRunning.value = false
            stopSelf()
            return START_NOT_STICKY
        }

        _isRunning.value = true
        NotificationWatchdogJobService.schedulePeriodic(applicationContext)

        serviceScope.launch {
            PaymentNotificationListenerService.ensureConnectedAndBackfillSilently(
                context = applicationContext,
                reason = "守护启动巡检"
            )
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // When the user swipes EasySpend out of Recents, ensure the watchdog and listener stay alive.
        if (PaymentNotificationListenerService.isNotificationAccessEnabled(applicationContext)) {
            NotificationWatchdogJobService.scheduleImmediateRecovery(applicationContext)
            PaymentNotificationListenerService.requestRebindIfPossible(applicationContext)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        _isRunning.value = false
        unregisterScreenWakeReceiver()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerScreenWakeReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                screenWakeReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            screenReceiverRegistered = true
        }
    }

    private fun unregisterScreenWakeReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenWakeReceiver) }
        screenReceiverRegistered = false
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.keep_alive_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.keep_alive_channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.keep_alive_notif_title))
            .setContentText(getString(R.string.keep_alive_notif_body))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val TAG = "NotifKeepAliveSvc"
        private const val CHANNEL_ID = "easyspend_capture_keep_alive"
        private const val NOTIFICATION_ID = 4108
        private const val ACTION_START = "com.example.action.KEEP_ALIVE_START"
        private const val ACTION_STOP = "com.example.action.KEEP_ALIVE_STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun sync(context: Context, keepAliveEnabled: Boolean) {
            val appCtx = context.applicationContext
            val accessEnabled = PaymentNotificationListenerService.isNotificationAccessEnabled(appCtx)
            if (accessEnabled) {
                NotificationWatchdogJobService.schedulePeriodic(appCtx)
            } else {
                NotificationWatchdogJobService.cancel(appCtx)
            }

            if (accessEnabled && keepAliveEnabled) {
                start(appCtx)
            } else {
                stop(appCtx)
            }
        }

        fun start(context: Context) {
            val appCtx = context.applicationContext
            if (!PaymentNotificationListenerService.isNotificationAccessEnabled(appCtx)) return
            val intent = Intent(appCtx, NotificationKeepAliveService::class.java).apply {
                action = ACTION_START
            }
            runCatching {
                ContextCompat.startForegroundService(appCtx, intent)
            }.onFailure { err ->
                Log.w(TAG, "Deferred foreground keep-alive start (background restricted)", err)
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            _isRunning.value = false
            runCatching {
                appCtx.stopService(Intent(appCtx, NotificationKeepAliveService::class.java))
            }
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            return runCatching {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            }.getOrDefault(false)
        }

        fun openBatteryOptimizationSettings(context: Context) {
            val primaryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val started = runCatching {
                context.startActivity(primaryIntent)
                true
            }.getOrDefault(false)

            if (!started) {
                runCatching {
                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                }
            }
        }
    }
}
