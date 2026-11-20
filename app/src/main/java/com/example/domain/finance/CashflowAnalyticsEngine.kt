package com.example.domain.finance

import com.example.data.model.CategoryEntity
import com.example.data.model.CategoryGroup
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ReportPeriod(
    val shortLabel: String,
    val digestLabel: String,
    val englishCode: String
) {
    WEEK("周", "周报", "WEEKLY"),
    MONTH("月", "月报", "MONTHLY"),
    YEAR("年", "年报", "YEARLY")
}

data class TrendBucketPoint(
    val index: Int,
    val label: String,
    val fullLabel: String,
    val incomeFen: Long,
    val expenseFen: Long,
    val cumulativeNetFen: Long,
    val txCount: Int
) {
    val netSavedFen: Long get() = incomeFen - expenseFen
}

data class CashflowTrendReport(
    val period: ReportPeriod,
    val periodTitle: String,
    val rangeSubtitle: String,
    val anchorMillis: Long,
    val startMillis: Long,
    val endExclusiveMillis: Long,
    val buckets: List<TrendBucketPoint>,
    val totalIncomeFen: Long,
    val totalExpenseFen: Long,
    val prevTotalIncomeFen: Long,
    val prevTotalExpenseFen: Long,
    val txCount: Int
) {
    val netSavedFen: Long get() = totalIncomeFen - totalExpenseFen
    val prevNetSavedFen: Long get() = prevTotalIncomeFen - prevTotalExpenseFen
    val netDeltaFen: Long get() = netSavedFen - prevNetSavedFen

    val savingsRatePercent: Int?
        get() = if (totalIncomeFen > 0L) {
            ((netSavedFen.toDouble() / totalIncomeFen.toDouble()) * 100.0).roundToInt()
        } else {
            null
        }

    val expenseDeltaPercent: Int?
        get() = if (prevTotalExpenseFen > 0L) {
            (((totalExpenseFen - prevTotalExpenseFen).toDouble() / prevTotalExpenseFen.toDouble()) * 100.0).roundToInt()
        } else {
            null
        }
}

data class DigestCategoryRank(
    val categoryId: String,
    val categoryName: String,
    val isEssential: Boolean,
    val amountFen: Long,
    val sharePercent: Int,
    val txCount: Int
)

data class DigestMerchantStat(
    val merchant: String,
    val count: Int,
    val totalFen: Long
)

data class FinancialDigestReport(
    val period: ReportPeriod,
    val issueTag: String,
    val title: String,
    val rangeSubtitle: String,
    val gradeCode: String,
    val gradeLabel: String,
    val headlineVerdict: String,
    val trend: CashflowTrendReport,
    val txCount: Int,
    val activeDaysCount: Int,
    val zeroExpenseDaysCount: Int,
    val totalPeriodDays: Int,
    val avgDailyExpenseFen: Long,
    val essentialSpentFen: Long,
    val flexibleSpentFen: Long,
    val topCategories: List<DigestCategoryRank>,
    val topSingleExpense: TransactionEntity?,
    val topFrequentMerchant: DigestMerchantStat?,
    val highlights: List<String>,
    val actionTips: List<String>,
    val sharePlainText: String
)

object CashflowAnalyticsEngine {

