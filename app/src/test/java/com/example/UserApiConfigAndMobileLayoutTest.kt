package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ApiConfig
import com.example.ai.EasySpendAiService
import com.example.ai.ProposalStatus
import com.example.data.local.ApiProviderType
import com.example.data.local.EasySpendDatabase
import com.example.data.local.UserSettings
import com.example.data.local.UserSettingsDataStore
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.CategoryBudgetStatus
import com.example.domain.finance.MonthlyFinancialSummary
import com.example.domain.finance.OverspendRiskLevel
import com.example.ui.EasySpendUiState
import com.example.ui.screens.AgentScreen
import com.example.ui.screens.ApiConfigDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.EasySpendTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserApiConfigAndMobileLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: EasySpendDatabase
    private lateinit var repository: EasySpendRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
    fun `UserSettingsDataStore persists custom API provider baseUrl apiKey and model`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = UserSettingsDataStore(context)

        store.saveApiConfig(
            provider = ApiProviderType.QWEN,
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/",
            apiKey = "sk-test-qwen-12345",
            model = "qwen-max"
        )

        val saved = store.settingsFlow.first()
        assertEquals(ApiProviderType.QWEN, saved.apiProvider)
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", saved.apiBaseUrl)
        assertEquals("sk-test-qwen-12345", saved.apiKey)
        assertEquals("qwen-max", saved.apiModel)
        assertTrue(saved.isApiConfigured)
    }

    @Test
    fun `EasySpendAiService executes OpenAI compatible tool loop and returns budget proposal`() = runBlocking {
        val monthKey = "2026-11"
        repository.loadCompetitionDemoCommittedBaseline(monthKey)

        val recordedRequests = mutableListOf<JSONObject>()
        var callCount = 0

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val buf = Buffer()
                req.body?.writeTo(buf)
                val bodyJson = JSONObject(buf.readUtf8())
                recordedRequests.add(bodyJson)
                callCount++

                val responseJson = if (callCount == 1) {
                    JSONObject().put(
                        "choices",
                        JSONArray().put(
                            JSONObject().put(
                                "message",
                                JSONObject()
                                    .put("role", "assistant")
                                    .put("content", JSONObject.NULL)
                                    .put(
                                        "tool_calls",
                                        JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put("id", "call_summary_1")
                                                    .put("type", "function")
                                                    .put(
                                                        "function",
                                                        JSONObject()
                                                            .put("name", "get_financial_summary")
                                                            .put("arguments", "{\"monthKey\":\"2026-11\"}")
                                                    )
                                            )
                                            .put(
                                                JSONObject()
                                                    .put("id", "call_prop_2")
                                                    .put("type", "function")
                                                    .put(
                                                        "function",
                                                        JSONObject()
                                                            .put("name", "propose_budget_change")
                                                            .put(
                                                                "arguments",
                                                                "{\"monthKey\":\"2026-11\",\"categoryId\":\"entertainment\",\"newLimitYuan\":120,\"reason\":\"压缩娱乐开支，保障饮食与学习费用\"}"
                                                            )
                                                    )
                                            )
                                    )
                            )
                        )
                    )
                } else {
                    JSONObject().put(
                        "choices",
                        JSONArray().put(
                            JSONObject().put(
                                "message",
                                JSONObject()
                                    .put("role", "assistant")
                                    .put("content", "已核对本月账本，建议将娱乐休闲预算下调至 ¥120.00，不影响饮食与学习。")
                            )
                        )
                    )
                }

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toString().toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val aiService = EasySpendAiService(repository, mockClient)
        val config = ApiConfig(
            provider = ApiProviderType.DEEPSEEK,
            baseUrl = "https://api.deepseek.com/v1",
            apiKey = "sk-deepseek-unit-test",
            model = "deepseek-chat"
        )

        val reply = aiService.askFinancialAgent(
            userPrompt = "这个月还能存下 1000 元吗？",
            monthKey = monthKey,
            config = config
        )

        assertEquals(2, callCount)
        assertEquals(2, reply.toolTraces.size)
        assertEquals(1, reply.proposals.size)
        assertEquals("entertainment", reply.proposals.first().categoryId)
        assertEquals(12000L, reply.proposals.first().proposedLimitFen)
        assertEquals(ProposalStatus.PENDING, reply.proposals.first().status)
        assertTrue(reply.text.contains("¥120.00"))

        // Second request must carry tool result messages with role == "tool"
        val secondMessages = recordedRequests[1].getJSONArray("messages")
        val roles = (0 until secondMessages.length()).map { secondMessages.getJSONObject(it).getString("role") }
        assertTrue(roles.contains("tool"))
    }

    @Test
    fun `EasySpendAiService Gemini REST uses user role for functionResponse and strips json fences`() = runBlocking {
        val monthKey = "2026-11"
        repository.loadCompetitionDemoCommittedBaseline(monthKey)

        val capturedTurnRoles = mutableListOf<String>()
        var agentRound = 0

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val buf = Buffer()
                req.body?.writeTo(buf)
                val bodyJson = JSONObject(buf.readUtf8())
                val url = req.url.toString()

                val contents = bodyJson.optJSONArray("contents")
                if (contents != null) {
                    for (i in 0 until contents.length()) {
                        capturedTurnRoles.add(contents.getJSONObject(i).optString("role"))
                    }
                }

                val responseJson = if (url.contains("generateContent") && agentRound == 0 && bodyJson.has("tools")) {
                    agentRound++
                    JSONObject().put(
                        "candidates",
                        JSONArray().put(
                            JSONObject().put(
                                "content",
                                JSONObject()
                                    .put("role", "model")
                                    .put(
                                        "parts",
                                        JSONArray().put(
                                            JSONObject().put(
                                                "functionCall",
                                                JSONObject()
                                                    .put("name", "get_financial_summary")
                                                    .put("args", JSONObject().put("monthKey", "2026-11"))
                                            )
                                        )
                                    )
                            )
                        )
                    )
                } else if (bodyJson.has("tools")) {
                    JSONObject().put(
                        "candidates",
                        JSONArray().put(
                            JSONObject().put(
                                "content",
                                JSONObject()
                                    .put("role", "model")
                                    .put(
                                        "parts",
                                        JSONArray().put(JSONObject().put("text", "Gemini 已核对账本完成。"))
                                    )
                            )
                        )
                    )
                } else {
                    // JSON insight request returning fenced JSON
                    JSONObject().put(
                        "candidates",
                        JSONArray().put(
                            JSONObject().put(
                                "content",
                                JSONObject()
                                    .put("role", "model")
                                    .put(
                                        "parts",
                                        JSONArray().put(
                                            JSONObject().put(
                                                "text",
                                                """
                                                ```json
                                                {
                                                  "headline": "本月必要支出可控，弹性消费略高",
                                                  "trendSummary": "饮食与教材稳步推进。",
                                                  "impulseAlert": "留意游戏充值支出。",
                                                  "savingsVerdict": "控制娱乐预算即可达成目标。",
                                                  "recommendations": ["食堂就餐为主", "延迟非必需下单", "每周复盘一次"]
                                                }
                                                ```
                                                """.trimIndent()
                                            )
                                        )
                                    )
                            )
                        )
                    )
                }

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toString().toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val aiService = EasySpendAiService(repository, mockClient)
        val geminiConfig = ApiConfig(
            provider = ApiProviderType.GEMINI_REST,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            apiKey = "AIza-test-key",
            model = "gemini-2.5-flash"
        )

        val reply = aiService.askFinancialAgent("本月还剩多少钱？", monthKey, geminiConfig)
        assertTrue(reply.text.contains("Gemini 已核对账本完成"))
        assertFalse("Gemini REST must never send role=function", capturedTurnRoles.contains("function"))
        assertTrue(capturedTurnRoles.all { it == "user" || it == "model" })

        val insights = aiService.generateSpendingInsights(monthKey, geminiConfig)
        assertEquals("本月必要支出可控，弹性消费略高", insights.headline)
        assertEquals(3, insights.recommendations.size)
    }

    @Test
    fun `ApiConfigDialog switches presets and enables save after entering key`() {
        var savedProvider: ApiProviderType? = null
        var savedKey: String? = null
        var savedModel: String? = null

        composeTestRule.setContent {
            EasySpendTheme(darkTheme = false) {
                ApiConfigDialog(
                    state = EasySpendUiState(),
                    onSaveApiConfig = { p, _, k, m ->
                        savedProvider = p
                        savedKey = k
                        savedModel = m
                    },
                    onTestApiConnection = { _, _, _, _ -> },
                    onClearFeedback = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("save_api_config_button").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("api_provider_qwen").performClick()
        composeTestRule.onNodeWithTag("api_key_input").performTextInput("sk-user-key-999")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("save_api_config_button").assertIsEnabled().performClick()
        assertEquals(ApiProviderType.QWEN, savedProvider)
        assertEquals("sk-user-key-999", savedKey)
        assertEquals(ApiProviderType.QWEN.defaultModel, savedModel)
    }

    @Test
    fun `AgentScreen shows API status banner and opens config callback`() {
        var opened = false
        composeTestRule.setContent {
            EasySpendTheme(darkTheme = true) {
                AgentScreen(
                    state = EasySpendUiState(settings = UserSettings(apiKey = "")),
                    prefillPrompt = null,
                    onClearPrefill = {},
                    onSendMessage = {},
                    onConfirmProposal = { _, _ -> },
                    onRejectProposal = { _, _ -> },
                    onOpenApiSettings = { opened = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("agent_api_status_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("去配置").assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test
    fun `DashboardScreen displays full Chinese quick buttons and full donut legend labels at 360dp width`() {
        val statuses = listOf(
            CategoryBudgetStatus("food", "饮食餐饮", true, 65000L, 100200L, -35200L, 1.54f, 120000L, OverspendRiskLevel.OVER_BUDGET),
            CategoryBudgetStatus("study", "学习教材", true, 25000L, 15600L, 9400L, 0.62f, 20000L, OverspendRiskLevel.SAFE),
            CategoryBudgetStatus("daily", "日常日用", true, 20000L, 4680L, 15320L, 0.23f, 8000L, OverspendRiskLevel.SAFE)
        )
        val summary = MonthlyFinancialSummary(
            monthKey = "2026-11",
            livingAllowanceIncomeFen = 180000L,
            partTimeIncomeFen = 50000L,
            totalIncomeFen = 230000L,
            grossExpenseFen = 146970L,
            refundTotalFen = 0L,
            netExpenseFen = 146970L,
            essentialSpentFen = 118570L,
            flexibleSpentFen = 28400L,
            currentCashBalanceFen = 83030L,
            savingsTargetFen = 100000L,
            projectedEndOfMonthExpenseFen = 180000L,
            projectedMonthEndSavingsFen = 50000L,
            isSavingsTargetFeasible = false,
            savingsFeasibilityNote = "按已到账收入测算尚差 ¥500.00",
            categoryStatuses = statuses,
            suggestedAdjustments = emptyList()
        )

        composeTestRule.setContent {
            EasySpendTheme(darkTheme = true) {
                Box(modifier = Modifier.width(360.dp)) {
                    DashboardScreen(
                        state = EasySpendUiState(summary = summary),
                        onLoadDemoQueue = {},
                        onLoadDemoBaseline = {},
                        onAdd300Essential = {},
                        onNavigateToImportQueue = {},
                        onNavigateToBudget = {},
                        onNavigateToAgentWithPrompt = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("demo_add_300_button"))
        composeTestRule.onNodeWithTag("demo_add_300_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("记¥300学习费").assertIsDisplayed()
        composeTestRule.onNodeWithText("问问财务助手").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("donut_legend_daily"))
        composeTestRule.onNodeWithTag("donut_legend_food").assertIsDisplayed()
        composeTestRule.onNodeWithTag("donut_legend_study").assertIsDisplayed()
        composeTestRule.onNodeWithTag("donut_legend_daily").assertIsDisplayed()
    }
}
