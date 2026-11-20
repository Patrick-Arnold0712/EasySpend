package com.example.data.repository

import com.example.data.local.EasySpendDatabase
import com.example.data.model.AgentToolLogEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.CandidateResolution
import com.example.data.model.CategoryEntity
import com.example.data.model.CategoryGroup
import com.example.data.model.ClassificationSource
import com.example.data.model.DiscrepancyType
import com.example.data.model.FundVault
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
import com.example.domain.finance.ParsedRawRecord
import com.example.domain.finance.StatementImporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

data class AutoImportSummary(
    val totalParsed: Int,
    val importedCount: Int,
    val duplicateSkippedCount: Int
)

sealed interface NotificationStageOutcome {
    data class Staged(val candidate: ImportCandidateEntity) : NotificationStageOutcome
    data object AlreadyRecorded : NotificationStageOutcome
    data class Unparseable(val reason: String) : NotificationStageOutcome
}

class EasySpendRepository(private val db: EasySpendDatabase) {

    private val categoryDao = db.categoryDao()
    private val txDao = db.transactionDao()
    private val importDao = db.importDao()
    private val budgetDao = db.budgetDao()
    private val savingsGoalDao = db.savingsGoalDao()
    private val merchantPrefDao = db.merchantPrefDao()
    private val toolLogDao = db.toolLogDao()

