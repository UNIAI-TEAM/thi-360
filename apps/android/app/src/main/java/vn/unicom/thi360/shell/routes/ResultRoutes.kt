package vn.unicom.thi360.shell.routes

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.feature.result.GradingPoller
import vn.unicom.thi360.feature.result.ResultClickGuard
import vn.unicom.thi360.feature.result.ResultRepository
import vn.unicom.thi360.feature.result.ResultScreen
import vn.unicom.thi360.feature.result.ResultSessionMemory
import vn.unicom.thi360.feature.result.ResultViewModel
import vn.unicom.thi360.feature.result.review.ReviewScreen
import vn.unicom.thi360.feature.result.review.ReviewViewModel
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.formula.RaTeXFormulaEngine
import vn.unicom.thi360.shell.Destination

data class ResultRouteDependencies(
    val repository: ResultRepository,
    val connectivityMonitor: ConnectivityMonitor,
    val pollerFactory: () -> GradingPoller = { GradingPoller() },
    val assetSourceFactory: ((Context, String) -> AttemptAssetSource?)? = null,
    val formulaEngineFactory: ((Context) -> FormulaEngine?)? = null,
    val clickGuardFactory: () -> ResultClickGuard = { ResultClickGuard() },
)

val LocalResultDependenciesOverride = staticCompositionLocalOf<ResultRouteDependencies?> { null }

fun NavGraphBuilder.resultGraph(
    tab: Destination,
    navigator: AppNavigator,
) {
    composable(
        route = AppRoute.tabRoute(tab, AppRoute.RESULT_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_ATTEMPT_ID) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val attemptId = AppRoute.decodeSegment(
            backStackEntry.arguments?.getString(AppRoute.ARG_ATTEMPT_ID).orEmpty(),
        ).orEmpty()
        ResultRoute(
            backStackEntry = backStackEntry,
            attemptId = attemptId,
            navigator = navigator,
        )
    }

    composable(
        route = AppRoute.tabRoute(tab, AppRoute.REVIEW_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_ATTEMPT_ID) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val attemptId = AppRoute.decodeSegment(
            backStackEntry.arguments?.getString(AppRoute.ARG_ATTEMPT_ID).orEmpty(),
        ).orEmpty()
        ReviewRoute(
            backStackEntry = backStackEntry,
            attemptId = attemptId,
            navigator = navigator,
        )
    }
}

private class ResultViewModelHolder(
    attemptId: String,
    repository: ResultRepository,
    connectivityMonitor: ConnectivityMonitor,
    poller: GradingPoller,
) : ViewModel() {
    val controller = ResultViewModel(
        attemptId = attemptId,
        repository = repository,
        connectivityMonitor = connectivityMonitor,
        scope = viewModelScope,
        poller = poller,
    )

    override fun onCleared() {
        controller.close()
    }
}

