package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EasySpendDatabase
import com.example.data.local.UserSettings
import com.example.data.model.ClassificationSource
import com.example.data.model.FundVault
import com.example.data.model.FundingSource
import com.example.data.model.PaymentChannel
import com.example.data.model.ReviewStatus
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionSourceType
import com.example.data.repository.EasySpendRepository
import com.example.data.repository.NotificationStageOutcome
import com.example.domain.finance.BudgetAndForecastEngine
import com.example.domain.finance.ClassificationEngine
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.StatementImporter
import com.example.service.PermissionSetupManager
import com.example.ui.EasySpendUiState
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OneTapPermissionDialog
import com.example.ui.theme.EasySpendTheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocationVaultAndPermissionsTest {

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
    fun `location context improves classification of ambiguous personal QR merchants`() {
        val medicalResult = ClassificationEngine.classify(
            merchant = "张医生个人收款",
            description = "扫码付款",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 6800L,
            locationLabel = "广州·天河区·中山大学校医院"
        )
        assertEquals("medical", medicalResult.categoryId)

        val canteenResult = ClassificationEngine.classify(
            merchant = "王阿姨",
            description = "微信扫码",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 1450L,
            locationLabel = "广州·海珠区·春晖园食堂"
        )
        assertEquals("food", canteenResult.categoryId)

        // Historical location memory should classify future ambiguous payment at the same venue
        val history = listOf(
            TransactionEntity(
                id = 1L,
                externalRefId = "H1",
                occurredAtMillis = System.currentTimeMillis() - 86_400_000L,
                monthKey = MoneyFormatter.monthKeyFromMillis(System.currentTimeMillis()),
                amountFen = 4500L,
                direction = TransactionDirection.EXPENSE,
                channel = PaymentChannel.WECHAT_PAY,
                fundingSource = FundingSource.UNASSIGNED,
                fundVault = FundVault.POCKET,
                sourceType = TransactionSourceType.MANUAL_ENTRY,
                merchant = "柜台收款",
                rawDescription = "扫码",
                categoryId = "entertainment",
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1.0f,
                reviewStatus = ReviewStatus.USER_CONFIRMED,
                locationLabel = "深圳·南山区·海岸城保利影院"
            )
        )
        val historyMatch = ClassificationEngine.classify(
            merchant = "小陈收款",
            description = "扫码付款",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 5200L,
            historicalTransactions = history,
            locationLabel = "深圳·南山区·海岸城保利影院"
        )
        assertEquals("entertainment", historyMatch.categoryId)
    }

    @Test
    fun `vault portfolio separates pocket and savings inflow outflow balance and survives csv roundtrip`() = runTest {
        val now = System.currentTimeMillis()
        val monthKey = MoneyFormatter.monthKeyFromMillis(now)

        // Pocket: +1500 yuan income, -200 yuan expense, +50 yuan refund => netExpense = 150 yuan, netBalance = +1350 yuan
        repository.addManualTransaction(
            amountFen = 150_000L,
            direction = TransactionDirection.INCOME,
            channel = PaymentChannel.BANK_CARD,
            fundingSource = FundingSource.LIVING_ALLOWANCE,
            categoryId = "allowance",
            merchant = "家里生活费",
            note = "本月零花",
            occurredAtMillis = now - 5_000L,
            fundVault = FundVault.POCKET
        )
        val expenseId = repository.addManualTransaction(
            amountFen = 20_000L,
            direction = TransactionDirection.EXPENSE,
            channel = PaymentChannel.WECHAT_PAY,
            fundingSource = FundingSource.LIVING_ALLOWANCE,
            categoryId = "food",
            merchant = "食堂聚餐",
            note = "零花消费",
            occurredAtMillis = now - 4_000L,
            fundVault = FundVault.POCKET,
            locationLabel = "广州·番禺区·学一食堂"
        )
        repository.addManualTransaction(
            amountFen = 5_000L,
            direction = TransactionDirection.REFUND,
            channel = PaymentChannel.WECHAT_PAY,
            fundingSource = FundingSource.LIVING_ALLOWANCE,
            categoryId = "food",
            merchant = "食堂退款",
            note = "退款回零花",
            occurredAtMillis = now - 3_000L,
            fundVault = FundVault.POCKET
        )

        // Savings: +800 yuan income, -100 yuan expense => netExpense = 100 yuan, netBalance = +700 yuan
        repository.addManualTransaction(
            amountFen = 80_000L,
            direction = TransactionDirection.INCOME,
            channel = PaymentChannel.ALIPAY,
            fundingSource = FundingSource.PART_TIME_INCOME,
            categoryId = "part_time",
            merchant = "家教兼职",
            note = "存进储蓄",
            occurredAtMillis = now - 2_000L,
            fundVault = FundVault.SAVINGS
        )
        repository.addManualTransaction(
            amountFen = 10_000L,
            direction = TransactionDirection.EXPENSE,
            channel = PaymentChannel.ALIPAY,
            fundingSource = FundingSource.UNASSIGNED,
            categoryId = "study",
            merchant = "考试报名费",
            note = "动用储蓄",
            occurredAtMillis = now - 1_000L,
            fundVault = FundVault.SAVINGS
        )

        val summary = repository.getMonthlySummarySnapshot(monthKey)
        assertEquals(150_000L, summary.monthlyVaultSummary.pocket.incomeFen)
        assertEquals(15_000L, summary.monthlyVaultSummary.pocket.netExpenseFen)
        assertEquals(135_000L, summary.monthlyVaultSummary.pocket.netBalanceFen)
        assertEquals(1, summary.monthlyVaultSummary.pocket.incomeCount)
        assertEquals(1, summary.monthlyVaultSummary.pocket.expenseCount)

        assertEquals(80_000L, summary.monthlyVaultSummary.savings.incomeFen)
        assertEquals(10_000L, summary.monthlyVaultSummary.savings.netExpenseFen)
        assertEquals(70_000L, summary.monthlyVaultSummary.savings.netBalanceFen)

        // Editing vault & reverting works cleanly
        val prev = repository.updateTransactionCategoryAndVault(
            txId = expenseId,
            categoryId = "social",
            fundVault = FundVault.SAVINGS,
            rememberMerchant = false
        )
        assertNotNull(prev)
        assertEquals(FundVault.POCKET, prev!!.fundVault)
        val afterEdit = db.transactionDao().getById(expenseId)!!
        assertEquals(FundVault.SAVINGS, afterEdit.fundVault)

        repository.revertTransactionCategoryAndVault(
            txId = expenseId,
            previousCategoryId = prev.categoryId,
            previousVault = prev.fundVault,
            previousSource = prev.classificationSource
        )
        assertEquals(FundVault.POCKET, db.transactionDao().getById(expenseId)!!.fundVault)

        // Export & re-parse CSV preserves both FundVault and locationLabel
        val csv = repository.exportAllTransactionsCsv()
        assertTrue(csv.contains("资金库"))
        assertTrue(csv.contains("消费地点"))
        assertTrue(csv.contains("储蓄库"))
        assertTrue(csv.contains("广州·番禺区·学一食堂"))

        val reparsed = StatementImporter.parseCsvStatement(csv)
        assertEquals(5, reparsed.size)
        val savingsIncome = reparsed.first { it.merchant == "家教兼职" }
        assertEquals(FundVault.SAVINGS, savingsIncome.fundVault)
        val canteenExpense = reparsed.first { it.merchant == "食堂聚餐" }
        assertEquals("广州·番禺区·学一食堂", canteenExpense.locationLabel)
    }

    @Test
    fun `staged notification carries location label into classification and confirmed transaction`() = runTest {
        val outcome = repository.stagePaymentNotificationDetailed(
            packageName = "com.tencent.mm",
            title = "微信支付",
            text = "已向 李医生收款 支付人民币42.00元",
            occurredAtMillis = System.currentTimeMillis(),
            locationLabel = "广州·越秀区·中山眼科医院"
        )
        assertTrue(outcome is NotificationStageOutcome.Staged)
        val staged = (outcome as NotificationStageOutcome.Staged).candidate
        assertEquals("medical", staged.suggestedCategoryId)
        assertEquals("广州·越秀区·中山眼科医院", staged.locationLabel)

        val txId = repository.confirmCandidateToLedger(
            candidateId = staged.id,
            selectedCategoryId = staged.suggestedCategoryId,
            selectedFundingSource = staged.fundingSource,
            rememberMerchantPreference = false,
            note = "",
            selectedFundVault = FundVault.SAVINGS
        )
        assertNotNull(txId)
        val tx = db.transactionDao().getById(txId!!)!!
        assertEquals(FundVault.SAVINGS, tx.fundVault)
        assertEquals("广州·越秀区·中山眼科医院", tx.locationLabel)
    }

    @Test
    fun `manifest declares coarse location without background location and permission snapshot tracks 4 items`() {
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(requested.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertFalse(requested.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))

        val snap = PermissionSetupManager.snapshot(context)
        assertEquals(4, snap.totalCount)
        assertTrue(snap.grantedCount in 0..4)
    }

    @Test
    fun `DashboardScreen shows FundVaultSummaryCard only when detailedModeEnabled is true`() {
        val now = System.currentTimeMillis()
        val monthKey = MoneyFormatter.monthKeyFromMillis(now)
        val sampleTxs = listOf(
            TransactionEntity(
                id = 1L,
                externalRefId = "V1",
                occurredAtMillis = now,
                monthKey = monthKey,
                amountFen = 100_000L,
                direction = TransactionDirection.INCOME,
                channel = PaymentChannel.BANK_CARD,
                fundingSource = FundingSource.LIVING_ALLOWANCE,
                fundVault = FundVault.POCKET,
                sourceType = TransactionSourceType.MANUAL_ENTRY,
                merchant = "生活费",
                rawDescription = "",
                categoryId = "allowance",
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1f,
                reviewStatus = ReviewStatus.USER_CONFIRMED
            ),
            TransactionEntity(
                id = 2L,
                externalRefId = "V2",
                occurredAtMillis = now,
                monthKey = monthKey,
                amountFen = 50_000L,
                direction = TransactionDirection.INCOME,
                channel = PaymentChannel.ALIPAY,
                fundingSource = FundingSource.PART_TIME_INCOME,
                fundVault = FundVault.SAVINGS,
                sourceType = TransactionSourceType.MANUAL_ENTRY,
                merchant = "奖学金",
                rawDescription = "",
                categoryId = "part_time",
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1f,
                reviewStatus = ReviewStatus.USER_CONFIRMED
            )
        )
        val summary = BudgetAndForecastEngine.evaluateMonth(
            monthKey = monthKey,
            transactions = sampleTxs,
            budgets = emptyList(),
            categories = ClassificationEngine.defaultCategories,
            savingsGoal = null
        )

        var detailedEnabled = true
        val stateHolder = androidx.compose.runtime.mutableStateOf(
            EasySpendUiState(
                settings = UserSettings(activeMonthKey = monthKey, detailedModeEnabled = true),
                categories = ClassificationEngine.defaultCategories,
                transactions = sampleTxs,
                allTransactions = sampleTxs,
                summary = summary
            )
        )

        composeRule.setContent {
            EasySpendTheme {
                DashboardScreen(
                    state = stateHolder.value,
                    onLoadDemoQueue = {},
                    onLoadDemoBaseline = {},
                    onAdd300Essential = {},
                    onNavigateToImportQueue = {},
                    onNavigateToBudget = {},
                    onNavigateToAgentWithPrompt = {},
                    onToggleDetailedMode = { enabled ->
                        detailedEnabled = enabled
                        stateHolder.value = stateHolder.value.copy(
                            settings = stateHolder.value.settings.copy(detailedModeEnabled = enabled)
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("fund_vault_summary_card"))
        composeRule.onNodeWithTag("fund_vault_summary_card").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_card_pocket").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_card_savings").assertIsDisplayed()

        // Turn off detailed mode -> vault card must disappear completely
        composeRule.runOnUiThread {
            stateHolder.value = stateHolder.value.copy(
                settings = stateHolder.value.settings.copy(detailedModeEnabled = false)
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fund_vault_summary_card").assertDoesNotExist()
        assertTrue(detailedEnabled)
    }

    @Test
    fun `OneTapPermissionDialog renders all 4 permission items and primary one-tap button`() {
        composeRule.setContent {
            EasySpendTheme {
                OneTapPermissionDialog(
                    keepAliveEnabled = true,
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("one_tap_permission_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("perm_row_post_notifications").assertIsDisplayed()
        composeRule.onNodeWithTag("perm_row_location").assertIsDisplayed()
        composeRule.onNodeWithTag("perm_row_notification_listener").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("perm_row_battery_optimization").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("one_tap_permission_setup_button").assertIsDisplayed()
    }
}