    val categoriesFlow: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val allTransactionsFlow: Flow<List<TransactionEntity>> = txDao.observeAll()
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
            val allCategories = categoryDao.getAll()
            val committed = wxRecords.map { rec ->
                val cls = ClassificationEngine.classify(
                    merchant = rec.merchant,
                    description = rec.rawDescription,
                    direction = rec.direction,
                    preferences = prefs,
                    amountFen = rec.amountFen,
                    customCategories = allCategories,
                    occurredAtMillis = rec.occurredAtMillis,
                    channel = rec.channel,
                    note = rec.note,
                    historicalTransactions = existing,
                    locationLabel = rec.locationLabel
                )
                TransactionEntity(
                    externalRefId = rec.externalRefId,
                    occurredAtMillis = rec.occurredAtMillis,
                    monthKey = monthKey,
                    amountFen = rec.amountFen,
                    direction = rec.direction,
                    channel = rec.channel,
                    fundingSource = rec.fundingSource,
                    fundVault = rec.fundVault,
                    sourceType = rec.sourceType,
                    merchant = rec.merchant,
                    rawDescription = rec.rawDescription,
                    categoryId = cls.categoryId,
                    classificationSource = cls.source,
                    confidenceScore = cls.confidence,
                    reviewStatus = ReviewStatus.USER_CONFIRMED,
                    note = "演示基线账单",
                    locationLabel = rec.locationLabel
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
        return stageParsedRecords(records, sourceLabel, preferredChannel)
    }

    suspend fun importXlsxIntoReviewQueue(
        xlsxBytes: ByteArray,
        sourceLabel: String,
        preferredChannel: PaymentChannel? = null
    ): Result<ImportBatchEntity> {
        val records = StatementImporter.parseXlsxStatement(xlsxBytes, preferredChannel)
        if (records.isEmpty()) {
            return Result.failure(IllegalArgumentException("未在 Excel (.xlsx) 账单中识别到有效的交易记录"))
        }
        return stageParsedRecords(records, sourceLabel, preferredChannel)
    }

    /**
     * Exports all transactions of the current account into a UTF-8 BOM CSV string.
     */
    suspend fun exportAllTransactionsCsv(): String {
        val allTxs = txDao.getAll()
        val categories = categoryDao.getAll().ifEmpty { ClassificationEngine.defaultCategories }
        return StatementImporter.exportTransactionsToCsv(allTxs, categories)
    }

    /**
     * One-click import & automatic smart classification straight into the formal ledger.
     * Supports EasySpend exported CSV, WeChat CSV/XLSX, and Alipay CSV while skipping duplicates.
     */
    suspend fun importAndAutoClassifyCsv(
        csvContent: String,
        sourceLabel: String,
        preferredChannel: PaymentChannel? = null
    ): Result<AutoImportSummary> {
        val records = StatementImporter.parseCsvStatement(csvContent, preferredChannel)
        if (records.isEmpty()) {
            return Result.failure(IllegalArgumentException("未在文件中识别到有效的收支记录"))
        }
        return commitAutoClassifiedRecords(records, sourceLabel, preferredChannel)
    }

    suspend fun importAndAutoClassifyXlsx(
        xlsxBytes: ByteArray,
        sourceLabel: String,
        preferredChannel: PaymentChannel? = null
    ): Result<AutoImportSummary> {
        val records = StatementImporter.parseXlsxStatement(xlsxBytes, preferredChannel)
        if (records.isEmpty()) {
            return Result.failure(IllegalArgumentException("未在 Excel (.xlsx) 文件中识别到有效的收支记录"))
        }
        return commitAutoClassifiedRecords(records, sourceLabel, preferredChannel)
    }

    private suspend fun commitAutoClassifiedRecords(
        records: List<ParsedRawRecord>,
        sourceLabel: String,
        preferredChannel: PaymentChannel?
    ): Result<AutoImportSummary> {
        val months = records.map { MoneyFormatter.monthKeyFromMillis(it.occurredAtMillis) }.distinct()
        months.forEach { ensureInitialized(it) }

        val existingTxs = txDao.getAll()
        val prefs = merchantPrefDao.getAll()
        val allCategories = categoryDao.getAll().ifEmpty { ClassificationEngine.defaultCategories }

        val candidates = StatementImporter.buildStagingCandidates(
            batchId = 0L,
            records = records,
            existingTransactions = existingTxs,
            preferences = prefs,
            existingPendingCandidates = emptyList(),
            customCategories = allCategories
        )

        var importedCount = 0
        var duplicateCount = 0
        val toInsert = mutableListOf<TransactionEntity>()

        candidates.forEachIndexed { idx, candidate ->
            val rec = records[idx]
            if (candidate.discrepancyType == DiscrepancyType.POTENTIAL_DUPLICATE) {
                duplicateCount++
            } else {
                toInsert.add(
                    TransactionEntity(
                        externalRefId = candidate.externalRefId,
                        occurredAtMillis = candidate.occurredAtMillis,
                        monthKey = candidate.monthKey,
                        amountFen = candidate.amountFen,
                        direction = candidate.direction,
                        channel = candidate.channel,
                        fundingSource = candidate.fundingSource,
                        fundVault = candidate.fundVault,
                        sourceType = candidate.sourceType,
                        merchant = candidate.merchant,
                        rawDescription = candidate.rawDescription,
                        categoryId = candidate.suggestedCategoryId,
                        classificationSource = candidate.classificationSource,
                        confidenceScore = candidate.confidenceScore,
                        reviewStatus = ReviewStatus.AUTO_RULE_CLEAN,
                        note = rec.note.ifBlank { candidate.rawDescription },
                        locationLabel = candidate.locationLabel
                    )
                )
                importedCount++
            }
        }

        if (toInsert.isNotEmpty()) {
            txDao.insertAll(toInsert)
        }
        importDao.insertBatch(
            ImportBatchEntity(
                channel = preferredChannel ?: records.first().channel,
                sourceLabel = sourceLabel,
                totalCount = records.size,
                cleanCount = importedCount,
                discrepancyCount = duplicateCount
            )
        )
        return Result.success(
            AutoImportSummary(
                totalParsed = records.size,
                importedCount = importedCount,
                duplicateSkippedCount = duplicateCount
            )
        )
    }

    private suspend fun stageParsedRecords(
        records: List<ParsedRawRecord>,
        sourceLabel: String,
        preferredChannel: PaymentChannel?
    ): Result<ImportBatchEntity> {
        ensureInitialized(MoneyFormatter.monthKeyFromMillis(records.first().occurredAtMillis))
        val channel = preferredChannel ?: records.first().channel
        val existingTxs = txDao.getAll()
        val existingPending = importDao.getPendingCandidates()
        val prefs = merchantPrefDao.getAll()
        val allCategories = categoryDao.getAll()

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
            existingPendingCandidates = existingPending,
            customCategories = allCategories
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

    suspend fun stagePaymentNotificationDetailed(
        packageName: String,
        title: String,
        text: String,
        occurredAtMillis: Long = System.currentTimeMillis(),
        skipIfAlreadyRecorded: Boolean = false,
        sourceLabel: String = "支付通知实时捕获",
        locationLabel: String = ""
    ): NotificationStageOutcome {
        val rec = StatementImporter.parsePaymentNotification(
            packageName = packageName,
            title = title,
            text = text,
            nowMillis = occurredAtMillis,
            locationLabel = locationLabel
        )
            ?: return NotificationStageOutcome.Unparseable("未能从通知中解析出有效的支付金额或渠道")

        ensureInitialized(MoneyFormatter.monthKeyFromMillis(rec.occurredAtMillis))

        val existingTransactions = txDao.getAll()
        val allCandidates = importDao.getAllCandidates()
        val existingPendingCandidates = allCandidates.filter { it.resolutionStatus == CandidateResolution.PENDING }

        if (skipIfAlreadyRecorded) {
            val normMerchant = normalizeNotificationMatchText(rec.merchant)
            val normDesc = normalizeNotificationMatchText(rec.rawDescription)
            val matchedTx = existingTransactions.any { tx ->
                tx.channel == rec.channel &&
                    tx.amountFen == rec.amountFen &&
                    tx.direction == rec.direction &&
                    kotlin.math.abs(tx.occurredAtMillis - rec.occurredAtMillis) <= 90_000L &&
                    (
                        normalizeNotificationMatchText(tx.merchant) == normMerchant ||
                            normalizeNotificationMatchText(tx.rawDescription) == normDesc
                        )
            }
            val matchedCandidate = allCandidates.any { cand ->
                cand.channel == rec.channel &&
                    cand.amountFen == rec.amountFen &&
                    cand.direction == rec.direction &&
                    kotlin.math.abs(cand.occurredAtMillis - rec.occurredAtMillis) <= 90_000L &&
                    (
                        normalizeNotificationMatchText(cand.merchant) == normMerchant ||
                            normalizeNotificationMatchText(cand.rawDescription) == normDesc
                        )
            }
            if (matchedTx || matchedCandidate) {
                return NotificationStageOutcome.AlreadyRecorded
            }
        }

        val candidates = StatementImporter.buildStagingCandidates(
            batchId = 0L,
            records = listOf(rec),
            existingTransactions = existingTransactions,
            preferences = merchantPrefDao.getAll(),
            existingPendingCandidates = existingPendingCandidates,
            customCategories = categoryDao.getAll()
        )
        val stagedCandidate = candidates.first()
        val isClean = stagedCandidate.discrepancyType == DiscrepancyType.NONE
        val batchId = importDao.insertBatch(
            ImportBatchEntity(
                channel = rec.channel,
                sourceLabel = sourceLabel,
                totalCount = 1,
                cleanCount = if (isClean) 1 else 0,
                discrepancyCount = if (isClean) 0 else 1
            )
        )
        val finalCandidate = stagedCandidate.copy(batchId = batchId)
        val newId = importDao.insertCandidate(finalCandidate)
        return NotificationStageOutcome.Staged(finalCandidate.copy(id = newId))
    }

    private fun normalizeNotificationMatchText(raw: String): String =
        raw.lowercase(Locale.ROOT).replace(Regex("""\s+"""), "").trim()

    suspend fun stagePaymentNotification(
        packageName: String,
        title: String,
        text: String,
        occurredAtMillis: Long = System.currentTimeMillis(),
        locationLabel: String = ""
    ): Result<ImportCandidateEntity> {
        return when (
            val outcome = stagePaymentNotificationDetailed(
                packageName = packageName,
                title = title,
                text = text,
                occurredAtMillis = occurredAtMillis,
                skipIfAlreadyRecorded = false,
                locationLabel = locationLabel
            )
        ) {
            is NotificationStageOutcome.Staged -> Result.success(outcome.candidate)
            NotificationStageOutcome.AlreadyRecorded -> Result.failure(IllegalStateException("该笔通知记录已存在"))
            is NotificationStageOutcome.Unparseable -> Result.failure(IllegalArgumentException(outcome.reason))
        }
    }

    suspend fun stageSimulatedNotification(
        packageName: String,
        title: String,
        text: String,
        locationLabel: String = ""
    ): Result<ImportCandidateEntity> = stagePaymentNotification(
        packageName = packageName,
        title = title,
        text = text,
        locationLabel = locationLabel
    )

    suspend fun confirmCandidateToLedger(
        candidateId: Long,
        selectedCategoryId: String,
        selectedFundingSource: FundingSource,
        rememberMerchantPreference: Boolean,
        note: String,
        selectedFundVault: FundVault? = null
    ): Long? {
        val c = importDao.getCandidateById(candidateId) ?: return null
        if (c.resolutionStatus != CandidateResolution.PENDING) return null

        val insertedId = txDao.insert(
            TransactionEntity(
                externalRefId = c.externalRefId,
                occurredAtMillis = c.occurredAtMillis,
                monthKey = c.monthKey,
                amountFen = c.amountFen,
                direction = c.direction,
                channel = c.channel,
                fundingSource = selectedFundingSource,
                fundVault = selectedFundVault ?: c.fundVault,
                sourceType = c.sourceType,
                merchant = c.merchant,
                rawDescription = c.rawDescription,
                categoryId = selectedCategoryId,
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1.0f,
                reviewStatus = ReviewStatus.USER_CONFIRMED,
                note = note.ifBlank { c.rawDescription },
                locationLabel = c.locationLabel
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
        return insertedId
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
        occurredAtMillis: Long = System.currentTimeMillis(),
        fundVault: FundVault = FundVault.POCKET,
        locationLabel: String = ""
    ): Long {
        val monthKey = MoneyFormatter.monthKeyFromMillis(occurredAtMillis)
        ensureInitialized(monthKey)
        return txDao.insert(
            TransactionEntity(
                externalRefId = "MANUAL_${occurredAtMillis}_$amountFen",
                occurredAtMillis = occurredAtMillis,
                monthKey = monthKey,
                amountFen = amountFen,
                direction = direction,
                channel = channel,
                fundingSource = fundingSource,
                fundVault = fundVault,
                sourceType = TransactionSourceType.MANUAL_ENTRY,
                merchant = merchant.ifBlank { "手动记账" },
                rawDescription = note,
                categoryId = categoryId,
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1.0f,
                reviewStatus = ReviewStatus.USER_CONFIRMED,
                note = note,
                locationLabel = locationLabel
            )
        )
    }

    suspend fun updateTransactionCategory(
        txId: Long,
        categoryId: String,
        rememberMerchant: Boolean
    ): TransactionEntity? {
        val tx = txDao.getById(txId) ?: return null
        return updateTransactionCategoryAndVault(
            txId = txId,
            categoryId = categoryId,
            fundVault = tx.fundVault,
            rememberMerchant = rememberMerchant
        )
    }

    suspend fun updateTransactionCategoryAndVault(
        txId: Long,
        categoryId: String,
        fundVault: FundVault,
        rememberMerchant: Boolean
    ): TransactionEntity? {
        val tx = txDao.getById(txId) ?: return null
        txDao.updateCategoryAndVault(txId, categoryId, fundVault, ClassificationSource.USER_CONFIRMED)
        if (rememberMerchant && tx.merchant.isNotBlank()) {
            merchantPrefDao.upsert(
                MerchantPreferenceEntity(
                    merchantKey = tx.merchant.trim(),
                    categoryId = categoryId
                )
            )
        }
        return tx
    }

    suspend fun deleteTransaction(txId: Long): TransactionEntity? {
        val existing = txDao.getById(txId) ?: return null
        txDao.deleteById(txId)
        return existing
    }

    suspend fun restoreDeletedTransaction(tx: TransactionEntity): Long {
        return txDao.insert(tx)
    }

    suspend fun revertTransactionCategory(
        txId: Long,
        previousCategoryId: String,
        previousSource: ClassificationSource
    ) {
        txDao.updateCategory(txId, previousCategoryId, previousSource)
    }

    suspend fun revertTransactionCategoryAndVault(
        txId: Long,
        previousCategoryId: String,
        previousVault: FundVault,
        previousSource: ClassificationSource
    ) {
        txDao.updateCategoryAndVault(txId, previousCategoryId, previousVault, previousSource)
    }

    suspend fun revertConfirmedCandidate(txId: Long, candidateId: Long) {
        txDao.deleteById(txId)
        importDao.updateResolution(candidateId, CandidateResolution.PENDING)
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

    suspend fun createCustomCategory(
        name: String,
        group: CategoryGroup,
        keywords: String,
        monthlyLimitFen: Long?,
        monthKey: String
    ): Result<CategoryEntity> {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 16) {
            return Result.failure(IllegalArgumentException("分类名称长度需为 1~16 个字符"))
        }
        ensureInitialized(monthKey)
        val existing = categoryDao.getAll()
        if (existing.any { it.name.equals(cleanName, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("已存在同名分类「$cleanName」"))
        }
        val nextSortOrder = (existing.maxOfOrNull { it.sortOrder } ?: 21) + 1
        val newCategory = CategoryEntity(
            id = "custom_${UUID.randomUUID().toString().replace("-", "").take(8)}",
            name = cleanName,
            group = group,
            sortOrder = nextSortOrder,
            isCustom = true,
            keywords = keywords.trim()
        )
        categoryDao.upsert(newCategory)
        if (group == CategoryGroup.ESSENTIAL_EXPENSE || group == CategoryGroup.FLEXIBLE_EXPENSE) {
            val limit = (monthlyLimitFen ?: 15000L).coerceAtLeast(0L)
            budgetDao.upsert(BudgetEntity(monthKey = monthKey, categoryId = newCategory.id, monthlyLimitFen = limit))
        }
        return Result.success(newCategory)
    }

    suspend fun updateCustomCategory(
        categoryId: String,
        name: String,
        group: CategoryGroup,
        keywords: String
    ): Result<CategoryEntity> {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 16) {
            return Result.failure(IllegalArgumentException("分类名称长度需为 1~16 个字符"))
        }
        val existing = categoryDao.getById(categoryId)
            ?: return Result.failure(IllegalArgumentException("未找到该分类"))
        val duplicate = categoryDao.getAll().firstOrNull {
            it.id != categoryId && it.name.equals(cleanName, ignoreCase = true)
        }
        if (duplicate != null) {
            return Result.failure(IllegalArgumentException("已存在同名分类「$cleanName」"))
        }
        val updated = existing.copy(
            name = cleanName,
            group = if (existing.isCustom) group else existing.group,
            keywords = keywords.trim()
        )
        categoryDao.upsert(updated)
        return Result.success(updated)
    }

    suspend fun deleteCustomCategory(categoryId: String): Result<Unit> {
        val existing = categoryDao.getById(categoryId)
            ?: return Result.failure(IllegalArgumentException("未找到该分类"))
        if (!existing.isCustom) {
            return Result.failure(IllegalArgumentException("系统内置分类不可删除"))
        }
        val fallbackId = when (existing.group) {
            CategoryGroup.INCOME -> "other_income"
            CategoryGroup.NEUTRAL -> "transfer"
            else -> "other_expense"
        }
        txDao.reassignCategory(categoryId, fallbackId)
        importDao.reassignCategory(categoryId, fallbackId)
        budgetDao.deleteByCategory(categoryId)
        merchantPrefDao.deleteByCategory(categoryId)
        categoryDao.deleteById(categoryId)
        return Result.success(Unit)
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
