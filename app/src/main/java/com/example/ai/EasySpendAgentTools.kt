package com.example.ai

import com.example.data.model.TransactionDirection
import com.example.data.repository.EasySpendRepository
import com.example.domain.finance.BudgetAdjustmentSuggestion
import com.example.domain.finance.MoneyFormatter
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToLong

enum class ProposalStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}

data class PendingBudgetProposal(
    val id: String = UUID.randomUUID().toString(),
    val monthKey: String,
    val categoryId: String,
    val categoryName: String,
    val currentLimitFen: Long,
    val proposedLimitFen: Long,
    val reason: String,
    val status: ProposalStatus = ProposalStatus.PENDING
)

data class ToolExecutionResult(
    val toolName: String,
    val argumentsJson: String,
    val resultJson: JSONObject,
    val humanSummary: String,
    val proposal: PendingBudgetProposal? = null
)

class EasySpendAgentTools(private val repository: EasySpendRepository) {

    suspend fun executeTool(
        sessionId: String,
        toolName: String,
        args: Map<String, Any?>,
        defaultMonthKey: String
    ): ToolExecutionResult {
        val monthKey = (args["monthKey"] as? String)?.takeIf { it.isNotBlank() } ?: defaultMonthKey
        val argsJsonString = JSONObject(args.mapValues { (_, v) -> v ?: JSONObject.NULL }).toString()

        val result = when (toolName) {
            "get_financial_summary" -> handleGetFinancialSummary(monthKey, argsJsonString)
            "query_transactions" -> handleQueryTransactions(monthKey, args, argsJsonString)
            "analyze_spending" -> handleAnalyzeSpending(monthKey, argsJsonString)
            "calculate_budget" -> handleCalculateBudget(monthKey, argsJsonString)
            "forecast_cashflow" -> handleForecastCashflow(monthKey, argsJsonString)
            "plan_savings" -> handlePlanSavings(monthKey, args, argsJsonString)
            "propose_budget_change" -> handleProposeBudgetChange(monthKey, args, argsJsonString)
            else -> ToolExecutionResult(
                toolName = toolName,
                argumentsJson = argsJsonString,
                resultJson = JSONObject().put("error", "未知工具名称: $toolName"),
                humanSummary = "未识别的工具调用：$toolName"
            )
        }

        repository.logToolCall(
            sessionId = sessionId,
            toolName = toolName,
            argumentsJson = result.argumentsJson,
            resultSummary = result.humanSummary,
            requiresConfirmation = result.proposal != null,
            confirmedByUser = false
        )
        return result
    }

    private suspend fun handleGetFinancialSummary(
        monthKey: String,
        argsJsonString: String
    ): ToolExecutionResult {
        val s = repository.getMonthlySummarySnapshot(monthKey)
        val goal = repository.getSavingsGoalSnapshot(monthKey)
        val json = JSONObject().apply {
            put("monthKey", s.monthKey)
            put("livingAllowanceIncomeYuan", s.livingAllowanceIncomeFen / 100.0)
            put("partTimeIncomeYuan", s.partTimeIncomeFen / 100.0)
            put("totalArrivedIncomeYuan", s.totalIncomeFen / 100.0)
            put("expectedPartTimeReferenceYuan", (goal?.partTimeExpectedFen ?: 0L) / 100.0)
            put("grossExpenseYuan", s.grossExpenseFen / 100.0)
            put("refundTotalYuan", s.refundTotalFen / 100.0)
            put("netExpenseYuan", s.netExpenseFen / 100.0)
            put("essentialSpentYuan", s.essentialSpentFen / 100.0)
            put("flexibleSpentYuan", s.flexibleSpentFen / 100.0)
            put("currentCashBalanceYuan", s.currentCashBalanceFen / 100.0)
            put("savingsTargetYuan", s.savingsTargetFen / 100.0)
        }
        val summary = "已查 $monthKey 收支总览：已到账生活费 ${MoneyFormatter.formatFen(s.livingAllowanceIncomeFen)} + 已到账兼职 ${MoneyFormatter.formatFen(s.partTimeIncomeFen)}，净支出 ${MoneyFormatter.formatFen(s.netExpenseFen)}，当前结余 ${MoneyFormatter.formatFen(s.currentCashBalanceFen)}"
        return ToolExecutionResult("get_financial_summary", argsJsonString, json, summary)
    }

