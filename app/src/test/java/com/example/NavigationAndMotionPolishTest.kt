package com.example

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalAccountStore
import com.example.data.local.UserSettings
import com.example.data.local.UserSettingsDataStore
import com.example.domain.finance.MonthlyFinancialSummary
import com.example.ui.EasySpendApp
import com.example.ui.EasySpendUiState
import com.example.ui.EasySpendViewModel
import com.example.ui.motion.EasySpendSplashOverlay
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.EasySpendTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavigationAndMotionPolishTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun streamlinedHeroShowsBalanceNetIncomeExpenseAndSavingsProgressOnly() {
        val verboseNote = "按当前收入与消费速率，预计月底可结余 ¥1,671.85。"
        val summary = MonthlyFinancialSummary(
            monthKey = "2026-11",
            livingAllowanceIncomeFen = 180_000L,
            partTimeIncomeFen = 50_000L,
            totalIncomeFen = 230_000L,
            grossExpenseFen = 44_170L,
            refundTotalFen = 0L,
            netExpenseFen = 44_170L,
            essentialSpentFen = 30_000L,
            flexibleSpentFen = 14_170L,
            currentCashBalanceFen = 185_830L,
            savingsTargetFen = 100_000L,
            projectedEndOfMonthExpenseFen = 62_815L,
            projectedMonthEndSavingsFen = 167_185L,
            isSavingsTargetFeasible = true,
            savingsFeasibilityNote = verboseNote,
            categoryStatuses = emptyList(),
            suggestedAdjustments = emptyList()
        )

        composeTestRule.setContent {
            EasySpendTheme(darkTheme = false) {
                Box(modifier = Modifier.width(360.dp)) {
                    DashboardScreen(
                        state = EasySpendUiState(
                            settings = UserSettings(activeMonthKey = "2026-11"),
                            summary = summary
                        ),
                        onLoadDemoQueue = {},
                        onLoadDemoBaseline = {},
                        onAdd300Essential = {},
                        onNavigateToImportQueue = {},
                        onNavigateToBudget = {},
                        onNavigateToAgentWithPrompt = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hero_balance_text")
            .assertIsDisplayed()
            .assertTextEquals("¥1,858.30")
        composeTestRule.onNodeWithText("2026-11 本月结余").assertIsDisplayed()
        composeTestRule.onNodeWithText("净收入").assertIsDisplayed()
        composeTestRule.onNodeWithText("净支出").assertIsDisplayed()
        composeTestRule.onNodeWithText("¥2,300.00").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("¥441.70").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("存钱进度").assertIsDisplayed()

        // Removed cluttered metrics, verbose hero note, and inline beginner guide
        composeTestRule.onAllNodesWithText("家庭生活费").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("兼职已到账").assertCountEquals(0)
        composeTestRule.onAllNodesWithText(verboseNote).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("beginner_guide_card").assertCountEquals(0)
    }

    @Test
    fun newlyRegisteredAccountEntersPagedOnboardingAndCanReplayFromTopBar() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val accountStore = LocalAccountStore(app)
        runBlocking {
            accountStore.logout()
            accountStore.register("fresh_${System.nanoTime() % 1_000_000L}", "123456", "小新同学").getOrThrow()
        }
        val vm = EasySpendViewModel(app)

        composeTestRule.setContent {
            EasySpendApp(viewModel = vm, showSplashOnLaunch = false)
        }

        // Immediately lands on the dedicated paged onboarding wizard
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("onboarding_wizard_screen").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("认识易支道").assertIsDisplayed()

        // Page 0 -> Page 1 (Conversational fill-in-the-blank goals)
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_allowance_input").performTextClearance()
        composeTestRule.onNodeWithTag("onboarding_allowance_input").performTextInput("2200")
        composeTestRule.onNodeWithTag("onboarding_savings_input").performTextClearance()
        composeTestRule.onNodeWithTag("onboarding_savings_input").performTextInput("800")
        composeTestRule.onNodeWithTag("onboarding_live_plan_bubble")
            .performScrollTo()
            .assertIsDisplayed()

        // Page 1 -> Page 2 (Capture & setup)
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_open_notif_settings_button").assertIsDisplayed()

        // Finish onboarding -> enters Dashboard
        composeTestRule.onNodeWithTag("onboarding_finish_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("dashboard_lazy_list").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        // Replay onboarding anytime with one tap from the TopAppBar button
        composeTestRule.onNodeWithTag("replay_onboarding_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("onboarding_wizard_screen").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithTag("onboarding_skip_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("dashboard_lazy_list").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun bottomBarNavigationAlwaysReturnsToDashboardAndThemeToggleAnimatesCleanly() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val accountStore = LocalAccountStore(app)
        runBlocking {
            accountStore.logout()
            val acc = accountStore.register("nav_${System.nanoTime() % 1_000_000L}", "123456", "测试同学").getOrThrow()
            UserSettingsDataStore(app, acc.id).setOnboardingCompleted(true)
        }
        val vm = EasySpendViewModel(app)

        composeTestRule.setContent {
            EasySpendApp(viewModel = vm, showSplashOnLaunch = false)
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("dashboard_lazy_list").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        // Navigate Dashboard -> Budget -> Dashboard
        composeTestRule.onNodeWithTag("nav_budget").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("预算与分析").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_dashboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_lazy_list").assertIsDisplayed()

        // Navigate Dashboard -> Ledger -> Agent -> Dashboard
        composeTestRule.onNodeWithTag("nav_ledger").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tab_import_review").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_agent").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("财务助手").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_dashboard").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_lazy_list").assertIsDisplayed()

        // Toggle dark/light theme twice to exercise animated ColorScheme + ThemeTransitionVeil
        composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_lazy_list").assertIsDisplayed()
    }

    @Test
    fun splashOverlayDisplaysBrandWelcomeAndFadesOutWhenReady() {
        var finished by mutableStateOf(false)
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            EasySpendTheme(darkTheme = false) {
                if (!finished) {
                    EasySpendSplashOverlay(
                        isReady = true,
                        onFinished = { finished = true }
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(350L)
        composeTestRule.onNodeWithTag("easyspend_splash_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithText("易支道").assertIsDisplayed()
        composeTestRule.onNodeWithText("EASYSPEND").assertIsDisplayed()
        composeTestRule.onNodeWithText("每一笔收支，都清晰有道").assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(1_600L)
        composeTestRule.waitForIdle()
        assertTrue("Splash overlay should complete its exit transition when ready", finished)
    }
}
