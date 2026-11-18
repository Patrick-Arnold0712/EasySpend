package com.example.data.repository

import com.example.data.local.EasySpendDatabase
import com.example.data.model.AgentToolLogEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.CandidateResolution
import com.example.data.model.CategoryEntity
import com.example.data.model.ClassificationSource
import com.example.data.model.DiscrepancyType
import com.example.data.model.FundingSource
import com.example.data.model.ImportBatchEntity
import com.example.data.model.ImportCandidateEntity
import com.example.data.model.MerchantPreferenceEntity
import com.example.data.model.PaymentChannel
import com.example.data.model.ReviewStatus
import com.example.data.model.SavingsGoalEntity
import com.example.data.model.TransactionDirection
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionSourceType
import com.example.domain.finance.BudgetAndForecastEngine
import com.example.domain.finance.ClassificationEngine
import com.example.domain.finance.DemoStatementFactory
import com.example.domain.finance.MoneyFormatter
import com.example.domain.finance.MonthlyFinancialSummary
import com.example.domain.finance.StatementImporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Locale

class EasySpendRepository(private val db: EasySpendDatabase) {

    private val categoryDao = db.categoryDao()
    private val txDao = db.transactionDao()
    private val importDao = db.importDao()
    private val budgetDao = db.budgetDao()
    private val savingsGoalDao = db.savingsGoalDao()
    private val merchantPrefDao = db.merchantPrefDao()
    private val toolLogDao = db.toolLogDao()

    val categoriesFlow: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val pendingCandidatesFlow: Flow<List<ImportCandidateEntity>> = importDao.observePendingCandidates()
    val importBatchesFlow: Flow<List<ImportBatchEntity>> = importDao.observeBatches()
    val merchantPreferencesFlow: Flow<List<MerchantPreferenceEntity>> = merchantPrefDao.observeAll()
    val toolLogsFlow: Flow<List<AgentToolLogEntity>> = toolLogDao.observeRecent()

    fun observeTransactionsForMonth(monthKey: String): Flow<List<TransactionEntity>> =
        txDao.observeForMonth(monthKey)

    fun observeSavingsGoalForMonth(monthKey: String): Flow<SavingsGoalEntity?> =
        savingsGoalDao.observeForMonth(monthKey)

    fun observeMonthlySummary(monthKey: String): Flow<MonthlyFinancialSummary> {
        return combine(
            txDao.observeForMonth(monthKey),
            budgetDao.observeForMonth(monthKey),
            categoryDao.observeAll(),
            savingsGoalDao.observeForMonth(monthKey)
        ) { txs, budgets, categories, goal ->
            val effectiveCategories = categories.ifEmpty { ClassificationEngine.defaultCategories }
            BudgetAndForecastEngine.evaluateMonth(
                monthKey = monthKey,
                transactions = txs,
                budgets = budgets,
                categories = effectiveCategories,
                savingsGoal = goal
            )
        }
    }

    suspend fun ensureInitialized(monthKey: String) {
        categoryDao.insertIgnore(ClassificationEngine.defaultCategories)
        val defaultBudgets = BudgetAndForecastEngine.defaultLimitByCategoryFen.map { (catId, limit) ->
            BudgetEntity(monthKey = monthKey, categoryId = catId, monthlyLimitFen = limit)
        }
        budgetDao.insertIgnore(defaultBudgets)
        savingsGoalDao.insertIgnore(
            SavingsGoalEntity(
                monthKey = monthKey,
                title = "每月储蓄挑战目标",
                targetFen = 100000L,
                livingAllowanceExpectedFen = 180000L,
                partTimeExpectedFen = 50000L
            )
        )
    }

    suspend fun loadCompetitionDemoIntoQueue(monthKey: String) {
        ensureInitialized(monthKey)
        importCsvIntoReviewQueue(
            csvContent = DemoStatementFactory.demoWeChatCsv,
            sourceLabel = "微信演示账单",
            preferredChannel = PaymentChannel.WECHAT_PAY
        )
        importCsvIntoReviewQueue(
            csvContent = DemoStatementFactory.demoAlipayCsv,
            sourceLabel = "支付宝演示账单",
            preferredChannel = PaymentChannel.ALIPAY
        )
    }

