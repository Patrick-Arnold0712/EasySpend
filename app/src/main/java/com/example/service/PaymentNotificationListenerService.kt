package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.local.EasySpendDatabase
import com.example.data.local.LocalAccountStore
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.StatementImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

class PaymentNotificationListenerService : NotificationListenerService() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val serialScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isListenerBound.value = true
        runCatching {
            activeNotifications?.forEach { sbn ->
                handleStatusBarNotification(sbn)
            }
        }
    }

    override fun onListenerDisconnected() {
        _isListenerBound.value = false
        runCatching {
            requestRebind(ComponentName(this, PaymentNotificationListenerService::class.java))
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        handleStatusBarNotification(sbn ?: return)
    }

    private fun handleStatusBarNotification(sbn: StatusBarNotification) {
        val payloads = extractPayloads(sbn)
        if (payloads.isEmpty()) return

        serialScope.launch {
            val activeAccount = runCatching {
                LocalAccountStore(applicationContext).getCurrentAccount()
            }.getOrNull()
            val db = if (activeAccount != null) {
                EasySpendDatabase.getInstanceForAccount(applicationContext, activeAccount.id)
            } else {
                EasySpendDatabase.getInstance(applicationContext)
            }
            val repo = EasySpendRepository(db)

            for (payload in payloads) {
                if (shouldSkipRecentlyProcessed(payload)) continue

                try {
                    val result = repo.stagePaymentNotification(
                        packageName = payload.packageName,
                        title = payload.title,
                        text = payload.body,
                        occurredAtMillis = payload.postedAtMillis
                    )
                    val channelLabel = if (payload.packageName.contains("tencent.mm")) "微信" else "支付宝"
                    if (result.isSuccess) {
                        val staged = result.getOrThrow()
                        recordDiagnostic(
                            channelLabel = channelLabel,
                            preview = "${staged.merchant} ${MoneyFormatter.formatFen(staged.amountFen)}",
                            captured = true,
                            reason = "已捕获进入待核对队列"
                        )
                    } else {
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
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stage payment notification", e)
                }
            }
        }
    }

    override fun onDestroy() {
        _isListenerBound.value = false
        serialScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PaymentNotifListener"
        private const val MAX_DIAGNOSTIC_ITEMS = 12
        private const val MAX_SEEN_CACHE = 160
        private const val EXACT_REPOST_WINDOW_MS = 10 * 60 * 1000L
        private const val BUNDLE_LINE_SEEN_WINDOW_MS = 15 * 60 * 1000L

        private val supportedPackages = setOf("com.tencent.mm", "com.eg.android.AlipayGphone")
        private val paymentHintWithoutAmountKeywords = listOf(
            "微信支付", "支付凭证", "扣费凭证", "扣款凭证", "扣费通知", "扣款通知",
            "校园轻松付", "支付宝通知", "收款通知", "消费通知"
        )

        private val _isListenerBound = MutableStateFlow(false)
        val isListenerBoundFlow: StateFlow<Boolean> = _isListenerBound.asStateFlow()

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

        internal fun shouldSkipRecentlyProcessed(payload: CapturedNotificationPayload): Boolean {
            val normTitle = payload.title.replace(Regex("""\s+"""), " ").trim()
            val normBody = payload.body.replace(Regex("""\s+"""), " ").trim()
            val baseKey = "${payload.packageName}|${payload.notificationKey}|$normTitle|$normBody"
            val exactKey = "$baseKey|${payload.postedAtMillis}"
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
                    seenBundleLines[baseKey] = now
                    trimMap(seenBundleLines)
                }
                seenExactPosts[exactKey] = now
                trimMap(seenExactPosts)
                return false
            }
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
                // Skip generic group summary banners like "2条支付通知" unless they actually carry a parseable payment
                if (StatementImporter.parsePaymentNotification(pkg, title, body, postedAt) == null) {
                    return emptyList()
                }
            } else if (textLines.size > 1 && StatementImporter.parsePaymentNotification(pkg, title, body, postedAt) == null) {
                // If an InboxStyle notification mixed a noise line (e.g. "撤回了一条消息") with a valid payment line, rescue the payment line
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
            // Keep only parseable payment messages if multiple messages exist; if none parse, return empty to allow fallback
            val paymentOnly = parsedPayloads.filter {
                StatementImporter.parsePaymentNotification(pkg, it.title, it.body, it.postedAtMillis) != null
            }
            return paymentOnly
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
