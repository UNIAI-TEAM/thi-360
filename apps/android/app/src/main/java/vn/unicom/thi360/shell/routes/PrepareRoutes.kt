package vn.unicom.thi360.shell.routes

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
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
import vn.unicom.thi360.core.network.generated.api.AttemptsApi
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.feature.prepare.OpenApiPrepareApi
import vn.unicom.thi360.feature.prepare.PrepareApi
import vn.unicom.thi360.feature.prepare.PrepareRouteHolderViewModel
import vn.unicom.thi360.feature.prepare.PrepareSavedSnapshot
import vn.unicom.thi360.feature.prepare.PrepareScreen
import vn.unicom.thi360.feature.prepare.PrepareSessionMemory
import vn.unicom.thi360.feature.prepare.PrepareViewModel
import vn.unicom.thi360.feature.prepare.StartContextStore
import vn.unicom.thi360.shell.Destination

internal val LocalPrepareSupportContactOverride = staticCompositionLocalOf<SupportContact?> { null }
internal val LocalPrepareClockOverride = staticCompositionLocalOf<(() -> Long)?> { null }
internal val LocalPrepareApiOverride = staticCompositionLocalOf<PrepareApi?> { null }
internal val LocalPrepareStartContextStoreOverride = staticCompositionLocalOf<StartContextStore?> { null }

private tailrec fun Context.findComponentActivityOrNull(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivityOrNull()
    else -> null
}

private val PrepareSavedSnapshotSaver = Saver<PrepareSavedSnapshot, List<Any?>>(
    save = { snap ->
        listOf(
            snap.selectedOptionCode,
            snap.selectedSubjectCodes,
            snap.hasExplicitSelection,
            snap.waitingAttemptId,
            snap.waitingKind,
            snap.waitingElapsedMs,
            snap.waitingAbortReason,
            snap.waitingPreChoiceOption,
            snap.waitingPreChoiceSubjects,
        )
    },
    restore = { raw ->
        @Suppress("UNCHECKED_CAST")
        PrepareSavedSnapshot(
            selectedOptionCode = raw.getOrNull(0) as? String,
            selectedSubjectCodes = (raw.getOrNull(1) as? List<String>).orEmpty(),
            hasExplicitSelection = (raw.getOrNull(2) as? Boolean) ?: false,
            waitingAttemptId = raw.getOrNull(3) as? String,
            waitingKind = raw.getOrNull(4) as? String,
            waitingElapsedMs = (raw.getOrNull(5) as? Long) ?: 0L,
            waitingAbortReason = raw.getOrNull(6) as? String,
            waitingPreChoiceOption = raw.getOrNull(7) as? String,
            waitingPreChoiceSubjects = raw.getOrNull(8) as? List<String>,
        )
    },
)

fun NavGraphBuilder.prepareGraph(
    tab: Destination,
    navigator: AppNavigator,
) {
    composable(
        route = AppRoute.tabRoute(tab, AppRoute.PREPARE_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_ENTITLEMENT_ID) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val entitlementId = AppRoute.decodeSegment(
            backStackEntry.arguments?.getString(AppRoute.ARG_ENTITLEMENT_ID).orEmpty(),
        ).orEmpty()
        PrepareRoute(
            backStackEntry = backStackEntry,
            entitlementId = entitlementId,
            navigator = navigator,
        )
    }
}