    suspend fun loadCompetitionDemoCommittedBaseline(monthKey: String) {
        ensureInitialized(monthKey)
        val existing = txDao.getForMonth(monthKey)
        if (existing.isEmpty()) {
            val wxRecords = StatementImporter.parseCsvStatement(
                DemoStatementFactory.demoWeChatCsv,
                PaymentChannel.WECHAT_PAY
            )
            val prefs = merchantPrefDao.getAll()
            val committed = wxRecords.map { rec ->
                val cls = ClassificationEngine.classify(rec.merchant, rec.rawDescription, rec.direction, prefs)
                TransactionEntity(
                    externalRefId = rec.externalRefId,
                    occurredAtMillis = rec.occurredAtMillis,
                    monthKey = monthKey,
                    amountFen = rec.amountFen,
                    direction = rec.direction,
                    channel = rec.channel,
                    fundingSource = rec.fundingSource,
                    sourceType = rec.sourceType,
                    merchant = rec.merchant,
                    rawDescription = rec.rawDescription,
                    categoryId = cls.categoryId,
                    classificationSource = cls.source,
                    confidenceScore = cls.confidence,
                    reviewStatus = ReviewStatus.USER_CONFIRMED,
                    note = "演示基线账单"
                )
            }
            txDao.insertAll(committed)
        }
        // Stage Alipay statement so user still experiences duplicate + refund reconciliation
        importCsvIntoReviewQueue(
            csvContent = DemoStatementFactory.demoAlipayCsv,
            sourceLabel = "支付宝周对账单（含重复与退款样本）",
            preferredChannel = PaymentChannel.ALIPAY
        )
    }

    suspend fun importCsvIntoReviewQueue(
        csvContent: String,
        sourceLabel: String,
        preferredChannel: PaymentChannel? = null
    ): Result<ImportBatchEntity> {
        val records = StatementImporter.parseCsvStatement(csvContent, preferredChannel)
        if (records.isEmpty()) {
            return Result.failure(IllegalArgumentException("未在文件中识别到有效的微信/支付宝交易行"))
        }
        val channel = preferredChannel ?: records.first().channel
        val existingTxs = txDao.getAll()
        val existingPending = importDao.getPendingCandidates()
        val prefs = merchantPrefDao.getAll()

        val initialBatchId = importDao.insertBatch(
            ImportBatchEntity(
                channel = channel,
                sourceLabel = sourceLabel,
                totalCount = records.size,
                cleanCount = 0,
                discrepancyCount = 0
            )
        )
        val candidates = StatementImporter.buildStagingCandidates(
            batchId = initialBatchId,
            records = records,
            existingTransactions = existingTxs,
            preferences = prefs,
            existingPendingCandidates = existingPending
        )
        val cleanCount = candidates.count { it.discrepancyType == DiscrepancyType.NONE }
        val discrepancyCount = candidates.size - cleanCount
        importDao.insertCandidates(candidates)
        importDao.updateBatchCounts(initialBatchId, cleanCount, discrepancyCount)

        val finalBatch = ImportBatchEntity(
            id = initialBatchId,
            channel = channel,
            sourceLabel = sourceLabel,
            totalCount = candidates.size,
            cleanCount = cleanCount,
            discrepancyCount = discrepancyCount
        )
        return Result.success(finalBatch)
    }

