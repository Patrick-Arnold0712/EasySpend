package com.example

import com.example.data.model.ClassificationSource
import com.example.data.model.DiscrepancyType
import com.example.data.model.FundingSource
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
import com.example.domain.finance.StatementImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EasySpendEngineTest {

    @Test
    fun `money formatter parses and formats fen accurately`() {
        assertEquals(180000L, MoneyFormatter.parseYuanToFen("¥1,800.00"))
        assertEquals(1850L, MoneyFormatter.parseYuanToFen("18.50元"))
        assertEquals("¥1,800.00", MoneyFormatter.formatFen(180000L))
    }

    @Test
    fun `multi-purpose merchant triggers uncertainty unless preference authorized`() {
        val noPref = ClassificationEngine.classify(
            merchant = "全家便利店",
            description = "消费",
            direction = TransactionDirection.EXPENSE,
            preferences = emptyList()
        )
        assertTrue(noPref.needsReview)

        val withPref = ClassificationEngine.classify(
            merchant = "全家便利店",
            description = "消费",
            direction = TransactionDirection.EXPENSE,
            preferences = listOf(MerchantPreferenceEntity("全家便利店", "food"))
        )
        assertFalse(withPref.needsReview)
        assertEquals("food", withPref.categoryId)
        assertEquals(ClassificationSource.MERCHANT_PREFERENCE, withPref.source)
    }

    @Test
    fun `statement importer parses WeChat and Alipay CSV and notifications`() {
        val wx = StatementImporter.parseCsvStatement(DemoStatementFactory.demoWeChatCsv, PaymentChannel.WECHAT_PAY)
        val ali = StatementImporter.parseCsvStatement(DemoStatementFactory.demoAlipayCsv, PaymentChannel.ALIPAY)
        assertEquals(9, wx.size)
        assertEquals(6, ali.size)
        assertEquals(180000L, wx.first().amountFen)
        assertEquals(TransactionDirection.INCOME, wx.first().direction)

        val notif = StatementImporter.parsePaymentNotification(
            packageName = "com.tencent.mm",
            title = "微信支付",
            text = "已向 瑞幸咖啡 支付人民币19.90元"
        )
        assertNotNull(notif)
        assertEquals(1990L, notif?.amountFen)
        assertEquals(PaymentChannel.WECHAT_PAY, notif?.channel)
    }

    @Test
    fun `budget engine separates income sources and detects infeasible savings without cutting essentials`() {
        val txs = listOf(
            TransactionEntity(
                id = 1,
                externalRefId = "INC1",
                occurredAtMillis = 1L,
                monthKey = "2026-11",
                amountFen = 180000L,
                direction = TransactionDirection.INCOME,
                channel = PaymentChannel.WECHAT_PAY,
                fundingSource = FundingSource.LIVING_ALLOWANCE,
                sourceType = TransactionSourceType.MANUAL_ENTRY,
                merchant = "家庭生活费",
                rawDescription = "",
                categoryId = "allowance",
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1f,
                reviewStatus = ReviewStatus.USER_CONFIRMED
            ),
            TransactionEntity(
                id = 2,
                externalRefId = "EXP1",
                occurredAtMillis = 2L,
                monthKey = "2026-11",
                amountFen = 95000L,
                direction = TransactionDirection.EXPENSE,
                channel = PaymentChannel.WECHAT_PAY,
                fundingSource = FundingSource.LIVING_ALLOWANCE,
                sourceType = TransactionSourceType.MANUAL_ENTRY,
                merchant = "教材与餐饮",
                rawDescription = "",
                categoryId = "food",
                classificationSource = ClassificationSource.USER_CONFIRMED,
                confidenceScore = 1f,
                reviewStatus = ReviewStatus.USER_CONFIRMED
            )
        )
        val goal = SavingsGoalEntity(
            monthKey = "2026-11",
            title = "挑战目标",
            targetFen = 100000L,
            livingAllowanceExpectedFen = 180000L,
            partTimeExpectedFen = 50000L // Expected, NOT arrived
        )

        val summary = BudgetAndForecastEngine.evaluateMonth(
            monthKey = "2026-11",
            transactions = txs,
            budgets = emptyList(),
            categories = ClassificationEngine.defaultCategories,
            savingsGoal = goal,
            currentDayOfMonth = 17
        )

        // Only arrived income (1800) counts; expected part-time (500) is NOT counted as arrived cash
        assertEquals(180000L, summary.totalIncomeFen)
        assertEquals(0L, summary.partTimeIncomeFen)
        assertFalse(summary.isSavingsTargetFeasible)
        assertTrue(summary.savingsFeasibilityNote.contains("绝不压缩"))
    }
}
