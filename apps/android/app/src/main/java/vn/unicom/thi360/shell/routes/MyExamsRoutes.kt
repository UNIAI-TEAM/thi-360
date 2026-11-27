package vn.unicom.thi360.shell.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.feature.history.HistoryScreen
import vn.unicom.thi360.feature.myexams.MyExamsScreen
import vn.unicom.thi360.feature.myexams.MyExamsSessionMemory
import vn.unicom.thi360.feature.myexams.NavigationClickGuard
import vn.unicom.thi360.feature.myexams.resolveGreetingName
import vn.unicom.thi360.shell.Destination

internal val LocalSupportContactOverride = staticCompositionLocalOf<SupportContact?> { null }
internal val LocalMyExamsClockOverride = staticCompositionLocalOf<(() -> Long)?> { null }

fun NavGraphBuilder.myExamsGraph(
    tab: Destination,
    navigator: AppNavigator,
    showCompactBrandHeader: Boolean = false,
) {
    if (tab == Destination.MyExams) {
        composable(AppRoute.tabRoute(tab, AppRoute.MY_EXAMS_PATH)) {
            MyExamsRoute(
                navigator = navigator,
                showCompactBrandHeader = showCompactBrandHeader,
            )
        }
    }

    if (tab == Destination.History) {
        composable(
            route = AppRoute.tabRoute(tab, AppRoute.HISTORY_PATTERN),
            arguments = listOf(
                navArgument(AppRoute.ARG_RESET_FILTERS) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            HistoryRoute(
                backStackEntry = backStackEntry,
                navigator = navigator,
                showCompactBrandHeader = showCompactBrandHeader,
            )
        }
    }
}

@Composable
private fun MyExamsRoute(
    navigator: AppNavigator,
    showCompactBrandHeader: Boolean,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as? Thi360Application }
    val sessionStack = remember(context) { AppSessionContainer.get(context) }
    val connectivityMonitor = remember(app) {
        app?.connectivityMonitor ?: MutableConnectivityMonitor(initialOnline = true)
    }
    val clockOverride = LocalMyExamsClockOverride.current
    val memory = remember(sessionStack, connectivityMonitor, clockOverride) {
        MyExamsSessionMemory.forSession(
            sessionStack = sessionStack,
            connectivityMonitor = connectivityMonitor,
            sessionCleanup = app?.sessionCleanup,
            wallClockMillis = clockOverride ?: System::currentTimeMillis,
        )
    }
    val supportContact = LocalSupportContactOverride.current ?: AppEnvironment.supportContact

    val sessionState by sessionStack.sessionManager.state.collectAsStateWithLifecycle()
    val isActiveSession = sessionState is SessionState.Active
    val sessionGeneration by sessionStack.sessionManager.sessionGeneration.collectAsStateWithLifecycle()
    val feedState by memory.myExamsFeed.state.collectAsStateWithLifecycle()
    val greetingIdentity by memory.greetingIdentity.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val clickGuard = remember { NavigationClickGuard() }

    LaunchedEffect(memory, isActiveSession, sessionGeneration) {
        memory.syncAccountScope()
        if (isActiveSession) {
            memory.ensureGreetingIdentityLoaded()
        }
    }

    DisposableEffect(lifecycleOwner, memory, isActiveSession) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isActiveSession) {
                memory.syncAccountScope()
                memory.ensureGreetingIdentityLoaded()
                memory.myExamsFeed.onAppear()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val greetingName = resolveGreetingName(
        fullName = greetingIdentity.fullName,
        username = greetingIdentity.username ?: (sessionState as? SessionState.Active)?.username,
    )

    MyExamsScreen(
        feedState = feedState,
        greetingName = greetingName,
        supportContact = supportContact,
        showCompactBrandHeader = showCompactBrandHeader,
        onPullToRefresh = {
            if (sessionState is SessionState.Active) {
                memory.myExamsFeed.pullToRefresh()
            }
        },
        onRetry = {
            if (sessionState is SessionState.Active) {
                memory.myExamsFeed.retry()
            }
        },
        onExploreExams = { navigator.switchTab(Destination.Home, reload = false) },
        onOpenOrder = { orderCode -> navigator.openOrder(orderCode) },
        onOpenProduct = { code -> navigator.openProduct(code) },
        onContinueAttempt = { attemptId -> navigator.openAttempt(attemptId) },
        onStartPrepare = { entitlementId -> navigator.openPrepare(entitlementId) },
        onOpenResult = { attemptId -> navigator.openResult(attemptId) },
        onOpenHistoryReset = { navigator.switchTab(Destination.History, reload = true) },
        onOpenCheckout = { code -> navigator.openCheckout(code) },
        clickGuard = clickGuard,
    )
}

@Composable
private fun HistoryRoute(
    backStackEntry: NavBackStackEntry,
    navigator: AppNavigator,
    showCompactBrandHeader: Boolean,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as? Thi360Application }
    val sessionStack = remember(context) { AppSessionContainer.get(context) }
    val connectivityMonitor = remember(app) {
        app?.connectivityMonitor ?: MutableConnectivityMonitor(initialOnline = true)
    }
    val clockOverride = LocalMyExamsClockOverride.current
    val memory = remember(sessionStack, connectivityMonitor, clockOverride) {
        MyExamsSessionMemory.forSession(
            sessionStack = sessionStack,
            connectivityMonitor = connectivityMonitor,
            sessionCleanup = app?.sessionCleanup,
            wallClockMillis = clockOverride ?: System::currentTimeMillis,
        )
    }

    val sessionState by sessionStack.sessionManager.state.collectAsStateWithLifecycle()
    val isActiveSession = sessionState is SessionState.Active
    val sessionGeneration by sessionStack.sessionManager.sessionGeneration.collectAsStateWithLifecycle()
    val reloadCounters by navigator.reloadCounters.collectAsStateWithLifecycle()
    val historyReloadCounter = reloadCounters[Destination.History] ?: 0L
    val resetFilters = backStackEntry.arguments?.getBoolean(AppRoute.ARG_RESET_FILTERS) ?: false
    val resetKey = "${backStackEntry.id}:$historyReloadCounter"
    val feedState by memory.historyFeed.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val clickGuard = remember { NavigationClickGuard() }

    LaunchedEffect(memory, isActiveSession, sessionGeneration) {
        memory.syncAccountScope()
    }

    DisposableEffect(lifecycleOwner, memory, isActiveSession, resetFilters, resetKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isActiveSession) {
                memory.syncAccountScope()
                memory.historyFeed.onAppear(
                    resetFilters = resetFilters,
                    resetKey = resetKey,
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    HistoryScreen(
        feedState = feedState,
        showCompactBrandHeader = showCompactBrandHeader,
        onSearchInputChanged = memory.historyFeed::onSearchInputChanged,
        onSearchSubmit = memory.historyFeed::onSearchSubmit,
        onSelectKind = memory.historyFeed::selectExamKind,
        onSelectScope = memory.historyFeed::selectScope,
        onSelectFromDate = memory.historyFeed::selectFromDate,
        onSelectToDate = memory.historyFeed::selectToDate,
        onClearAllFilters = memory.historyFeed::clearAllFilters,
        onLoadNextPage = memory.historyFeed::loadNextPage,
        onRetry = {
            if (sessionState is SessionState.Active) {
                memory.historyFeed.retry()
            }
        },
        onGoToMyExams = { navigator.switchTab(Destination.MyExams, reload = false) },
        onContinueAttempt = { attemptId -> navigator.openAttempt(attemptId) },
        onOpenResult = { attemptId -> navigator.openResult(attemptId) },
        clickGuard = clickGuard,
    )
}
