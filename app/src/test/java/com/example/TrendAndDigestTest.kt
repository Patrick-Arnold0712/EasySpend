package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import com.example.data.model.ClassificationSource
import com.example.data.model.FundingSource
import com.example.data.model.PaymentChannel
import com.example.data.model.ReviewStatus
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionSourceType
import com.example.domain.finance.CashflowAnalyticsEngine
import com.example.domain.finance.ClassificationEngine
import com.example.domain.finance.ReportPeriod
import com.example.ui.components.CashflowTrendChartCard
import com.example.ui.screens.FinancialDigestDialog
import com.example.ui.theme.EasySpendTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class TrendAndDigestTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun ts(s: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).parse(s)!!.time

    private fun tx(
        id: Long,
        time: String,
        amountFen: Long,
        direction: TransactionDirection,
        merchant: String,
        categoryId: String
    ): TransactionEntity = TransactionEntity(
        id = id,
        externalRefId = "T_$id",
        occurredAtMillis = ts(time),
        monthKey = time.substring(0, 7),
        amountFen = amountFen,
        direction = direction,
        channel = PaymentChannel.WECHAT_PAY,
        fundingSource = FundingSource.LIVING_ALLOWANCE,
        sourceType = TransactionSourceType.MANUAL_ENTRY,
        merchant = merchant,
        rawDescription = "",
        categoryId = categoryId,
        classificationSource = ClassificationSource.USER_CONFIRMED,
        confidenceScore = 1f,
        reviewStatus = ReviewStatus.USER_CONFIRMED,
        note = ""
    )

    private val sampleTransactions: List<TransactionEntity> by lazy {
        listOf(
            // Oct 2026 (previous month for MoM comparison)
            tx(1, "2026-10-05 09:00:00", 180_000L, TransactionDirection.INCOME, "生活费", "allowance"),
            tx(2, "2026-10-12 12:00:00", 50_000L, TransactionDirection.EXPENSE, "校园食堂", "food"),
            // Nov 2026 (current month & week)
            tx(3, "2026-11-02 09:00:00", 200_000L, TransactionDirection.INCOME, "生活费", "allowance"),
            tx(4, "2026-11-16 12:10:00", 2_500L, TransactionDirection.EXPENSE, "一食堂窗口", "food"),
            tx(5, "2026-11-17 15:30:00", 1_800L, TransactionDirection.EXPENSE, "瑞幸咖啡", "beverage"),
            tx(6, "2026-11-18 12:20:00", 2_200L, TransactionDirection.EXPENSE, "一食堂窗口", "food"),
            tx(7, "2026-11-18 19:00:00", 15_000L, TransactionDirection.EXPENSE, "耐克奥莱", "shopping"),
            tx(8, "2026-11-18 20:00:00", 3_000L, TransactionDirection.REFUND, "耐克奥莱", "shopping"),
            // Mar 2026 (earlier in the year for Yearly report)
            tx(9, "2026-03-10 10:00:00", 100_000L, TransactionDirection.INCOME, "兼职家教", "part_time"),
            tx(10, "2026-03-11 18:00:00", 12_000L, TransactionDirection.EXPENSE, "书店教材", "study")
        )
    }

    @Test
    fun analyticsEngine_computesWeekMonthYearTrendsAndDigestsAccurately() {
        val anchor = ts("2026-11-18 12:00:00")

        // 1. Weekly trend (2026-11-16 Mon .. 2026-11-22 Sun)
        val weekTrend = CashflowAnalyticsEngine.buildTrendReport(ReportPeriod.WEEK, anchor, sampleTransactions)
        assertEquals(7, weekTrend.buckets.size)
        assertEquals(0L, weekTrend.totalIncomeFen)
        // 2500 + 1800 + 2200 + 15000 - 3000 refund = 18500
        assertEquals(18_500L, weekTrend.totalExpenseFen)

        // 2. Monthly trend (2026-11) with MoM comparison vs 2026-10
        val monthTrend = CashflowAnalyticsEngine.buildTrendReport(ReportPeriod.MONTH, anchor, sampleTransactions)
        assertEquals(30, monthTrend.buckets.size)
        assertEquals(200_000L, monthTrend.totalIncomeFen)
        assertEquals(18_500L, monthTrend.totalExpenseFen)
        assertEquals(181_500L, monthTrend.netSavedFen)
        assertEquals(50_000L, monthTrend.prevTotalExpenseFen)
        assertEquals(-63, monthTrend.expenseDeltaPercent)

        // 3. Yearly trend (2026: 12 monthly buckets)
        val yearTrend = CashflowAnalyticsEngine.buildTrendReport(ReportPeriod.YEAR, anchor, sampleTransactions)
        assertEquals(12, yearTrend.buckets.size)
        // Income: 100_000 (Mar) + 180_000 (Oct) + 200_000 (Nov) = 480_000
        assertEquals(480_000L, yearTrend.totalIncomeFen)
        // Expense: 12_000 (Mar) + 50_000 (Oct) + 18_500 (Nov) = 80_500
        assertEquals(80_500L, yearTrend.totalExpenseFen)

        // 4. Monthly Digest checks grade, top categories, top merchant, share text
        val monthDigest = CashflowAnalyticsEngine.buildDigestReport(
            period = ReportPeriod.MONTH,
            anchorMillis = anchor,
            allTransactions = sampleTransactions,
            categories = ClassificationEngine.defaultCategories,
            savingsTargetFen = 100_000L
        )
        assertEquals("S", monthDigest.gradeCode)
        assertEquals("shopping", monthDigest.topCategories.first().categoryId)
        assertEquals(12_000L, monthDigest.topCategories.first().amountFen)
        assertNotNull(monthDigest.topSingleExpense)
        assertEquals("耐克奥莱", monthDigest.topSingleExpense?.merchant)
        assertEquals("一食堂窗口", monthDigest.topFrequentMerchant?.merchant)
        assertEquals(2, monthDigest.topFrequentMerchant?.count)
        assertTrue(monthDigest.sharePlainText.contains("2026年11月"))
        assertTrue(monthDigest.highlights.isNotEmpty())
        assertTrue(monthDigest.actionTips.isNotEmpty())
    }

    @Test
    fun trendChartCard_switchesWeekMonthYearAndTriggersDigest() {
        var openedPeriod: ReportPeriod? = null
        composeRule.setContent {
            EasySpendTheme(darkTheme = false) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CashflowTrendChartCard(
                        activeMonthKey = "2026-11",
                        allTransactions = sampleTransactions,
                        onOpenDigest = { period, _ -> openedPeriod = period }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("cashflow_trend_card").assertIsDisplayed()
        composeRule.onNodeWithTag("trend_period_label").assertIsDisplayed()
        composeRule.onNodeWithText("2026年11月").assertIsDisplayed()

        // Switch to Week view
        composeRule.onNodeWithTag("trend_period_week").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("查看周报").assertIsDisplayed()

        // Switch to Year view
        composeRule.onNodeWithTag("trend_period_year").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2026年度").assertIsDisplayed()
        composeRule.onNodeWithText("查看年报").assertIsDisplayed()

        // Open digest from trend card
        composeRule.onNodeWithTag("trend_open_digest_button").performClick()
        assertEquals(ReportPeriod.YEAR, openedPeriod)
    }

    @Test
    fun financialDigestDialog_switchesPeriodsAndInvokesAgentDeepDive() {
        var agentPromptReceived: String? = null
        var dismissed = false
        val anchor = ts("2026-11-18 12:00:00")

        composeRule.setContent {
            EasySpendTheme(darkTheme = false) {
                FinancialDigestDialog(
                    initialPeriod = ReportPeriod.MONTH,
                    initialAnchorMillis = anchor,
                    allTransactions = sampleTransactions,
                    categories = ClassificationEngine.defaultCategories,
                    savingsTargetFen = 100_000L,
                    onAskAgent = { agentPromptReceived = it },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithTag("financial_digest_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("digest_masthead_card").assertIsDisplayed()
        composeRule.onNodeWithText("2026年11月 · 财务月报").assertIsDisplayed()

        // Switch to Weekly Digest
        composeRule.onNodeWithTag("digest_period_week").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("digest_title_text").assertIsDisplayed()

        // Switch to Yearly Digest
        composeRule.onNodeWithTag("digest_period_year").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2026年度 · 财务年报").assertIsDisplayed()

        // Scroll to deep-dive Agent CTA and click
        composeRule.onNodeWithTag("digest_lazy_list").performScrollToKey("digest_ask_agent_cta")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("digest_ask_agent_button").performClick()
        assertTrue(dismissed)
        assertNotNull(agentPromptReceived)
        assertTrue(agentPromptReceived!!.contains("2026年度"))
    }
}
