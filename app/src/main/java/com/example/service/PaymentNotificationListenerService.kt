package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.local.EasySpendDatabase
import com.example.data.local.LocalAccountStore
import com.example.data.local.UserSettingsDataStore
import com.example.data.repository.EasySpendRepository
import com.example.data.repository.NotificationStageOutcome
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.StatementImporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import kotlin.math.abs

data class CapturedNotificationPayload(
    val packageName: String,
    val title: String,
    val body: String,
    val postedAtMillis: Long,
    val notificationKey: String = "",
    val isFromBundle: Boolean = false
)

data class NotificationDiagnosticEntry(
    val id: Long,
    val timestampMillis: Long,
    val channelLabel: String,
    val preview: String,
    val captured: Boolean,
    val reason: String
)

data class BackfillReport(
    val connected: Boolean,
    val permissionMissing: Boolean = false,
    val rebindTriggered: Boolean = false,
    val scannedNotifications: Int = 0,
    val recoveredCount: Int = 0,
    val alreadyRecordedCount: Int = 0,
    val message: String
)

data class NotificationCaptureHealth(
    val isBound: Boolean = false,
    val lastConnectedAtMillis: Long = 0L,
    val lastCapturedAtMillis: Long = 0L,
    val lastScanAtMillis: Long = 0L,
    val lastScanSummary: String = ""
)

