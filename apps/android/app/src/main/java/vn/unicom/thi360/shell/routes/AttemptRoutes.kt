package vn.unicom.thi360.shell.routes

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import java.util.UUID
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.api.AttemptsApi
import vn.unicom.thi360.core.network.generated.api.MyExamsApi
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.attemptengine.AttemptApi
import vn.unicom.thi360.feature.attemptengine.AttemptEngineViewModel
import vn.unicom.thi360.feature.attemptengine.EngineClock
import vn.unicom.thi360.feature.attemptengine.EngineLifecycle
import vn.unicom.thi360.feature.attemptengine.EngineState
import vn.unicom.thi360.feature.attemptengine.OpenApiAttemptApi
import vn.unicom.thi360.feature.attemptengine.SystemEngineClock
import vn.unicom.thi360.feature.player.GENERATION_BUSY_NOTICE_DELAY_MS
import vn.unicom.thi360.feature.player.OpenApiPartChoiceMetadataApi
import vn.unicom.thi360.feature.player.PartChoiceMetadataApi
import vn.unicom.thi360.feature.player.PartChoiceOptionsLoader
import vn.unicom.thi360.feature.player.PlayerHost
import vn.unicom.thi360.feature.player.PlayerLocalUi
import vn.unicom.thi360.feature.player.PlayerScreen
import vn.unicom.thi360.feature.prepare.OpenApiPrepareApi
import vn.unicom.thi360.feature.prepare.PrepareApi
import vn.unicom.thi360.feature.prepare.StartContextStore
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.shell.Destination

data class PlayerRouteOverrides(
    val attemptApi: AttemptApi? = null,
    val prepareApi: PrepareApi? = null,
    val partChoiceMetadataApi: PartChoiceMetadataApi? = null,
    val store: AccountScopedStore? = null,
    val accountId: String? = null,
    val clock: EngineClock = SystemEngineClock,
    val connectivity: ConnectivityMonitor? = null,
    val startContextStore: StartContextStore? = null,
    val waitingClockMillis: () -> Long = System::currentTimeMillis,
    val formulaEngine: FormulaEngine? = null,
    val busyNoticeDelayMs: Long = GENERATION_BUSY_NOTICE_DELAY_MS,
)

val LocalPlayerRouteOverrides = staticCompositionLocalOf<PlayerRouteOverrides?> { null }

private tailrec fun Context.findAttemptComponentActivityOrNull(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findAttemptComponentActivityOrNull()
    else -> null
}

