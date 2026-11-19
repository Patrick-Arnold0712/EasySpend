package com.example.domain.finance

import com.example.data.model.BudgetEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.CategoryGroup
import com.example.data.model.FundingSource
import com.example.data.model.SavingsGoalEntity
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import java.util.Calendar

enum class OverspendRiskLevel(val label: String) {
    SAFE("安全"),
    WARNING("偏快"),
    HIGH_RISK("高风险"),
    OVER_BUDGET("已超支")
}

data class CategoryBudgetStatus(
    val categoryId: String,
    val categoryName: String,
    val isEssential: Boolean,
    val limitFen: Long,
    val spentFen: Long,
    val remainingFen: Long,
    val usageRatio: Float,
    val projectedEndOfMonthFen: Long,
    val riskLevel: OverspendRiskLevel
)

data class BudgetAdjustmentSuggestion(
    val categoryId: String,
    val categoryName: String,
    val currentLimitFen: Long,
    val proposedLimitFen: Long,
    val reason: String
)

data class MonthlyFinancialSummary(
    val monthKey: String,
    val livingAllowanceIncomeFen: Long,
    val partTimeIncomeFen: Long,
    val totalIncomeFen: Long,
    val grossExpenseFen: Long,
    val refundTotalFen: Long,
    val netExpenseFen: Long,
    val essentialSpentFen: Long,
    val flexibleSpentFen: Long,
    val currentCashBalanceFen: Long,
    val savingsTargetFen: Long,
    val projectedEndOfMonthExpenseFen: Long,
    val projectedMonthEndSavingsFen: Long,
    val isSavingsTargetFeasible: Boolean,
    val savingsFeasibilityNote: String,
    val categoryStatuses: List<CategoryBudgetStatus>,
    val suggestedAdjustments: List<BudgetAdjustmentSuggestion>
)

object BudgetAndForecastEngine {

    val defaultLimitByCategoryFen: Map<String, Long> = mapOf(
        "food" to 65000L,
        "study" to 25000L,
        "transit" to 12000L,
        "daily" to 18000L,
        "housing" to 15000L,
        "medical" to 10000L,
        "telecom" to 8000L,
        "beverage" to 12000L,
        "entertainment" to 20000L,
        "shopping" to 20000L,
        "beauty" to 15000L,
        "social" to 15000L,
        "sports" to 10000L,
        "travel" to 20000L,
        "other_expense" to 10000L
    )