    suspend fun stagePaymentNotification(
        packageName: String,
        title: String,
        text: String,
        occurredAtMillis: Long = System.currentTimeMillis()
    ): Result<ImportCandidateEntity> {
        val rec = StatementImporter.parsePaymentNotification(packageName, title, text, occurredAtMillis)
            ?: return Result.failure(IllegalArgumentException("未能从通知中解析出有效的支付金额或渠道"))

        ensureInitialized(MoneyFormatter.monthKeyFromMillis(rec.occurredAtMillis))

        val candidates = StatementImporter.buildStagingCandidates(
            batchId = 0L,
            records = listOf(rec),
            existingTransactions = txDao.getAll(),
            preferences = merchantPrefDao.getAll(),
            existingPendingCandidates = importDao.getPendingCandidates()
        )
        val stagedCandidate = candidates.first()
        val isClean = stagedCandidate.discrepancyType == DiscrepancyType.NONE
        val batchId = importDao.insertBatch(
            ImportBatchEntity(
                channel = rec.channel,
                sourceLabel = "支付通知实时捕获",
                totalCount = 1,
                cleanCount = if (isClean) 1 else 0,
                discrepancyCount = if (isClean) 0 else 1
            )
        )
        val finalCandidate = stagedCandidate.copy(batchId = batchId)
        val newId = importDao.insertCandidate(finalCandidate)
        return Result.success(finalCandidate.copy(id = newId))
    }

    suspend fun stageSimulatedNotification(
        packageName: String,
        title: String,
        text: String
    ): Result<ImportCandidateEntity> = stagePaymentNotification(packageName, title, text)

    suspend fun confirmCandidateToLedger(
        candidateId: Long,
        selectedCategoryId: String,
        selectedFundingSource: FundingSource,
        rememberMerchantPreference: Boolean,
        note: String
    ) {
        val c = importDao.getCandidateById(candidateId) ?: return
        if (c.resolutionStatus != CandidateResolution.PENDING) return

        txDao.insert(
            TransactionEntity(
                externalRefId = c.externalRefId,
                occurredAtMillis = c.occurredAtMillis,
                monthKey = c.monthKey,
                amountFen = c.amountFen,
                direction = c.direction,
                channel = c.channel,
                fundingSource = selectedFundingSource,
                sourceType = c.sourceType,
                merchant = c.merchant,
                rawDescription = c.rawDescription,
                categoryId = selectedCategoryId,
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1.0f,
                reviewStatus = ReviewStatus.USER_CONFIRMED,
                note = note.ifBlank { c.rawDescription }
            )
        )
        importDao.updateResolution(candidateId, CandidateResolution.CONFIRMED_ADDED)

        if (rememberMerchantPreference && c.merchant.isNotBlank()) {
            merchantPrefDao.upsert(
                MerchantPreferenceEntity(
                    merchantKey = c.merchant.trim(),
                    categoryId = selectedCategoryId
                )
            )
        }
    }

    suspend fun confirmAllCleanCandidates() {
        val pending = importDao.getPendingCandidates()
        val clean = pending.filter { it.discrepancyType == DiscrepancyType.NONE }
        for (c in clean) {
            confirmCandidateToLedger(
                candidateId = c.id,
                selectedCategoryId = c.suggestedCategoryId,
                selectedFundingSource = c.fundingSource,
                rememberMerchantPreference = false,
                note = ""
            )
        }
    }

    suspend fun resolveCandidateAsSkipOrDismiss(candidateId: Long, resolution: CandidateResolution) {
        importDao.updateResolution(candidateId, resolution)
    }

    suspend fun updateCandidateSuggestion(
        candidateId: Long,
        categoryId: String,
        confidence: Float,
        source: ClassificationSource,
        note: String
    ) {
        importDao.updateCandidateSuggestion(candidateId, categoryId, confidence, source, note)
    }

    suspend fun addManualTransaction(
        amountFen: Long,
        direction: TransactionDirection,
        channel: PaymentChannel,
        fundingSource: FundingSource,
        categoryId: String,
        merchant: String,
        note: String,
        occurredAtMillis: Long = System.currentTimeMillis()
    ) {
        val monthKey = MoneyFormatter.monthKeyFromMillis(occurredAtMillis)
        ensureInitialized(monthKey)
        txDao.insert(
            TransactionEntity(
                externalRefId = "MANUAL_${occurredAtMillis}_$amountFen",
                occurredAtMillis = occurredAtMillis,
                monthKey = monthKey,
                amountFen = amountFen,
                direction = direction,
                channel = channel,
                fundingSource = fundingSource,
                sourceType = TransactionSourceType.MANUAL_ENTRY,
                merchant = merchant.ifBlank { "手动记账" },
                rawDescription = note,
                categoryId = categoryId,
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1.0f,
                reviewStatus = ReviewStatus.USER_CONFIRMED,
                note = note
            )
        )
    }

