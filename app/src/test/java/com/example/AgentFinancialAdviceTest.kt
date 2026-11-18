package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.EasySpendAgentTools
import com.example.ai.EasySpendAiService
import com.example.ai.PendingBudgetProposal
import com.example.ai.ProposalStatus
import com.example.ai.ToolExecutionResult
import com.example.data.local.EasySpendDatabase
import com.example.data.model.CandidateResolution
import com.example.data.model.DiscrepancyType
import com.example.data.model.FundingSource
import com.example.data.model.PaymentChannel
import com.example.data.model.TransactionDirection
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.DemoStatementFactory
import com.example.domain.finance.OverspendRiskLevel
import com.example.ui.AgentChatMessage
import com.example.ui.EasySpendUiState
import com.example.ui.screens.AgentScreen
import com.example.ui.theme.EasySpendTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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
class AgentFinancialAdviceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: EasySpendDatabase
    private lateinit var repository: EasySpendRepository
    private lateinit var tools: EasySpendAgentTools

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, EasySpendDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EasySpendRepository(db)
        tools = EasySpendAgentTools(repository)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `all 6 read and analysis agent tools return exact ledger facts for competition demo`() = runTest {
        val monthKey = "2026-11"
        repository.loadCompetitionDemoCommittedBaseline(monthKey)

        // Confirm Alipay items except the duplicate Steam ¥98 record
        val pending = repository.pendingCandidatesFlow.first()
        for (c in pending) {
            if (c.discrepancyType == DiscrepancyType.POTENTIAL_DUPLICATE) {
                repository.resolveCandidateAsSkipOrDismiss(c.id, CandidateResolution.MERGED_SKIP)
            } else {
                repository.confirmCandidateToLedger(
                    candidateId = c.id,
                    selectedCategoryId = c.suggestedCategoryId,
                    selectedFundingSource = c.fundingSource,
                    rememberMerchantPreference = false,
                    note = ""
                )
            }
        }

        // 1. get_financial_summary
        val summaryRes = tools.executeTool("test-s1", "get_financial_summary", mapOf("monthKey" to monthKey), monthKey)
        assertEquals(1800.0, summaryRes.resultJson.getDouble("livingAllowanceIncomeYuan"), 0.01)
        assertEquals(500.0, summaryRes.resultJson.getDouble("partTimeIncomeYuan"), 0.01)
        assertEquals(2300.0, summaryRes.resultJson.getDouble("totalArrivedIncomeYuan"), 0.01)
        assertEquals(38.0, summaryRes.resultJson.getDouble("refundTotalYuan"), 0.01)

        // 2. query_transactions
        val queryRes = tools.executeTool(
            "test-s1",
            "query_transactions",
            mapOf("monthKey" to monthKey, "keyword" to "食堂", "direction" to "EXPENSE", "limit" to 10),
            monthKey
        )
        assertTrue(queryRes.resultJson.getInt("matchedCount") >= 2)

        // Also verify literal "null" string / omitted optional args return all expenses cleanly
        val queryAllRes = tools.executeTool(
            "test-s1",
            "query_transactions",
            mapOf("monthKey" to monthKey, "categoryId" to "null", "keyword" to "null"),
            monthKey
        )
        assertTrue(queryAllRes.resultJson.getInt("matchedCount") > 2)

        // 3. analyze_spending
        val analyzeRes = tools.executeTool("test-s1", "analyze_spending", mapOf("monthKey" to monthKey), monthKey)
        assertTrue(analyzeRes.resultJson.getDouble("essentialSpentYuan") > 0.0)
        assertTrue(analyzeRes.resultJson.getDouble("flexibleSpentYuan") > 0.0)
        assertTrue(analyzeRes.resultJson.getJSONArray("topMerchants").length() > 0)

        // 4. calculate_budget
        val budgetRes = tools.executeTool("test-s1", "calculate_budget", mapOf("monthKey" to monthKey), monthKey)
        assertEquals(7, budgetRes.resultJson.getJSONArray("categories").length())

        // 5. forecast_cashflow
        val forecastRes = tools.executeTool("test-s1", "forecast_cashflow", mapOf("monthKey" to monthKey), monthKey)
        assertEquals(2300.0, forecastRes.resultJson.getDouble("totalArrivedIncomeYuan"), 0.01)
        assertTrue(forecastRes.resultJson.has("projectedMonthEndSavingsYuan"))

        // 6. plan_savings
        val planRes = tools.executeTool(
            "test-s1",
            "plan_savings",
            mapOf("monthKey" to monthKey, "targetYuan" to 1000.0),
            monthKey
        )
        assertEquals(1000.0, planRes.resultJson.getDouble("targetSavingsYuan"), 0.01)
        assertTrue(planRes.resultJson.getString("essentialProtectionPolicy").contains("饮食"))

        // Audit logs should persist all 7 tool executions
        val logs = repository.toolLogsFlow.first()
        assertEquals(7, logs.size)
        assertTrue(logs.all { !it.requiresConfirmation })
    }

    @Test
    fun `adding 300 yuan essential study expense triggers savings plan protection without counting unarrived part-time`() = runTest {
        val monthKey = "2026-11"
        repository.ensureInitialized(monthKey)

        // Stage WeChat demo CSV (contains ¥1,800 living allowance + campus expenses; no part-time arrived yet)
        repository.importCsvIntoReviewQueue(
            DemoStatementFactory.demoWeChatCsv,
            "微信账单",
            PaymentChannel.WECHAT_PAY
        ).getOrThrow()
        repository.confirmAllCleanCandidates()
        // Confirm remaining uncertain items (全家便利店)
        repository.pendingCandidatesFlow.first().forEach { c ->
            repository.confirmCandidateToLedger(c.id, c.suggestedCategoryId, c.fundingSource, false, "")
        }

        // Add ¥300.00 essential study expense occurring on 2026-11-17
        val nov17Millis = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
            .parse("2026-11-17 15:00:00")!!.time
        repository.addManualTransaction(
            amountFen = 30000L,
            direction = TransactionDirection.EXPENSE,
            channel = PaymentChannel.WECHAT_PAY,
            fundingSource = FundingSource.LIVING_ALLOWANCE,
            categoryId = "study",
            merchant = "中山大学教材服务站",
            note = "专业实验教材与四六级资料",
            occurredAtMillis = nov17Millis
        )

        val summary = repository.getMonthlySummarySnapshot(monthKey)
        val studyStatus = summary.categoryStatuses.first { it.categoryId == "study" }
        assertEquals(OverspendRiskLevel.OVER_BUDGET, studyStatus.riskLevel)
        assertEquals(180000L, summary.totalIncomeFen)
        assertEquals(0L, summary.partTimeIncomeFen)
        assertFalse(summary.isSavingsTargetFeasible)

        val planRes = tools.executeTool("test-s2", "plan_savings", mapOf("monthKey" to monthKey), monthKey)
        assertFalse(planRes.resultJson.getBoolean("isFeasibleAtCurrentPace"))
        val adj = planRes.resultJson.getJSONArray("recommendedFlexibleAdjustments")
        // Essential categories (food, transit, daily) must NEVER be cut in flexible adjustments
        for (i in 0 until adj.length()) {
            val catId = adj.getJSONObject(i).getString("categoryId")
            assertFalse("Essential category $catId should never be suggested for cuts", catId in listOf("food", "transit", "daily"))
        }
    }

    @Test
    fun `propose_budget_change stages proposal without mutating database until explicit user confirmation`() = runTest {
        val monthKey = "2026-11"
        repository.ensureInitialized(monthKey)

        val beforeSummary = repository.getMonthlySummarySnapshot(monthKey)
        val beforeEntLimit = beforeSummary.categoryStatuses.first { it.categoryId == "entertainment" }.limitFen
        assertEquals(20000L, beforeEntLimit)

        // Agent proposes cutting entertainment budget from ¥200 to ¥120
        val proposeRes = tools.executeTool(
            sessionId = "test-s3",
            toolName = "propose_budget_change",
            args = mapOf(
                "monthKey" to monthKey,
                "categoryId" to "entertainment",
                "newLimitYuan" to 120.0,
                "reason" to "保留必要餐饮学习开支，温和压缩娱乐预算以接近储蓄目标"
            ),
            defaultMonthKey = monthKey
        )

        val proposal = proposeRes.proposal
        assertNotNull(proposal)
        assertEquals(ProposalStatus.PENDING, proposal!!.status)
        assertEquals(20000L, proposal.currentLimitFen)
        assertEquals(12000L, proposal.proposedLimitFen)
        assertFalse(proposeRes.resultJson.getBoolean("appliedToDatabase"))

        // Verify database was NOT modified before user confirmation
        val midSummary = repository.getMonthlySummarySnapshot(monthKey)
        assertEquals(20000L, midSummary.categoryStatuses.first { it.categoryId == "entertainment" }.limitFen)

        // Simulate explicit user confirmation (same call path as ViewModel.confirmAgentProposal)
        repository.updateBudgetLimit(proposal.monthKey, proposal.categoryId, proposal.proposedLimitFen)
        repository.logToolCall(
            sessionId = "user_confirm",
            toolName = "apply_budget_change_confirmed",
            argumentsJson = "{\"categoryId\":\"entertainment\",\"newLimitFen\":12000}",
            resultSummary = "用户已确认调整娱乐休闲预算为 ¥120.00",
            requiresConfirmation = true,
            confirmedByUser = true
        )

        val afterSummary = repository.getMonthlySummarySnapshot(monthKey)
        assertEquals(12000L, afterSummary.categoryStatuses.first { it.categoryId == "entertainment" }.limitFen)

        val logs = repository.toolLogsFlow.first()
        assertTrue(logs.any { it.toolName == "propose_budget_change" && it.requiresConfirmation && !it.confirmedByUser })
        assertTrue(logs.any { it.toolName == "apply_budget_change_confirmed" && it.requiresConfirmation && it.confirmedByUser })
    }

    @Test
    fun `agent service initializes lazily without eager crash and handles invalid tool gracefully`() = runTest {
        repository.ensureInitialized("2026-11")
        // Constructing EasySpendAiService must not crash even when Firebase lazy models haven't been invoked
        val service = EasySpendAiService(repository)
        assertNotNull(service)

        val unknownRes = tools.executeTool("test-s4", "non_existent_tool", emptyMap(), "2026-11")
        assertNull(unknownRes.proposal)
        assertTrue(unknownRes.resultJson.has("error"))
    }

    @Test
    fun `AgentScreen renders tool traces accordion and interactive proposal confirm and reject cards`() {
        val sampleProposal = PendingBudgetProposal(
            id = "prop-1",
            monthKey = "2026-11",
            categoryId = "entertainment",
            categoryName = "娱乐休闲",
            currentLimitFen = 20000L,
            proposedLimitFen = 12000L,
            reason = "优先保障饮食与学习支出，温和调整娱乐预算至 ¥120",
            status = ProposalStatus.PENDING
        )
        val traces = listOf(
            ToolExecutionResult(
                toolName = "plan_savings",
                argumentsJson = "{\"monthKey\":\"2026-11\"}",
                resultJson = JSONObject().put("isFeasibleAtCurrentPace", false),
                humanSummary = "储蓄规划：距目标尚差 ¥180.00，已保护必要支出"
            ),
            ToolExecutionResult(
                toolName = "propose_budget_change",
                argumentsJson = "{\"categoryId\":\"entertainment\",\"newLimitYuan\":120.0}",
                resultJson = JSONObject().put("status", "PENDING_USER_CONFIRMATION"),
                humanSummary = "已生成「娱乐休闲」预算调整建议卡片：¥200.00 → ¥120.00",
                proposal = sampleProposal
            )
        )
        val agentMsg = AgentChatMessage(
            id = "msg-ai-1",
            isUser = false,
            text = "按已到账收入测算，建议优先保障饮食与教材，仅温和下调娱乐休闲预算。",
            toolTraces = traces,
            proposals = listOf(sampleProposal)
        )

        val sentPrompts = mutableListOf<String>()
        var confirmedProposalId: String? = null
        var rejectedProposalId: String? = null

        composeTestRule.setContent {
            EasySpendTheme(darkTheme = false) {
                AgentScreen(
                    state = EasySpendUiState(agentMessages = listOf(agentMsg)),
                    prefillPrompt = null,
                    onClearPrefill = {},
                    onSendMessage = { sentPrompts.add(it) },
                    onConfirmProposal = { _, p -> confirmedProposalId = p.id },
                    onRejectProposal = { _, p -> rejectedProposalId = p.id }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify proposal card and amounts are visible
        composeTestRule.onNodeWithTag("proposal_card_entertainment").assertIsDisplayed()
        composeTestRule.onNodeWithText("¥200.00 → ¥120.00").assertIsDisplayed()

        // Expand tool trace accordion and verify tool summary appears
        composeTestRule.onNodeWithText("已核对 2 项账本数据（点击查看依据）").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("储蓄规划：距目标尚差 ¥180.00，已保护必要支出").assertIsDisplayed()

        // Click confirm proposal (scroll into view after accordion expansion)
        composeTestRule.onNodeWithTag("confirm_proposal_entertainment").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("prop-1", confirmedProposalId)

        // Click reject proposal
        composeTestRule.onNodeWithTag("reject_proposal_entertainment").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("prop-1", rejectedProposalId)

        // Click quick prompt chip & test text composer
        composeTestRule.onNodeWithTag("agent_quick_prompt_0").performClick()
        composeTestRule.waitForIdle()
        assertEquals("这个月还能存下 1000 元吗？", sentPrompts.firstOrNull())

        composeTestRule.onNodeWithTag("agent_input_field").performTextInput("帮我看看下周怎么控制外卖开销")
        composeTestRule.onNodeWithTag("agent_send_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("帮我看看下周怎么控制外卖开销", sentPrompts.lastOrNull())
    }
}
