package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val group: CategoryGroup,
    val sortOrder: Int = 0,
    val isCustom: Boolean = false,
    val keywords: String = ""
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val externalRefId: String? = null,
    val occurredAtMillis: Long,
    val monthKey: String,
    val amountFen: Long,
    val direction: TransactionDirection,
    val channel: PaymentChannel,
    val fundingSource: FundingSource,
    val sourceType: TransactionSourceType,
    val merchant: String,
    val rawDescription: String = "",
    val categoryId: String,
    val classificationSource: ClassificationSource,
    val confidenceScore: Float = 1f,
    val reviewStatus: ReviewStatus,
    val note: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val fundVault: FundVault = FundVault.POCKET,
    val locationLabel: String = ""
)

@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val channel: PaymentChannel,
    val sourceLabel: String,
    val importedAtMillis: Long = System.currentTimeMillis(),
    val totalCount: Int,
    val cleanCount: Int,
    val discrepancyCount: Int
)

@Entity(tableName = "import_candidates")
data class ImportCandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val batchId: Long,
    val externalRefId: String,
    val occurredAtMillis: Long,
    val monthKey: String,
    val amountFen: Long,
    val direction: TransactionDirection,
    val channel: PaymentChannel,
    val fundingSource: FundingSource,
    val sourceType: TransactionSourceType,
    val merchant: String,
    val rawDescription: String,
    val suggestedCategoryId: String,
    val classificationSource: ClassificationSource,
    val confidenceScore: Float,
    val discrepancyType: DiscrepancyType,
    val matchedTransactionId: Long? = null,
    val discrepancyNote: String,
    val resolutionStatus: CandidateResolution = CandidateResolution.PENDING,
    val fundVault: FundVault = FundVault.POCKET,
    val locationLabel: String = ""
)

@Entity(tableName = "budgets", primaryKeys = ["monthKey", "categoryId"])
data class BudgetEntity(
    val monthKey: String,
    val categoryId: String,
    val monthlyLimitFen: Long
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey val monthKey: String,
    val title: String,
    val targetFen: Long,
    val livingAllowanceExpectedFen: Long,
    val partTimeExpectedFen: Long
)

@Entity(tableName = "merchant_preferences")
data class MerchantPreferenceEntity(
    @PrimaryKey val merchantKey: String,
    val categoryId: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_tool_logs")
data class AgentToolLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val toolName: String,
    val argumentsJson: String,
    val resultSummary: String,
    val requiresConfirmation: Boolean = false,
    val confirmedByUser: Boolean = false,
    val executedAtMillis: Long = System.currentTimeMillis()
)
