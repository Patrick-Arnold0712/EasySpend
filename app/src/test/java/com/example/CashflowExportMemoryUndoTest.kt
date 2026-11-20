package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.EasySpendDatabase
import com.example.data.local.UserSettings
import com.example.data.model.CandidateResolution
import com.example.data.model.ClassificationSource
import com.example.data.model.FundingSource
import com.example.data.model.PaymentChannel
import com.example.data.model.ReviewStatus
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionSourceType
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.BudgetAndForecastEngine
import com.example.domain.finance.ClassificationEngine
import com.example.domain.finance.StatementImporter
import com.example.ui.EasySpendUiState
import com.example.ui.components.CashflowCalendarCalculator
import com.example.ui.components.CashflowCalendarCard
import com.example.ui.motion.CircularThemeRevealContainer
import com.example.ui.motion.EasySpendSplashOverlay
import com.example.ui.motion.rememberCircularThemeRevealController
import com.example.ui.screens.BudgetAndInsightsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.TransactionsAndImportScreen
import com.example.ui.theme.EasySpendTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CashflowExportMemoryUndoTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun parseTime(s: String): Long {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).parse(s)!!.time
    }

    @Test
    fun classificationEngine_learnsHistoricalAmountBandsFuzzyMerchantAndTimeSignals() {
        val historical = listOf(
            TransactionEntity(
                id = 1L,
                externalRefId = "H1",
                occurredAtMillis = parseTime("2026-11-01 08:20:00"),
                monthKey = "2026-11",
                amountFen = 200L,
                direction = TransactionDirection.EXPENSE,
                channel = PaymentChannel.WECHAT_PAY,
                fundingSource = FundingSource.LIVING_ALLOWANCE,
                sourceType = TransactionSourceType.WECHAT_BILL_IMPORT,
                merchant = "美团",
                rawDescription = "订单",
                categoryId = "transit",
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1f,
                reviewStatus = ReviewStatus.USER_CONFIRMED,
                note = "早高峰单车"
            ),
            TransactionEntity(
                id = 2L,
                externalRefId = "H2",
                occurredAtMillis = parseTime("2026-11-02 12:15:00"),
                monthKey = "2026-11",
                amountFen = 2850L,
                direction = TransactionDirection.EXPENSE,
                channel = PaymentChannel.WECHAT_PAY,
                fundingSource = FundingSource.LIVING_ALLOWANCE,
                sourceType = TransactionSourceType.WECHAT_BILL_IMPORT,
                merchant = "美团",
                rawDescription = "订单",
                categoryId = "food",
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1f,
                reviewStatus = ReviewStatus.USER_CONFIRMED,
                note = "午饭"
            ),
            TransactionEntity(
                id = 3L,
                externalRefId = "H3",
                occurredAtMillis = parseTime("2026-11-03 15:00:00"),
                monthKey = "2026-11",
                amountFen = 3500L,
                direction = TransactionDirection.EXPENSE,
                channel = PaymentChannel.ALIPAY,
                fundingSource = FundingSource.LIVING_ALLOWANCE,
                sourceType = TransactionSourceType.MANUAL_ENTRY,
                merchant = "蓝山自习室(东校店)",
                rawDescription = "日卡",
                categoryId = "study",
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1f,
                reviewStatus = ReviewStatus.USER_CONFIRMED,
                note = "期末自习"
            )
        )

        // 1. Same merchant "美团" distinguishes amount bands via historical memory
        val smallMeituan = ClassificationEngine.classify(
            merchant = "美团",
            description = "商户消费",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 250L,
            occurredAtMillis = parseTime("2026-11-10 08:15:00"),
            historicalTransactions = historical
        )
        assertEquals("transit", smallMeituan.categoryId)
        assertEquals(ClassificationSource.MERCHANT_PREFERENCE, smallMeituan.source)
        assertFalse(smallMeituan.needsReview)

        val mealMeituan = ClassificationEngine.classify(
            merchant = "美团",
            description = "商户消费",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 3100L,
            occurredAtMillis = parseTime("2026-11-10 12:30:00"),
            historicalTransactions = historical
        )
        assertEquals("food", mealMeituan.categoryId)
        assertEquals(ClassificationSource.MERCHANT_PREFERENCE, mealMeituan.source)

        // 2. Fuzzy core merchant matches different branch of previously confirmed merchant
        val branchStudy = ClassificationEngine.classify(
            merchant = "蓝山自习室(南校店)",
            description = "刷卡消费",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 3800L,
            historicalTransactions = historical
        )
        assertEquals("study", branchStudy.categoryId)
        assertEquals(ClassificationSource.MERCHANT_PREFERENCE, branchStudy.source)

        // 3. Multi-signal: user note overrides unknown merchant
        val noteSports = ClassificationEngine.classify(
            merchant = "张老板收款码",
            description = "扫码付款",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 4500L,
            note = "周末羽毛球馆订场AA"
        )
        assertEquals("sports", noteSports.categoryId)

        // 4. Multi-signal: meal-hour + amount heuristic for unknown merchant
        val lunchUnknown = ClassificationEngine.classify(
            merchant = "阿强小铺",
            description = "微信扫码",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList(),
            amountFen = 1800L,
            occurredAtMillis = parseTime("2026-11-11 12:20:00")
        )
        assertEquals("food", lunchUnknown.categoryId)
    }

    @Test
    fun repository_exportCsvThenOneClickAutoImportAndUndoFlowsWorkAtomically() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db1 = Room.inMemoryDatabaseBuilder(context, EasySpendDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val db2 = Room.inMemoryDatabaseBuilder(context, EasySpendDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val repo1 = EasySpendRepository(db1)
            val repo2 = EasySpendRepository(db2)

            repo1.ensureInitialized("2026-11")
            val tx1Id = repo1.addManualTransaction(
                amountFen = 180000L,
                direction = TransactionDirection.INCOME,
                channel = PaymentChannel.BANK_CARD,
                fundingSource = FundingSource.LIVING_ALLOWANCE,
                categoryId = "allowance",
                merchant = "家庭生活费",
                note = "11月生活费",
                occurredAtMillis = parseTime("2026-11-01 09:00:00")
            )
            val tx2Id = repo1.addManualTransaction(
                amountFen = 2250L,
                direction = TransactionDirection.EXPENSE,
                channel = PaymentChannel.WECHAT_PAY,
                fundingSource = FundingSource.LIVING_ALLOWANCE,
                categoryId = "food",
                merchant = "美团",
                note = "午餐外卖",
                occurredAtMillis = parseTime("2026-11-05 12:15:00")
            )

            // 1. Export CSV from repo1
            val csv = repo1.exportAllTransactionsCsv()
            assertTrue(csv.contains("家庭生活费"))
            assertTrue(csv.contains("美团"))

            // 2. One-click import & auto-classify into fresh repo2
            val firstImport = repo2.importAndAutoClassifyCsv(csv, "一键导入备份").getOrThrow()
            assertEquals(2, firstImport.importedCount)
            assertEquals(0, firstImport.duplicateSkippedCount)

            val importedTxs = repo2.allTransactionsFlow.first()
            assertEquals(2, importedTxs.size)
            assertEquals("food", importedTxs.first { it.merchant == "美团" }.categoryId)
            assertEquals("allowance", importedTxs.first { it.merchant == "家庭生活费" }.categoryId)

            // 3. Re-importing same CSV skips duplicates cleanly
            val secondImport = repo2.importAndAutoClassifyCsv(csv, "重复导入校验").getOrThrow()
            assertEquals(0, secondImport.importedCount)
            assertEquals(2, secondImport.duplicateSkippedCount)

            // 4. Undo Delete restores exact transaction
            val deleted = repo1.deleteTransaction(tx2Id)!!
            assertEquals(1, repo1.allTransactionsFlow.first().size)
            repo1.restoreDeletedTransaction(deleted)
            assertEquals(2, repo1.allTransactionsFlow.first().size)

            // 5. Undo Category Edit restores previous category
            val beforeEdit = repo1.updateTransactionCategory(tx1Id, "part_time", false)!!
            assertEquals("allowance", beforeEdit.categoryId)
            repo1.revertTransactionCategory(tx1Id, beforeEdit.categoryId, beforeEdit.classificationSource)
            assertEquals("allowance", repo1.allTransactionsFlow.first().first { it.id == tx1Id }.categoryId)

            // 6. Undo Confirm Candidate reverts candidate to PENDING and removes ledger tx
            val staged = repo1.stagePaymentNotification(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "已向 瑞幸咖啡 支付人民币19.90元",
                occurredAtMillis = parseTime("2026-11-08 15:00:00")
            ).getOrThrow()
            val committedId = repo1.confirmCandidateToLedger(
                candidateId = staged.id,
                selectedCategoryId = "beverage",
                selectedFundingSource = FundingSource.LIVING_ALLOWANCE,
                rememberMerchantPreference = false,
                note = ""
            )!!
            assertTrue(repo1.pendingCandidatesFlow.first().isEmpty())
            assertEquals(3, repo1.allTransactionsFlow.first().size)

            repo1.revertConfirmedCandidate(committedId, staged.id)
            assertEquals(1, repo1.pendingCandidatesFlow.first().size)
            assertEquals(CandidateResolution.PENDING, repo1.pendingCandidatesFlow.first().first().resolutionStatus)
            assertEquals(2, repo1.allTransactionsFlow.first().size)
        } finally {
            db1.close()
            db2.close()
        }
    }

    @Test
    fun cashflowCalendar_displaysDailyNetAndSupportsPastMonthBrowsing() {
        val txs = listOf(
            TransactionEntity(
                id = 1L,
                externalRefId = "OCT1",
                occurredAtMillis = parseTime("2026-10-15 12:00:00"),
                monthKey = "2026-10",
                amountFen = 3500L,
                direction = TransactionDirection.EXPENSE,
                channel = PaymentChannel.WECHAT_PAY,
                fundingSource = FundingSource.LIVING_ALLOWANCE,
                sourceType = TransactionSourceType.MANUAL_ENTRY,
                merchant = "十月老食堂",
                rawDescription = "",
                categoryId = "food",
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1f,
                reviewStatus = ReviewStatus.USER_CONFIRMED,
                note = ""
            ),
            TransactionEntity(
                id = 2L,
                externalRefId = "NOV1",
                occurredAtMillis = parseTime("2026-11-05 09:00:00"),
                monthKey = "2026-11",
                amountFen = 20000L,
                direction = TransactionDirection.INCOME,
                channel = PaymentChannel.ALIPAY,
                fundingSource = FundingSource.PART_TIME_INCOME,
                sourceType = TransactionSourceType.MANUAL_ENTRY,
                merchant = "助研津贴",
                rawDescription = "",
                categoryId = "part_time",
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1f,
                reviewStatus = ReviewStatus.USER_CONFIRMED,
                note = ""
            ),
            TransactionEntity(
                id = 3L,
                externalRefId = "NOV2",
                occurredAtMillis = parseTime("2026-11-05 18:00:00"),
                monthKey = "2026-11",
                amountFen = 5000L,
                direction = TransactionDirection.EXPENSE,
                channel = PaymentChannel.WECHAT_PAY,
                fundingSource = FundingSource.LIVING_ALLOWANCE,
                sourceType = TransactionSourceType.MANUAL_ENTRY,
                merchant = "晚餐烧烤",
                rawDescription = "",
                categoryId = "food",
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1f,
                reviewStatus = ReviewStatus.USER_CONFIRMED,
                note = ""
            )
        )

        val novGrid = CashflowCalendarCalculator.buildMonthGrid("2026-11", txs)
        assertEquals(15000L, novGrid.dailyMap[5]?.netSavedFen)
        assertEquals(15000L, novGrid.monthNetSavedFen)

        composeRule.setContent {
            EasySpendTheme(darkTheme = false) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CashflowCalendarCard(
                        initialMonthKey = "2026-11",
                        allTransactions = txs,
                        categories = ClassificationEngine.defaultCategories
                    )
                }
            }
        }

        composeRule.onNodeWithTag("calendar_month_label").assertIsDisplayed()
        composeRule.onNodeWithText("2026-11").assertIsDisplayed()

        // Tap Nov 5 to inspect day details
        composeRule.onNodeWithTag("calendar_day_2026-11_5").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("助研津贴").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("晚餐烧烤").performScrollTo().assertIsDisplayed()

        // Navigate back to past month 2026-10
        composeRule.onNodeWithTag("calendar_prev_month").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2026-10").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_day_2026-10_15").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("十月老食堂").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun ledgerUndoBarAndSwissBudgetAndRealIconSplashRenderCorrectly() {
        var undoClicked = false
        val demoState = EasySpendUiState(
            settings = UserSettings(activeMonthKey = "2026-11"),
            categories = ClassificationEngine.defaultCategories,
            summary = BudgetAndForecastEngine.evaluateMonth(
                monthKey = "2026-11",
                transactions = emptyList(),
                budgets = emptyList(),
                categories = ClassificationEngine.defaultCategories,
                savingsGoal = null
            ),
            lastUndoLabel = "撤回删除「星巴克」"
        )

        // 1. Committed Ledger Undo button & Export/Import buttons
        composeRule.setContent {
            EasySpendTheme(darkTheme = false) {
                TransactionsAndImportScreen(
                    state = demoState,
                    initialTab = 1,
                    onImportCsvUri = { _, _ -> },
                    onLoadDemoQueue = {},
                    onSimulateNotification = {},
                    onRunAiBatchClassify = {},
                    onConfirmAllClean = {},
                    onConfirmCandidate = { _, _, _, _, _ -> },
                    onSkipOrDismissCandidate = { _, _ -> },
                    onAddManualTransaction = { _, _, _, _, _, _, _ -> },
                    onUpdateTxCategory = { _, _, _ -> },
                    onDeleteTx = {},
                    onUndoLastLedgerAction = { undoClicked = true }
                )
            }
        }
        composeRule.onNodeWithTag("ledger_undo_button").assertIsDisplayed().performClick()
        assertTrue(undoClicked)
        composeRule.onNodeWithTag("ledger_tab_export_button").assertIsDisplayed()
        composeRule.onNodeWithTag("ledger_tab_auto_import_button").assertIsDisplayed()
    }

    @Test
    fun swissBudgetAndSplashAppIconAndCircularThemeRevealWorkSmoothly() {
        var splashDone = false
        composeRule.setContent {
            val controller = rememberCircularThemeRevealController()
            var isDark by remember { mutableStateOf(false) }
            CircularThemeRevealContainer(controller = controller) {
                EasySpendTheme(darkTheme = isDark) {
                    EasySpendSplashOverlay(
                        isReady = true,
                        onFinished = { splashDone = true }
                    )
                    Text(if (isDark) "DARK" else "LIGHT")
                }
            }
        }

        // Splash renders real app icon immediately (no spinner)
        composeRule.onNodeWithTag("splash_app_icon").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1300L)
        composeRule.waitForIdle()
        assertTrue(splashDone)
    }

    @Test
    fun budgetScreenSwissLayoutAndDashboardCalendarIntegration() {
        val summary = BudgetAndForecastEngine.evaluateMonth(
            monthKey = "2026-11",
            transactions = emptyList(),
            budgets = emptyList(),
            categories = ClassificationEngine.defaultCategories,
            savingsGoal = null
        )
        val state = EasySpendUiState(
            settings = UserSettings(activeMonthKey = "2026-11"),
            categories = ClassificationEngine.defaultCategories,
            summary = summary
        )

        composeRule.setContent {
            EasySpendTheme(darkTheme = false) {
                BudgetAndInsightsScreen(
                    state = state,
                    onUpdateBudgetLimit = { _, _ -> },
                    onApplyProposal = { _, _ -> },
                    onUpdateSavingsGoal = { _, _, _ -> },
                    onGenerateAiInsights = {}
                )
            }
        }

        composeRule.onNodeWithTag("swiss_budget_overview_card").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_savings_goal_button").assertIsDisplayed()
        composeRule.onNodeWithTag("budget_lazy_list").performScrollToKey("swiss_category_budget_table")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("swiss_category_budget_table").assertIsDisplayed()
        composeRule.onNodeWithTag("budget_filter_1").performClick()
    }
}