    private suspend fun handleQueryTransactions(
        monthKey: String,
        args: Map<String, Any?>,
        argsJsonString: String
    ): ToolExecutionResult {
        val catFilter = (args["categoryId"] as? String)?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        val dirFilterStr = (args["direction"] as? String)?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        val keyword = (args["keyword"] as? String)?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        val limit = ((args["limit"] as? Number)?.toInt() ?: 15).coerceIn(1, 30)

        val categories = repository.getCategoriesSnapshot().associateBy { it.id }
        val allTxs = repository.getTransactionsSnapshot(monthKey)
        val filtered = allTxs.filter { tx ->
            val matchCat = catFilter == null || tx.categoryId == catFilter
            val matchDir = dirFilterStr == null || tx.direction.name.equals(dirFilterStr, ignoreCase = true)
            val matchKw = keyword == null ||
                tx.merchant.contains(keyword, ignoreCase = true) ||
                tx.rawDescription.contains(keyword, ignoreCase = true) ||
                tx.note.contains(keyword, ignoreCase = true)
            matchCat && matchDir && matchKw
        }.take(limit)

        val arr = JSONArray()
        filtered.forEach { tx ->
            arr.put(
                JSONObject().apply {
                    put("id", tx.id)
                    put("time", MoneyFormatter.formatTimestamp(tx.occurredAtMillis))
                    put("merchant", tx.merchant)
                    put("direction", tx.direction.name)
                    put("categoryId", tx.categoryId)
                    put("categoryName", categories[tx.categoryId]?.name ?: tx.categoryId)
                    put("amountYuan", tx.amountFen / 100.0)
                    put("channel", tx.channel.displayName)
                    put("fundingSource", tx.fundingSource.displayName)
                    put("note", tx.note.ifBlank { tx.rawDescription })
                }
            )
        }
        val json = JSONObject().apply {
            put("monthKey", monthKey)
            put("matchedCount", filtered.size)
            put("transactions", arr)
        }
        val summary = "检索 $monthKey 正式账本，共返回 ${filtered.size} 笔匹配交易"
        return ToolExecutionResult("query_transactions", argsJsonString, json, summary)
    }

    private suspend fun handleAnalyzeSpending(
        monthKey: String,
        argsJsonString: String
    ): ToolExecutionResult {
        val s = repository.getMonthlySummarySnapshot(monthKey)
        val txs = repository.getTransactionsSnapshot(monthKey)
        val expenses = txs.filter { it.direction == TransactionDirection.EXPENSE }

        val topMerchants = expenses
            .groupBy { it.merchant }
            .map { (merchant, list) ->
                Triple(merchant, list.size, list.sumOf { it.amountFen })
            }
            .sortedByDescending { it.third }
            .take(5)

        val merchantArr = JSONArray()
        topMerchants.forEach { (merchant, count, totalFen) ->
            merchantArr.put(
                JSONObject().apply {
                    put("merchant", merchant)
                    put("count", count)
                    put("totalYuan", totalFen / 100.0)
                }
            )
        }

        val catArr = JSONArray()
        s.categoryStatuses.filter { it.spentFen > 0L }.sortedByDescending { it.spentFen }.forEach { st ->
            catArr.put(
                JSONObject().apply {
                    put("categoryId", st.categoryId)
                    put("categoryName", st.categoryName)
                    put("isEssential", st.isEssential)
                    put("spentYuan", st.spentFen / 100.0)
                }
            )
        }

        val essentialRatio = if (s.grossExpenseFen > 0L) {
            (s.essentialSpentFen.toDouble() / s.grossExpenseFen.toDouble() * 100).toInt()
        } else 0

        val json = JSONObject().apply {
            put("monthKey", monthKey)
            put("essentialSpentYuan", s.essentialSpentFen / 100.0)
            put("flexibleSpentYuan", s.flexibleSpentFen / 100.0)
            put("essentialSharePercent", essentialRatio)
            put("categoryBreakdown", catArr)
            put("topMerchants", merchantArr)
        }
        val topMerchantDesc = topMerchants.firstOrNull()?.let { "${it.first}(${MoneyFormatter.formatFen(it.third)})" } ?: "暂无"
        val summary = "消费结构分析：必要支出占 $essentialRatio%（${MoneyFormatter.formatFen(s.essentialSpentFen)}），弹性支出 ${MoneyFormatter.formatFen(s.flexibleSpentFen)}，最高支出商户 $topMerchantDesc"
        return ToolExecutionResult("analyze_spending", argsJsonString, json, summary)
    }

