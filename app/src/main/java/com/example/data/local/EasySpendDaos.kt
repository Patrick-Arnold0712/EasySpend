package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AgentToolLogEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.CandidateResolution
import com.example.data.model.CategoryEntity
import com.example.data.model.ClassificationSource
import com.example.data.model.ImportBatchEntity
import com.example.data.model.ImportCandidateEntity
import com.example.data.model.MerchantPreferenceEntity
import com.example.data.model.SavingsGoalEntity
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CategoryEntity?

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE monthKey = :monthKey ORDER BY occurredAtMillis DESC, id DESC")
    fun observeForMonth(monthKey: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE monthKey = :monthKey ORDER BY occurredAtMillis DESC, id DESC")
    suspend fun getForMonth(monthKey: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY occurredAtMillis DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(txs: List<TransactionEntity>)

    @Query("UPDATE transactions SET categoryId = :categoryId, classificationSource = :source WHERE id = :txId")
    suspend fun updateCategory(txId: Long, categoryId: String, source: ClassificationSource)

    @Query("UPDATE transactions SET categoryId = :fallbackId WHERE categoryId = :oldId")
    suspend fun reassignCategory(oldId: String, fallbackId: String)

    @Query("DELETE FROM transactions WHERE id = :txId")
    suspend fun deleteById(txId: Long)
}

@Dao
interface ImportDao {
    @Query("SELECT * FROM import_candidates WHERE resolutionStatus = 'PENDING' ORDER BY occurredAtMillis DESC, id DESC")
    fun observePendingCandidates(): Flow<List<ImportCandidateEntity>>

    @Query("SELECT * FROM import_candidates WHERE resolutionStatus = 'PENDING' ORDER BY occurredAtMillis DESC, id DESC")
    suspend fun getPendingCandidates(): List<ImportCandidateEntity>

    @Query("SELECT * FROM import_candidates WHERE id = :id LIMIT 1")
    suspend fun getCandidateById(id: Long): ImportCandidateEntity?

    @Query("SELECT * FROM import_batches ORDER BY importedAtMillis DESC, id DESC")
    fun observeBatches(): Flow<List<ImportBatchEntity>>

    @Insert
    suspend fun insertBatch(batch: ImportBatchEntity): Long

    @Query("UPDATE import_batches SET cleanCount = :cleanCount, discrepancyCount = :discrepancyCount WHERE id = :batchId")
    suspend fun updateBatchCounts(batchId: Long, cleanCount: Int, discrepancyCount: Int)

    @Insert
    suspend fun insertCandidates(candidates: List<ImportCandidateEntity>)

    @Insert
    suspend fun insertCandidate(candidate: ImportCandidateEntity): Long

    @Query("UPDATE import_candidates SET resolutionStatus = :status WHERE id = :candidateId")
    suspend fun updateResolution(candidateId: Long, status: CandidateResolution)

    @Query(
        "UPDATE import_candidates SET suggestedCategoryId = :categoryId, confidenceScore = :confidence, " +
            "classificationSource = :source, discrepancyNote = :note WHERE id = :candidateId"
    )
    suspend fun updateCandidateSuggestion(
        candidateId: Long,
        categoryId: String,
        confidence: Float,
        source: ClassificationSource,
        note: String
    )

    @Query("UPDATE import_candidates SET suggestedCategoryId = :fallbackId WHERE suggestedCategoryId = :oldId")
    suspend fun reassignCategory(oldId: String, fallbackId: String)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey")
    fun observeForMonth(monthKey: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey")
    suspend fun getForMonth(monthKey: String): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<BudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: String)
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals WHERE monthKey = :monthKey LIMIT 1")
    fun observeForMonth(monthKey: String): Flow<SavingsGoalEntity?>

    @Query("SELECT * FROM savings_goals WHERE monthKey = :monthKey LIMIT 1")
    suspend fun getForMonth(monthKey: String): SavingsGoalEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(goal: SavingsGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: SavingsGoalEntity)
}

@Dao
interface MerchantPrefDao {
    @Query("SELECT * FROM merchant_preferences ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<MerchantPreferenceEntity>>

    @Query("SELECT * FROM merchant_preferences")
    suspend fun getAll(): List<MerchantPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: MerchantPreferenceEntity)

    @Query("DELETE FROM merchant_preferences WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: String)
}

@Dao
interface ToolLogDao {
    @Query("SELECT * FROM agent_tool_logs ORDER BY executedAtMillis DESC, id DESC LIMIT 100")
    fun observeRecent(): Flow<List<AgentToolLogEntity>>

    @Insert
    suspend fun insert(log: AgentToolLogEntity): Long
}
