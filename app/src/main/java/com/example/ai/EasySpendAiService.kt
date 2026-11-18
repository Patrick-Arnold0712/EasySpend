package com.example.ai

import android.util.Log
import com.example.data.model.CategoryEntity
import com.example.data.model.ImportCandidateEntity
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.MoneyFormatter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.json.JSONObject
import java.util.UUID

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

class EasySpendAiService(private val repository: EasySpendRepository) {

    private val toolsExecutor = EasySpendAgentTools(repository)

    private val functionDeclarations: List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = "get_financial_summary",
            description = "获取指定月份已到账收入（区分生活费与兼职）、净支出、必要支出、弹性支出与当前结余",
            parameters = mapOf("monthKey" to Schema.string("月份，格式如 2026-11"))
        ),
        FunctionDeclaration(
            name = "query_transactions",
            description = "按条件检索指定月份的正式账本交易记录",
            parameters = mapOf(
                "monthKey" to Schema.string("月份，格式如 2026-11"),
                "categoryId" to Schema.string("分类ID，如 food, study, transit, daily, entertainment, shopping", nullable = true),
                "direction" to Schema.string("交易方向: EXPENSE, INCOME, REFUND, TRANSFER", nullable = true),
                "keyword" to Schema.string("商户或备注关键词", nullable = true),
                "limit" to Schema.integer("返回条数上限，默认15", nullable = true)
            ),
            optionalParameters = listOf("categoryId", "direction", "keyword", "limit")
        ),
        FunctionDeclaration(
            name = "analyze_spending",
            description = "分析指定月份的消费结构、必要与弹性支出占比以及高频商户排行",
            parameters = mapOf("monthKey" to Schema.string("月份，格式如 2026-11"))
        ),
        FunctionDeclaration(
            name = "calculate_budget",
            description = "计算指定月份各分类预算限额、已使用金额、剩余预算与超支风险等级",
            parameters = mapOf("monthKey" to Schema.string("月份，格式如 2026-11"))
        ),
        FunctionDeclaration(
            name = "forecast_cashflow",
            description = "基于当前消费速率预测月底总支出、月底结余以及高风险超支分类",
            parameters = mapOf("monthKey" to Schema.string("月份，格式如 2026-11"))
        ),
        FunctionDeclaration(
            name = "plan_savings",
            description = "评估储蓄目标可行性，在保障饮食/学习/交通等必要生活支出的前提下计算可行方案",
            parameters = mapOf(
                "monthKey" to Schema.string("月份，格式如 2026-11"),
                "targetYuan" to Schema.double("目标储蓄金额（元），缺省使用当前设定目标", nullable = true)
            ),
            optionalParameters = listOf("targetYuan")
        ),
        FunctionDeclaration(
            name = "propose_budget_change",
            description = "提出单个分类月度预算修改建议，不会直接改库，会生成确认卡片交由用户审批",
            parameters = mapOf(
                "monthKey" to Schema.string("月份，格式如 2026-11"),
                "categoryId" to Schema.string("分类ID，如 food, study, transit, daily, entertainment, shopping"),
                "newLimitYuan" to Schema.double("建议调整后的月度预算上限（元）"),
                "reason" to Schema.string("调整理由说明")
            )
        )
    )

    private val agentModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
                maxOutputTokens = 1536
            },
            systemInstruction = content {
                text(
                    """
                    你是「易支道 EasySpend」个人财务管理与智能预算规划 Agent，服务于中国大学生日常财务管理。
                    严格遵守以下原则：
                    1. 所有金额、预算与预测必须通过工具读取真实账本与确定性引擎结果，严禁编造交易记录或虚构金额。
                    2. 为提高响应速度，请在同一轮内一次性并行调用所有需要的查询工具（如同时调用 get_financial_summary、forecast_cashflow、plan_savings），避免逐个串行多轮调用。
                    3. 严格区分已到账收入与预计收入，绝不把未到账的预计兼职当作已到账现金。
                    4. 储蓄目标（如1000元）属于挑战目标，必须优先保障饮食（food）、学习（study）、交通（transit）、日常必需（daily）等必要生活支出，绝不建议以牺牲基本生活健康为代价硬凑储蓄。
                    5. 若需修改分类预算，必须调用 propose_budget_change 工具生成待确认方案，并提示用户点击确认卡片后才会生效。
                    6. 回复简洁精炼、条理清晰（使用简体中文），直接给出关键金额与可执行建议。
                    """.trimIndent()
                )
            },
            tools = listOf(Tool.functionDeclarations(functionDeclarations))
        )
    }

    private val insightSchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string("一句话财务诊断标题"),
            "trendSummary" to Schema.string("消费趋势与结构分析（2-3句话）"),
            "impulseAlert" to Schema.string("冲动消费或弹性支出提醒"),
            "savingsVerdict" to Schema.string("储蓄挑战可行性评价与必要生活保障说明"),
            "recommendations" to Schema.array(
                items = Schema.string("一条具体可执行的省钱或预算习惯建议"),
                description = "3条个性化建议"
            )
        )
    )

    private val insightModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
                responseMimeType = "application/json"
                responseSchema = insightSchema
            }
        )
    }

    private val batchClassifySchema = Schema.obj(
        properties = mapOf(
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "candidateId" to Schema.integer("待确认候选记录ID"),
                        "suggestedCategoryId" to Schema.enumeration(
                            values = listOf("food", "study", "transit", "daily", "entertainment", "shopping", "other_expense", "allowance", "part_time", "refund", "transfer"),
                            description = "最可能的分类ID"
                        ),
                        "confidence" to Schema.double("0.0到1.0之间的置信度"),
                        "reason" to Schema.string("简短分类依据，多用途商户请提示需用户核实")
                    )
                )
            )
        )
    )

    private val classifyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = batchClassifySchema
            }
        )
    }

    suspend fun askFinancialAgent(
        userPrompt: String,
        monthKey: String,
        conversationHistory: List<Content> = emptyList(),
        onProgress: (String) -> Unit = {},
        onPartialText: (String) -> Unit = {}
    ): AgentChatReply {
        val sessionId = UUID.randomUUID().toString().take(8)
        val traces = mutableListOf<ToolExecutionResult>()
        val proposals = mutableListOf<PendingBudgetProposal>()
        val latestText = StringBuilder()

        try {
            val chat = agentModel.startChat(history = conversationHistory)
            onProgress("正在向 Gemini 发送请求并规划工具调用...")

            suspend fun collectRoundStream(streamPrompt: Content): List<com.google.firebase.ai.type.FunctionCallPart> {
                val calls = mutableListOf<com.google.firebase.ai.type.FunctionCallPart>()
                latestText.clear()
                withTimeout(60_000L) {
                    chat.sendMessageStream(streamPrompt).collect { chunk ->
                        val chunkText = chunk.text
                        if (!chunkText.isNullOrEmpty()) {
                            latestText.append(chunkText)
                            onPartialText(latestText.toString())
                        }
                        if (chunk.functionCalls.isNotEmpty()) {
                            calls.addAll(chunk.functionCalls)
                        }
                    }
                }
                return calls
            }

            var pendingCalls = collectRoundStream(
                content("user") { text("当前账期：$monthKey。用户问题：$userPrompt") }
            )

            var round = 0
            while (pendingCalls.isNotEmpty() && round < 3) {
                round++
                val callNames = pendingCalls.joinToString("、") { it.name }
                onProgress("正在执行本地确定性工具：$callNames")

                val parts = mutableListOf<FunctionResponsePart>()
                for (call in pendingCalls) {
                    val kotlinArgs = call.args.mapValues { (_, v) -> jsonElementToPrimitive(v) }
                    val exec = toolsExecutor.executeTool(
                        sessionId = sessionId,
                        toolName = call.name,
                        args = kotlinArgs,
                        defaultMonthKey = monthKey
                    )
                    traces.add(exec)
                    exec.proposal?.let { proposals.add(it) }

                    val kotlinxObj = Json.parseToJsonElement(exec.resultJson.toString()) as JsonObject
                    parts.add(FunctionResponsePart(call.name, kotlinxObj))
                }
                onProgress("已获取 ${traces.size} 项账本数据，正在流式生成分析建议...")
                pendingCalls = collectRoundStream(
                    content("user") {
                        parts.forEach { part(it) }
                    }
                )
            }

            val text = latestText.toString().trim().takeIf { it.isNotBlank() }
                ?: "已完成账本核查，请查看下方工具执行结果与建议。"
            return AgentChatReply(text = text, toolTraces = traces, proposals = proposals)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Agent call timed out (completed ${traces.size} tools, partial=${latestText.length} chars)", e)
            val partial = latestText.toString().trim()
            if (partial.isNotBlank() || traces.isNotEmpty()) {
                val fallbackText = buildString {
                    if (partial.isNotBlank()) {
                        appendLine(partial)
                        appendLine()
                        append("（注：流式生成最后片段超时，以上内容及下方 ${traces.size} 项本地确定性工具数据已保留。）")
                    } else {
                        append("模型生成最终文本超时，但以下 ${traces.size} 项本地确定性工具已完成核对。您可以展开工具轨迹查看精确数据，或重新提问。")
                    }
                }
                return AgentChatReply(
                    text = fallbackText,
                    toolTraces = traces,
                    proposals = proposals
                )
            }
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Agent call failed", e)
            throw e
        }
    }

    suspend fun generateSpendingInsights(monthKey: String): SpendingInsightReport {
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
            请根据以下确定性财务摘要数据，生成结构化消费趋势分析与习惯改进建议。
            严禁编造摘要之外的金额，禁止建议过度压缩饮食/学习/交通等必要生活开支：
            $digest
        """.trimIndent()

        try {
            return withTimeout(60_000L) {
                val response = insightModel.generateContent(prompt)
                val rawJson = response.text ?: throw IllegalStateException("模型返回空响应")
                val json = JSONObject(rawJson)
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
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Insights generation timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Insights generation failed", e)
            throw e
        }
    }

    suspend fun suggestCategoriesForCandidates(
        candidates: List<ImportCandidateEntity>,
        categories: List<CategoryEntity>
    ): List<CandidateAiClassification> {
        if (candidates.isEmpty()) return emptyList()
        val catListDesc = categories.joinToString(", ") { "${it.id}(${it.name})" }
        val itemsText = candidates.take(15).joinToString("\n") { c ->
            "id=${c.id} | 商户=${c.merchant} | 描述=${c.rawDescription} | 方向=${c.direction.displayName} | 金额=${MoneyFormatter.formatFen(c.amountFen)}"
        }
        val prompt = """
            请为以下待确认交易提供消费预分类建议（仅作预分类，用户仍会复核）。
            可选分类ID: $catListDesc
            如果是美团/淘宝/便利店等多用途商户且商品描述不明确，请将 confidence 设为 <= 0.72 并在 reason 提示需用户确认。
            待分类记录：
            $itemsText
        """.trimIndent()

        try {
            return withTimeout(60_000L) {
                val response = classifyModel.generateContent(prompt)
                val rawJson = response.text ?: throw IllegalStateException("模型返回空响应")
                val json = JSONObject(rawJson)
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
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Batch classification timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Batch classification failed", e)
            throw e
        }
    }

    private fun jsonElementToPrimitive(el: JsonElement): Any? {
        return when (el) {
            is JsonNull -> null
            is JsonPrimitive -> when {
                el.isString -> el.content
                el.booleanOrNull != null -> el.booleanOrNull
                el.longOrNull != null -> el.longOrNull
                el.doubleOrNull != null -> el.doubleOrNull
                else -> el.content
            }
            else -> el.toString()
        }
    }

    companion object {
        private const val TAG = "EasySpendAiService"
    }
}
