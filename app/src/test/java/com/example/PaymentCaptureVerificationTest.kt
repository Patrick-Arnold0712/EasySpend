package com.example

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EasySpendDatabase
import com.example.data.model.CandidateResolution
import com.example.data.model.DiscrepancyType
import com.example.data.model.FundingSource
import com.example.data.model.ImportCandidateEntity
import com.example.data.model.PaymentChannel
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionSourceType
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.ClassificationEngine
import com.example.domain.finance.DemoStatementFactory
import com.example.domain.finance.StatementImporter
import com.example.service.PaymentNotificationListenerService
import com.example.ui.EasySpendUiState
import com.example.ui.screens.TransactionsAndImportScreen
import com.example.ui.theme.EasySpendTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaymentCaptureVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: EasySpendDatabase
    private lateinit var repository: EasySpendRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, EasySpendDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EasySpendRepository(db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `manifest registers PaymentNotificationListenerService and access status check works`() {
        val pm = context.packageManager
        val intent = Intent(NotificationListenerService.SERVICE_INTERFACE).setPackage(context.packageName)
        @Suppress("DEPRECATION")
        val services = pm.queryIntentServices(intent, 0)
        assertTrue(
            "PaymentNotificationListenerService must be registered for NotificationListenerService action",
            services.any { it.serviceInfo.name == PaymentNotificationListenerService::class.java.name }
        )
        val serviceInfo = services.first { it.serviceInfo.name == PaymentNotificationListenerService::class.java.name }.serviceInfo
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", serviceInfo.permission)

        // Default disabled -> after adding component to Settings.Secure, helper reports enabled
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "")
        assertFalse(PaymentNotificationListenerService.isNotificationAccessEnabled(context))

        val cn = ComponentName(context, PaymentNotificationListenerService::class.java)
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", cn.flattenToString())
        assertTrue(PaymentNotificationListenerService.isNotificationAccessEnabled(context))
    }

    @Test
    fun `notification listener extracts bigText payment payload and skips summary ongoing and unrelated apps`() {
        val postTime = 1_794_988_800_000L // 2026-11-18

        // 1. Real WeChat Pay expandable notification using EXTRA_BIG_TEXT
        val wxNotification = Notification.Builder(context, "pay")
            .setContentTitle("微信支付")
            .setContentText("你有一笔新支出")
            .setStyle(Notification.BigTextStyle().bigText("你已成功向 中大学一食堂 付款¥18.50"))
            .build()
        val wxSbn = StatusBarNotification(
            "com.tencent.mm",
            "com.tencent.mm",
            101,
            null,
            0,
            0,
            0,
            wxNotification,
            Process.myUserHandle(),
            postTime
        )
        val extractedWx = PaymentNotificationListenerService.extractPayload(wxSbn)
        assertNotNull(extractedWx)
        assertEquals("com.tencent.mm", extractedWx?.packageName)
        assertEquals("微信支付", extractedWx?.title)
        assertTrue(extractedWx?.body?.contains("中大学一食堂") == true)
        assertTrue(extractedWx?.body?.contains("18.50") == true)
        assertEquals(postTime, extractedWx?.postedAtMillis)

        // 2. Real Alipay notification
        val aliNotification = Notification.Builder(context, "pay")
            .setContentTitle("支付宝通知")
            .setContentText("收到淘宝电商平台退款38.00元")
            .build()
        val aliSbn = StatusBarNotification(
            "com.eg.android.AlipayGphone",
            "com.eg.android.AlipayGphone",
            102,
            null,
            0,
            0,
            0,
            aliNotification,
            Process.myUserHandle(),
            postTime
        )
        val extractedAli = PaymentNotificationListenerService.extractPayload(aliSbn)
        assertNotNull(extractedAli)
        assertEquals("com.eg.android.AlipayGphone", extractedAli?.packageName)

        // 3. Group summary notification MUST be skipped
        val summaryNotif = Notification.Builder(context, "pay")
            .setContentTitle("微信支付")
            .setContentText("2条支付通知")
            .setGroupSummary(true)
            .build()
        val summarySbn = StatusBarNotification(
            "com.tencent.mm",
            "com.tencent.mm",
            103,
            null,
            0,
            0,
            0,
            summaryNotif,
            Process.myUserHandle(),
            postTime
        )
        assertNull(PaymentNotificationListenerService.extractPayload(summarySbn))

        // 4. Ongoing foreground notification MUST be skipped
        val ongoingNotif = Notification.Builder(context, "pay")
            .setContentTitle("支付宝")
            .setContentText("正在后台保护支付安全")
            .setOngoing(true)
            .build()
        val ongoingSbn = StatusBarNotification(
            "com.eg.android.AlipayGphone",
            "com.eg.android.AlipayGphone",
            104,
            null,
            0,
            0,
            0,
            ongoingNotif,
            Process.myUserHandle(),
            postTime
        )
        assertNull(PaymentNotificationListenerService.extractPayload(ongoingSbn))

        // 5. Unrelated package MUST be skipped
        val otherNotif = Notification.Builder(context, "other")
            .setContentTitle("系统消息")
            .setContentText("付款10.00元")
            .build()
        val otherSbn = StatusBarNotification(
            "com.example.other",
            "com.example.other",
            105,
            null,
            0,
            0,
            0,
            otherNotif,
            Process.myUserHandle(),
            postTime
        )
        assertNull(PaymentNotificationListenerService.extractPayload(otherSbn))
    }

    @Test
    fun `notification parser handles diverse WeChat and Alipay payment formats and rejects friend chats`() {
        val now = 1_794_988_800_000L

        // Unspaced WeChat/Alipay payment string
        val unspaced = StatementImporter.parsePaymentNotification(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝",
            text = "你已成功向中大学一食堂付款15.00元",
            nowMillis = now
        )
        assertNotNull(unspaced)
        assertEquals(PaymentChannel.ALIPAY, unspaced?.channel)
        assertEquals(TransactionDirection.EXPENSE, unspaced?.direction)
        assertEquals(1500L, unspaced?.amountFen)
        assertEquals("中大学一食堂", unspaced?.merchant)

        // WeChat small receipt income
        val receipt = StatementImporter.parsePaymentNotification(
            packageName = "com.tencent.mm",
            title = "微信收款助手",
            text = "微信支付收款0.01元",
            nowMillis = now
        )
        assertNotNull(receipt)
        assertEquals(PaymentChannel.WECHAT_PAY, receipt?.channel)
        assertEquals(TransactionDirection.INCOME, receipt?.direction)
        assertEquals(1L, receipt?.amountFen)

        // Comma-separated part-time income arrival
        val largeIncome = StatementImporter.parsePaymentNotification(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝",
            text = "你有一笔1,200.50元转账已到账，来自广州智算助研项目",
            nowMillis = now
        )
        assertNotNull(largeIncome)
        assertEquals(TransactionDirection.INCOME, largeIncome?.direction)
        assertEquals(120050L, largeIncome?.amountFen)
        assertEquals("广州智算助研项目", largeIncome?.merchant)

        // Refund notification
        val refund = StatementImporter.parsePaymentNotification(
            packageName = "com.eg.android.AlipayGphone",
            title = "支付宝通知",
            text = "收到淘宝电商平台退款38.00元",
            nowMillis = now
        )
        assertNotNull(refund)
        assertEquals(TransactionDirection.REFUND, refund?.direction)
        assertEquals(3800L, refund?.amountFen)
        assertEquals("淘宝电商平台", refund?.merchant)

        // False-positive protection: regular WeChat friend chat messages MUST NOT be captured
        assertNull(
            StatementImporter.parsePaymentNotification(
                packageName = "com.tencent.mm",
                title = "舍友小李",
                text = "我在3号楼12:30等你",
                nowMillis = now
            )
        )
        assertNull(
            StatementImporter.parsePaymentNotification(
                packageName = "com.tencent.mm",
                title = "张三",
                text = "昨天聚餐AA每个人35元",
                nowMillis = now
            )
        )
        assertNull(
            StatementImporter.parsePaymentNotification(
                packageName = "com.tencent.mm",
                title = "微信",
                text = "2条新消息",
                nowMillis = now
            )
        )
    }

    @Test
    fun `repository stages notification deduplicates repeat alerts and commits into ledger`() = runTest {
        val baseTime = 1_794_988_800_000L // 2026-11-18
        val monthKey = com.example.domain.finance.MoneyFormatter.monthKeyFromMillis(baseTime)

        // 1. Stage first captured WeChat Pay notification
        val first = repository.stagePaymentNotification(
            packageName = "com.tencent.mm",
            title = "微信支付",
            text = "已向 瑞幸咖啡 支付人民币19.90元",
            occurredAtMillis = baseTime
        ).getOrThrow()

        assertEquals(DiscrepancyType.NONE, first.discrepancyType)
        assertEquals(TransactionSourceType.NOTIFICATION_CAPTURE, first.sourceType)
        assertEquals(1990L, first.amountFen)
        assertEquals("beverage", first.suggestedCategoryId)
        assertEquals(0, repository.getTransactionsSnapshot(monthKey).size)

        // 2. Duplicate re-notification 5 seconds later MUST be flagged as POTENTIAL_DUPLICATE against pending queue
        val duplicatePending = repository.stagePaymentNotification(
            packageName = "com.tencent.mm",
            title = "微信支付",
            text = "已向 瑞幸咖啡 支付人民币19.90元",
            occurredAtMillis = baseTime + 5_000L
        ).getOrThrow()

        assertEquals(DiscrepancyType.POTENTIAL_DUPLICATE, duplicatePending.discrepancyType)
        assertEquals(2, repository.pendingCandidatesFlow.first().size)

        // 3. Skip duplicate & confirm genuine notification -> writes 1 transaction to ledger
        repository.resolveCandidateAsSkipOrDismiss(duplicatePending.id, CandidateResolution.MERGED_SKIP)
        repository.confirmCandidateToLedger(
            candidateId = first.id,
            selectedCategoryId = "beverage",
            selectedFundingSource = FundingSource.LIVING_ALLOWANCE,
            rememberMerchantPreference = true,
            note = "早八提神咖啡"
        )

        val txs = repository.getTransactionsSnapshot(monthKey)
        assertEquals(1, txs.size)
        assertEquals("瑞幸咖啡", txs.first().merchant)
        assertEquals(1990L, txs.first().amountFen)
        assertEquals(TransactionSourceType.NOTIFICATION_CAPTURE, txs.first().sourceType)
        assertTrue(repository.pendingCandidatesFlow.first().isEmpty())

        // 4. Also verify UTF-8 BOM CSV import works cleanly
        val bomCsv = "\uFEFF" + DemoStatementFactory.demoWeChatCsv
        val bomBatch = repository.importCsvIntoReviewQueue(bomCsv, "BOM微信账单", PaymentChannel.WECHAT_PAY).getOrThrow()
        assertEquals(9, bomBatch.totalCount)
    }

    @Test
    fun `TransactionsAndImportScreen displays notification capture status triggers simulation and renders staged card`() {
        var simulateCalled = false
        val stagedCandidate = ImportCandidateEntity(
            id = 99L,
            batchId = 1L,
            externalRefId = "NOTIF_WECHAT_TEST_2850",
            occurredAtMillis = 1_794_988_800_000L,
            monthKey = "2026-11",
            amountFen = 2850L,
            direction = TransactionDirection.EXPENSE,
            channel = PaymentChannel.WECHAT_PAY,
            fundingSource = FundingSource.LIVING_ALLOWANCE,
            sourceType = TransactionSourceType.NOTIFICATION_CAPTURE,
            merchant = "美团外卖",
            rawDescription = "微信支付 已向 美团外卖 支付人民币28.50元",
            suggestedCategoryId = "food",
            classificationSource = com.example.data.model.ClassificationSource.RULE_ENGINE,
            confidenceScore = 0.94f,
            discrepancyType = DiscrepancyType.NONE,
            discrepancyNote = "校验通过，等待用户确认入账"
        )

        composeRule.setContent {
            EasySpendTheme(darkTheme = false) {
                TransactionsAndImportScreen(
                    state = EasySpendUiState(
                        categories = ClassificationEngine.defaultCategories,
                        pendingCandidates = listOf(stagedCandidate)
                    ),
                    initialTab = 0,
                    onImportCsvUri = { _, _ -> },
                    onLoadDemoQueue = {},
                    onSimulateNotification = { isWeChat ->
                        if (isWeChat) simulateCalled = true
                    },
                    onRunAiBatchClassify = {},
                    onConfirmAllClean = {},
                    onConfirmCandidate = { _, _, _, _, _ -> },
                    onSkipOrDismissCandidate = { _, _ -> },
                    onAddManualTransaction = { _, _, _, _, _, _, _ -> },
                    onUpdateTxCategory = { _, _, _ -> },
                    onDeleteTx = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("notification_listener_status_card")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("open_notification_settings_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("simulate_wechat_notif_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(simulateCalled)
        composeRule.onNodeWithTag("import_review_lazy_list")
            .performScrollToNode(hasTestTag("candidate_card_99"))
        composeRule.onNodeWithTag("candidate_card_99")
            .assertIsDisplayed()
        composeRule.onNodeWithText("美团外卖")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `wechat campus auto-debit and stacked multi-window notifications are all extracted and parsed`() {
        PaymentNotificationListenerService.clearDiagnosticsForTests()
        val postTime = 1_794_988_900_000L

        // 1. Exact screenshot wording: 中山大学 通过零钱扣款 ¥ 4.50 扣费服务 校园轻松付 扣费内容 松涛园1楼大众窗口15
        val campusAutoDebit = StatementImporter.parsePaymentNotification(
            packageName = "com.tencent.mm",
            title = "微信支付",
            text = "中山大学\n通过零钱扣款\n¥ 4.50\n扣费服务 校园轻松付\n扣费内容 松涛园1楼大众窗口15",
            nowMillis = postTime
        )
        assertNotNull(campusAutoDebit)
        assertEquals(450L, campusAutoDebit?.amountFen)
        assertEquals(TransactionDirection.EXPENSE, campusAutoDebit?.direction)
        assertEquals("松涛园1楼大众窗口15", campusAutoDebit?.merchant)

        // 2. [2条] stacked prefix + 扣费 without ¥/元 symbol
        val bundledPrefix = StatementImporter.parsePaymentNotification(
            packageName = "com.tencent.mm",
            title = "[2条] 微信支付",
            text = "2条新消息 校园轻松付扣费4.50 扣费内容：松涛园1楼大众窗口19",
            nowMillis = postTime + 1_000L
        )
        assertNotNull(bundledPrefix)
        assertEquals(450L, bundledPrefix?.amountFen)
        assertEquals("松涛园1楼大众窗口19", bundledPrefix?.merchant)

        // 3. InboxStyle (EXTRA_TEXT_LINES) with 3 consecutive cafeteria deductions in one notification
        val inboxNotification = Notification.Builder(context, "pay")
            .setContentTitle("微信支付")
            .setContentText("3条新消息")
            .setStyle(
                Notification.InboxStyle()
                    .addLine("微信支付: 通过零钱扣款¥6.00 扣费内容：松涛园1楼大众窗口19")
                    .addLine("微信支付: 通过零钱扣款¥4.50 扣费内容：松涛园1楼大众窗口15")
                    .addLine("微信支付: 通过零钱扣款¥2.50 扣费内容：松涛园1楼汤品窗口3")
            )
            .build()
        @Suppress("DEPRECATION")
        val inboxSbn = StatusBarNotification(
            "com.tencent.mm",
            "com.tencent.mm",
            201,
            null,
            0,
            0,
            0,
            inboxNotification,
            Process.myUserHandle(),
            postTime
        )
        val inboxPayloads = PaymentNotificationListenerService.extractPayloads(inboxSbn)
        assertEquals(3, inboxPayloads.size)
        assertTrue(inboxPayloads.all { it.isFromBundle })

        // First delivery marks all 3 bundle lines; subsequent update adding a 4th line skips the first 3
        inboxPayloads.forEach { p ->
            assertFalse(PaymentNotificationListenerService.shouldSkipRecentlyProcessed(p))
        }
        inboxPayloads.forEach { p ->
            assertTrue(PaymentNotificationListenerService.shouldSkipRecentlyProcessed(p))
        }

        // 4. MessagingStyle (EXTRA_MESSAGES) with 2 consecutive cafeteria messages
        val person = android.app.Person.Builder().setName("微信支付").build()
        val messagingNotification = Notification.Builder(context, "pay")
            .setContentTitle("微信支付")
            .setStyle(
                Notification.MessagingStyle(person)
                    .addMessage("通过零钱扣款¥4.50 扣费服务 校园轻松付 扣费内容 松涛园1楼大众窗口19", postTime, person)
                    .addMessage("通过零钱扣款¥4.50 扣费服务 校园轻松付 扣费内容 松涛园1楼大众窗口15", postTime + 15_000L, person)
            )
            .build()
        @Suppress("DEPRECATION")
        val msgSbn = StatusBarNotification(
            "com.tencent.mm",
            "com.tencent.mm",
            202,
            null,
            0,
            0,
            0,
            messagingNotification,
            Process.myUserHandle(),
            postTime + 15_000L
        )
        val msgPayloads = PaymentNotificationListenerService.extractPayloads(msgSbn)
        assertEquals(2, msgPayloads.size)
        assertEquals(postTime, msgPayloads[0].postedAtMillis)
        assertEquals(postTime + 15_000L, msgPayloads[1].postedAtMillis)
    }

    @Test
    fun `consecutive cafeteria auto-debit windows with same or different amounts are staged without false duplicates`() = runTest {
        val t0 = 1_794_988_900_000L

        // Window 19: ¥4.50
        val c1 = repository.stagePaymentNotification(
            packageName = "com.tencent.mm",
            title = "微信支付",
            text = "中山大学 通过零钱扣款 ¥4.50 扣费服务 校园轻松付 扣费内容 松涛园1楼大众窗口19",
            occurredAtMillis = t0
        ).getOrThrow()

        // Window 15: SAME amount ¥4.50 just 8 seconds later -> distinct window, MUST NOT be flagged duplicate
        val c2 = repository.stagePaymentNotification(
            packageName = "com.tencent.mm",
            title = "微信支付",
            text = "中山大学 通过零钱扣款 ¥4.50 扣费服务 校园轻松付 扣费内容 松涛园1楼大众窗口15",
            occurredAtMillis = t0 + 8_000L
        ).getOrThrow()

        // Generic 中山大学 ¥6.00 at +40s, then another generic 中山大学 ¥6.00 at +100s (60s apart -> distinct purchase)
        val c3 = repository.stagePaymentNotification(
            packageName = "com.tencent.mm",
            title = "微信支付",
            text = "中山大学 通过零钱扣款 ¥6.00 扣费服务 校园轻松付",
            occurredAtMillis = t0 + 40_000L
        ).getOrThrow()
        val c4 = repository.stagePaymentNotification(
            packageName = "com.tencent.mm",
            title = "微信支付",
            text = "中山大学 通过零钱扣款 ¥6.00 扣费服务 校园轻松付",
            occurredAtMillis = t0 + 100_000L
        ).getOrThrow()

        // Confirm c3 to ledger, then stage another ¥3.50 at 中山大学 20s later -> MUST NOT be AMOUNT_MISMATCH
        repository.confirmCandidateToLedger(c3.id, "food", FundingSource.LIVING_ALLOWANCE, false, "")
        val c5 = repository.stagePaymentNotification(
            packageName = "com.tencent.mm",
            title = "微信支付",
            text = "中山大学 通过零钱扣款 ¥3.50 扣费服务 校园轻松付",
            occurredAtMillis = t0 + 120_000L
        ).getOrThrow()

        assertEquals(DiscrepancyType.NONE, c1.discrepancyType)
        assertEquals("food", c1.suggestedCategoryId)
        assertEquals(DiscrepancyType.NONE, c2.discrepancyType)
        assertEquals("food", c2.suggestedCategoryId)
        assertEquals(DiscrepancyType.NONE, c3.discrepancyType)
        assertEquals(DiscrepancyType.NONE, c4.discrepancyType)
        assertEquals(DiscrepancyType.NONE, c5.discrepancyType)
        assertEquals("松涛园1楼大众窗口19", c1.merchant)
        assertEquals("松涛园1楼大众窗口15", c2.merchant)
    }
}