    private suspend fun handleCalculateBudget(
        monthKey: String,
        argsJsonString: String
    ): ToolExecutionResult {
        val s = repository.getMonthlySummarySnapshot(monthKey)
        val arr = JSONArray()
        s.categoryStatuses.forEach { st ->
            arr.put(
                JSONObject().apply {
                    put("categoryId", st.categoryId)
                    put("categoryName", st.categoryName)
                    put("isEssential", st.isEssential)
                    put("limitYuan", st.limitFen / 100.0)
                    put("spentYuan", st.spentFen / 100.0)
                    put("remainingYuan", st.remainingFen / 100.0)
                    put("usagePercent", (st.usageRatio * 100).toInt())
                    put("riskLevel", st.riskLevel.label)
                }
            )
        }
        val json = JSONObject().apply {
            put("monthKey", monthKey)
            put("categories", arr)
        }
        val riskCount = s.categoryStatuses.count { it.riskLevel.name != "SAFE" }
        val summary = "预算核对完成：共 ${s.categoryStatuses.size} 个分类，其中 $riskCount 项处于偏快/高风险或已超支状态"
        return ToolExecutionResult("calculate_budget", argsJsonString, json, summary)
    }

    private suspend fun handleForecastCashflow(
        monthKey: String,
        argsJsonString: String
    ): ToolExecutionResult {
        val s = repository.getMonthlySummarySnapshot(monthKey)
        val atRisk = s.categoryStatuses.filter { it.riskLevel.name != "SAFE" }
        val riskArr = JSONArray()
        atRisk.forEach { st ->
            riskArr.put(
                JSONObject().apply {
                    put("categoryId", st.categoryId)
                    put("categoryName", st.categoryName)
                    put("isEssential", st.isEssential)
                    put("limitYuan", st.limitFen / 100.0)
                    put("projectedEndOfMonthYuan", st.projectedEndOfMonthFen / 100.0)
                    put("riskLevel", st.riskLevel.label)
                }
            )
        }
        val json = JSONObject().apply {
            put("monthKey", monthKey)
            put("totalArrivedIncomeYuan", s.totalIncomeFen / 100.0)
            put("projectedEndOfMonthExpenseYuan", s.projectedEndOfMonthExpenseFen / 100.0)
            put("projectedMonthEndSavingsYuan", s.projectedMonthEndSavingsFen / 100.0)
            put("atRiskCategories", riskArr)
        }
        val riskLabels = if (atRisk.isEmpty()) "无超支风险" else atRisk.joinToString("、") { "${it.categoryName}(${it.riskLevel.label})" }
        val summary = "现金流预测：预计月底净支出 ${MoneyFormatter.formatFen(s.projectedEndOfMonthExpenseFen)}，月底预计结余 ${MoneyFormatter.formatFen(s.projectedMonthEndSavingsFen)}；风险项：$riskLabels"
        return ToolExecutionResult("forecast_cashflow", argsJsonString, json, summary)
    }

    private suspend fun handlePlanSavings(
        monthKey: String,
        args: Map<String, Any?>,
        argsJsonString: String
    ): ToolExecutionResult {
        val s = repository.getMonthlySummarySnapshot(monthKey)
        val customTargetFen = (args["targetYuan"] as? Number)?.let { (it.toDouble() * 100).roundToLong() }
        val targetFen = customTargetFen ?: s.savingsTargetFen
        val feasibleAtPace = s.projectedMonthEndSavingsFen >= targetFen
        val shortfallFen = (targetFen - s.projectedMonthEndSavingsFen).coerceAtLeast(0L)

        val flexibleCategories = s.categoryStatuses.filter { !it.isEssential }
        val flexRemainingSum = flexibleCategories.sumOf { (it.limitFen - it.spentFen).coerceAtLeast(0L) }

        val adjArr = JSONArray()
        val suggestions: List<BudgetAdjustmentSuggestion> = if (customTargetFen == null) {
            s.suggestedAdjustments
        } else {
            // Recompute gentle flexible-only cuts against customTargetFen
            val out = mutableListOf<BudgetAdjustmentSuggestion>()
            var gap = shortfallFen
            for (st in flexibleCategories.sortedByDescending { it.limitFen - it.spentFen }) {
                if (gap <= 0L) break
                val slack = (st.limitFen - st.spentFen).coerceAtLeast(0L)
                val maxCut = (slack * 0.65).toLong()
                val cut = minOf(gap, maxCut)
                if (cut >= 2000L) {
                    val proposed = ((st.limitFen - cut) / 1000L) * 1000L
                    if (proposed < st.limitFen && proposed >= st.spentFen) {
                        out.add(
                            BudgetAdjustmentSuggestion(
                                categoryId = st.categoryId,
                                categoryName = st.categoryName,
                                currentLimitFen = st.limitFen,
                                proposedLimitFen = proposed,
                                reason = "优先保障必要开支的前提下，温和收紧弹性「${st.categoryName}」预算"
                            )
                        )
                        gap -= (st.limitFen - proposed)
                    }
                }
            }
            out
        }

        suggestions.forEach { adj ->
            adjArr.put(
                JSONObject().apply {
                    put("categoryId", adj.categoryId)
                    put("categoryName", adj.categoryName)
                    put("currentLimitYuan", adj.currentLimitFen / 100.0)
                    put("proposedLimitYuan", adj.proposedLimitFen / 100.0)
                    put("reason", adj.reason)
                }
            )
        }

        val json = JSONObject().apply {
            put("monthKey", monthKey)
            put("targetSavingsYuan", targetFen / 100.0)
            put("projectedSavingsYuan", s.projectedMonthEndSavingsFen / 100.0)
            put("isFeasibleAtCurrentPace", feasibleAtPace)
            put("shortfallYuan", shortfallFen / 100.0)
            put("essentialProtectionPolicy", "严格优先保障饮食、学习资料、交通出行与日常必需支出，绝不通过压缩基本生活费来硬凑储蓄目标。")
            put("flexibleRemainingRoomYuan", flexRemainingSum / 100.0)
            put("recommendedFlexibleAdjustments", adjArr)
            put("feasibilityNote", s.savingsFeasibilityNote)
        }
        val summary = if (feasibleAtPace) {
            "储蓄规划：目标 ${MoneyFormatter.formatFen(targetFen)} 可行（预计月底结余 ${MoneyFormatter.formatFen(s.projectedMonthEndSavingsFen)}）"
        } else {
            "储蓄规划：目标 ${MoneyFormatter.formatFen(targetFen)} 当前缺口 ${MoneyFormatter.formatFen(shortfallFen)}，已生成 ${suggestions.size} 条不压缩必要生活的弹性调整参考"
        }
        return ToolExecutionResult("plan_savings", argsJsonString, json, summary)
    }

