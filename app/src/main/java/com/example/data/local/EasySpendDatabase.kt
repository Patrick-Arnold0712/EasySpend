package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.AgentToolLogEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.CandidateResolution
import com.example.data.model.CategoryEntity
import com.example.data.model.CategoryGroup
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

class RoomTypeConverters {
    @TypeConverter fun fromPaymentChannel(v: PaymentChannel): String = v.name
    @TypeConverter fun toPaymentChannel(v: String): PaymentChannel = PaymentChannel.valueOf(v)

    @TypeConverter fun fromDirection(v: TransactionDirection): String = v.name
    @TypeConverter fun toDirection(v: String): TransactionDirection = TransactionDirection.valueOf(v)

    @TypeConverter fun fromFundingSource(v: FundingSource): String = v.name
    @TypeConverter fun toFundingSource(v: String): FundingSource = FundingSource.valueOf(v)

    @TypeConverter fun fromSourceType(v: TransactionSourceType): String = v.name
    @TypeConverter fun toSourceType(v: String): TransactionSourceType = TransactionSourceType.valueOf(v)

    @TypeConverter fun fromClassificationSource(v: ClassificationSource): String = v.name
    @TypeConverter fun toClassificationSource(v: String): ClassificationSource = ClassificationSource.valueOf(v)

    @TypeConverter fun fromReviewStatus(v: ReviewStatus): String = v.name
    @TypeConverter fun toReviewStatus(v: String): ReviewStatus = ReviewStatus.valueOf(v)

    @TypeConverter fun fromDiscrepancyType(v: DiscrepancyType): String = v.name
    @TypeConverter fun toDiscrepancyType(v: String): DiscrepancyType = DiscrepancyType.valueOf(v)

    @TypeConverter fun fromCandidateResolution(v: CandidateResolution): String = v.name
    @TypeConverter fun toCandidateResolution(v: String): CandidateResolution = CandidateResolution.valueOf(v)

    @TypeConverter fun fromCategoryGroup(v: CategoryGroup): String = v.name
    @TypeConverter fun toCategoryGroup(v: String): CategoryGroup = CategoryGroup.valueOf(v)
}

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        ImportBatchEntity::class,
        ImportCandidateEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class,
        MerchantPreferenceEntity::class,
        AgentToolLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class EasySpendDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun importDao(): ImportDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun merchantPrefDao(): MerchantPrefDao
    abstract fun toolLogDao(): ToolLogDao

    companion object {
        @Volatile
        private var INSTANCE: EasySpendDatabase? = null

        fun getInstance(context: Context): EasySpendDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EasySpendDatabase::class.java,
                    "easyspend.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