class PaymentNotificationListenerService : NotificationListenerService() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val serialScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val now = System.currentTimeMillis()
        activeInstanceRef = WeakReference(this)
        _isListenerBound.value = true
        _captureHealth.value = _captureHealth.value.copy(
            isBound = true,
            lastConnectedAtMillis = now
        )
        NotificationWatchdogJobService.schedulePeriodic(applicationContext)

        serialScope.launch {
            val report = scanCurrentNotificationsForBackfill(
                sourceLabel = "断线重连自动补录",
                rebindTriggered = true
            )
            completePendingManualBackfill(report)
        }
    }

    override fun onListenerDisconnected() {
        if (activeInstanceRef?.get() === this) {
            activeInstanceRef = null
        }
        _isListenerBound.value = false
        _captureHealth.value = _captureHealth.value.copy(isBound = false)
        runCatching {
            requestRebind(ComponentName(this, PaymentNotificationListenerService::class.java))
        }
        NotificationWatchdogJobService.scheduleImmediateRecovery(applicationContext)
        super.onListenerDisconnected()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isNotificationAccessEnabled(applicationContext)) {
            NotificationWatchdogJobService.scheduleImmediateRecovery(applicationContext)
            requestRebindIfPossible(applicationContext)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        activeInstanceRef = WeakReference(this)
        if (!_isListenerBound.value) {
            _isListenerBound.value = true
            _captureHealth.value = _captureHealth.value.copy(
                isBound = true,
                lastConnectedAtMillis = System.currentTimeMillis()
            )
        }
        handleStatusBarNotification(statusBarNotification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        val payloads = extractPayloads(statusBarNotification)
        if (payloads.isEmpty()) return

        // Safety net: if a payment notification was dropped or delayed during deep Doze and then
        // cleared/opened by the user, backfill it silently without creating duplicates.
        serialScope.launch {
            val repo = resolveActiveRepository(applicationContext)
            val counts = backfillPayloadsToRepository(
                repo = repo,
                payloads = payloads,
                sourceLabel = "通知清除兜底补录",
                context = applicationContext
            )
            if (counts.recoveredCount > 0) {
                val now = System.currentTimeMillis()
                _captureHealth.value = _captureHealth.value.copy(
                    lastCapturedAtMillis = now,
                    lastScanAtMillis = now,
                    lastScanSummary = "兜底补录 ${counts.recoveredCount} 笔即将清除的支付通知"
                )
            }
        }
    }

    private fun handleStatusBarNotification(sbn: StatusBarNotification) {
        val payloads = extractPayloads(sbn)
        if (payloads.isEmpty()) return

        serialScope.launch {
            val repo = resolveActiveRepository(applicationContext)
            for (payload in payloads) {
                if (isRecentlyProcessed(payload)) continue

                try {
                    val locationLabel = resolveLocationLabelIfEnabled(
                        context = applicationContext,
                        postedAtMillis = payload.postedAtMillis
                    )
                    val outcome = repo.stagePaymentNotificationDetailed(
                        packageName = payload.packageName,
                        title = payload.title,
                        text = payload.body,
                        occurredAtMillis = payload.postedAtMillis,
                        skipIfAlreadyRecorded = false,
                        sourceLabel = "支付通知实时捕获",
                        locationLabel = locationLabel
                    )
                    val channelLabel = if (payload.packageName.contains("tencent.mm")) "微信" else "支付宝"
                    when (outcome) {
                        is NotificationStageOutcome.Staged -> {
                            markProcessed(payload)
                            val now = System.currentTimeMillis()
                            _captureHealth.value = _captureHealth.value.copy(lastCapturedAtMillis = now)
                            val locSuffix = if (locationLabel.isNotBlank()) " · 定位:$locationLabel" else ""
                            recordDiagnostic(
                                channelLabel = channelLabel,
                                preview = "${outcome.candidate.merchant} ${MoneyFormatter.formatFen(outcome.candidate.amountFen)}",
                                captured = true,
                                reason = "已捕获进入待核对队列$locSuffix"
                            )
                        }
                        NotificationStageOutcome.AlreadyRecorded -> {
                            markProcessed(payload)
                        }
                        is NotificationStageOutcome.Unparseable -> {
                            val combined = "${payload.title} ${payload.body}".trim()
                            if (looksLikePaymentNoticeWithoutAmount(combined)) {
                                recordDiagnostic(
                                    channelLabel = channelLabel,
                                    preview = combined.take(40),
                                    captured = false,
                                    reason = "通知不含金额，请在微信开启「通知显示消息详情」"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stage payment notification", e)
                }
            }
        }
    }

    /**
     * Scans all currently active (and API 26+ snoozed) notifications in the status bar
     * and recovers any WeChat/Alipay payment notifications that are not yet in the local database.
     */
    internal suspend fun scanCurrentNotificationsForBackfill(
        sourceLabel: String,
        rebindTriggered: Boolean = false
    ): BackfillReport = withContext(Dispatchers.IO) {
        val notifications = collectSnapshotNotifications()
            ?: return@withContext BackfillReport(
                connected = false,
                rebindTriggered = rebindTriggered,
                message = "通知监听连接已失效，正在请求系统重连"
            )

        val repo = resolveActiveRepository(applicationContext)
        val result = backfillPayloadsToRepository(
            repo = repo,
            payloads = notifications.flatMap { extractPayloads(it) },
            sourceLabel = sourceLabel,
            context = applicationContext
        )

        val now = System.currentTimeMillis()
        val summaryText = when {
            result.recoveredCount > 0 ->
                "${if (rebindTriggered) "重连成功，" else ""}已从状态栏补录 ${result.recoveredCount} 笔支付通知"
            result.alreadyRecordedCount > 0 ->
                "监听在线 · 状态栏 ${result.alreadyRecordedCount} 条支付通知均已入账，无漏单"
            else ->
                "监听已在线 · 状态栏当前无未读支付通知（已划掉的历史漏单请点「一键导入并分类」补录）"
        }

        _captureHealth.value = _captureHealth.value.copy(
            isBound = true,
            lastScanAtMillis = now,
            lastCapturedAtMillis = if (result.recoveredCount > 0) now else _captureHealth.value.lastCapturedAtMillis,
            lastScanSummary = summaryText
        )

        BackfillReport(
            connected = true,
            rebindTriggered = rebindTriggered,
            scannedNotifications = notifications.size,
            recoveredCount = result.recoveredCount,
            alreadyRecordedCount = result.alreadyRecordedCount,
            message = summaryText
        )
    }

    private fun collectSnapshotNotifications(): List<StatusBarNotification>? {
        return try {
            val active = activeNotifications?.toList().orEmpty()
            val snoozed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { snoozedNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            (active + snoozed).distinctBy { sbn ->
                val key = runCatching { sbn.key }.getOrNull().orEmpty()
                "$key|${sbn.postTime}"
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Binder detached while reading activeNotifications", e)
            _isListenerBound.value = false
            _captureHealth.value = _captureHealth.value.copy(isBound = false)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query activeNotifications", e)
            null
        }
    }

    override fun onDestroy() {
        if (activeInstanceRef?.get() === this) {
            activeInstanceRef = null
        }
        _isListenerBound.value = false
        _captureHealth.value = _captureHealth.value.copy(isBound = false)
        serialScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PaymentNotifListener"
        private const val MAX_DIAGNOSTIC_ITEMS = 12
        private const val MAX_SEEN_CACHE = 160
        private const val EXACT_REPOST_WINDOW_MS = 10 * 60 * 1000L
        private const val BUNDLE_LINE_SEEN_WINDOW_MS = 15 * 60 * 1000L
        private const val REBIND_WAIT_TIMEOUT_MS = 3500L
        private const val LOCATION_ATTACH_MAX_AGE_MS = 15 * 60 * 1000L

        private val supportedPackages = setOf("com.tencent.mm", "com.eg.android.AlipayGphone")
        private val paymentHintWithoutAmountKeywords = listOf(
            "微信支付", "支付凭证", "扣费凭证", "扣款凭证", "扣费通知", "扣款通知",
            "校园轻松付", "支付宝通知", "收款通知", "消费通知"
        )

        @Volatile
        private var activeInstanceRef: WeakReference<PaymentNotificationListenerService>? = null

        private val pendingManualBackfillLock = Any()
        private var pendingManualBackfill: CompletableDeferred<BackfillReport>? = null

        private val _isListenerBound = MutableStateFlow(false)
        val isListenerBoundFlow: StateFlow<Boolean> = _isListenerBound.asStateFlow()

        private val _captureHealth = MutableStateFlow(NotificationCaptureHealth())
        val captureHealthFlow: StateFlow<NotificationCaptureHealth> = _captureHealth.asStateFlow()

        private val _recentDiagnostics = MutableStateFlow<List<NotificationDiagnosticEntry>>(emptyList())
        val recentDiagnosticsFlow: StateFlow<List<NotificationDiagnosticEntry>> = _recentDiagnostics.asStateFlow()

        private var diagnosticSeq = 0L
        private val seenLock = Any()
        private val seenExactPosts = LinkedHashMap<String, Long>(64, 0.75f, true)
        private val seenBundleLines = LinkedHashMap<String, Long>(64, 0.75f, true)

        fun clearDiagnosticsForTests() {
            synchronized(seenLock) {
                seenExactPosts.clear()
                seenBundleLines.clear()
            }
            _recentDiagnostics.value = emptyList()
            _captureHealth.value = NotificationCaptureHealth(isBound = _isListenerBound.value)
        }

        private suspend fun resolveActiveRepository(context: Context): EasySpendRepository {
            val activeAccount = runCatching {
                LocalAccountStore(context.applicationContext).getCurrentAccount()
            }.getOrNull()
            val db = if (activeAccount != null) {
                EasySpendDatabase.getInstanceForAccount(context.applicationContext, activeAccount.id)
            } else {
                EasySpendDatabase.getInstance(context.applicationContext)
            }
            return EasySpendRepository(db)
        }

        private suspend fun resolveLocationLabelIfEnabled(
            context: Context,
            postedAtMillis: Long
        ): String {
            val age = abs(System.currentTimeMillis() - postedAtMillis)
            if (age > LOCATION_ATTACH_MAX_AGE_MS) return ""
            val appCtx = context.applicationContext
            val enabled = runCatching {
                val activeAccount = LocalAccountStore(appCtx).getCurrentAccount()
                UserSettingsDataStore(appCtx, activeAccount?.id).settingsFlow.first().locationClassificationEnabled
            }.getOrDefault(true)
            if (!enabled) return ""
            return runCatching {
                LocationContextResolver.captureCurrentLocation(appCtx)?.label.orEmpty()
            }.getOrDefault("")
        }

        internal data class PayloadBackfillCounts(
            val recoveredCount: Int,
            val alreadyRecordedCount: Int
        )

        /**
         * Database-backed backfill helper (also directly unit-testable without a live NotificationManager).
         * Stages every unrecorded payment payload into the pending review queue and silently skips
         * payloads already present in transactions or import_candidates.
         */
        internal suspend fun backfillPayloadsToRepository(
            repo: EasySpendRepository,
            payloads: List<CapturedNotificationPayload>,
            sourceLabel: String = "断线重连补录",
            context: Context? = null
        ): PayloadBackfillCounts {
            var recovered = 0
            var alreadyRecorded = 0

            for (payload in payloads) {
                try {
                    val locationLabel = if (context != null) {
                        resolveLocationLabelIfEnabled(context, payload.postedAtMillis)
                    } else {
                        ""
                    }
                    val outcome = repo.stagePaymentNotificationDetailed(
                        packageName = payload.packageName,
                        title = payload.title,
                        text = payload.body,
                        occurredAtMillis = payload.postedAtMillis,
                        skipIfAlreadyRecorded = true,
                        sourceLabel = sourceLabel,
                        locationLabel = locationLabel
                    )
                    val channelLabel = if (payload.packageName.contains("tencent.mm")) "微信" else "支付宝"
                    when (outcome) {
                        is NotificationStageOutcome.Staged -> {
                            recovered++
                            markProcessed(payload)
                            val locSuffix = if (locationLabel.isNotBlank()) " · 定位:$locationLabel" else ""
                            recordDiagnostic(
                                channelLabel = channelLabel,
                                preview = "${outcome.candidate.merchant} ${MoneyFormatter.formatFen(outcome.candidate.amountFen)}",
                                captured = true,
                                reason = "已通过$sourceLabel 补录$locSuffix"
                            )
                        }
                        NotificationStageOutcome.AlreadyRecorded -> {
                            alreadyRecorded++
                            markProcessed(payload)
                        }
                        is NotificationStageOutcome.Unparseable -> {
                            val combined = "${payload.title} ${payload.body}".trim()
                            if (looksLikePaymentNoticeWithoutAmount(combined)) {
                                recordDiagnostic(
                                    channelLabel = channelLabel,
                                    preview = combined.take(40),
                                    captured = false,
                                    reason = "通知不含金额，请在微信开启「通知显示消息详情」"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to backfill payment notification", e)
                }
            }
            return PayloadBackfillCounts(recoveredCount = recovered, alreadyRecordedCount = alreadyRecorded)
        }

        /**
         * User-triggered "重连并补录":
         * 1. If already bound and live binder responds, immediately scans active + snoozed notifications.
         * 2. If unbound or binder is dead/frozen (common after long screen-off or OEM freezer),
         *    performs PackageManager component toggle + requestRebind and awaits backfill on reconnect.
         */
        suspend fun reconnectAndBackfill(context: Context): BackfillReport = withContext(Dispatchers.IO) {
            val appCtx = context.applicationContext
            if (!isNotificationAccessEnabled(appCtx)) {
                return@withContext BackfillReport(
                    connected = false,
                    permissionMissing = true,
                    message = "请先在系统设置中开启「通知使用权」"
                )
            }

            NotificationWatchdogJobService.schedulePeriodic(appCtx)

            val liveService = activeInstanceRef?.get()
            val immediateReport = liveService?.scanCurrentNotificationsForBackfill(
                sourceLabel = "手动重连补录",
                rebindTriggered = false
            )
            if (immediateReport != null && immediateReport.connected && immediateReport.recoveredCount > 0) {
                requestRebindIfPossible(appCtx)
                return@withContext immediateReport
            }

            // Even if liveService was non-null with 0 recovered items, perform a real component toggle
            // + requestRebind to break OEM zombie-binder states where activeNotifications returns empty
            // or new events stop arriving after long screen-off Doze.
            val waiter = CompletableDeferred<BackfillReport>()
            synchronized(pendingManualBackfillLock) {
                pendingManualBackfill?.cancel()
                pendingManualBackfill = waiter
            }

            forceToggleAndRebind(appCtx)

            val awaited = withTimeoutOrNull(REBIND_WAIT_TIMEOUT_MS) {
                waiter.await()
            }
            if (awaited != null) {
                return@withContext awaited
            }

            synchronized(pendingManualBackfillLock) {
                if (pendingManualBackfill === waiter) {
                    pendingManualBackfill = null
                }
            }

            val lateLive = activeInstanceRef?.get()
            if (lateLive != null) {
                val lateReport = lateLive.scanCurrentNotificationsForBackfill(
                    sourceLabel = "手动重连补录",
                    rebindTriggered = true
                )
                if (lateReport.connected) return@withContext lateReport
            }

            if (immediateReport != null && immediateReport.connected) {
                return@withContext immediateReport.copy(
                    rebindTriggered = true,
                    message = if (immediateReport.alreadyRecordedCount > 0) {
                        "已刷新重连 · 状态栏 ${immediateReport.alreadyRecordedCount} 条支付通知均已入账，无漏单"
                    } else {
                        "已刷新重连 · 状态栏当前无未读支付通知（已划掉的历史漏单请点「一键导入并分类」补录）"
                    }
                )
            }

            BackfillReport(
                connected = _isListenerBound.value,
                rebindTriggered = true,
                message = "已强制触发系统重连；若仍显示离线，请将易支道设为「电池无限制」并在通知权限页关闭再重开"
            )
        }

        /**
         * Silent self-healing called on app resume, screen unlock, and JobScheduler watchdog ticks.
         */
        suspend fun ensureConnectedAndBackfillSilently(
            context: Context,
            reason: String = "自动巡检补录"
        ) = withContext(Dispatchers.IO) {
            val appCtx = context.applicationContext
            if (!isNotificationAccessEnabled(appCtx)) return@withContext

            val liveService = activeInstanceRef?.get()
            if (liveService != null) {
                val report = liveService.scanCurrentNotificationsForBackfill(
                    sourceLabel = reason,
                    rebindTriggered = false
                )
                if (report.connected) return@withContext
            }

            // Listener detached during screen-off / Doze / swipe-away -> force rebind
            forceToggleAndRebind(appCtx)
        }

        private fun completePendingManualBackfill(report: BackfillReport) {
            val waiter = synchronized(pendingManualBackfillLock) {
                val current = pendingManualBackfill
                pendingManualBackfill = null
                current
            }
            waiter?.complete(report)
        }

        /**
         * OEM-resilient rebind: toggles the service component DISABLED -> ENABLED (without killing the app)
         * and calls [NotificationListenerService.requestRebind] to break zombie binder states on MIUI/HyperOS/
         * HarmonyOS/ColorOS/OriginOS.
         */
        fun forceToggleAndRebind(context: Context): Boolean {
            val appCtx = context.applicationContext
            if (!isNotificationAccessEnabled(appCtx)) return false
            val cn = ComponentName(appCtx, PaymentNotificationListenerService::class.java)
            return runCatching {
                val pm = appCtx.packageManager
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                requestRebind(cn)
                true
            }.getOrElse {
                requestRebindIfPossible(appCtx)
            }
        }

        private fun recordDiagnostic(
            channelLabel: String,
            preview: String,
            captured: Boolean,
            reason: String
        ) {
            val entry = NotificationDiagnosticEntry(
                id = ++diagnosticSeq,
                timestampMillis = System.currentTimeMillis(),
                channelLabel = channelLabel,
                preview = preview,
                captured = captured,
                reason = reason
            )
            _recentDiagnostics.value = (listOf(entry) + _recentDiagnostics.value).take(MAX_DIAGNOSTIC_ITEMS)
        }

        private fun looksLikePaymentNoticeWithoutAmount(text: String): Boolean {
            return paymentHintWithoutAmountKeywords.any { text.contains(it, ignoreCase = true) }
        }

        private fun cacheKeys(payload: CapturedNotificationPayload): Pair<String, String> {
            val normTitle = payload.title.replace(Regex("""\s+"""), " ").trim()
            val normBody = payload.body.replace(Regex("""\s+"""), " ").trim()
            val baseKey = "${payload.packageName}|${payload.notificationKey}|$normTitle|$normBody"
            val exactKey = "$baseKey|${payload.postedAtMillis}"
            return baseKey to exactKey
        }

        private fun isRecentlyProcessed(payload: CapturedNotificationPayload): Boolean {
            val (baseKey, exactKey) = cacheKeys(payload)
            val now = System.currentTimeMillis()
            synchronized(seenLock) {
                val prevExact = seenExactPosts[exactKey]
                if (prevExact != null && now - prevExact <= EXACT_REPOST_WINDOW_MS) {
                    return true
                }
                if (payload.isFromBundle) {
                    val prevLine = seenBundleLines[baseKey]
                    if (prevLine != null && now - prevLine <= BUNDLE_LINE_SEEN_WINDOW_MS) {
                        return true
                    }
                }
                return false
            }
        }

        private fun markProcessed(payload: CapturedNotificationPayload) {
            val (baseKey, exactKey) = cacheKeys(payload)
            val now = System.currentTimeMillis()
            synchronized(seenLock) {
                if (payload.isFromBundle) {
                    seenBundleLines[baseKey] = now
                    trimMap(seenBundleLines)
                }
                seenExactPosts[exactKey] = now
                trimMap(seenExactPosts)
            }
        }

        internal fun shouldSkipRecentlyProcessed(payload: CapturedNotificationPayload): Boolean {
            if (isRecentlyProcessed(payload)) return true
            markProcessed(payload)
            return false
        }

        private fun trimMap(map: LinkedHashMap<String, Long>) {
            while (map.size > MAX_SEEN_CACHE) {
                val firstKey = map.entries.firstOrNull()?.key ?: break
                map.remove(firstKey)
            }
        }

        /**
         * Backward-compatible helper returning the first extracted payload (or null).
         */
        fun extractPayload(sbn: StatusBarNotification): CapturedNotificationPayload? {
            return extractPayloads(sbn).firstOrNull()
        }

        /**
         * Extracts one or more payment payloads from a WeChat/Alipay notification,
         * handling MessagingStyle (EXTRA_MESSAGES), InboxStyle (EXTRA_TEXT_LINES),
         * multi-line BigText stacked deductions, and tickerText fallbacks.
         */
        fun extractPayloads(sbn: StatusBarNotification): List<CapturedNotificationPayload> {
            val pkg = sbn.packageName.orEmpty()
            if (pkg !in supportedPackages) return emptyList()

            val notification = sbn.notification ?: return emptyList()
            val flags = notification.flags
            if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) return emptyList()
            val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0

            val extras = notification.extras ?: Bundle.EMPTY
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
                .ifBlank { extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim().orEmpty() }
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
            val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim().orEmpty()
            val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.trim().orEmpty()
            val tickerText = notification.tickerText?.toString()?.trim().orEmpty()

            val postedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
            val notifKey = runCatching { sbn.key }.getOrNull().orEmpty()

            // 1. MessagingStyle (EXTRA_MESSAGES) — used by WeChat on modern Android for stacked messages
            val messagingPayloads = extractMessagingStylePayloads(pkg, title, extras, postedAt, notifKey)
            if (messagingPayloads.isNotEmpty()) {
                return if (isGroupSummary) {
                    messagingPayloads.filter {
                        StatementImporter.parsePaymentNotification(pkg, it.title, it.body, it.postedAtMillis) != null
                    }
                } else {
                    messagingPayloads
                }
            }

            // 2. InboxStyle (EXTRA_TEXT_LINES) — used when multiple cafeteria auto-debits stack in one shade item
            val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
                .orEmpty()
            if (textLines.isNotEmpty()) {
                val parseableLines = textLines.filter { line ->
                    StatementImporter.parsePaymentNotification(pkg, title, line, postedAt) != null
                }
                if (parseableLines.size >= 2) {
                    return parseableLines.map { line ->
                        CapturedNotificationPayload(
                            packageName = pkg,
                            title = title,
                            body = line,
                            postedAtMillis = postedAt,
                            notificationKey = notifKey,
                            isFromBundle = true
                        )
                    }
                } else if (parseableLines.size == 1 && isGroupSummary) {
                    return listOf(
                        CapturedNotificationPayload(
                            packageName = pkg,
                            title = title,
                            body = parseableLines.first(),
                            postedAtMillis = postedAt,
                            notificationKey = notifKey,
                            isFromBundle = textLines.size > 1
                        )
                    )
                }
            }

            // 3. Multi-line BigText where 2+ separate lines are each distinct payments
            if (bigText.contains('\n')) {
                val bigLines = bigText.lines().map { it.trim() }.filter { it.isNotBlank() }
                val parseableBigLines = bigLines.filter { line ->
                    StatementImporter.parsePaymentNotification(pkg, title, line, postedAt) != null
                }
                if (parseableBigLines.size >= 2) {
                    return parseableBigLines.map { line ->
                        CapturedNotificationPayload(
                            packageName = pkg,
                            title = title,
                            body = line,
                            postedAtMillis = postedAt,
                            notificationKey = notifKey,
                            isFromBundle = true
                        )
                    }
                }
            }

            // 4. Standard single-card notification (combine all fields so merchant + amount + window stay intact)
            val bodyParts = buildList {
                add(bigText)
                add(text)
                if (textLines.isNotEmpty()) add(textLines.joinToString(" "))
                add(subText)
                add(summaryText)
                add(infoText)
                add(tickerText)
            }
            val body = bodyParts
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

            if (body.isBlank()) return emptyList()

            if (isGroupSummary) {
                if (StatementImporter.parsePaymentNotification(pkg, title, body, postedAt) == null) {
                    return emptyList()
                }
            } else if (textLines.size > 1 && StatementImporter.parsePaymentNotification(pkg, title, body, postedAt) == null) {
                val rescued = textLines.filter { line ->
                    StatementImporter.parsePaymentNotification(pkg, title, line, postedAt) != null
                }
                if (rescued.isNotEmpty()) {
                    return rescued.map { line ->
                        CapturedNotificationPayload(
                            packageName = pkg,
                            title = title,
                            body = line,
                            postedAtMillis = postedAt,
                            notificationKey = notifKey,
                            isFromBundle = true
                        )
                    }
                }
            }

            return listOf(
                CapturedNotificationPayload(
                    packageName = pkg,
                    title = title,
                    body = body,
                    postedAtMillis = postedAt,
                    notificationKey = notifKey,
                    isFromBundle = false
                )
            )
        }

        private fun extractMessagingStylePayloads(
            pkg: String,
            fallbackTitle: String,
            extras: Bundle,
            fallbackPostedAt: Long,
            notifKey: String
        ): List<CapturedNotificationPayload> {
            val rawMessages: Array<out Parcelable>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            }
            if (rawMessages.isNullOrEmpty()) return emptyList()

            val parsedPayloads = mutableListOf<CapturedNotificationPayload>()
            val isBundle = rawMessages.size > 1
            for (item in rawMessages) {
                val bundle = item as? Bundle ?: continue
                val msgText = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                if (msgText.isBlank()) continue
                val sender = bundle.getCharSequence("sender")?.toString()?.trim().orEmpty()
                    .ifBlank {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val person = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                bundle.getParcelable("sender_person", android.app.Person::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                bundle.getParcelable("sender_person")
                            }
                            person?.name?.toString()?.trim().orEmpty()
                        } else {
                            ""
                        }
                    }
                    .ifBlank { fallbackTitle }
                val msgTime = bundle.getLong("time", 0L).takeIf { it > 0L } ?: fallbackPostedAt
                parsedPayloads.add(
                    CapturedNotificationPayload(
                        packageName = pkg,
                        title = sender,
                        body = msgText,
                        postedAtMillis = msgTime,
                        notificationKey = notifKey,
                        isFromBundle = isBundle
                    )
                )
            }

            if (parsedPayloads.size <= 1) return parsedPayloads
            return parsedPayloads.filter {
                StatementImporter.parsePaymentNotification(pkg, it.title, it.body, it.postedAtMillis) != null
            }
        }

        fun requestRebindIfPossible(context: Context): Boolean {
            return runCatching {
                requestRebind(ComponentName(context, PaymentNotificationListenerService::class.java))
                true
            }.getOrDefault(false)
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
