package com.example.ai

import android.util.Log
import com.example.data.local.ApiProviderType
import com.example.data.local.UserSettings
import com.example.data.model.CategoryEntity
import com.example.data.model.ImportCandidateEntity
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.MoneyFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ApiConfig(
    val provider: ApiProviderType = ApiProviderType.DEEPSEEK,
    val baseUrl: String = ApiProviderType.DEEPSEEK.defaultBaseUrl,
    val apiKey: String = "",
    val model: String = ApiProviderType.DEEPSEEK.defaultModel
) {
    val isValid: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

fun UserSettings.toApiConfig(): ApiConfig = ApiConfig(
    provider = apiProvider,
    baseUrl = apiBaseUrl,
    apiKey = apiKey,
    model = apiModel
)

data class ConversationTurn(
    val role: String, // "user" or "assistant"
    val text: String
)

data class SpendingInsightReport(
    val headline: String,
    val trendSummary: String,
    val impulseAlert: String,
    val savingsVerdict: String,
    val recommendations: List<String>
)

data class CandidateAiClassification(
    val candidateId: Long,
    val suggestedCategoryId: String,
    val confidence: Float,
    val reason: String
)

data class AgentChatReply(
    val text: String,
    val toolTraces: List<ToolExecutionResult>,
    val proposals: List<PendingBudgetProposal>
)

class EasySpendAiService(
    private val repository: EasySpendRepository,
    private val httpClient: OkHttpClient = defaultHttpClient
) {

    private val toolsExecutor = EasySpendAgentTools(repository)

    private val systemPrompt = """
        你是「易支道 EasySpend」个人财务管理与智能预算规划 Agent，服务于中国大学生日常财务管理。
        严格遵守以下原则：
        1. 所有金额、预算与预测必须通过工具读取真实账本与确定性引擎结果，严禁编造交易记录或虚构金额。
        2. 为提高响应速度，请在同一轮内一次性并行调用所有需要的查询工具（如同时调用 get_financial_summary、forecast_cashflow、plan_savings），避免逐个串行多轮调用。
        3. 严格区分已到账收入与预计收入，绝不把未到账的预计兼职当作已到账现金。
        4. 储蓄目标（如1000元）属于挑战目标，必须优先保障饮食（food）、学习（study）、交通（transit）、日常必需（daily）等必要生活支出，绝不建议以牺牲基本生活健康为代价硬凑储蓄。
        5. 若需修改分类预算，必须调用 propose_budget_change 工具生成待确认方案，并提示用户点击确认卡片后才会生效。
        6. 回复简洁精炼、条理清晰（使用简体中文），直接给出关键金额与可执行建议。
    """.trimIndent()

    suspend fun testConnection(config: ApiConfig): Result<String> {
        if (!config.isValid) {
            return Result.failure(IllegalArgumentException("请先填写完整的 Base URL、API Key 与模型名称"))
        }
        return try {
            val start = System.currentTimeMillis()
            withTimeout(20_000L) {
                if (config.provider.isGeminiNative) {
                    val body = JSONObject().apply {
                        put(
                            "contents",
                            JSONArray().put(
                                JSONObject()
                                    .put("role", "user")
                                    .put("parts", JSONArray().put(JSONObject().put("text", "请只回复：OK")))
                            )
                        )
                    }
                    val resp = postJson(buildGeminiEndpoint(config), body, emptyMap())
                    val text = extractGeminiText(resp)
                    if (text.isBlank()) throw IllegalStateException("接口返回空内容")
                } else {
                    val body = JSONObject().apply {
                        put("model", config.model.trim())
                        put(
                            "messages",
                            JSONArray().put(
                                JSONObject()
                                    .put("role", "user")
                                    .put("content", "请只回复：OK")
                            )
                        )
                        put("max_tokens", 16)
                        put("temperature", 0)
                    }
                    val resp = postJson(
                        buildOpenAiEndpoint(config),
                        body,
                        mapOf("Authorization" to "Bearer ${config.apiKey.trim()}")
                    )
                    val choices = resp.optJSONArray("choices")
                    if (choices == null || choices.length() == 0) {
                        throw IllegalStateException("接口未返回 choices 结果")
                    }
                }
            }
            val elapsed = System.currentTimeMillis() - start
            Result.success("连接成功（${config.model} · ${elapsed}ms）")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun askFinancialAgent(
        userPrompt: String,
        monthKey: String,
        config: ApiConfig,
        conversationHistory: List<ConversationTurn> = emptyList(),
        onProgress: (String) -> Unit = {},
        onPartialText: (String) -> Unit = {}
    ): AgentChatReply {
        requireValidConfig(config)
        val sessionId = UUID.randomUUID().toString().take(8)
        return if (config.provider.isGeminiNative) {
            askViaGeminiRest(
                sessionId = sessionId,
                userPrompt = userPrompt,
                monthKey = monthKey,
                config = config,
                history = conversationHistory,
                onProgress = onProgress,
                onPartialText = onPartialText
            )
        } else {
            askViaOpenAiCompat(
                sessionId = sessionId,
                userPrompt = userPrompt,
                monthKey = monthKey,
                config = config,
                history = conversationHistory,
                onProgress = onProgress,
                onPartialText = onPartialText
            )
        }
    }

    private suspend fun askViaOpenAiCompat(
        sessionId: String,
        userPrompt: String,
        monthKey: String,
        config: ApiConfig,
        history: List<ConversationTurn>,
        onProgress: (String) -> Unit,
        onPartialText: (String) -> Unit
    ): AgentChatReply {
        val traces = mutableListOf<ToolExecutionResult>()
        val proposals = mutableListOf<PendingBudgetProposal>()
        val endpoint = buildOpenAiEndpoint(config)
        val headers = mapOf("Authorization" to "Bearer ${config.apiKey.trim()}")

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.takeLast(12).forEach { turn ->
                put(JSONObject().put("role", if (turn.role == "user") "user" else "assistant").put("content", turn.text))
            }
            put(JSONObject().put("role", "user").put("content", "当前账期：$monthKey。用户问题：$userPrompt"))
        }

        val toolsSpec = buildOpenAiToolsArray()
        var finalText = ""

        onProgress("正在请求 ${config.model} 规划查账...")
        withTimeout(65_000L) {
            var round = 0
            while (round < 4) {
                round++
                val payload = JSONObject().apply {
                    put("model", config.model.trim())
                    put("messages", messages)
                    put("tools", toolsSpec)
                    put("tool_choice", "auto")
                    put("temperature", 0.3)
                    put("max_tokens", 1536)
                }

                val resp = postJson(endpoint, payload, headers)
                val choice = resp.optJSONArray("choices")?.optJSONObject(0)
                    ?: throw IllegalStateException("模型未返回有效回复 choices")
                val msg = choice.optJSONObject("message")
                    ?: throw IllegalStateException("模型回复缺少 message 字段")

                val contentText = msg.optString("content").orEmpty().let {
                    if (it == "null") "" else it
                }
                if (contentText.isNotBlank()) {
                    finalText = contentText
                    onPartialText(finalText)
                }

                val toolCalls = msg.optJSONArray("tool_calls")
                if (toolCalls == null || toolCalls.length() == 0) {
                    break
                }

                // Append assistant message with tool_calls as required by OpenAI spec
                messages.put(msg)

                val toolNames = (0 until toolCalls.length()).mapNotNull { i ->
                    toolCalls.optJSONObject(i)?.optJSONObject("function")?.optString("name")
                }
                onProgress("正在执行本地确定性工具：${toolNames.joinToString("、")}")

                for (i in 0 until toolCalls.length()) {
                    val callObj = toolCalls.getJSONObject(i)
                    val callId = callObj.optString("id").ifBlank { "call_${round}_$i" }
                    val fnObj = callObj.getJSONObject("function")
                    val fnName = fnObj.getString("name")
                    val rawArgsStr = fnObj.optString("arguments", "{}").ifBlank { "{}" }
                    val argsMap = parseJsonArgsToMap(rawArgsStr)

                    val exec = toolsExecutor.executeTool(
                        sessionId = sessionId,
                        toolName = fnName,
                        args = argsMap,
                        defaultMonthKey = monthKey
                    )
                    traces.add(exec)
                    exec.proposal?.let { proposals.add(it) }

                    messages.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", callId)
                            .put("name", fnName)
                            .put("content", exec.resultJson.toString())
                    )
                }
                onProgress("已获取 ${traces.size} 项账本数据，正在生成理财建议...")
            }
        }

        val resolvedText = finalText.trim().ifBlank {
            "已完成 ${traces.size} 项账本核对，请查看下方工具依据与调整建议。"
        }
        return AgentChatReply(text = resolvedText, toolTraces = traces, proposals = proposals)
    }

    private suspend fun askViaGeminiRest(
        sessionId: String,
        userPrompt: String,
        monthKey: String,
        config: ApiConfig,
        history: List<ConversationTurn>,
        onProgress: (String) -> Unit,
        onPartialText: (String) -> Unit
    ): AgentChatReply {
        val traces = mutableListOf<ToolExecutionResult>()
        val proposals = mutableListOf<PendingBudgetProposal>()
        val endpoint = buildGeminiEndpoint(config)

        val contents = JSONArray().apply {
            history.takeLast(12).forEach { turn ->
                put(
                    JSONObject()
                        .put("role", if (turn.role == "user") "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", turn.text)))
                )
            }
            put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", "当前账期：$monthKey。用户问题：$userPrompt")))
            )
        }

        val toolsArray = JSONArray().put(
            JSONObject().put("functionDeclarations", buildToolDeclarationsJsonArray())
        )
        var finalText = ""

        onProgress("正在请求 ${config.model} 规划查账...")
        withTimeout(65_000L) {
            var round = 0
            while (round < 4) {
                round++
                val payload = JSONObject().apply {
                    put(
                        "systemInstruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    )
                    put("contents", contents)
                    put("tools", toolsArray)
                    put(
                        "generationConfig",
                        JSONObject()
                            .put("temperature", 0.3)
                            .put("maxOutputTokens", 1536)
                    )
                }

                val resp = postJson(endpoint, payload, emptyMap())
                val candidate = resp.optJSONArray("candidates")?.optJSONObject(0)
                    ?: throw IllegalStateException("Gemini 未返回 candidates 结果")
                val modelContent = candidate.optJSONObject("content")
                    ?: throw IllegalStateException("Gemini 回复缺少 content 字段")
                val parts = modelContent.optJSONArray("parts") ?: JSONArray()

                contents.put(modelContent)

                val functionCalls = mutableListOf<Pair<String, Map<String, Any?>>>()
                val textBuffer = StringBuilder()

                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val t = part.optString("text")
                    if (t.isNotBlank() && t != "null") {
                        textBuffer.append(t)
                    }
                    val fc = part.optJSONObject("functionCall")
                    if (fc != null) {
                        val name = fc.getString("name")
                        val argsObj = fc.optJSONObject("args") ?: JSONObject()
                        functionCalls.add(name to jsonObjectToMap(argsObj))
                    }
                }

                if (textBuffer.isNotBlank()) {
                    finalText = textBuffer.toString()
                    onPartialText(finalText)
                }

                if (functionCalls.isEmpty()) {
                    break
                }

                onProgress("正在执行本地确定性工具：${functionCalls.joinToString("、") { it.first }}")
                val responseParts = JSONArray()
                for ((fnName, fnArgs) in functionCalls) {
                    val exec = toolsExecutor.executeTool(
                        sessionId = sessionId,
                        toolName = fnName,
                        args = fnArgs,
                        defaultMonthKey = monthKey
                    )
                    traces.add(exec)
                    exec.proposal?.let { proposals.add(it) }

                    responseParts.put(
                        JSONObject().put(
                            "functionResponse",
                            JSONObject()
                                .put("name", fnName)
                                .put("response", exec.resultJson)
                        )
                    )
                }

                // Gemini REST strictly accepts "user" or "model" role for content turns
                contents.put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", responseParts)
                )
                onProgress("已获取 ${traces.size} 项账本数据，正在生成理财建议...")
            }
        }

        val resolvedText = finalText.trim().ifBlank {
            "已完成 ${traces.size} 项账本核对，请查看下方工具依据与调整建议。"
        }
        return AgentChatReply(text = resolvedText, toolTraces = traces, proposals = proposals)
    }

    suspend fun generateSpendingInsights(monthKey: String, config: ApiConfig): SpendingInsightReport {
        requireValidConfig(config)
        val summary = repository.getMonthlySummarySnapshot(monthKey)
        val txs = repository.getTransactionsSnapshot(monthKey)

        val digest = buildString {
            appendLine("账期: $monthKey")
            appendLine("家庭生活费已到账: ${MoneyFormatter.formatFen(summary.livingAllowanceIncomeFen)}")
            appendLine("兼职收入已到账: ${MoneyFormatter.formatFen(summary.partTimeIncomeFen)}")
            appendLine("净支出: ${MoneyFormatter.formatFen(summary.netExpenseFen)} (必要 ${MoneyFormatter.formatFen(summary.essentialSpentFen)} / 弹性 ${MoneyFormatter.formatFen(summary.flexibleSpentFen)})")
            appendLine("储蓄目标: ${MoneyFormatter.formatFen(summary.savingsTargetFen)}, 预测月末结余: ${MoneyFormatter.formatFen(summary.projectedMonthEndSavingsFen)}")
            appendLine("分类预算与预测:")
            summary.categoryStatuses.forEach { c ->
                appendLine("- ${c.categoryName}(${if (c.isEssential) "必要" else "弹性"}): 已花 ${MoneyFormatter.formatFen(c.spentFen)} / 预算 ${MoneyFormatter.formatFen(c.limitFen)}, 预测月末 ${MoneyFormatter.formatFen(c.projectedEndOfMonthFen)}, 风险=${c.riskLevel.label}")
            }
            appendLine("近期部分脱敏交易摘要:")
            txs.take(12).forEach { tx ->
                appendLine("- ${tx.merchant} ${MoneyFormatter.formatFen(tx.amountFen)} [${tx.direction.displayName}]")
            }
        }

        val prompt = """
            请根据以下确定性财务摘要数据，输出严格 JSON 格式的消费趋势分析与习惯改进建议（不要输出 Markdown 代码块之外的多余文字）。
            JSON 字段必须包含：
            - "headline": 一句话财务诊断标题
            - "trendSummary": 消费趋势与结构分析（2-3句话）
            - "impulseAlert": 冲动消费或弹性支出提醒
            - "savingsVerdict": 储蓄挑战可行性评价与必要生活保障说明
            - "recommendations": 字符串数组，包含 3 条具体可执行的省钱或预算习惯建议
            严禁编造摘要之外的金额，禁止建议过度压缩饮食/学习/交通等必要生活开支：
            $digest
        """.trimIndent()

        return withTimeout(60_000L) {
            val rawText = requestPlainJsonCompletion(prompt, config)
            val json = JSONObject(stripMarkdownFences(rawText))
            val recsArray = json.optJSONArray("recommendations")
            val recs = if (recsArray != null) {
                (0 until recsArray.length()).mapNotNull { idx ->
                    recsArray.optString(idx).takeIf { it.isNotBlank() }
                }
            } else emptyList()

            SpendingInsightReport(
                headline = json.optString("headline", "月度消费洞察已生成"),
                trendSummary = json.optString("trendSummary", ""),
                impulseAlert = json.optString("impulseAlert", ""),
                savingsVerdict = json.optString("savingsVerdict", summary.savingsFeasibilityNote),
                recommendations = recs
            )
        }
    }

    suspend fun suggestCategoriesForCandidates(
        candidates: List<ImportCandidateEntity>,
        categories: List<CategoryEntity>,
        config: ApiConfig
    ): List<CandidateAiClassification> {
        if (candidates.isEmpty()) return emptyList()
        requireValidConfig(config)

        val catListDesc = categories.joinToString(", ") { "${it.id}(${it.name})" }
        val itemsText = candidates.take(15).joinToString("\n") { c ->
            "id=${c.id} | 商户=${c.merchant} | 描述=${c.rawDescription} | 方向=${c.direction.displayName} | 金额=${MoneyFormatter.formatFen(c.amountFen)}"
        }
        val prompt = """
            请为以下待确认交易提供消费预分类建议，严格返回 JSON 对象（不要输出额外说明）：
            格式：{"items":[{"candidateId":1,"suggestedCategoryId":"food","confidence":0.92,"reason":"简短依据"}]}
            可选分类ID: $catListDesc
            如果是美团/淘宝/便利店等多用途商户且商品描述不明确，请将 confidence 设为 <= 0.72 并在 reason 提示需用户确认。
            待分类记录：
            $itemsText
        """.trimIndent()

        return withTimeout(60_000L) {
            val rawText = requestPlainJsonCompletion(prompt, config)
            val json = JSONObject(stripMarkdownFences(rawText))
            val arr = json.optJSONArray("items") ?: return@withTimeout emptyList()
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                CandidateAiClassification(
                    candidateId = item.getLong("candidateId"),
                    suggestedCategoryId = item.getString("suggestedCategoryId"),
                    confidence = item.optDouble("confidence", 0.7).toFloat().coerceIn(0f, 1f),
                    reason = item.optString("reason", "AI 智能预分类")
                )
            }
        }
    }

    private suspend fun requestPlainJsonCompletion(prompt: String, config: ApiConfig): String {
        return if (config.provider.isGeminiNative) {
            val payload = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    )
                )
                put(
                    "generationConfig",
                    JSONObject()
                        .put("responseMimeType", "application/json")
                        .put("temperature", 0.2)
                )
            }
            val resp = postJson(buildGeminiEndpoint(config), payload, emptyMap())
            extractGeminiText(resp).ifBlank { throw IllegalStateException("模型返回空响应") }
        } else {
            val payload = JSONObject().apply {
                put("model", config.model.trim())
                put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", "你是一位严谨的 JSON 结构化输出助手，请只返回合法 JSON。"))
                        .put(JSONObject().put("role", "user").put("content", prompt))
                )
                put("temperature", 0.2)
            }
            val resp = postJson(
                buildOpenAiEndpoint(config),
                payload,
                mapOf("Authorization" to "Bearer ${config.apiKey.trim()}")
            )
            resp.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?: throw IllegalStateException("模型返回空响应")
        }
    }

    private suspend fun postJson(
        url: String,
        body: JSONObject,
        extraHeaders: Map<String, String>
    ): JSONObject = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
        extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

        val response = try {
            httpClient.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            Log.e(TAG, "Network request failed for $url", e)
            throw IllegalStateException("网络请求失败：${e.localizedMessage ?: "请检查网络连接或 API 地址"}", e)
        }

        response.use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val errMsg = parseApiErrorMessage(resp.code, rawBody)
                Log.e(TAG, "API HTTP ${resp.code}: $errMsg")
                throw IllegalStateException(errMsg)
            }
            if (rawBody.isBlank()) {
                throw IllegalStateException("服务端返回空内容（HTTP ${resp.code}）")
            }
            try {
                JSONObject(rawBody)
            } catch (e: Exception) {
                throw IllegalStateException("服务端返回的不是有效 JSON 格式：${rawBody.take(120)}", e)
            }
        }
    }

    private fun parseApiErrorMessage(code: Int, rawBody: String): String {
        val detail = runCatching {
            val obj = JSONObject(rawBody)
            obj.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull() ?: rawBody.take(160)

        return when (code) {
            401, 403 -> "API Key 鉴权失败（HTTP $code）：请检查密钥是否正确或余额是否充足。$detail"
            404 -> "接口或模型不存在（HTTP 404）：请检查 Base URL 与模型名称。$detail"
            429 -> "请求过于频繁或额度不足（HTTP 429）：请稍后重试。$detail"
            else -> "模型接口返回错误（HTTP $code）：$detail"
        }
    }

    private fun buildOpenAiEndpoint(config: ApiConfig): String {
        val trimmed = config.baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    private fun buildGeminiEndpoint(config: ApiConfig): String {
        val base = config.baseUrl.trim().trimEnd('/')
            .ifBlank { ApiProviderType.GEMINI_REST.defaultBaseUrl }
        val modelName = config.model.trim().removePrefix("models/")
        return "$base/models/$modelName:generateContent?key=${config.apiKey.trim()}"
    }

    private fun extractGeminiText(resp: JSONObject): String {
        val parts = resp.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val text = parts.optJSONObject(i)?.optString("text").orEmpty()
            if (text.isNotBlank() && text != "null") sb.append(text)
        }
        return sb.toString()
    }

    private fun buildOpenAiToolsArray(): JSONArray {
        val declarations = buildToolDeclarationsJsonArray()
        return JSONArray().apply {
            for (i in 0 until declarations.length()) {
                val fn = declarations.getJSONObject(i)
                put(
                    JSONObject()
                        .put("type", "function")
                        .put("function", fn)
                )
            }
        }
    }

    private fun buildToolDeclarationsJsonArray(): JSONArray {
        fun paramProp(type: String, desc: String): JSONObject =
            JSONObject().put("type", type).put("description", desc)

        fun toolObj(
            name: String,
            description: String,
            properties: Map<String, JSONObject>,
            required: List<String>
        ): JSONObject = JSONObject()
            .put("name", name)
            .put("description", description)
            .put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject().apply {
                        properties.forEach { (k, v) -> put(k, v) }
                    })
                    .put("required", JSONArray(required))
            )

        return JSONArray().apply {
            put(
                toolObj(
                    name = "get_financial_summary",
                    description = "获取指定月份已到账收入（区分生活费与兼职）、净支出、必要支出、弹性支出与当前结余",
                    properties = mapOf("monthKey" to paramProp("string", "月份，格式如 2026-11")),
                    required = listOf("monthKey")
                )
            )
            put(
                toolObj(
                    name = "query_transactions",
                    description = "按条件检索指定月份的正式账本交易记录",
                    properties = mapOf(
                        "monthKey" to paramProp("string", "月份，格式如 2026-11"),
                        "categoryId" to paramProp("string", "可选，分类ID，如 food, study, transit, daily, entertainment, shopping"),
                        "direction" to paramProp("string", "可选，交易方向: EXPENSE, INCOME, REFUND, TRANSFER"),
                        "keyword" to paramProp("string", "可选，商户或备注关键词"),
                        "limit" to paramProp("integer", "可选，返回条数上限，默认15")
                    ),
                    required = listOf("monthKey")
                )
            )
            put(
                toolObj(
                    name = "analyze_spending",
                    description = "分析指定月份的消费结构、必要与弹性支出占比以及高频商户排行",
                    properties = mapOf("monthKey" to paramProp("string", "月份，格式如 2026-11")),
                    required = listOf("monthKey")
                )
            )
            put(
                toolObj(
                    name = "calculate_budget",
                    description = "计算指定月份各分类预算限额、已使用金额、剩余预算与超支风险等级",
                    properties = mapOf("monthKey" to paramProp("string", "月份，格式如 2026-11")),
                    required = listOf("monthKey")
                )
            )
            put(
                toolObj(
                    name = "forecast_cashflow",
                    description = "基于当前消费速率预测月底总支出、月底结余以及高风险超支分类",
                    properties = mapOf("monthKey" to paramProp("string", "月份，格式如 2026-11")),
                    required = listOf("monthKey")
                )
            )
            put(
                toolObj(
                    name = "plan_savings",
                    description = "评估储蓄目标可行性，在保障饮食/学习/交通等必要生活支出的前提下计算可行方案",
                    properties = mapOf(
                        "monthKey" to paramProp("string", "月份，格式如 2026-11"),
                        "targetYuan" to paramProp("number", "可选，目标储蓄金额（元），缺省使用当前设定目标")
                    ),
                    required = listOf("monthKey")
                )
            )
            put(
                toolObj(
                    name = "propose_budget_change",
                    description = "提出单个分类月度预算修改建议，不会直接改库，会生成确认卡片交由用户审批",
                    properties = mapOf(
                        "monthKey" to paramProp("string", "月份，格式如 2026-11"),
                        "categoryId" to paramProp("string", "分类ID，如 food, study, transit, daily, entertainment, shopping"),
                        "newLimitYuan" to paramProp("number", "建议调整后的月度预算上限（元）"),
                        "reason" to paramProp("string", "调整理由说明")
                    ),
                    required = listOf("monthKey", "categoryId", "newLimitYuan", "reason")
                )
            )
        }
    }

    private fun parseJsonArgsToMap(rawArgs: String): Map<String, Any?> {
        return runCatching { jsonObjectToMap(JSONObject(rawArgs)) }.getOrDefault(emptyMap())
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.opt(k)
            result[k] = if (v == null || v === JSONObject.NULL) null else v
        }
        return result
    }

    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        val match = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""").find(trimmed)
        return match?.groupValues?.getOrNull(1)?.trim() ?: trimmed
    }

    private fun requireValidConfig(config: ApiConfig) {
        if (!config.isValid) {
            throw IllegalStateException("尚未配置模型 API Key，请先点击「配置 API Key」填入你的大模型接口密钥。")
        }
    }

    companion object {
        private const val TAG = "EasySpendAiService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(65, TimeUnit.SECONDS)
                .build()
        }
    }
}