    private suspend fun handleProposeBudgetChange(
        monthKey: String,
        args: Map<String, Any?>,
        argsJsonString: String
    ): ToolExecutionResult {
        val categoryId = (args["categoryId"] as? String)?.trim().orEmpty()
        val newLimitYuan = (args["newLimitYuan"] as? Number)?.toDouble() ?: -1.0
        val reason = (args["reason"] as? String)?.trim().orEmpty()
            .ifBlank { "根据本月消费节奏与储蓄目标建议调整预算" }

        val summarySnapshot = repository.getMonthlySummarySnapshot(monthKey)
        val status = summarySnapshot.categoryStatuses.firstOrNull { it.categoryId == categoryId }
            ?: return ToolExecutionResult(
                toolName = "propose_budget_change",
                argumentsJson = argsJsonString,
                resultJson = JSONObject().put("error", "未找到分类 $categoryId"),
                humanSummary = "预算调整提议失败：未找到分类 $categoryId"
            )

        val proposedFen = (newLimitYuan * 100).roundToLong().coerceAtLeast(0L)
        if (newLimitYuan < 0) {
            return ToolExecutionResult(
                toolName = "propose_budget_change",
                argumentsJson = argsJsonString,
                resultJson = JSONObject().put("error", "预算金额必须大于等于 0"),
                humanSummary = "预算调整提议失败：金额无效"
            )
        }

        // Essential category protection guard: do not let a proposal cut essential budget below current spent
        val guardedReason = if (status.isEssential && proposedFen < status.spentFen) {
            "$reason（系统提醒：该分类属必要支出且已花 ${MoneyFormatter.formatFen(status.spentFen)}，确认前请慎重评估基本生活需要）"
        } else {
            reason
        }

        val proposal = PendingBudgetProposal(
            monthKey = monthKey,
            categoryId = status.categoryId,
            categoryName = status.categoryName,
            currentLimitFen = status.limitFen,
            proposedLimitFen = proposedFen,
            reason = guardedReason,
            status = ProposalStatus.PENDING
        )

        val json = JSONObject().apply {
            put("status", "PENDING_USER_CONFIRMATION")
            put("monthKey", monthKey)
            put("categoryId", status.categoryId)
            put("categoryName", status.categoryName)
            put("isEssential", status.isEssential)
            put("currentLimitYuan", status.limitFen / 100.0)
            put("proposedLimitYuan", proposedFen / 100.0)
            put("appliedToDatabase", false)
            put("message", "已生成待确认卡片，等待用户点击确认后才修改本地预算。")
        }
        val summary = "已生成「${status.categoryName}」预算调整建议卡片：${MoneyFormatter.formatFen(status.limitFen)} → ${MoneyFormatter.formatFen(proposedFen)}（等待用户确认）"
        return ToolExecutionResult("propose_budget_change", argsJsonString, json, summary, proposal)
    }
}
