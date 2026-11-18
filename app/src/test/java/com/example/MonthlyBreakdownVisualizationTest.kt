package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.domain.finance.CategoryBudgetStatus
import com.example.domain.finance.MonthlyFinancialSummary
import com.example.domain.finance.OverspendRiskLevel
import com.example.ui.components.MonthlyCategoryBreakdownCard
import com.example.ui.theme.EasySpendTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MonthlyBreakdownVisualizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleSummary(): MonthlyFinancialSummary {
        val statuses = listOf(
            CategoryBudgetStatus("food", "饮食餐饮", true, 65000L, 42000L, 23000L, 0.65f, 61000L, OverspendRiskLevel.SAFE),
            CategoryBudgetStatus("study", "学习资料", true, 25000L, 18000L, 7000L, 0.72f, 22000L, OverspendRiskLevel.SAFE),
            CategoryBudgetStatus("entertainment", "娱乐休闲", false, 20000L, 24000L, -4000L, 1.20f, 32000L, OverspendRiskLevel.OVER_BUDGET),
            CategoryBudgetStatus("shopping", "数码服饰", false, 20000L, 16000L, 4000L, 0.80f, 21000L, OverspendRiskLevel.WARNING)
        )
        return MonthlyFinancialSummary(
            monthKey = "2026-11",
            livingAllowanceIncomeFen = 180000L,
            partTimeIncomeFen = 50000L,
            totalIncomeFen = 230000L,
            grossExpenseFen = 100000L,
            refundTotalFen = 0L,
            netExpenseFen = 100000L,
            essentialSpentFen = 60000L,
            flexibleSpentFen = 40000L,
            currentCashBalanceFen = 130000L,
            savingsTargetFen = 100000L,
            projectedEndOfMonthExpenseFen = 136000L,
            projectedMonthEndSavingsFen = 94000L,
            isSavingsTargetFeasible = false,
            savingsFeasibilityNote = "test note",
            categoryStatuses = statuses,
            suggestedAdjustments = emptyList()
        )
    }

    @Test
    fun `donut and bar chart modes switch cleanly and support tap selection and group filtering`() {
        composeTestRule.setContent {
            EasySpendTheme(darkTheme = false) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    MonthlyCategoryBreakdownCard(
                        monthKey = "2026-11",
                        summary = sampleSummary()
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Donut mode active initially
        composeTestRule.onNodeWithTag("monthly_breakdown_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("donut_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("essential_flexible_ratio_bar").assertIsDisplayed()

        // Tap food legend row -> detail callout appears below chart
        composeTestRule.onNodeWithTag("donut_legend_food").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_selected_detail").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("饮食餐饮 · 占当前视图 42%（必要支出）").assertIsDisplayed()

        // Switch to custom Canvas horizontal bar comparison chart
        composeTestRule.onNodeWithTag("chart_mode_bar").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bar_chart_container").performScrollTo().assertIsDisplayed()

        // Tap entertainment over-budget bar row
        composeTestRule.onNodeWithTag("bar_row_entertainment").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_selected_detail").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("已花 ¥240.00 / 预算 ¥200.00 · 已超预算 ¥40.00").assertIsDisplayed()

        // Filter to essential only -> food visible, entertainment absent
        composeTestRule.onNodeWithTag("chart_filter_essential").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bar_row_food").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("bar_row_entertainment").assertDoesNotExist()

        // Filter to flexible only -> entertainment visible, food absent
        composeTestRule.onNodeWithTag("chart_filter_flexible").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bar_row_entertainment").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("bar_row_food").assertDoesNotExist()
    }

    @Test
    fun `empty spending renders gracefully in dark theme without division errors`() {
        val emptySummary = sampleSummary().copy(
            grossExpenseFen = 0L,
            netExpenseFen = 0L,
            essentialSpentFen = 0L,
            flexibleSpentFen = 0L,
            categoryStatuses = listOf(
                CategoryBudgetStatus("food", "饮食餐饮", true, 65000L, 0L, 65000L, 0f, 0L, OverspendRiskLevel.SAFE)
            )
        )

        composeTestRule.setContent {
            EasySpendTheme(darkTheme = true) {
                MonthlyCategoryBreakdownCard(
                    monthKey = "2026-11",
                    summary = emptySummary
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("monthly_breakdown_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("donut_canvas").assertIsDisplayed()
    }
}