    private val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /**
     * Picks the best initial anchor timestamp:
     * 1) Latest transaction inside activeMonthKey (so demo/real records show immediately)
     * 2) Otherwise today if activeMonthKey matches today, or the 15th of activeMonthKey
     */
    fun defaultAnchorMillis(
        activeMonthKey: String,
        allTransactions: List<TransactionEntity>
    ): Long {
        val monthTxs = allTransactions.filter { it.monthKey == activeMonthKey }
        if (monthTxs.isNotEmpty()) {
            return monthTxs.maxOf { it.occurredAtMillis }
        }
        val (y, m) = parseMonthKey(activeMonthKey)
        val now = Calendar.getInstance()
        if (now.get(Calendar.YEAR) == y && now.get(Calendar.MONTH) + 1 == m) {
            return now.timeInMillis
        }
        return newMondayCalendar().apply {
            clear()
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m - 1)
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 12)
        }.timeInMillis
    }

    fun shiftAnchor(anchorMillis: Long, period: ReportPeriod, delta: Int): Long {
        val cal = newMondayCalendar().apply { timeInMillis = anchorMillis }
        when (period) {
            ReportPeriod.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, delta)
            ReportPeriod.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, delta)
            }
            ReportPeriod.YEAR -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.YEAR, delta)
            }
        }
        return cal.timeInMillis
    }

    fun buildTrendReport(
        period: ReportPeriod,
        anchorMillis: Long,
        allTransactions: List<TransactionEntity>
    ): CashflowTrendReport {
        val (startMillis, endExclusiveMillis, prevStartMillis) = periodBounds(period, anchorMillis)
        val currentTxs = allTransactions.filter { it.occurredAtMillis in startMillis until endExclusiveMillis }
        val prevTxs = allTransactions.filter { it.occurredAtMillis in prevStartMillis until startMillis }

        val (prevIncome, prevExpense) = aggregateIncomeAndNetExpense(prevTxs)

        val buckets = when (period) {
            ReportPeriod.WEEK -> buildWeekBuckets(startMillis, currentTxs)
            ReportPeriod.MONTH -> buildMonthBuckets(startMillis, currentTxs)
            ReportPeriod.YEAR -> buildYearBuckets(startMillis, currentTxs)
        }

        val (totalIncome, totalExpense) = aggregateIncomeAndNetExpense(currentTxs)
        val (title, subtitle) = formatPeriodHeader(period, startMillis, endExclusiveMillis)

        return CashflowTrendReport(
            period = period,
            periodTitle = title,
            rangeSubtitle = subtitle,
            anchorMillis = anchorMillis,
            startMillis = startMillis,
            endExclusiveMillis = endExclusiveMillis,
            buckets = buckets,
            totalIncomeFen = totalIncome,
            totalExpenseFen = totalExpense,
            prevTotalIncomeFen = prevIncome,
            prevTotalExpenseFen = prevExpense,
            txCount = currentTxs.size
        )
    }

    fun buildDigestReport(
        period: ReportPeriod,
        anchorMillis: Long,
        allTransactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        savingsTargetFen: Long = 0L
    ): FinancialDigestReport {
        val trend = buildTrendReport(period, anchorMillis, allTransactions)
        val periodTxs = allTransactions.filter {
            it.occurredAtMillis in trend.startMillis until trend.endExclusiveMillis
        }
        val categoryMap = categories.associateBy { it.id }

        val totalPeriodDays = ((trend.endExclusiveMillis - trend.startMillis) / 86_400_000L)
            .toInt()
            .coerceAtLeast(1)

        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val activeDayKeys = mutableSetOf<String>()
        val expenseDayKeys = mutableSetOf<String>()
        periodTxs.forEach { tx ->
            val key = dayFmt.format(Date(tx.occurredAtMillis))
            activeDayKeys.add(key)
            if (tx.direction == TransactionDirection.EXPENSE) {
                expenseDayKeys.add(key)
            }
        }
        val zeroExpenseDays = (totalPeriodDays - expenseDayKeys.size).coerceAtLeast(0)
        val avgDailyExpenseFen = trend.totalExpenseFen / totalPeriodDays

        var essentialSpent = 0L
        var flexibleSpent = 0L
        val catNetSpent = mutableMapOf<String, Long>()
        val catCounts = mutableMapOf<String, Int>()

        periodTxs.forEach { tx ->
            when (tx.direction) {
                TransactionDirection.EXPENSE -> {
                    val cat = categoryMap[tx.categoryId]
                    if (cat?.group == CategoryGroup.ESSENTIAL_EXPENSE) {
                        essentialSpent += tx.amountFen
                    } else if (cat?.group != CategoryGroup.INCOME && cat?.group != CategoryGroup.NEUTRAL) {
                        flexibleSpent += tx.amountFen
                    }
                    catNetSpent[tx.categoryId] = (catNetSpent[tx.categoryId] ?: 0L) + tx.amountFen
                    catCounts[tx.categoryId] = (catCounts[tx.categoryId] ?: 0) + 1
                }
                TransactionDirection.REFUND -> {
                    val cat = categoryMap[tx.categoryId]
                    if (cat?.group == CategoryGroup.ESSENTIAL_EXPENSE) {
                        essentialSpent = (essentialSpent - tx.amountFen).coerceAtLeast(0L)
                    } else if (cat?.group != CategoryGroup.INCOME && cat?.group != CategoryGroup.NEUTRAL) {
                        flexibleSpent = (flexibleSpent - tx.amountFen).coerceAtLeast(0L)
                    }
                    catNetSpent[tx.categoryId] = ((catNetSpent[tx.categoryId] ?: 0L) - tx.amountFen).coerceAtLeast(0L)
                }
                TransactionDirection.INCOME, TransactionDirection.TRANSFER -> Unit
            }
        }

        val totalCatSpent = catNetSpent.values.sum().coerceAtLeast(1L)
        val topCategories = catNetSpent.entries
            .filter { it.value > 0L }
            .sortedByDescending { it.value }
            .take(5)
            .map { (catId, amount) ->
                val cat = categoryMap[catId]
                DigestCategoryRank(
                    categoryId = catId,
                    categoryName = cat?.name ?: catId,
                    isEssential = cat?.group == CategoryGroup.ESSENTIAL_EXPENSE,
                    amountFen = amount,
                    sharePercent = ((amount.toDouble() / totalCatSpent.toDouble()) * 100.0).roundToInt(),
                    txCount = catCounts[catId] ?: 0
                )
            }

        val expenseTxs = periodTxs.filter { it.direction == TransactionDirection.EXPENSE }
        val topSingleExpense = expenseTxs.maxByOrNull { it.amountFen }
        val topFrequentMerchant = expenseTxs
            .filter { it.merchant.isNotBlank() }
            .groupBy { it.merchant.trim() }
            .map { (merchant, list) ->
                DigestMerchantStat(
                    merchant = merchant,
                    count = list.size,
                    totalFen = list.sumOf { it.amountFen }
                )
            }
            .sortedWith(compareByDescending<DigestMerchantStat> { it.count }.thenByDescending { it.totalFen })
            .firstOrNull()

        val (gradeCode, gradeLabel, verdict) = evaluateGradeAndVerdict(
            period = period,
            trend = trend,
            essentialSpentFen = essentialSpent,
            flexibleSpentFen = flexibleSpent,
            savingsTargetFen = savingsTargetFen
        )

        val highlights = buildHighlights(
            period = period,
            trend = trend,
            topCategories = topCategories,
            topSingleExpense = topSingleExpense,
            topFrequentMerchant = topFrequentMerchant,
            zeroExpenseDays = zeroExpenseDays,
            essentialSpentFen = essentialSpent,
            flexibleSpentFen = flexibleSpent
        )

        val tips = buildActionTips(
            period = period,
            trend = trend,
            topCategories = topCategories,
            essentialSpentFen = essentialSpent,
            flexibleSpentFen = flexibleSpent,
            savingsTargetFen = savingsTargetFen
        )

        val issueTag = "EASYSPEND ${period.englishCode} DIGEST"
        val title = "${trend.periodTitle} · 财务${period.digestLabel}"
        val shareText = buildSharePlainText(
            title = title,
            rangeSubtitle = trend.rangeSubtitle,
            gradeCode = gradeCode,
            gradeLabel = gradeLabel,
            verdict = verdict,
            trend = trend,
            avgDailyExpenseFen = avgDailyExpenseFen,
            zeroExpenseDays = zeroExpenseDays,
            topCategories = topCategories,
            highlights = highlights,
            tips = tips
        )

        return FinancialDigestReport(
            period = period,
            issueTag = issueTag,
            title = title,
            rangeSubtitle = trend.rangeSubtitle,
            gradeCode = gradeCode,
            gradeLabel = gradeLabel,
            headlineVerdict = verdict,
            trend = trend,
            txCount = periodTxs.size,
            activeDaysCount = activeDayKeys.size,
            zeroExpenseDaysCount = zeroExpenseDays,
            totalPeriodDays = totalPeriodDays,
            avgDailyExpenseFen = avgDailyExpenseFen,
            essentialSpentFen = essentialSpent,
            flexibleSpentFen = flexibleSpent,
            topCategories = topCategories,
            topSingleExpense = topSingleExpense,
            topFrequentMerchant = topFrequentMerchant,
            highlights = highlights,
            actionTips = tips,
            sharePlainText = shareText
        )
    }

    fun buildAgentDeepDivePrompt(digest: FinancialDigestReport): String {
        val topCatText = digest.topCategories.take(3).joinToString("、") {
            "${it.categoryName}(${MoneyFormatter.formatFen(it.amountFen)}, ${it.sharePercent}%)"
        }.ifBlank { "暂无支出分类" }
        return "请结合我的「${digest.title}」给我一份简明复盘和下期攒钱建议：" +
            "周期收入 ${MoneyFormatter.formatFen(digest.trend.totalIncomeFen)}，" +
            "净支出 ${MoneyFormatter.formatFen(digest.trend.totalExpenseFen)}，" +
            "净攒 ${MoneyFormatter.formatFen(digest.trend.netSavedFen)}，" +
            "Top 支出为 $topCatText。"
    }

    private fun buildWeekBuckets(
        weekStartMillis: Long,
        txs: List<TransactionEntity>
    ): List<TrendBucketPoint> {
        val dayFmt = SimpleDateFormat("MM月dd日", Locale.CHINA)
        val cal = newMondayCalendar().apply { timeInMillis = weekStartMillis }
        val byDay = Array(7) { mutableListOf<TransactionEntity>() }
        txs.forEach { tx ->
            val offset = ((tx.occurredAtMillis - weekStartMillis) / 86_400_000L).toInt().coerceIn(0, 6)
            byDay[offset].add(tx)
        }
        var runningNet = 0L
        return (0 until 7).map { i ->
            val bucketStart = cal.timeInMillis
            val (inc, exp) = aggregateIncomeAndNetExpense(byDay[i])
            runningNet += (inc - exp)
            val point = TrendBucketPoint(
                index = i,
                label = weekdayLabels[i],
                fullLabel = "${dayFmt.format(Date(bucketStart))} ${weekdayLabels[i]}",
                incomeFen = inc,
                expenseFen = exp,
                cumulativeNetFen = runningNet,
                txCount = byDay[i].size
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
            point
        }
    }

    private fun buildMonthBuckets(
        monthStartMillis: Long,
        txs: List<TransactionEntity>
    ): List<TrendBucketPoint> {
        val cal = newMondayCalendar().apply { timeInMillis = monthStartMillis }
        val monthNum = cal.get(Calendar.MONTH) + 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val byDay = Array(daysInMonth) { mutableListOf<TransactionEntity>() }
        val txCal = newMondayCalendar()
        txs.forEach { tx ->
            txCal.timeInMillis = tx.occurredAtMillis
            val idx = (txCal.get(Calendar.DAY_OF_MONTH) - 1).coerceIn(0, daysInMonth - 1)
            byDay[idx].add(tx)
        }
        var runningNet = 0L
        return (0 until daysInMonth).map { i ->
            val dayNum = i + 1
            val (inc, exp) = aggregateIncomeAndNetExpense(byDay[i])
            runningNet += (inc - exp)
            TrendBucketPoint(
                index = i,
                label = "${dayNum}日",
                fullLabel = "${monthNum}月${dayNum}日",
                incomeFen = inc,
                expenseFen = exp,
                cumulativeNetFen = runningNet,
                txCount = byDay[i].size
            )
        }
    }

    private fun buildYearBuckets(
        yearStartMillis: Long,
        txs: List<TransactionEntity>
    ): List<TrendBucketPoint> {
        val cal = newMondayCalendar().apply { timeInMillis = yearStartMillis }
        val year = cal.get(Calendar.YEAR)
        val byMonth = Array(12) { mutableListOf<TransactionEntity>() }
        val txCal = newMondayCalendar()
        txs.forEach { tx ->
            txCal.timeInMillis = tx.occurredAtMillis
            val idx = txCal.get(Calendar.MONTH).coerceIn(0, 11)
            byMonth[idx].add(tx)
        }
        var runningNet = 0L
        return (0 until 12).map { i ->
            val m = i + 1
            val (inc, exp) = aggregateIncomeAndNetExpense(byMonth[i])
            runningNet += (inc - exp)
            TrendBucketPoint(
                index = i,
                label = "${m}月",
                fullLabel = "${year}年${m}月",
                incomeFen = inc,
                expenseFen = exp,
                cumulativeNetFen = runningNet,
                txCount = byMonth[i].size
            )
        }
    }

    private fun periodBounds(period: ReportPeriod, anchorMillis: Long): Triple<Long, Long, Long> {
        val start = newMondayCalendar().apply {
            timeInMillis = anchorMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            ReportPeriod.WEEK -> {
                val dow = start.get(Calendar.DAY_OF_WEEK)
                val diffToMonday = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
                start.add(Calendar.DAY_OF_MONTH, diffToMonday)
                val startMs = start.timeInMillis
                val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 7) }
                val prev = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -7) }
                return Triple(startMs, end.timeInMillis, prev.timeInMillis)
            }
            ReportPeriod.MONTH -> {
                start.set(Calendar.DAY_OF_MONTH, 1)
                val startMs = start.timeInMillis
                val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                val prev = (start.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                return Triple(startMs, end.timeInMillis, prev.timeInMillis)
            }
            ReportPeriod.YEAR -> {
                start.set(Calendar.MONTH, Calendar.JANUARY)
                start.set(Calendar.DAY_OF_MONTH, 1)
                val startMs = start.timeInMillis
                val end = (start.clone() as Calendar).apply { add(Calendar.YEAR, 1) }
                val prev = (start.clone() as Calendar).apply { add(Calendar.YEAR, -1) }
                return Triple(startMs, end.timeInMillis, prev.timeInMillis)
            }
        }
    }

    private fun formatPeriodHeader(
        period: ReportPeriod,
        startMillis: Long,
        endExclusiveMillis: Long
    ): Pair<String, String> {
        val startCal = newMondayCalendar().apply { timeInMillis = startMillis }
        val endInclusiveMs = endExclusiveMillis - 1L
        return when (period) {
            ReportPeriod.WEEK -> {
                val weekYear = startCal.get(Calendar.YEAR)
                val weekOfYear = startCal.get(Calendar.WEEK_OF_YEAR)
                val rangeFmt = SimpleDateFormat("MM.dd", Locale.US)
                val title = "${weekYear}年 第${weekOfYear}周"
                val subtitle = "${rangeFmt.format(Date(startMillis))} – ${rangeFmt.format(Date(endInclusiveMs))}"
                title to subtitle
            }
            ReportPeriod.MONTH -> {
                val year = startCal.get(Calendar.YEAR)
                val month = startCal.get(Calendar.MONTH) + 1
                val days = startCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val title = "${year}年${month}月"
                val subtitle = String.format(Locale.US, "%04d.%02d.01 – %02d.%02d", year, month, month, days)
                title to subtitle
            }
            ReportPeriod.YEAR -> {
                val year = startCal.get(Calendar.YEAR)
                val title = "${year}年度"
                val subtitle = "$year.01.01 – $year.12.31"
                title to subtitle
            }
        }
    }

    private fun aggregateIncomeAndNetExpense(txs: List<TransactionEntity>): Pair<Long, Long> {
        var income = 0L
        var grossExpense = 0L
        var refund = 0L
        txs.forEach { tx ->
            when (tx.direction) {
                TransactionDirection.INCOME -> income += tx.amountFen
                TransactionDirection.EXPENSE -> grossExpense += tx.amountFen
                TransactionDirection.REFUND -> refund += tx.amountFen
                TransactionDirection.TRANSFER -> Unit
            }
        }
        return income to (grossExpense - refund).coerceAtLeast(0L)
    }

    private fun evaluateGradeAndVerdict(
        period: ReportPeriod,
        trend: CashflowTrendReport,
        essentialSpentFen: Long,
        flexibleSpentFen: Long,
        savingsTargetFen: Long
    ): Triple<String, String, String> {
        if (trend.txCount == 0) {
            return Triple("—", "暂无数据", "本${period.shortLabel}尚未录入账单，记一笔即可生成专属小报。")
        }
        val rate = trend.savingsRatePercent
        val totalSpent = (essentialSpentFen + flexibleSpentFen).coerceAtLeast(1L)
        val flexRatio = flexibleSpentFen.toFloat() / totalSpent.toFloat()

        return when {
            trend.totalIncomeFen > 0L && (rate ?: 0) >= 35 -> {
                Triple(
                    "S",
                    "攒钱标兵",
                    "本${period.shortLabel}储蓄率达 ${rate}%，净攒 ${MoneyFormatter.formatFen(trend.netSavedFen)}，理财节奏极佳。"
                )
            }
            trend.totalIncomeFen > 0L && (rate ?: 0) >= 18 -> {
                Triple(
                    "A",
                    "稳健有道",
                    "收支盈余 ${MoneyFormatter.formatFen(trend.netSavedFen)}（储蓄率 ${rate}%），保持当前花销节奏即可稳步达标。"
                )
            }
            trend.netSavedFen >= 0L && trend.totalIncomeFen > 0L -> {
                Triple(
                    "B",
                    "均衡可控",
                    "本${period.shortLabel}保持正结余 ${MoneyFormatter.formatFen(trend.netSavedFen)}，适度收紧弹性消费可进一步提升存钱效率。"
                )
            }
            trend.totalIncomeFen == 0L -> {
                val label = if (flexRatio <= 0.35f) "克制消费" else "支出为主"
                val code = if (flexRatio <= 0.35f) "A" else "B"
                Triple(
                    code,
                    label,
                    "本${period.shortLabel}共支出 ${MoneyFormatter.formatFen(trend.totalExpenseFen)}，其中必要开支占 ${((1f - flexRatio) * 100).roundToInt()}%。"
                )
            }
            else -> {
                val targetHint = if (period == ReportPeriod.MONTH && savingsTargetFen > 0L) {
                    "，距本月存钱目标仍有差距"
                } else {
                    ""
                }
                Triple(
                    "C",
                    "留意超支",
                    "本${period.shortLabel}支出超出收入 ${MoneyFormatter.formatFen(abs(trend.netSavedFen))}$targetHint，建议优先复盘弹性消费。"
                )
            }
        }
    }

    private fun buildHighlights(
        period: ReportPeriod,
        trend: CashflowTrendReport,
        topCategories: List<DigestCategoryRank>,
        topSingleExpense: TransactionEntity?,
        topFrequentMerchant: DigestMerchantStat?,
        zeroExpenseDays: Int,
        essentialSpentFen: Long,
        flexibleSpentFen: Long
    ): List<String> {
        if (trend.txCount == 0) {
            return listOf("本${period.shortLabel}暂无已入账记录，可切换上期回看历史或导入账单。")
        }
        val list = mutableListOf<String>()

        trend.expenseDeltaPercent?.let { delta ->
            val diffFen = trend.totalExpenseFen - trend.prevTotalExpenseFen
            when {
                delta <= -5 -> list.add("支出环比下降 ${abs(delta)}%（少花 ${MoneyFormatter.formatFen(abs(diffFen))}），控费成效明显。")
                delta >= 10 -> list.add("支出环比上升 +${delta}%（多花 ${MoneyFormatter.formatFen(diffFen)}），可留意大额或新增项。")
                else -> list.add("支出与上${period.shortLabel}基本持平（${if (delta >= 0) "+$delta" else "$delta"}%），节奏稳定。")
            }
        }

        topCategories.firstOrNull()?.let { top ->
            list.add("头号支出为「${top.categoryName}」，共 ${MoneyFormatter.formatFen(top.amountFen)}（占比 ${top.sharePercent}%，${top.txCount} 笔）。")
        }

        topSingleExpense?.let { maxTx ->
            val dFmt = SimpleDateFormat("M月d日", Locale.CHINA)
            list.add("最大单笔消费：${dFmt.format(Date(maxTx.occurredAtMillis))} 在「${maxTx.merchant}」支出 ${MoneyFormatter.formatFen(maxTx.amountFen)}。")
        }

        topFrequentMerchant?.takeIf { it.count >= 2 }?.let { freq ->
            list.add("最高频常去：「${freq.merchant}」累计 ${freq.count} 次，合计 ${MoneyFormatter.formatFen(freq.totalFen)}。")
        }

        val totalSpent = essentialSpentFen + flexibleSpentFen
        if (totalSpent > 0L) {
            val essPct = ((essentialSpentFen.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()
            list.add("消费结构：必要支出占 $essPct%，弹性支出占 ${100 - essPct}%；本${period.shortLabel}共达成 $zeroExpenseDays 天零消费。")
        }

        return list
    }

    private fun buildActionTips(
        period: ReportPeriod,
        trend: CashflowTrendReport,
        topCategories: List<DigestCategoryRank>,
        essentialSpentFen: Long,
        flexibleSpentFen: Long,
        savingsTargetFen: Long
    ): List<String> {
        if (trend.txCount == 0) {
            return listOf("开启支付通知辅助或导入账单，让易支道持续追踪你的周/月/年理财进展。")
        }
        val tips = mutableListOf<String>()
        val totalSpent = (essentialSpentFen + flexibleSpentFen).coerceAtLeast(1L)
        val flexPct = ((flexibleSpentFen.toDouble() / totalSpent.toDouble()) * 100.0).roundToInt()

        val topFlex = topCategories.firstOrNull { !it.isEssential }
        if (flexPct >= 40 && topFlex != null) {
            val saveEst = (topFlex.amountFen * 0.2).toLong()
            tips.add("弹性支出占比达 $flexPct%，下${period.shortLabel}若将「${topFlex.categoryName}」微调 20%，预计可多攒约 ${MoneyFormatter.formatFen(saveEst)}。")
        } else if (trend.netSavedFen > 0L) {
            tips.add("当前收支盈余良好，建议将本${period.shortLabel}净攒的 ${MoneyFormatter.formatFen(trend.netSavedFen)} 及时转入专属储蓄罐隔离存放。")
        }

        if (period == ReportPeriod.MONTH && savingsTargetFen > 0L && trend.netSavedFen < savingsTargetFen) {
            val gap = savingsTargetFen - trend.netSavedFen
            tips.add("距离本月存钱目标（${MoneyFormatter.formatFen(savingsTargetFen)}）还差 ${MoneyFormatter.formatFen(gap)}，优先守住饮食/学习等必要开支并压缩非必要下单。")
        }

        val peakBucket = trend.buckets.maxByOrNull { it.expenseFen }
        if (peakBucket != null && peakBucket.expenseFen > 0L) {
            tips.add("本${period.shortLabel}支出峰值出现在「${peakBucket.fullLabel}」（${MoneyFormatter.formatFen(peakBucket.expenseFen)}），下期同节点可提前设置单日消费提醒。")
        }

        return tips.take(3)
    }

    private fun buildSharePlainText(
        title: String,
        rangeSubtitle: String,
        gradeCode: String,
        gradeLabel: String,
        verdict: String,
        trend: CashflowTrendReport,
        avgDailyExpenseFen: Long,
        zeroExpenseDays: Int,
        topCategories: List<DigestCategoryRank>,
        highlights: List<String>,
        tips: List<String>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("【易支道 · $title】")
        sb.appendLine("周期：$rangeSubtitle | 评级：$gradeCode $gradeLabel")
        sb.appendLine("总评：$verdict")
        sb.appendLine()
        sb.appendLine("■ 核心数读")
        sb.appendLine("• 收入：${MoneyFormatter.formatFen(trend.totalIncomeFen)}")
        sb.appendLine("• 支出：${MoneyFormatter.formatFen(trend.totalExpenseFen)}")
        val netSign = if (trend.netSavedFen > 0L) "+" else ""
        sb.appendLine("• 净攒：$netSign${MoneyFormatter.formatFen(trend.netSavedFen)}" +
            (trend.savingsRatePercent?.let { "（储蓄率 $it%）" } ?: ""))
        sb.appendLine("• 日均支出：${MoneyFormatter.formatFen(avgDailyExpenseFen)} | 零消费：${zeroExpenseDays} 天")
        if (topCategories.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("■ 支出排行 Top ${topCategories.size}")
            topCategories.forEachIndexed { idx, cat ->
                sb.appendLine("${idx + 1}. ${cat.categoryName}：${MoneyFormatter.formatFen(cat.amountFen)}（${cat.sharePercent}%）")
            }
        }
        if (highlights.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("■ 本期亮点")
            highlights.forEach { sb.appendLine("• $it") }
        }
        if (tips.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("■ 下期建议")
            tips.forEach { sb.appendLine("• $it") }
        }
        return sb.toString().trim()
    }

    private fun newMondayCalendar(): Calendar {
        return Calendar.getInstance(Locale.CHINA).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }
    }

    private fun parseMonthKey(monthKey: String): Pair<Int, Int> {
        val parts = monthKey.split("-")
        val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val m = (parts.getOrNull(1)?.toIntOrNull() ?: 11).coerceIn(1, 12)
        return y to m
    }
}
