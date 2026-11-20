package com.example.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Persisted periodic & immediate-recovery JobScheduler watchdog that wakes up after Doze maintenance
 * windows, Recents swipe-away, or reboot to verify NotificationListenerService health, force rebind
 * if detached, and sweep active/snoozed notifications for missed payment alerts.
 */
class NotificationWatchdogJobService : JobService() {

    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        if (!PaymentNotificationListenerService.isNotificationAccessEnabled(applicationContext)) {
            return false
        }

        jobScope.launch {
            try {
                PaymentNotificationListenerService.ensureConnectedAndBackfillSilently(
                    context = applicationContext,
                    reason = "后台定时巡检补录"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Watchdog check failed", e)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    override fun onDestroy() {
        jobScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotifWatchdogJob"
        const val JOB_ID_PERIODIC = 9101
        const val JOB_ID_IMMEDIATE_RECOVERY = 9102
        private const val PERIODIC_INTERVAL_MS = 15 * 60 * 1000L

        fun schedulePeriodic(context: Context): Boolean {
            return runCatching {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                    ?: return false
                if (scheduler.getPendingJob(JOB_ID_PERIODIC) != null) return true

                val component = ComponentName(context, NotificationWatchdogJobService::class.java)
                val jobInfo = JobInfo.Builder(JOB_ID_PERIODIC, component)
                    .setPeriodic(PERIODIC_INTERVAL_MS)
                    .setPersisted(true)
                    .build()
                scheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS
            }.getOrDefault(false)
        }

        fun scheduleImmediateRecovery(context: Context, delayMillis: Long = 2_500L): Boolean {
            return runCatching {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                    ?: return false
                val component = ComponentName(context, NotificationWatchdogJobService::class.java)
                val jobInfo = JobInfo.Builder(JOB_ID_IMMEDIATE_RECOVERY, component)
                    .setMinimumLatency(delayMillis)
                    .setOverrideDeadline(delayMillis + 15_000L)
                    .build()
                scheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS
            }.getOrDefault(false)
        }

        fun cancel(context: Context) {
            runCatching {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                scheduler?.cancel(JOB_ID_PERIODIC)
                scheduler?.cancel(JOB_ID_IMMEDIATE_RECOVERY)
            }
        }
    }
}
