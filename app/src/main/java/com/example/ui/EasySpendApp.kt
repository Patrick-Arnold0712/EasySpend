package com.example.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.AutoStories
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.local.ThemeMode
import com.example.data.model.FundVault
import com.example.domain.finance.ReportPeriod
import com.example.service.NotificationKeepAliveService
import com.example.service.PaymentNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.ui.motion.CircularThemeRevealContainer
import com.example.ui.motion.EasySpendMotion
import com.example.ui.motion.EasySpendSplashOverlay
import com.example.ui.motion.rememberCircularThemeRevealController
import com.example.ui.screens.AccountManagementDialog
import com.example.ui.screens.AgentScreen
import com.example.ui.screens.ApiConfigDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BudgetAndInsightsScreen
import com.example.ui.screens.CategoryManagementDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FinancialDigestDialog
import com.example.ui.screens.OnboardingWizardScreen
import com.example.ui.screens.OneTapPermissionDialog
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

private fun routeIndex(route: String?): Int = when (route) {
    ROUTE_DASHBOARD -> 0
    ROUTE_LEDGER -> 1
    ROUTE_BUDGET -> 2
    ROUTE_AGENT -> 3
    else -> 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EasySpendApp(
    viewModel: EasySpendViewModel = viewModel(),
    showSplashOnLaunch: Boolean = true
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (state.settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    var showSplash by remember(showSplashOnLaunch) { mutableStateOf(showSplashOnLaunch) }
    val themeRevealController = rememberCircularThemeRevealController()
    var themeButtonOrigin by remember { mutableStateOf<Offset?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    DisposableEffect(lifecycleOwner, context, state.settings.backgroundKeepAliveEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                NotificationKeepAliveService.sync(
                    context = context,
                    keepAliveEnabled = state.settings.backgroundKeepAliveEnabled
                )
                CoroutineScope(Dispatchers.IO).launch {
                    PaymentNotificationListenerService.ensureConnectedAndBackfillSilently(
                        context = context,
                        reason = "打开应用自动补录"
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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

    CircularThemeRevealContainer(controller = themeRevealController) {
        EasySpendTheme(darkTheme = darkTheme) {
            var showApiConfigDialog by remember { mutableStateOf(false) }
            var showPermissionWizard by remember { mutableStateOf(false) }
            val needsOnboarding = state.accountState.isLoaded &&
                state.accountState.currentAccount != null &&
                (!state.settings.onboardingCompleted || state.isReplayingOnboarding)

            Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = Triple(state.accountState.isLoaded, state.accountState.currentAccount, needsOnboarding),
                contentKey = { (loaded, acc, onboarding) -> "${loaded}_${acc?.id ?: "none"}_$onboarding" },
                transitionSpec = {
                    (fadeIn(tween(360, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.97f, animationSpec = tween(360, easing = FastOutSlowInEasing))) togetherWith
                        fadeOut(tween(240, easing = FastOutSlowInEasing))
                },
                label = "appSessionTransition"
            ) { (isLoaded, currentAccount, showOnboarding) ->
                if (!isLoaded) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!showSplash) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (currentAccount == null) {
                    AuthScreen(
                        existingAccounts = state.accountState.accounts,
                        authErrorMessage = state.authErrorMessage,
                        onClearError = { viewModel.clearAuthError() },
                        onLogin = { user, pwd -> viewModel.loginAccount(user, pwd) },
                        onRegister = { user, pwd, nick -> viewModel.registerAccount(user, pwd, nick) }
                    )
                } else if (showOnboarding) {
                    OnboardingWizardScreen(
                        state = state,
                        isReplay = state.isReplayingOnboarding,
                        onOpenApiConfig = { showApiConfigDialog = true },
                        onOpenPermissionWizard = { showPermissionWizard = true },
                        onComplete = { allowance, savings, partTime, recordNow, loadDemo ->
                            viewModel.completeOnboarding(
                                monthlyAllowanceYuan = allowance,
                                savingsTargetYuan = savings,
                                expectedPartTimeYuan = partTime,
                                recordAllowanceIncomeNow = recordNow,
                                loadDemoData = loadDemo
                            )
                        },
                        onSkip = { viewModel.skipOnboarding() }
                    )
                } else {
                    val navController = rememberNavController()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DASHBOARD

                    fun navigateToTab(targetRoute: String) {
                        if (currentRoute == targetRoute) return
                        if (targetRoute == ROUTE_DASHBOARD) {
                            val popped = navController.popBackStack(ROUTE_DASHBOARD, inclusive = false)
                            if (!popped) {
                                navController.navigate(ROUTE_DASHBOARD) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        } else {
                            navController.navigate(targetRoute) {
                                popUpTo(ROUTE_DASHBOARD) {
                                    inclusive = false
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }

                    val snackbarHostState = remember { SnackbarHostState() }
                    var showSettings by remember { mutableStateOf(false) }
                    var showCategoryManager by remember { mutableStateOf(false) }
                    var showAccountManager by remember { mutableStateOf(false) }
                    var digestDialogRequest by remember { mutableStateOf<Pair<ReportPeriod, Long>?>(null) }
                    var ledgerInitialTab by remember { mutableIntStateOf(0) }
                    var ledgerInitialVaultFilter by remember { mutableStateOf<FundVault?>(null) }
                    var agentPrefillPrompt by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(state.bannerMessage) {
            val msg = state.bannerMessage
            if (!msg.isNullOrBlank()) {
                val showUndo = state.lastUndoLabel != null &&
                    (msg.contains("已删除") || msg.contains("分类已更新") || msg.contains("已核对确认") || msg.contains("已手动记入"))
                val res = snackbarHostState.showSnackbar(
                    message = msg,
                    actionLabel = if (showUndo) "撤销" else null,
                    duration = SnackbarDuration.Short
                )
                viewModel.clearBanner()
                if (res == SnackbarResult.ActionPerformed) {
                    viewModel.undoLastLedgerAction()
                }
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
                                AnimatedContent(
                                    targetState = currentRoute,
                                    transitionSpec = {
                                        val dir = if (routeIndex(targetState) >= routeIndex(initialState)) 1 else -1
                                        (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                                            slideInHorizontally(EasySpendMotion.SpatialOffsetSpring) { it / 5 * dir }) togetherWith
                                            (fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                                                slideOutHorizontally(tween(160)) { -it / 5 * dir })
                                    },
                                    label = "topBarTitleTransition"
                                ) { route ->
                                    val label = when (route) {
                                        ROUTE_LEDGER -> "记账与核对"
                                        ROUTE_BUDGET -> "预算与分析"
                                        ROUTE_AGENT -> "财务助手"
                                        else -> "易支道 · ${currentAccount.displayLabel}"
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.openOnboardingReplay()
                                    },
                                    modifier = Modifier.testTag("replay_onboarding_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoStories,
                                        contentDescription = stringResource(R.string.cd_onboarding_guide)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showAccountManager = true
                                    },
                                    modifier = Modifier.testTag("account_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = stringResource(R.string.cd_account)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        themeRevealController.toggleWithReveal(themeButtonOrigin) {
                                            viewModel.toggleDarkMode(darkTheme)
                                        }
                                    },
                                    modifier = Modifier
                                        .onGloballyPositioned { coords ->
                                            val pos = coords.positionInRoot()
                                            themeButtonOrigin = Offset(
                                                x = pos.x + coords.size.width / 2f,
                                                y = pos.y + coords.size.height / 2f
                                            )
                                        }
                                        .testTag("theme_toggle_button")
                                ) {
                                    AnimatedContent(
                                        targetState = darkTheme,
                                        transitionSpec = {
                                            (fadeIn(tween(260)) + scaleIn(initialScale = 0.65f, animationSpec = EasySpendMotion.FluidSpring)) togetherWith
                                                (fadeOut(tween(160)) + scaleOut(targetScale = 0.65f, animationSpec = tween(160)))
                                        },
                                        label = "themeIconMorph"
                                    ) { isDark ->
                                        Icon(
                                            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                            contentDescription = stringResource(R.string.cd_toggle_theme)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showSettings = true
                                    },
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
                        HorizontalDivider(thickness = 0.75.dp, color = semantic.hairline)
                    }
                },
                bottomBar = {
                    if (!useNavRail) {
                        Column {
                            HorizontalDivider(thickness = 0.75.dp, color = semantic.hairline)
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp
                            ) {
                                destinations.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    val iconScale by animateFloatAsState(
                                        targetValue = if (selected) 1.14f else 1f,
                                        animationSpec = EasySpendMotion.BouncySpring,
                                        label = "navIconScale_${dest.route}"
                                    )
                                    val iconLift by animateDpAsState(
                                        targetValue = if (selected) (-1.5).dp else 0.dp,
                                        animationSpec = EasySpendMotion.FluidDpSpring,
                                        label = "navIconLift_${dest.route}"
                                    )
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            if (!selected) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                navigateToTab(dest.route)
                                            }
                                        },
                                        icon = {
                                            val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
                                            val scaleMod = Modifier.graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                                translationY = iconLift.toPx()
                                            }
                                            if (dest.route == ROUTE_LEDGER && pendingCount > 0) {
                                                BadgedBox(badge = { Badge { Text(pendingCount.toString()) } }) {
                                                    Icon(icon, contentDescription = null, modifier = scaleMod)
                                                }
                                            } else {
                                                Icon(icon, contentDescription = null, modifier = scaleMod)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(dest.labelRes),
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        },
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
                                        if (!selected) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            navigateToTab(dest.route)
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
                        enterTransition = {
                            val dir = if (routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)) 1 else -1
                            slideInHorizontally(EasySpendMotion.SpatialOffsetSpring) { it / 3 * dir } +
                                fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.955f, animationSpec = tween(280, easing = FastOutSlowInEasing))
                        },
                        exitTransition = {
                            val dir = if (routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)) 1 else -1
                            slideOutHorizontally(EasySpendMotion.SpatialOffsetSpring) { -it / 4 * dir } +
                                fadeOut(tween(190, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 0.965f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                        },
                        popEnterTransition = {
                            val dir = if (routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)) 1 else -1
                            slideInHorizontally(EasySpendMotion.SpatialOffsetSpring) { it / 3 * dir } +
                                fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.955f, animationSpec = tween(280, easing = FastOutSlowInEasing))
                        },
                        popExitTransition = {
                            val dir = if (routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)) 1 else -1
                            slideOutHorizontally(EasySpendMotion.SpatialOffsetSpring) { -it / 4 * dir } +
                                fadeOut(tween(190, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 0.965f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                state = state,
                                onLoadDemoQueue = {
                                    viewModel.loadDemoIntoReviewQueue()
                                    ledgerInitialTab = 0
                                    ledgerInitialVaultFilter = null
                                    navigateToTab(ROUTE_LEDGER)
                                },
                                onLoadDemoBaseline = { viewModel.loadDemoCommittedBaseline() },
                                onAdd300Essential = { viewModel.addDemo300EssentialExpense() },
                                onNavigateToImportQueue = {
                                    ledgerInitialTab = 0
                                    ledgerInitialVaultFilter = null
                                    navigateToTab(ROUTE_LEDGER)
                                },
                                onNavigateToBudget = {
                                    navigateToTab(ROUTE_BUDGET)
                                },
                                onNavigateToAgentWithPrompt = { prompt ->
                                    agentPrefillPrompt = prompt
                                    navigateToTab(ROUTE_AGENT)
                                },
                                onDismissBeginnerGuide = { viewModel.dismissBeginnerGuide() },
                                onOpenDigest = { period, anchor ->
                                    digestDialogRequest = period to anchor
                                },
                                onToggleDetailedMode = { enabled ->
                                    viewModel.setDetailedModeEnabled(enabled)
                                },
                                onNavigateToLedgerVault = { vault ->
                                    ledgerInitialTab = 1
                                    ledgerInitialVaultFilter = vault
                                    navigateToTab(ROUTE_LEDGER)
                                }
                            )
                        }
                        composable(ROUTE_LEDGER) {
                            TransactionsAndImportScreen(
                                state = state,
                                initialTab = ledgerInitialTab,
                                initialVaultFilter = ledgerInitialVaultFilter,
                                onImportCsvUri = { uri, channel -> viewModel.importCsvFromUri(context, uri, channel) },
                                onLoadDemoQueue = { viewModel.loadDemoIntoReviewQueue() },
                                onSimulateNotification = { isWeChat -> viewModel.simulatePaymentNotification(isWeChat) },
                                onRunAiBatchClassify = { viewModel.runAiBatchClassification() },
                                onConfirmAllClean = { viewModel.confirmAllCleanCandidates() },
                                onConfirmCandidate = { id, cat, fs, rem, note ->
                                    viewModel.confirmCandidate(id, cat, fs, rem, note)
                                },
                                onConfirmCandidateWithVault = { id, cat, fs, rem, note, vault ->
                                    viewModel.confirmCandidate(id, cat, fs, rem, note, vault)
                                },
                                onSkipOrDismissCandidate = { id, res -> viewModel.skipOrDismissCandidate(id, res) },
                                onAddManualTransaction = { amt, dir, ch, fs, cat, m, note ->
                                    viewModel.addManualTransaction(amt, dir, ch, fs, cat, m, note)
                                },
                                onAddManualTransactionDetailed = { amt, dir, ch, fs, cat, m, note, vault, locLabel ->
                                    viewModel.addManualTransaction(amt, dir, ch, fs, cat, m, note, vault, locLabel)
                                },
                                onUpdateTxCategory = { id, cat, rem -> viewModel.updateTransactionCategory(id, cat, rem) },
                                onUpdateTxCategoryAndVault = { id, cat, vault, rem ->
                                    viewModel.updateTransactionCategoryAndVault(id, cat, vault, rem)
                                },
                                onDeleteTx = { id -> viewModel.deleteTransaction(id) },
                                onUndoLastLedgerAction = { viewModel.undoLastLedgerAction() },
                                onExportRecordsUri = { uri -> viewModel.exportLedgerToUri(context, uri) },
                                onAutoImportRecordsUri = { uri -> viewModel.importAndAutoClassifyFromUri(context, uri) },
                                onOpenCategoryManager = { showCategoryManager = true },
                                onToggleBackgroundKeepAlive = { enabled ->
                                    viewModel.setBackgroundKeepAliveEnabled(enabled)
                                },
                                onOpenPermissionWizard = { showPermissionWizard = true },
                                onToggleLocationClassification = { enabled ->
                                    viewModel.setLocationClassificationEnabled(enabled)
                                }
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
                                onOpenCategoryManager = { showCategoryManager = true },
                                onOpenDigest = { period, anchor ->
                                    digestDialogRequest = period to anchor
                                }
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
                    onSelectThemeMode = { mode ->
                        themeRevealController.toggleWithReveal(themeButtonOrigin) {
                            viewModel.setThemeMode(mode)
                        }
                    },
                    onToggleAiCloud = { viewModel.setAiCloudAnalysisEnabled(it) },
                    onSetDailyReviewTime = { h, m -> viewModel.setDailyReviewTime(h, m) },
                    onSaveApiConfig = { p, url, key, model -> viewModel.saveApiConfig(p, url, key, model) },
                    onTestApiConnection = { p, url, key, model -> viewModel.testApiConnection(p, url, key, model) },
                    onClearApiTestFeedback = { viewModel.clearApiTestFeedback() },
                    onToggleBeginnerGuide = { show -> viewModel.setHideBeginnerGuide(!show) },
                    onReplayOnboarding = {
                        showSettings = false
                        viewModel.openOnboardingReplay()
                    },
                    onOpenCategoryManager = {
                        showSettings = false
                        showCategoryManager = true
                    },
                    onOpenAccountManager = {
                        showSettings = false
                        showAccountManager = true
                    },
                    onOpenPermissionWizard = {
                        showSettings = false
                        showPermissionWizard = true
                    },
                    onToggleDetailedMode = { enabled ->
                        viewModel.setDetailedModeEnabled(enabled)
                    },
                    onToggleLocationClassification = { enabled ->
                        viewModel.setLocationClassificationEnabled(enabled)
                    },
                    onDismiss = { showSettings = false }
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

            digestDialogRequest?.let { (period, anchorMillis) ->
                FinancialDigestDialog(
                    initialPeriod = period,
                    initialAnchorMillis = anchorMillis,
                    allTransactions = state.allTransactions,
                    categories = state.categories,
                    savingsTargetFen = state.summary?.savingsTargetFen ?: 0L,
                    onAskAgent = { prompt ->
                        agentPrefillPrompt = prompt
                        navigateToTab(ROUTE_AGENT)
                    },
                    onDismiss = { digestDialogRequest = null }
                )
            }
        }
                }
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

            if (showPermissionWizard) {
                OneTapPermissionDialog(
                    keepAliveEnabled = state.settings.backgroundKeepAliveEnabled,
                    onAllPermissionsReady = {
                        viewModel.showBanner("核心权限已全部就绪，自动抓账与同步定位分类已开启。")
                    },
                    onDismiss = { showPermissionWizard = false }
                )
            }

            if (showSplash) {
                EasySpendSplashOverlay(
                    isReady = state.accountState.isLoaded,
                    onFinished = { showSplash = false }
                )
            }
        }
        }
    }
}