    suspend fun updateTransactionCategory(txId: Long, categoryId: String, rememberMerchant: Boolean) {
        val tx = txDao.getById(txId) ?: return
        txDao.updateCategory(txId, categoryId, ClassificationSource.USER_CONFIRMED)
        if (rememberMerchant && tx.merchant.isNotBlank()) {
            merchantPrefDao.upsert(
                MerchantPreferenceEntity(
                    merchantKey = tx.merchant.trim(),
                    categoryId = categoryId
                )
            )
        }
    }

    suspend fun deleteTransaction(txId: Long) {
        txDao.deleteById(txId)
    }

    suspend fun updateBudgetLimit(monthKey: String, categoryId: String, newLimitFen: Long) {
        ensureInitialized(monthKey)
        budgetDao.upsert(
            BudgetEntity(
                monthKey = monthKey,
                categoryId = categoryId,
                monthlyLimitFen = newLimitFen.coerceAtLeast(0L)
            )
        )
    }

    suspend fun updateSavingsGoal(
        monthKey: String,
        targetFen: Long,
        livingAllowanceExpectedFen: Long,
        partTimeExpectedFen: Long
    ) {
        ensureInitialized(monthKey)
        savingsGoalDao.upsert(
            SavingsGoalEntity(
                monthKey = monthKey,
                title = "每月储蓄挑战目标",
                targetFen = targetFen.coerceAtLeast(0L),
                livingAllowanceExpectedFen = livingAllowanceExpectedFen.coerceAtLeast(0L),
                partTimeExpectedFen = partTimeExpectedFen.coerceAtLeast(0L)
            )
        )
    }

    suspend fun getMonthlySummarySnapshot(monthKey: String): MonthlyFinancialSummary {
        ensureInitialized(monthKey)
        val txs = txDao.getForMonth(monthKey)
        val budgets = budgetDao.getForMonth(monthKey)
        val categories = categoryDao.getAll().ifEmpty { ClassificationEngine.defaultCategories }
        val goal = savingsGoalDao.getForMonth(monthKey)
        val dayOfMonth = inferReferenceDayOfMonth(monthKey, txs)
        return BudgetAndForecastEngine.evaluateMonth(
            monthKey = monthKey,
            transactions = txs,
            budgets = budgets,
            categories = categories,
            savingsGoal = goal,
            currentDayOfMonth = dayOfMonth
        )
    }

    suspend fun getTransactionsSnapshot(monthKey: String): List<TransactionEntity> {
        return txDao.getForMonth(monthKey)
    }

    suspend fun getCategoriesSnapshot(): List<CategoryEntity> {
        return categoryDao.getAll().ifEmpty { ClassificationEngine.defaultCategories }
    }

    suspend fun getSavingsGoalSnapshot(monthKey: String): SavingsGoalEntity? {
        return savingsGoalDao.getForMonth(monthKey)
    }

    suspend fun logToolCall(
        sessionId: String,
        toolName: String,
        argumentsJson: String,
        resultSummary: String,
        requiresConfirmation: Boolean,
        confirmedByUser: Boolean
    ) {
        toolLogDao.insert(
            AgentToolLogEntity(
                sessionId = sessionId,
                toolName = toolName,
                argumentsJson = argumentsJson,
                resultSummary = resultSummary,
                requiresConfirmation = requiresConfirmation,
                confirmedByUser = confirmedByUser
            )
        )
    }

    private fun inferReferenceDayOfMonth(monthKey: String, txs: List<TransactionEntity>): Int {
        val currentMonthKey = MoneyFormatter.monthKeyFromMillis(System.currentTimeMillis())
        if (monthKey == currentMonthKey) {
            return java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH).coerceIn(1, 30)
        }
        val latest = txs.maxOfOrNull { it.occurredAtMillis } ?: return 17
        val dayFmt = SimpleDateFormat("d", Locale.CHINA)
        return dayFmt.format(java.util.Date(latest)).toIntOrNull()?.coerceIn(1, 30) ?: 17
    }
}
