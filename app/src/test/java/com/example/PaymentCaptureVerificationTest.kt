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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        assertEquals("food", first.suggestedCategoryId)
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
            selectedCategoryId = "food",
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
        composeRule.onNodeWithTag("candidate_card_99")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("美团外卖")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