    fun evaluateMonth(
        monthKey: String,
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        categories: List<CategoryEntity>,
        savingsGoal: SavingsGoalEntity?,
        currentDayOfMonth: Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).coerceIn(1, 30),
        daysInMonth: Int = 30
    ): MonthlyFinancialSummary {
        val monthTxs = transactions.filter { it.monthKey == monthKey }

        var livingAllowanceFen = 0L
        var partTimeFen = 0L
        var otherIncomeFen = 0L
        var grossExpenseFen = 0L
        var refundTotalFen = 0L
        val spentByCategory = mutableMapOf<String, Long>()

        for (tx in monthTxs) {
            when (tx.direction) {
                TransactionDirection.INCOME -> {
                    when {
                        tx.fundingSource == FundingSource.LIVING_ALLOWANCE || tx.categoryId == "allowance" ->
                            livingAllowanceFen += tx.amountFen
                        tx.fundingSource == FundingSource.PART_TIME_INCOME || tx.categoryId == "part_time" ->
                            partTimeFen += tx.amountFen
                        else -> otherIncomeFen += tx.amountFen
                    }
                }
                TransactionDirection.EXPENSE -> {
                    grossExpenseFen += tx.amountFen
                    spentByCategory[tx.categoryId] = (spentByCategory[tx.categoryId] ?: 0L) + tx.amountFen
                }
                TransactionDirection.REFUND -> {
                    refundTotalFen += tx.amountFen
                    if (tx.categoryId != "refund" && spentByCategory.containsKey(tx.categoryId)) {
                        spentByCategory[tx.categoryId] = ((spentByCategory[tx.categoryId] ?: 0L) - tx.amountFen).coerceAtLeast(0L)
                    }
                }
                TransactionDirection.TRANSFER -> Unit
            }
        }

        val totalIncomeFen = livingAllowanceFen + partTimeFen + otherIncomeFen
        val netExpenseFen = (grossExpenseFen - refundTotalFen).coerceAtLeast(0L)

        // Proportionally reflect unallocated refunds across flexible/other spending only when needed,
        // while keeping category totals exact to gross expense minus category-specific refunds.
        val expenseCategories = categories.filter {
            it.group == CategoryGroup.ESSENTIAL_EXPENSE || it.group == CategoryGroup.FLEXIBLE_EXPENSE
        }.sortedBy { it.sortOrder }

        val budgetMap = budgets.filter { it.monthKey == monthKey }.associateBy { it.categoryId }
        val elapsedDays = currentDayOfMonth.coerceIn(1, daysInMonth)

        var essentialSpentFen = 0L
        var flexibleSpentFen = 0L
        var projectedEssentialTotal = 0L
        var projectedFlexibleTotal = 0L

        val statuses = expenseCategories.map { cat ->
            val isEssential = cat.group == CategoryGroup.ESSENTIAL_EXPENSE
            val spent = spentByCategory[cat.id] ?: 0L
            val limit = budgetMap[cat.id]?.monthlyLimitFen ?: (defaultLimitByCategoryFen[cat.id] ?: 15000L)
            val remaining = limit - spent
            val ratio = if (limit > 0L) spent.toFloat() / limit.toFloat() else if (spent > 0L) 1.5f else 0f

            // Essential categories like study/transit often occur in discrete chunks, so dampen linear extrapolation slightly
            val rawProjected = if (isEssential && spent > 0L) {
                val linear = (spent.toDouble() / elapsedDays) * daysInMonth
                val damped = spent + (linear - spent) * 0.55
                damped.toLong().coerceAtLeast(spent)
            } else {
                ((spent.toDouble() / elapsedDays) * daysInMonth).toLong().coerceAtLeast(spent)
            }

            val expectedPaceRatio = elapsedDays.toFloat() / daysInMonth.toFloat()
            val risk = when {
                limit > 0L && spent > limit -> OverspendRiskLevel.OVER_BUDGET
                limit > 0L && rawProjected > (limit * 1.10).toLong() -> OverspendRiskLevel.HIGH_RISK
                limit > 0L && (ratio > (expectedPaceRatio + 0.15f) || rawProjected > limit) -> OverspendRiskLevel.WARNING
                else -> OverspendRiskLevel.SAFE
            }

            if (isEssential) {
                essentialSpentFen += spent
                projectedEssentialTotal += rawProjected
            } else {
                flexibleSpentFen += spent
                projectedFlexibleTotal += rawProjected
            }

            CategoryBudgetStatus(
                categoryId = cat.id,
                categoryName = cat.name,
                isEssential = isEssential,
                limitFen = limit,
                spentFen = spent,
                remainingFen = remaining,
                usageRatio = ratio,
                projectedEndOfMonthFen = rawProjected,
                riskLevel = risk
            )
        }

        val currentCashBalanceFen = totalIncomeFen - netExpenseFen
        val savingsTargetFen = savingsGoal?.targetFen ?: 100000L
        val projectedTotalExpense = (projectedEssentialTotal + projectedFlexibleTotal - refundTotalFen)
            .coerceAtLeast(netExpenseFen)
        val projectedMonthEndSavingsFen = totalIncomeFen - projectedTotalExpense
        val isFeasible = projectedMonthEndSavingsFen >= savingsTargetFen

        val suggestions = mutableListOf<BudgetAdjustmentSuggestion>()
        if (!isFeasible) {
            var shortfall = savingsTargetFen - projectedMonthEndSavingsFen
            // Only propose trimming flexible categories, NEVER essential categories
            val flexibleTargets = statuses
                .filter { !it.isEssential && it.limitFen > it.spentFen + 2000L }
                .sortedByDescending { it.limitFen - it.spentFen }

            for (st in flexibleTargets) {
                if (shortfall <= 0L) break
                val slack = st.limitFen - st.spentFen
                val maxSafeCut = (slack * 0.65).toLong()
                val cut = minOf(shortfall, maxSafeCut)
                if (cut >= 2000L) {
                    val proposed = ((st.limitFen - cut) / 1000L) * 1000L
                    if (proposed < st.limitFen && proposed >= st.spentFen) {
                        suggestions.add(
                            BudgetAdjustmentSuggestion(
                                categoryId = st.categoryId,
                                categoryName = st.categoryName,
                                currentLimitFen = st.limitFen,
                                proposedLimitFen = proposed,
                                reason = "为逼近月度储蓄目标且优先保障必要生活支出，建议温和收紧弹性「${st.categoryName}」预算"
                            )
                        )
                        shortfall -= (st.limitFen - proposed)
                    }
                }
            }
        }

        // Also highlight essential categories that legitimately went over budget due to real needs (suggest raising rather than cutting)
        statuses.filter { it.isEssential && it.riskLevel == OverspendRiskLevel.OVER_BUDGET }.forEach { ess ->
            val raisedLimit = ((ess.spentFen + 5000L) / 1000L) * 1000L
            suggestions.add(
                BudgetAdjustmentSuggestion(
                    categoryId = ess.categoryId,
                    categoryName = ess.categoryName,
                    currentLimitFen = ess.limitFen,
                    proposedLimitFen = raisedLimit,
                    reason = "「${ess.categoryName}」属于必要生活支出，已发生真实刚需，建议适度上调预算上限而非压缩生活质量"
                )
            )
        }

        val partTimeExpected = savingsGoal?.partTimeExpectedFen ?: 0L
        val note = if (isFeasible) {
            "按当前仅计已到账收入（${MoneyFormatter.formatFen(totalIncomeFen)}）与消费速率，预计月底可结余 ${MoneyFormatter.formatFen(projectedMonthEndSavingsFen)}，有望达成 ${MoneyFormatter.formatFen(savingsTargetFen)} 储蓄挑战目标。"
        } else {
            val gap = savingsTargetFen - projectedMonthEndSavingsFen
            val expectedHint = if (partTimeExpected > 0L && partTimeFen < partTimeExpected) {
                "（注：尚有预计兼职 ${MoneyFormatter.formatFen(partTimeExpected - partTimeFen)} 未到账，按严格现金原则暂不计入当前结余）"
            } else ""
            "按已到账收入 ${MoneyFormatter.formatFen(totalIncomeFen)} 测算，预计月底结余 ${MoneyFormatter.formatFen(projectedMonthEndSavingsFen)}，距挑战目标尚差 ${MoneyFormatter.formatFen(gap)}$expectedHint。系统坚持优先保障饮食、学习与交通等必要开支，绝不压缩基本生活费来硬凑目标。"
        }

        return MonthlyFinancialSummary(
            monthKey = monthKey,
            livingAllowanceIncomeFen = livingAllowanceFen,
            partTimeIncomeFen = partTimeFen,
            totalIncomeFen = totalIncomeFen,
            grossExpenseFen = grossExpenseFen,
            refundTotalFen = refundTotalFen,
            netExpenseFen = netExpenseFen,
            essentialSpentFen = essentialSpentFen,
            flexibleSpentFen = flexibleSpentFen,
            currentCashBalanceFen = currentCashBalanceFen,
            savingsTargetFen = savingsTargetFen,
            projectedEndOfMonthExpenseFen = projectedTotalExpense,
            projectedMonthEndSavingsFen = projectedMonthEndSavingsFen,
            isSavingsTargetFeasible = isFeasible,
            savingsFeasibilityNote = note,
            categoryStatuses = statuses,
            suggestedAdjustments = suggestions
        )
    }
}