private class ReviewViewModelHolder(
    attemptId: String,
    repository: ResultRepository,
    connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {
    val controller = ReviewViewModel(
        attemptId = attemptId,
        repository = repository,
        connectivityMonitor = connectivityMonitor,
        scope = viewModelScope,
    )

    override fun onCleared() {
        controller.close()
    }
}

@Composable
private fun ResultRoute(
    backStackEntry: NavBackStackEntry,
    attemptId: String,
    navigator: AppNavigator,
) {
    val context = LocalContext.current
    val override = LocalResultDependenciesOverride.current
    val app = remember(context) { context.applicationContext as? Thi360Application }
    val sessionStack = remember(context) { AppSessionContainer.get(context) }
    val memory = remember(sessionStack, app) {
        ResultSessionMemory.forSession(
            sessionStack = sessionStack,
            sessionCleanup = app?.sessionCleanup,
        )
    }
    val repository = override?.repository ?: memory.repository
    val connectivityMonitor = override?.connectivityMonitor
        ?: remember(app) { app?.connectivityMonitor ?: MutableConnectivityMonitor(initialOnline = true) }
    val clickGuard = remember(override) {
        override?.clickGuardFactory?.invoke() ?: ResultClickGuard()
    }

    val holder = remember(backStackEntry, attemptId, repository, connectivityMonitor, override) {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ResultViewModelHolder(
                    attemptId = attemptId,
                    repository = repository,
                    connectivityMonitor = connectivityMonitor,
                    poller = override?.pollerFactory?.invoke() ?: GradingPoller(),
                ) as T
            }
        }
        ViewModelProvider(backStackEntry, factory)[
            "result:$attemptId:${System.identityHashCode(repository)}",
            ResultViewModelHolder::class.java,
        ]
    }
    val controller = holder.controller
    val uiState by controller.state.collectAsStateWithLifecycle()

    LaunchedEffect(controller) {
        if (override == null) {
            memory.syncAccountScope()
        }
        controller.onEnterComposition()
    }

    DisposableEffect(controller) {
        onDispose {
            controller.onLeaveComposition()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        var wasStopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    wasStopped = true
                    controller.onBackground()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasStopped) {
                        wasStopped = false
                        controller.onForeground()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ResultScreen(
        uiState = uiState,
        onBack = { navigator.back() },
        onRetry = controller::retry,
        onReloadFromTimeout = controller::reloadFromGradingTimeout,
        onOpenReview = { id -> navigator.openReview(id) },
        onContinueAttempt = { id -> navigator.openAttempt(id) },
        onGoToMyExams = { navigator.switchTab(Destination.MyExams, reload = true) },
        onGoToHistory = { navigator.switchTab(Destination.History, reload = true) },
        clickGuard = clickGuard,
    )
}

@Composable
private fun ReviewRoute(
    backStackEntry: NavBackStackEntry,
    attemptId: String,
    navigator: AppNavigator,
) {
    val context = LocalContext.current
    val override = LocalResultDependenciesOverride.current
    val app = remember(context) { context.applicationContext as? Thi360Application }
    val sessionStack = remember(context) { AppSessionContainer.get(context) }
    val memory = remember(sessionStack, app) {
        ResultSessionMemory.forSession(
            sessionStack = sessionStack,
            sessionCleanup = app?.sessionCleanup,
        )
    }
    val repository = override?.repository ?: memory.repository
    val connectivityMonitor = override?.connectivityMonitor
        ?: remember(app) { app?.connectivityMonitor ?: MutableConnectivityMonitor(initialOnline = true) }
    val clickGuard = remember(override) {
        override?.clickGuardFactory?.invoke() ?: ResultClickGuard()
    }

    val holder = remember(backStackEntry, attemptId, repository, connectivityMonitor) {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReviewViewModelHolder(
                    attemptId = attemptId,
                    repository = repository,
                    connectivityMonitor = connectivityMonitor,
                ) as T
            }
        }
        ViewModelProvider(backStackEntry, factory)[
            "review:$attemptId:${System.identityHashCode(repository)}",
            ReviewViewModelHolder::class.java,
        ]
    }
    val controller = holder.controller
    val uiState by controller.state.collectAsStateWithLifecycle()

    val assetSource = remember(context, attemptId, sessionStack, override) {
        val factory = override?.assetSourceFactory
        if (factory != null) {
            factory(context, attemptId)
        } else {
            AttemptAssetSource.create(
                context = context,
                attemptId = attemptId,
                sessionStack = sessionStack,
                baseUrl = sessionStack.baseUrl,
                sessionCleanup = app?.sessionCleanup,
            )
        }
    }

    val formulaEngine = remember(context, override) {
        val factory = override?.formulaEngineFactory
        if (factory != null) {
            factory(context)
        } else {
            runCatching { RaTeXFormulaEngine(context) }.getOrNull()
        }
    }

    DisposableEffect(assetSource) {
        onDispose {
            assetSource?.close()
        }
    }

    LaunchedEffect(controller) {
        if (override == null) {
            memory.syncAccountScope()
        }
        controller.onAppear()
    }

    ReviewScreen(
        uiState = uiState,
        onBack = { navigator.back() },
        onRetry = controller::retry,
        onSelectPart = controller::selectPart,
        onSelectGroup = controller::selectGroup,
        onPreviousGroup = controller::previousGroup,
        onNextGroup = controller::nextGroup,
        onGoToMyExams = { navigator.switchTab(Destination.MyExams, reload = true) },
        onGoToHistory = { navigator.switchTab(Destination.History, reload = true) },
        assets = assetSource,
        formulaEngine = formulaEngine,
        clickGuard = clickGuard,
    )
}