@Composable
private fun PrepareRoute(
    backStackEntry: NavBackStackEntry,
    entitlementId: String,
    navigator: AppNavigator,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivityOrNull() }
    val app = remember(context) { context.applicationContext as? Thi360Application }
    val sessionStack = remember(context) { AppSessionContainer.get(context) }
    val sessionState by sessionStack.sessionManager.state.collectAsStateWithLifecycle()
    val sessionGeneration by sessionStack.sessionManager.sessionGeneration.collectAsStateWithLifecycle()
    val isActiveSession = sessionState is SessionState.Active
    val locale = LocalAppLocale.current
    val supportContact = LocalPrepareSupportContactOverride.current ?: AppEnvironment.supportContact
    val clockOverride = LocalPrepareClockOverride.current
    val apiOverride = LocalPrepareApiOverride.current
    val storeOverride = LocalPrepareStartContextStoreOverride.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val prepareApi = remember(apiOverride, sessionStack) {
        apiOverride ?: OpenApiPrepareApi(
            sessionStack.authenticatedService(AttemptsApi::class.java),
        )
    }
    val startContextStore = remember(storeOverride, app) {
        storeOverride ?: StartContextStore(
            storeProvider = { app?.accountScopedStoreProvider?.current() },
        )
    }
    val memory = remember(sessionStack, app) {
        PrepareSessionMemory.forSession(
            sessionManager = sessionStack.sessionManager,
            sessionCleanup = app?.sessionCleanup,
        )
    }
    val entryKey = remember(sessionGeneration, backStackEntry.id, entitlementId, apiOverride) {
        PrepareSessionMemory.EntryKey(
            sessionGeneration = sessionGeneration,
            backStackEntryId = backStackEntry.id,
            entitlementId = entitlementId,
            apiIdentity = System.identityHashCode(apiOverride),
        )
    }

    val holder = remember(backStackEntry) {
        ViewModelProvider(
            backStackEntry,
            PrepareRouteHolderViewModel.FACTORY,
        )[PrepareRouteHolderViewModel::class.java]
    }

    var savedSnapshot by rememberSaveable(
        backStackEntry.id,
        entitlementId,
        sessionGeneration,
        stateSaver = PrepareSavedSnapshotSaver,
    ) {
        mutableStateOf(PrepareSavedSnapshot())
    }

    val openAttemptAndCleanup = remember(holder, navigator) {
        { attemptId: String ->
            holder.clearCurrent()
            navigator.openAttemptAfterStart(attemptId)
        }
    }

    val viewModel = remember(holder, memory, entryKey) {
        holder.getOrCreate(memory, entryKey) { vmScope ->
            PrepareViewModel(
                entitlementId = entitlementId,
                api = prepareApi,
                startContextStore = startContextStore,
                scope = vmScope,
                clockMillis = clockOverride ?: System::currentTimeMillis,
                localeProvider = { locale },
                isOnlineProvider = { app?.connectivityMonitor?.online?.value ?: true },
                onOpenAttempt = openAttemptAndCleanup,
            ).also { vm ->
                if (savedSnapshot.hasExplicitSelection || savedSnapshot.waitingAttemptId != null) {
                    vm.restoreSnapshot(savedSnapshot)
                }
            }
        }
    }

    SideEffect {
        viewModel.updateRuntimeBindings(
            localeProvider = { locale },
            isOnlineProvider = { app?.connectivityMonitor?.online?.value ?: true },
        )
    }

    DisposableEffect(viewModel, openAttemptAndCleanup) {
        viewModel.attachOpenAttemptHandler(openAttemptAndCleanup)
        onDispose {
            viewModel.detachOpenAttemptHandler(openAttemptAndCleanup)
        }
    }

    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        savedSnapshot = viewModel.exportSnapshot()
    }

    LaunchedEffect(viewModel, isActiveSession, apiOverride) {
        if (isActiveSession || apiOverride != null) {
            viewModel.ensureLoaded()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel, activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setForeground(true)
                Lifecycle.Event.ON_STOP -> {
                    savedSnapshot = viewModel.exportSnapshot()
                    if (activity?.isChangingConfigurations != true) {
                        viewModel.setForeground(false)
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            savedSnapshot = viewModel.exportSnapshot()
        }
    }

    val leaveToMyExams: () -> Unit = {
        holder.clearCurrent()
        navigator.switchTab(Destination.MyExams, reload = true)
    }

    val leaveBack: () -> Unit = {
        holder.clearCurrent()
        navigator.back()
    }

    PrepareScreen(
        state = uiState,
        supportContact = supportContact,
        onBack = leaveBack,
        onMyExams = leaveToMyExams,
        onRetryLoad = viewModel::retryLoad,
        onSelectOption = viewModel::selectOption,
        onToggleSubject = viewModel::toggleSubject,
        onStartAttempt = viewModel::startAttempt,
        onContinueAttempt = { attemptId ->
            holder.clearCurrent()
            navigator.openAttemptAfterStart(attemptId.toString())
        },
        onBuyMoreAttempts = { code -> navigator.openCheckout(code) },
        onRetryWaiting = viewModel::retryFromWaiting,
        onLeaveWaiting = leaveBack,
    )
}