fun NavGraphBuilder.attemptGraph(
    navigator: AppNavigator,
) {
    composable(
        route = AppRoute.ATTEMPT_PATTERN,
        arguments = listOf(
            navArgument(AppRoute.ARG_ATTEMPT_ID) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val attemptId = AppRoute.decodeSegment(
            backStackEntry.arguments?.getString(AppRoute.ARG_ATTEMPT_ID).orEmpty(),
        ).orEmpty()
        PlayerRoute(
            backStackEntry = backStackEntry,
            attemptId = attemptId,
            navigator = navigator,
        )
    }
}

@Composable
private fun PlayerRoute(
    backStackEntry: NavBackStackEntry,
    attemptId: String,
    navigator: AppNavigator,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findAttemptComponentActivityOrNull() }
    val app = remember(context) { context.applicationContext as? Thi360Application }
    val sessionStack = remember(context) { AppSessionContainer.get(context) }
    val sessionState by sessionStack.sessionManager.state.collectAsStateWithLifecycle()
    val overrides = LocalPlayerRouteOverrides.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val localFormulaEngine = LocalFormulaEngine.current

    val parsedAttemptUuid = remember(attemptId) {
        runCatching { UUID.fromString(attemptId.trim()) }.getOrNull()
    }
    val resolvedStore = remember(overrides, app, sessionState) {
        overrides?.store ?: app?.accountScopedStoreProvider?.current()
    }
    val resolvedAccountId = remember(overrides, resolvedStore) {
        overrides?.accountId ?: resolvedStore?.accountId
    }

    if (parsedAttemptUuid == null || resolvedStore == null || resolvedAccountId.isNullOrBlank()) {
        PlayerScreen(
            attemptId = attemptId,
            engineState = EngineState(
                load = LoadState.Failed(
                    kind = LoadFailureKind.NotFound,
                    canRetry = false,
                ),
            ),
            localUi = PlayerLocalUi(),
            onCloseAttempt = { navigator.closeAttempt() },
            onBackToMyExams = { navigator.switchTab(Destination.MyExams, reload = true) },
        )
        return
    }

    val attemptsApi = remember(sessionStack) {
        sessionStack.authenticatedService(AttemptsApi::class.java)
    }
    val attemptApi = remember(overrides, attemptsApi) {
        overrides?.attemptApi ?: OpenApiAttemptApi(attemptsApi)
    }
    val prepareApi = remember(overrides, attemptsApi) {
        overrides?.prepareApi ?: OpenApiPrepareApi(attemptsApi)
    }
    val startContextStore = remember(overrides, resolvedStore) {
        overrides?.startContextStore ?: StartContextStore(resolvedStore)
    }
    val connectivity = remember(overrides, app) {
        overrides?.connectivity ?: app?.connectivityMonitor ?: MutableConnectivityMonitor(true)
    }
    val partChoiceMetadataApi = remember(overrides, sessionStack, prepareApi) {
        overrides?.partChoiceMetadataApi ?: OpenApiPartChoiceMetadataApi(
            myExamsApi = sessionStack.authenticatedService(MyExamsApi::class.java),
            prepareApi = prepareApi,
        )
    }
    val partChoiceLoader = remember(startContextStore, partChoiceMetadataApi, connectivity) {
        PartChoiceOptionsLoader(
            startContextStore = startContextStore,
            api = partChoiceMetadataApi,
            isOnlineProvider = { connectivity.online.value },
        )
    }

    val viewModel = remember(backStackEntry, parsedAttemptUuid, resolvedAccountId) {
        ViewModelProvider(
            backStackEntry,
            AttemptEngineViewModel.Factory(
                attemptId = parsedAttemptUuid,
                accountId = resolvedAccountId,
                api = attemptApi,
                store = resolvedStore,
                clock = overrides?.clock ?: SystemEngineClock,
                connectivity = connectivity,
                sessionCleanup = app?.sessionCleanup,
                storeProvider = app?.accountScopedStoreProvider,
            ),
        )[AttemptEngineViewModel::class.java]
    }
    val engine = viewModel.engine

    DisposableEffect(engine, lifecycleOwner, app, activity) {
        val engineLifecycle = EngineLifecycle(
            engine = engine,
            lifecycle = lifecycleOwner.lifecycle,
            inboundLinkRouter = app?.inboundLinkRouter,
            isChangingConfigurations = { activity?.isChangingConfigurations == true },
            isSessionUnlocked = {
                sessionStack.sessionManager.state.value is SessionState.Active || overrides != null
            },
        ).attach()
        onDispose {
            engineLifecycle.close()
            if (activity?.isChangingConfigurations != true) {
                engine.setForeground(false)
            }
        }
    }

    val assetSource = remember(context, attemptId, sessionStack) {
        AttemptAssetSource.create(
            context = context,
            attemptId = attemptId,
            sessionStack = sessionStack,
        )
    }
    DisposableEffect(assetSource) {
        onDispose {
            assetSource.close()
        }
    }

    PlayerHost(
        attemptId = attemptId,
        engine = engine,
        onCloseAttempt = { navigator.closeAttempt() },
        onBackToMyExams = { navigator.switchTab(Destination.MyExams, reload = true) },
        onViewResult = { navigator.openResultFromAttempt(attemptId) },
        onReplaceAttempt = { newAttemptId -> navigator.replaceAttempt(newAttemptId) },
        onOpenPrepare = { entitlementId -> navigator.openPrepare(entitlementId) },
        partChoiceLoader = partChoiceLoader,
        prepareApi = prepareApi,
        startContextStore = startContextStore,
        waitingClockMillis = overrides?.waitingClockMillis ?: System::currentTimeMillis,
        assets = assetSource,
        formulaEngine = overrides?.formulaEngine ?: localFormulaEngine,
        busyNoticeDelayMs = overrides?.busyNoticeDelayMs ?: GENERATION_BUSY_NOTICE_DELAY_MS,
    )
}
