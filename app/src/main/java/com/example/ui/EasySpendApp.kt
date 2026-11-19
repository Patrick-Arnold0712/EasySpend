package com.example.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.local.ThemeMode
import com.example.ui.screens.AccountManagementDialog
import com.example.ui.screens.AgentScreen
import com.example.ui.screens.ApiConfigDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BudgetAndInsightsScreen
import com.example.ui.screens.CategoryManagementDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsAndAuditDialog
import com.example.ui.screens.TransactionsAndImportScreen
import com.example.ui.theme.EasySpendTheme
import com.example.ui.theme.semanticColors

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_LEDGER = "ledger"
const val ROUTE_BUDGET = "budget"
const val ROUTE_AGENT = "agent"

data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val destinations = listOf(
    NavDestination(ROUTE_DASHBOARD, R.string.nav_dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
    NavDestination(ROUTE_LEDGER, R.string.nav_ledger, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "nav_ledger"),
    NavDestination(ROUTE_BUDGET, R.string.nav_budget, Icons.Filled.PieChart, Icons.Outlined.PieChart, "nav_budget"),
    NavDestination(ROUTE_AGENT, R.string.nav_agent, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_agent")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasySpendApp(viewModel: EasySpendViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (state.settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val context = LocalContext.current
    DisposableEffect(darkTheme) {
        val activity = context as? ComponentActivity
        activity?.enableEdgeToEdge(
            statusBarStyle = if (darkTheme) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            },
            navigationBarStyle = if (darkTheme) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            }
        )
        onDispose {}
    }

    EasySpendTheme(darkTheme = darkTheme) {
        if (!state.accountState.isLoaded) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@EasySpendTheme
        }

        val currentAccount = state.accountState.currentAccount
        if (currentAccount == null) {
            AuthScreen(
                existingAccounts = state.accountState.accounts,
                authErrorMessage = state.authErrorMessage,
                onClearError = { viewModel.clearAuthError() },
                onLogin = { user, pwd -> viewModel.loginAccount(user, pwd) },
                onRegister = { user, pwd, nick -> viewModel.registerAccount(user, pwd, nick) }
            )
            return@EasySpendTheme
        }

        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DASHBOARD

        val snackbarHostState = remember { SnackbarHostState() }
        var showSettings by remember { mutableStateOf(false) }
        var showApiConfigDialog by remember { mutableStateOf(false) }
        var showCategoryManager by remember { mutableStateOf(false) }
        var showAccountManager by remember { mutableStateOf(false) }
        var ledgerInitialTab by remember { mutableIntStateOf(0) }
        var agentPrefillPrompt by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(state.bannerMessage) {
            val msg = state.bannerMessage
            if (!msg.isNullOrBlank()) {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearBanner()
            }
        }

        val pendingCount = state.pendingCandidates.size
        val semantic = MaterialTheme.semanticColors

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 700.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                Text(
                                    text = when (currentRoute) {
                                        ROUTE_LEDGER -> "记账与核对"
                                        ROUTE_BUDGET -> "预算与分析"
                                        ROUTE_AGENT -> "财务助手"
                                        else -> "易支道 · ${currentAccount.displayLabel}"
                                    },
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1
                                )
                            },
                            actions = {
                                IconButton(
                                    onClick = { showAccountManager = true },
                                    modifier = Modifier.testTag("account_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = stringResource(R.string.cd_account)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.toggleDarkMode(darkTheme) },
                                    modifier = Modifier.testTag("theme_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                        contentDescription = stringResource(R.string.cd_toggle_theme)
                                    )
                                }
                                IconButton(
                                    onClick = { showSettings = true },
                                    modifier = Modifier.testTag("settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = stringResource(R.string.cd_settings)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)
                    }
                },
                bottomBar = {
                    if (!useNavRail) {
                        Column {
                            HorizontalDivider(thickness = 0.5.dp, color = semantic.hairline)
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp
                            ) {
                                destinations.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(dest.route) {
                                                popUpTo(ROUTE_DASHBOARD) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
                                            if (dest.route == ROUTE_LEDGER && pendingCount > 0) {
                                                BadgedBox(badge = { Badge { Text(pendingCount.toString()) } }) {
                                                    Icon(icon, contentDescription = null)
                                                }
                                            } else {
                                                Icon(icon, contentDescription = null)
                                            }
                                        },
                                        label = { Text(stringResource(dest.labelRes), maxLines = 1) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            destinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(ROUTE_DASHBOARD) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
                                        if (dest.route == ROUTE_LEDGER && pendingCount > 0) {
                                            BadgedBox(badge = { Badge { Text(pendingCount.toString()) } }) {
                                                Icon(icon, contentDescription = null)
                                            }
                                        } else {
                                            Icon(icon, contentDescription = null)
                                        }
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                state = state,
                                onLoadDemoQueue = {
                                    viewModel.loadDemoIntoReviewQueue()
                                    ledgerInitialTab = 0
                                    navController.navigate(ROUTE_LEDGER) { launchSingleTop = true }
                                },
                                onLoadDemoBaseline = { viewModel.loadDemoCommittedBaseline() },
                                onAdd300Essential = { viewModel.addDemo300EssentialExpense() },
                                onNavigateToImportQueue = {
                                    ledgerInitialTab = 0
                                    navController.navigate(ROUTE_LEDGER) { launchSingleTop = true }
                                },
                                onNavigateToBudget = {
                                    navController.navigate(ROUTE_BUDGET) { launchSingleTop = true }
                                },
                                onNavigateToAgentWithPrompt = { prompt ->
                                    agentPrefillPrompt = prompt
                                    navController.navigate(ROUTE_AGENT) { launchSingleTop = true }
                                },
                                onDismissBeginnerGuide = { viewModel.dismissBeginnerGuide() }
                            )
                        }
                        composable(ROUTE_LEDGER) {
                            TransactionsAndImportScreen(
                                state = state,
                                initialTab = ledgerInitialTab,
                                onImportCsvUri = { uri, channel -> viewModel.importCsvFromUri(context, uri, channel) },
                                onLoadDemoQueue = { viewModel.loadDemoIntoReviewQueue() },
                                onSimulateNotification = { isWeChat -> viewModel.simulatePaymentNotification(isWeChat) },
                                onRunAiBatchClassify = { viewModel.runAiBatchClassification() },
                                onConfirmAllClean = { viewModel.confirmAllCleanCandidates() },
                                onConfirmCandidate = { id, cat, fs, rem, note ->
                                    viewModel.confirmCandidate(id, cat, fs, rem, note)
                                },
                                onSkipOrDismissCandidate = { id, res -> viewModel.skipOrDismissCandidate(id, res) },
                                onAddManualTransaction = { amt, dir, ch, fs, cat, m, note ->
                                    viewModel.addManualTransaction(amt, dir, ch, fs, cat, m, note)
                                },
                                onUpdateTxCategory = { id, cat, rem -> viewModel.updateTransactionCategory(id, cat, rem) },
                                onDeleteTx = { id -> viewModel.deleteTransaction(id) },
                                onOpenCategoryManager = { showCategoryManager = true }
                            )
                        }
                        composable(ROUTE_BUDGET) {
                            BudgetAndInsightsScreen(
                                state = state,
                                onUpdateBudgetLimit = { catId, limitStr -> viewModel.updateBudgetLimit(catId, limitStr) },
                                onApplyProposal = { catId, fen -> viewModel.applyDeterministicProposal(catId, fen) },
                                onUpdateSavingsGoal = { t, a, p -> viewModel.updateSavingsGoal(t, a, p) },
                                onGenerateAiInsights = { viewModel.generateAiInsights() },
                                onOpenApiSettings = { showApiConfigDialog = true },
                                onOpenCategoryManager = { showCategoryManager = true }
                            )
                        }
                        composable(ROUTE_AGENT) {
                            AgentScreen(
                                state = state,
                                prefillPrompt = agentPrefillPrompt,
                                onClearPrefill = { agentPrefillPrompt = null },
                                onSendMessage = { viewModel.sendAgentMessage(it) },
                                onConfirmProposal = { msgId, prop -> viewModel.confirmAgentProposal(msgId, prop) },
                                onRejectProposal = { msgId, prop -> viewModel.rejectAgentProposal(msgId, prop) },
                                onOpenApiSettings = { showApiConfigDialog = true }
                            )
                        }
                    }
                }
            }

            if (showSettings) {
                SettingsAndAuditDialog(
                    state = state,
                    onSelectThemeMode = { viewModel.setThemeMode(it) },
                    onToggleAiCloud = { viewModel.setAiCloudAnalysisEnabled(it) },
                    onSetDailyReviewTime = { h, m -> viewModel.setDailyReviewTime(h, m) },
                    onSaveApiConfig = { p, url, key, model -> viewModel.saveApiConfig(p, url, key, model) },
                    onTestApiConnection = { p, url, key, model -> viewModel.testApiConnection(p, url, key, model) },
                    onClearApiTestFeedback = { viewModel.clearApiTestFeedback() },
                    onToggleBeginnerGuide = { show -> viewModel.setHideBeginnerGuide(!show) },
                    onOpenCategoryManager = {
                        showSettings = false
                        showCategoryManager = true
                    },
                    onOpenAccountManager = {
                        showSettings = false
                        showAccountManager = true
                    },
                    onDismiss = { showSettings = false }
                )
            }

            if (showApiConfigDialog) {
                ApiConfigDialog(
                    state = state,
                    onSaveApiConfig = { p, url, key, model -> viewModel.saveApiConfig(p, url, key, model) },
                    onTestApiConnection = { p, url, key, model -> viewModel.testApiConnection(p, url, key, model) },
                    onClearFeedback = { viewModel.clearApiTestFeedback() },
                    onDismiss = { showApiConfigDialog = false }
                )
            }

            if (showCategoryManager) {
                CategoryManagementDialog(
                    categories = state.categories,
                    onCreateCategory = { name, group, keywords, budgetStr ->
                        viewModel.createCustomCategory(name, group, keywords, budgetStr)
                    },
                    onUpdateCategory = { id, name, group, keywords ->
                        viewModel.updateCustomCategory(id, name, group, keywords)
                    },
                    onDeleteCategory = { id ->
                        viewModel.deleteCustomCategory(id)
                    },
                    onDismiss = { showCategoryManager = false }
                )
            }

            if (showAccountManager) {
                AccountManagementDialog(
                    currentAccount = currentAccount,
                    allAccountsCount = state.accountState.accounts.size,
                    onUpdateNickname = { viewModel.updateAccountNickname(it) },
                    onChangePassword = { oldPwd, newPwd -> viewModel.changeAccountPassword(oldPwd, newPwd) },
                    onLogout = { viewModel.logoutAccount() },
                    onDismiss = { showAccountManager = false }
                )
            }
        }
    }
}
