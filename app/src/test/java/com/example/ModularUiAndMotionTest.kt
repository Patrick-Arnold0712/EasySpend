package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.local.UserSettings
import com.example.domain.finance.BudgetAndForecastEngine
import com.example.domain.finance.ClassificationEngine
import com.example.ui.EasySpendUiState
import com.example.ui.screens.BudgetAndInsightsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsAndAuditDialog
import com.example.ui.screens.TransactionsAndImportScreen
import com.example.ui.theme.EasySpendTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModularUiAndMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleState(detailedMode: Boolean = false): EasySpendUiState {
        val summary = BudgetAndForecastEngine.evaluateMonth(
            monthKey = "2026-11",
            transactions = emptyList(),
            budgets = emptyList(),
            categories = ClassificationEngine.defaultCategories,
            savingsGoal = null
        )
        return EasySpendUiState(
            settings = UserSettings(
                activeMonthKey = "2026-11",
                detailedModeEnabled = detailedMode
            ),
            categories = ClassificationEngine.defaultCategories,
            summary = summary
        )
    }

    @Test
    fun dashboardAnalyticsHubSwitchesSmoothlyAcrossStructureCalendarAndDigest() {
        var toggledDetailed = false
        composeRule.setContent {
            EasySpendTheme(darkTheme = false) {
                DashboardScreen(
                    state = sampleState(),
                    onLoadDemoQueue = {},
                    onLoadDemoBaseline = {},
                    onAdd300Essential = {},
                    onNavigateToImportQueue = {},
                    onNavigateToBudget = {},
                    onNavigateToAgentWithPrompt = {},
                    onToggleDetailedMode = { toggledDetailed = it }
                )
            }
        }

        // Scroll to modular analytics hub
        composeRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("spending_donut_card"))
        composeRule.onNodeWithTag("spending_donut_card").assertIsDisplayed()

        // Toggle detailed mode chip
        composeRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("dashboard_detailed_mode_toggle"))
        composeRule.onNodeWithTag("dashboard_detailed_mode_toggle").performClick()
        assertTrue(toggledDetailed)

        // Switch to Calendar module
        composeRule.onNodeWithTag("dashboard_module_calendar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("cashflow_calendar_card"))
        composeRule.onNodeWithTag("cashflow_calendar_card").assertIsDisplayed()

        // Switch to Digest module
        composeRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("dashboard_module_digest"))
        composeRule.onNodeWithTag("dashboard_module_digest").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_lazy_list")
            .performScrollToNode(hasTestTag("dashboard_open_digest_month"))
        composeRule.onNodeWithTag("dashboard_digest_entry_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_open_digest_month").assertIsDisplayed()
    }

    @Test
    fun budgetScreenModularHubSwitchesAcrossBudgetChartsAndAiDiagnostics() {
        composeRule.setContent {
            EasySpendTheme(darkTheme = false) {
                BudgetAndInsightsScreen(
                    state = sampleState(),
                    onUpdateBudgetLimit = { _, _ -> },
                    onApplyProposal = { _, _ -> },
                    onUpdateSavingsGoal = { _, _, _ -> },
                    onGenerateAiInsights = {}
                )
            }
        }

        // Default module 0: Category Budget
        composeRule.onNodeWithTag("budget_module_categories").assertIsDisplayed()
        composeRule.onNodeWithTag("budget_lazy_list")
            .performScrollToNode(hasTestTag("swiss_category_budget_table"))
        composeRule.onNodeWithTag("swiss_category_budget_table").assertIsDisplayed()

        // Switch to module 1: Trend & Breakdown Charts
        composeRule.onNodeWithTag("budget_lazy_list")
            .performScrollToNode(hasTestTag("budget_module_charts"))
        composeRule.onNodeWithTag("budget_module_charts").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("budget_lazy_list")
            .performScrollToNode(hasTestTag("cashflow_trend_card"))
        composeRule.onNodeWithTag("cashflow_trend_card").assertIsDisplayed()

        // Switch to module 2: AI Diagnostics
        composeRule.onNodeWithTag("budget_lazy_list")
            .performScrollToNode(hasTestTag("budget_module_ai"))
        composeRule.onNodeWithTag("budget_module_ai").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("budget_lazy_list")
            .performScrollToNode(hasTestTag("generate_ai_insights_button"))
        composeRule.onNodeWithTag("generate_ai_insights_button").assertIsDisplayed()
    }

    @Test
    fun ledgerToolboxAccordionAndFilterDrawerCollapseAndExpandCleanly() {
        composeRule.setContent {
            EasySpendTheme(darkTheme = false) {
                TransactionsAndImportScreen(
                    state = sampleState(detailedMode = true),
                    initialTab = 0,
                    onImportCsvUri = { _, _ -> },
                    onLoadDemoQueue = {},
                    onSimulateNotification = {},
                    onRunAiBatchClassify = {},
                    onConfirmAllClean = {},
                    onConfirmCandidate = { _, _, _, _, _ -> },
                    onSkipOrDismissCandidate = { _, _ -> },
                    onAddManualTransaction = { _, _, _, _, _, _, _ -> },
                    onUpdateTxCategory = { _, _, _ -> },
                    onDeleteTx = {}
                )
            }
        }

        // Toolbox starts expanded
        composeRule.onNodeWithTag("import_wechat_csv_button").assertIsDisplayed()
        // Collapse toolbox via accordion header
        composeRule.onNodeWithTag("toggle_import_toolbox_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("import_wechat_csv_button").assertDoesNotExist()
        // Expand toolbox again
        composeRule.onNodeWithTag("toggle_import_toolbox_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("import_wechat_csv_button").assertIsDisplayed()

        // Switch to Committed Ledger tab & collapse filter drawer
        composeRule.onNodeWithTag("tab_formal_ledger").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ledger_vault_filter_pocket").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_ledger_filters_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ledger_vault_filter_pocket").assertDoesNotExist()
    }

    @Test
    fun settingsControlCenterSwitchesSmoothlyAcrossAllFourModules() {
        composeRule.setContent {
            EasySpendTheme(darkTheme = false) {
                SettingsAndAuditDialog(
                    state = sampleState(),
                    onSelectThemeMode = {},
                    onToggleAiCloud = {},
                    onSetDailyReviewTime = { _, _ -> },
                    onSaveApiConfig = { _, _, _, _ -> },
                    onTestApiConnection = { _, _, _, _ -> },
                    onClearApiTestFeedback = {},
                    onDismiss = {}
                )
            }
        }

        // Module 0: Preferences
        composeRule.onNodeWithTag("detailed_mode_switch").assertIsDisplayed()

        // Module 1: Ledger & Permissions
        composeRule.onNodeWithTag("settings_module_ledger").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_one_tap_permissions_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("location_classification_switch").performScrollTo().assertIsDisplayed()

        // Module 2: AI Model Config
        composeRule.onNodeWithTag("settings_module_ai").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("privacy_ai_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("api_key_input").performScrollTo().assertIsDisplayed()

        // Module 3: Audit Logs
        composeRule.onNodeWithTag("settings_module_audit").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_module_audit").assertIsDisplayed()
    }
}
