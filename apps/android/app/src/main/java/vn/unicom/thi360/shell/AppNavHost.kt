package vn.unicom.thi360.shell

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.biometric.BiometricAuthenticator
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.biometric.BiometricUnavailableReason
import vn.unicom.thi360.core.biometric.RecentsPrivacyPolicy
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.account.AccountDetailPane
import vn.unicom.thi360.feature.account.AccountProfileGateway
import vn.unicom.thi360.feature.account.AccountScreen
import vn.unicom.thi360.feature.account.OpenApiAccountProfileGateway
import vn.unicom.thi360.feature.account.ProfileLoadState
import vn.unicom.thi360.feature.lock.BiometricEnablePromptScreen
import vn.unicom.thi360.feature.lock.LockHost
import vn.unicom.thi360.feature.lock.LockTestTags
import vn.unicom.thi360.feature.login.LoginRoutes
import vn.unicom.thi360.feature.login.LoginScreen
import vn.unicom.thi360.feature.login.OfflineSessionBanner
import vn.unicom.thi360.feature.login.RoleNoticeScreen
import vn.unicom.thi360.feature.password.ChangePasswordDraftViewModel
import vn.unicom.thi360.feature.password.InvalidResetLinkScreen
import vn.unicom.thi360.feature.password.NewPasswordMode
import vn.unicom.thi360.feature.password.NewPasswordScreen
import vn.unicom.thi360.feature.recovery.ForgotPasswordScreen
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.RecoveryGateway
import vn.unicom.thi360.feature.recovery.RecoveryRoutes
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.feature.recovery.ResetLinkIntentResult
import vn.unicom.thi360.feature.register.RegistrationFlowScreen

internal sealed interface ActiveResetOverlay {
    data class Valid(val link: ResetLink) : ActiveResetOverlay {
        override fun toString(): String = "Valid(link=[REDACTED])"
    }

    data object InvalidLink : ActiveResetOverlay
}

private tailrec fun Context.findComponentActivityOrNull(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivityOrNull()
    else -> null
}

private fun clearConsumedResetIntent(activity: ComponentActivity?, allowedHost: String) {
    val current = activity?.intent ?: return
    if (ResetLink.parseIntent(current, allowedHost) is ResetLinkIntentResult.Ignored) {
        return
    }
    val savedComponent = current.component
    // Mutate the live Intent in place so ActivityClientRecord cannot retain the reset token URI in memory.
    current.action = Intent.ACTION_MAIN
    current.data = null
    current.replaceExtras(null as Bundle?)
    activity.intent = Intent(Intent.ACTION_MAIN).apply {
        component = savedComponent
    }
}

@Composable
fun AppRoot(
    preferences: AppPreferences,
    onSelectLocale: (AppLocale) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    brandPalette: BrandPalette = BrandPalette.Default,
    navController: NavHostController? = null,
    startDestination: String? = null,
    accountContent: (@Composable (isTwoPaneWidth: Boolean) -> Unit)? = null,
    extraDestinations: NavGraphBuilder.() -> Unit = {},
    sessionManager: SessionManager? = null,
    validateOnLaunch: Boolean = true,
    onForgotPasswordClick: (() -> Unit)? = null,
    onRegisterClick: (() -> Unit)? = null,
    recoveryGateway: RecoveryGateway? = null,
    initialIntent: Intent? = null,
    lockedContent: (@Composable () -> Unit)? = null,
    clockMillis: () -> Long = System::currentTimeMillis,
    biometricController: BiometricController? = null,
    biometricAuthenticator: BiometricAuthenticator? = null,
    profileGateway: AccountProfileGateway? = null,
    isAppBackgrounded: Boolean = false,
) {
    // Preserve direct shell entry when callers pin an explicit startDestination without a SessionManager.
    if (startDestination != null && sessionManager == null && initialIntent == null) {
        Thi360App(
            preferences = preferences,
            brandPalette = brandPalette,
            onSelectLocale = onSelectLocale,
            onSelectThemeMode = onSelectThemeMode,
            navController = navController ?: rememberNavController(),
            startDestination = startDestination,
            accountContent = accountContent,
            extraDestinations = extraDestinations,
        )
        return
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivityOrNull() }
    val activeSessionManager = remember(sessionManager, context) {
        sessionManager ?: AppSessionContainer.get(context).sessionManager
    }
    val activeRecoveryGateway = remember(recoveryGateway, context) {
        recoveryGateway ?: AppSessionContainer.get(context).let { stack ->
            OpenApiRecoveryGateway(
                publicAuthApi = stack.publicAuthApi,
                authenticatedAuthApi = stack.authenticatedAuthApi,
            )
        }
    }
    val activeProfileGateway = remember(profileGateway, context) {
        profileGateway ?: OpenApiAccountProfileGateway(
            profileApi = AppSessionContainer.get(context).profileApi,
        )
    }
    val allowedResetHost = remember { ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl) }

    SideEffect {
        AppSessionContainer.setLocale(preferences.effectiveLocale)
    }

    val sessionState by activeSessionManager.state.collectAsStateWithLifecycle()
    val biometricState by (biometricController?.state
        ?: remember { MutableStateFlow<BiometricState>(BiometricState.Unavailable(BiometricUnavailableReason.NO_HARDWARE)) })
        .collectAsStateWithLifecycle()

    LaunchedEffect(activity, biometricState, sessionState) {
        val window = activity?.window ?: return@LaunchedEffect
        val protect = RecentsPrivacyPolicy.shouldProtectWindow(
            biometricState = biometricState,
            sessionState = sessionState,
        )
        if (protect) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!protect)
        }
    }

    LaunchedEffect(activeSessionManager, validateOnLaunch) {
        if (validateOnLaunch && activeSessionManager.state.value !is SessionState.Locked) {
            activeSessionManager.validateSessionOnLaunch()
        }
    }

    // Keep reset link token strictly in ephemeral composition memory; never save to Bundle or disk.
    var activeResetOverlay by remember { mutableStateOf<ActiveResetOverlay?>(null) }
    var openForgotPasswordOverlay by remember { mutableStateOf(false) }
    // Persist only a plain consumed flag so Activity recreation / state restoration never replays a dismissed reset Intent.
    var initialIntentConsumed by rememberSaveable { mutableStateOf(false) }

    fun processResetIntent(candidate: Intent?) {
        when (val parsed = ResetLink.parseIntent(candidate, allowedResetHost)) {
            is ResetLinkIntentResult.Valid -> {
                activeResetOverlay = ActiveResetOverlay.Valid(parsed.link)
                openForgotPasswordOverlay = false
                clearConsumedResetIntent(activity, allowedResetHost)
            }

            ResetLinkIntentResult.MissingToken -> {
                activeResetOverlay = ActiveResetOverlay.InvalidLink
                openForgotPasswordOverlay = false
                clearConsumedResetIntent(activity, allowedResetHost)
            }

            ResetLinkIntentResult.Ignored -> Unit
        }
    }

    LaunchedEffect(initialIntent, activity) {
        if (!initialIntentConsumed) {
            initialIntentConsumed = true
            processResetIntent(initialIntent ?: activity?.intent)
        }
    }

    DisposableEffect(activity, allowedResetHost) {
        if (activity == null) {
            onDispose {}
        } else {
            val listener = Consumer<Intent> { newIntent ->
                processResetIntent(newIntent)
            }
            activity.addOnNewIntentListener(listener)
            onDispose {
                activity.removeOnNewIntentListener(listener)
            }
        }
    }

    AppSessionContent(
        sessionState = sessionState,
        sessionManager = activeSessionManager,
        recoveryGateway = activeRecoveryGateway,
        profileGateway = activeProfileGateway,
        preferences = preferences,
        brandPalette = brandPalette,
        onSelectLocale = onSelectLocale,
        onSelectThemeMode = onSelectThemeMode,
        navController = navController,
        startDestination = startDestination,
        accountContent = accountContent,
        extraDestinations = extraDestinations,
        onForgotPasswordClick = onForgotPasswordClick,
        onRegisterClick = onRegisterClick,
        lockedContent = lockedContent,
        activeResetOverlay = activeResetOverlay,
        onDismissResetOverlay = {
            activeResetOverlay = null
            clearConsumedResetIntent(activity, allowedResetHost)
        },
        onMarkResetLinkInvalid = {
            activeResetOverlay = ActiveResetOverlay.InvalidLink
            clearConsumedResetIntent(activity, allowedResetHost)
        },
        openForgotPasswordOverlay = openForgotPasswordOverlay,
        onOpenForgotPasswordOverlay = { openForgotPasswordOverlay = it },
        clockMillis = clockMillis,
        biometricController = biometricController,
        biometricAuthenticator = biometricAuthenticator,
        isAppBackgrounded = isAppBackgrounded,
    )
}

@Composable
internal fun AppSessionContent(
    sessionState: SessionState,
    sessionManager: SessionManager,
    recoveryGateway: RecoveryGateway,
    preferences: AppPreferences,
    brandPalette: BrandPalette,
    onSelectLocale: (AppLocale) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    navController: NavHostController?,
    startDestination: String?,
    accountContent: (@Composable (isTwoPaneWidth: Boolean) -> Unit)?,
    extraDestinations: NavGraphBuilder.() -> Unit,
    onForgotPasswordClick: (() -> Unit)?,
    onRegisterClick: (() -> Unit)?,
    lockedContent: (@Composable () -> Unit)?,
    activeResetOverlay: ActiveResetOverlay?,
    onDismissResetOverlay: () -> Unit,
    onMarkResetLinkInvalid: () -> Unit,
    openForgotPasswordOverlay: Boolean,
    onOpenForgotPasswordOverlay: (Boolean) -> Unit,
    clockMillis: () -> Long,
    profileGateway: AccountProfileGateway? = null,
    biometricController: BiometricController? = null,
    biometricAuthenticator: BiometricAuthenticator? = null,
    isAppBackgrounded: Boolean = false,
) {
    val latestSelectLocale by rememberUpdatedState(onSelectLocale)
    val latestForgotPasswordClick by rememberUpdatedState(onForgotPasswordClick)
    val latestRegisterClick by rememberUpdatedState(onRegisterClick)
    val coroutineScope = rememberCoroutineScope()
    var isRetryingOffline by remember { mutableStateOf(false) }
    var pendingSignedOutRoute by remember { mutableStateOf<String?>(null) }

    val sessionGeneration by sessionManager.sessionGeneration.collectAsStateWithLifecycle()
    val biometricState by (biometricController?.state
        ?: remember { MutableStateFlow<BiometricState>(BiometricState.Unavailable(BiometricUnavailableReason.NO_HARDWARE)) })
        .collectAsStateWithLifecycle()
    val shouldPromptEnable by (biometricController?.shouldPromptEnable
        ?: remember { MutableStateFlow(false) })
        .collectAsStateWithLifecycle()

    // Session-scoped UI state survives Active <-> Locked transitions and wipes on sign-out / session replacement.
    var savedShellNavState by remember(sessionManager, sessionGeneration) { mutableStateOf<Bundle?>(null) }
    var profileState by remember(sessionManager, sessionGeneration) {
        mutableStateOf<ProfileLoadState>(ProfileLoadState.Loading)
    }
    var selectedAccountPane by rememberSaveable(sessionGeneration) {
        mutableStateOf(AccountDetailPane.CHANGE_PASSWORD)
    }
    var showSinglePaneChangePassword by rememberSaveable(sessionGeneration) {
        mutableStateOf(false)
    }

    val isSignedOut = sessionState is SessionState.SignedOut
    LaunchedEffect(isSignedOut) {
        if (isSignedOut) {
            ChangePasswordDraftViewModel.clearAllActiveDrafts()
            savedShellNavState = null
            profileState = ProfileLoadState.Loading
        }
    }

    var previousSessionState by remember { mutableStateOf(sessionState) }
    LaunchedEffect(sessionState) {
        if (previousSessionState is SessionState.MustChangePassword && sessionState is SessionState.Active) {
            biometricController?.onForcedPasswordChangeCompleted()
        }
        previousSessionState = sessionState
    }

    // Password reset deep links bypass Locked, MustChangePassword, Active, and SignedOut states.
    if (activeResetOverlay != null) {
        Thi360Theme(
            preferences = preferences,
            brandPalette = brandPalette,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Thi360Theme.colors.bgPage)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                when (activeResetOverlay) {
                    is ActiveResetOverlay.Valid -> {
                        NewPasswordScreen(
                            mode = NewPasswordMode.FromLink(activeResetOverlay.link.token),
                            recoveryGateway = recoveryGateway,
                            sessionManager = sessionManager,
                            onBack = onDismissResetOverlay,
                            onResetCompleted = onDismissResetOverlay,
                            onResetTokenInvalid = onMarkResetLinkInvalid,
                        )
                    }

                    ActiveResetOverlay.InvalidLink -> {
                        InvalidResetLinkScreen(
                            onRequestNewLink = {
                                onDismissResetOverlay()
                                if (sessionState is SessionState.SignedOut) {
                                    pendingSignedOutRoute = RecoveryRoutes.FORGOT_PASSWORD
                                } else {
                                    onOpenForgotPasswordOverlay(true)
                                }
                            },
                            onBackToLogin = onDismissResetOverlay,
                        )
                    }
                }
            }
        }
        return
    }

    if (openForgotPasswordOverlay) {
        Thi360Theme(
            preferences = preferences,
            brandPalette = brandPalette,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Thi360Theme.colors.bgPage)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                ForgotPasswordScreen(
                    recoveryGateway = recoveryGateway,
                    onBackToLogin = { onOpenForgotPasswordOverlay(false) },
                    clockMillis = clockMillis,
                )
            }
        }
        return
    }

    // Separate signed-out auth NavHost from authenticated Thi360App so shell graph startDestination
    // stays pinned to Home (preserving tab saveState/restoreState) and sign-out disposes prior tab state.
    if (sessionState is SessionState.SignedOut) {
        val authNavController = rememberNavController()
        val initialAuthRoute = remember(sessionManager) {
            routeForSignedOut(sessionState.reason)
        }
        var appliedReason by remember(sessionManager) { mutableStateOf(sessionState.reason) }
        val latestSignedOutState = rememberUpdatedState(sessionState)

        LaunchedEffect(sessionState.reason, authNavController) {
            if (sessionState.reason != appliedReason) {
                appliedReason = sessionState.reason
                val targetRoute = routeForSignedOut(sessionState.reason)
                if (authNavController.currentDestination?.route != targetRoute) {
                    authNavController.navigate(targetRoute) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        LaunchedEffect(pendingSignedOutRoute, authNavController) {
            val pending = pendingSignedOutRoute ?: return@LaunchedEffect
            pendingSignedOutRoute = null
            if (authNavController.currentDestination?.route != pending) {
                authNavController.navigate(pending) {
                    launchSingleTop = true
                }
            }
        }

        Thi360Theme(
            preferences = preferences,
            brandPalette = brandPalette,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Thi360Theme.colors.bgPage)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                NavHost(
                    navController = authNavController,
                    startDestination = initialAuthRoute,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(LoginRoutes.LOGIN) {
                        LoginScreen(
                            sessionManager = sessionManager,
                            signOutReason = latestSignedOutState.value.reason,
                            onSelectLocale = { latestSelectLocale(it) },
                            onForgotPasswordClick = {
                                val custom = latestForgotPasswordClick
                                if (custom != null) {
                                    custom()
                                } else {
                                    authNavController.navigate(RecoveryRoutes.FORGOT_PASSWORD) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onRegisterClick = {
                                val custom = latestRegisterClick
                                if (custom != null) {
                                    custom()
                                } else {
                                    authNavController.navigate(RecoveryRoutes.REGISTER) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onLoginAuthenticated = { mustChangePassword ->
                                coroutineScope.launch {
                                    biometricController?.onPasswordLoginCompleted(mustChangePassword)
                                }
                            },
                        )
                    }
                    composable(LoginRoutes.ROLE_NOTICE) {
                        val studentOnly = latestSignedOutState.value.reason as? SignOutReason.StudentOnly
                        RoleNoticeScreen(
                            username = studentOnly?.username.orEmpty(),
                            onSwitchAccount = { sessionManager.acknowledgeRoleNotice() },
                            onSelectLocale = { latestSelectLocale(it) },
                        )
                    }
                    composable(RecoveryRoutes.REGISTER) {
                        RegistrationFlowScreen(
                            recoveryGateway = recoveryGateway,
                            sessionManager = sessionManager,
                            onBackToLogin = {
                                authNavController.popBackStack(LoginRoutes.LOGIN, inclusive = false)
                            },
                            clockMillis = clockMillis,
                        )
                    }
                    composable(RecoveryRoutes.FORGOT_PASSWORD) {
                        ForgotPasswordScreen(
                            recoveryGateway = recoveryGateway,
                            onBackToLogin = {
                                authNavController.popBackStack(LoginRoutes.LOGIN, inclusive = false)
                            },
                            clockMillis = clockMillis,
                        )
                    }
                    extraDestinations()
                }
            }
        }
        return
    }

    if (sessionState is SessionState.MustChangePassword) {
        val isOffline = sessionState.isOffline
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isOffline) {
                    OfflineBannerSlot(
                        preferences = preferences,
                        brandPalette = brandPalette,
                        isRetrying = isRetryingOffline,
                        onRetry = {
                            if (!isRetryingOffline) {
                                isRetryingOffline = true
                                coroutineScope.launch {
                                    try {
                                        sessionManager.retryConnection()
                                    } finally {
                                        isRetryingOffline = false
                                    }
                                }
                            }
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .consumeWindowInsets(
                            if (isOffline) WindowInsets.statusBars else WindowInsets(0, 0, 0, 0),
                        ),
                ) {
                    Thi360Theme(
                        preferences = preferences,
                        brandPalette = brandPalette,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Thi360Theme.colors.bgPage)
                                .windowInsetsPadding(WindowInsets.safeDrawing),
                        ) {
                            NewPasswordScreen(
                                mode = NewPasswordMode.Forced(username = sessionState.username),
                                recoveryGateway = recoveryGateway,
                                sessionManager = sessionManager,
                            )
                        }
                    }
                }
            }
            if (isAppBackgrounded && biometricState is BiometricState.On) {
                RecentsPrivacyShieldOverlay(preferences = preferences, brandPalette = brandPalette)
            }
        }
        return
    }

    if (sessionState is SessionState.Locked) {
        val loadedProfile = (profileState as? ProfileLoadState.Loaded)?.profile
        Thi360Theme(
            preferences = preferences,
            brandPalette = brandPalette,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Thi360Theme.colors.bgPage)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .testTag("session_locked_host"),
            ) {
                if (lockedContent != null) {
                    lockedContent()
                } else {
                    LockHost(
                        username = loadedProfile?.username ?: sessionState.username,
                        displayName = loadedProfile?.fullName,
                        sessionManager = sessionManager,
                        biometricController = biometricController,
                        biometricAuthenticator = biometricAuthenticator,
                        recoveryGateway = recoveryGateway,
                        onSelectLocale = { latestSelectLocale(it) },
                        onPasswordFallbackAuthenticated = { mustChangePassword ->
                            coroutineScope.launch {
                                biometricController?.onPasswordLoginCompleted(mustChangePassword)
                            }
                        },
                        clockMillis = clockMillis,
                    )
                }
            }
        }
        return
    }

    val activeState = sessionState as SessionState.Active
    if (shouldPromptEnable && biometricController != null) {
        Thi360Theme(
            preferences = preferences,
            brandPalette = brandPalette,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Thi360Theme.colors.bgPage)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                BiometricEnablePromptScreen(
                    biometricController = biometricController,
                    biometricAuthenticator = biometricAuthenticator,
                    sessionManager = sessionManager,
                )
            }
        }
        return
    }

    key(sessionGeneration) {
        val shellNavController = navController ?: rememberNavController()
        if (navController == null) {
            val bundleToRestore = savedShellNavState
            if (bundleToRestore != null) {
                remember(shellNavController, bundleToRestore) {
                    shellNavController.restoreState(bundleToRestore)
                    savedShellNavState = null
                    true
                }
            }
        }
        DisposableEffect(shellNavController) {
            onDispose {
                val nextState = sessionManager.state.value
                if (navController != null) {
                    if (nextState !is SessionState.Locked) {
                        Destination.entries.forEach { destination ->
                            shellNavController.clearBackStack(destination.route)
                        }
                        runCatching {
                            shellNavController.popBackStack(
                                destinationId = shellNavController.graph.id,
                                inclusive = true,
                            )
                        }
                    }
                } else if (nextState is SessionState.Locked) {
                    savedShellNavState = shellNavController.saveState()
                }
            }
        }

        val resolvedAccountContent: (@Composable (Boolean) -> Unit)? = accountContent ?: profileGateway?.let { gateway ->
            { isTwoPaneWidth ->
                AccountScreen(
                    isTwoPaneWidth = isTwoPaneWidth,
                    usernameFallback = activeState.username,
                    preferences = preferences,
                    onSelectLocale = onSelectLocale,
                    onSelectThemeMode = onSelectThemeMode,
                    sessionManager = sessionManager,
                    profileGateway = gateway,
                    recoveryGateway = recoveryGateway,
                    biometricController = biometricController,
                    biometricAuthenticator = biometricAuthenticator,
                    profileState = profileState,
                    onProfileStateChanged = { profileState = it },
                    selectedPane = selectedAccountPane,
                    onSelectPane = { selectedAccountPane = it },
                    showSinglePaneChangePassword = showSinglePaneChangePassword,
                    onShowSinglePaneChangePassword = { showSinglePaneChangePassword = it },
                )
            }
        }

        val isOffline = activeState.isOffline

        Box(modifier = Modifier.fillMaxSize()) {
            // Keep Thi360App at a stable slot in Column so toggling isOffline never unmounts the NavHost.
            Column(modifier = Modifier.fillMaxSize()) {
                if (isOffline) {
                    OfflineBannerSlot(
                        preferences = preferences,
                        brandPalette = brandPalette,
                        isRetrying = isRetryingOffline,
                        onRetry = {
                            if (!isRetryingOffline) {
                                isRetryingOffline = true
                                coroutineScope.launch {
                                    try {
                                        sessionManager.retryConnection()
                                    } finally {
                                        isRetryingOffline = false
                                    }
                                }
                            }
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .consumeWindowInsets(
                            if (isOffline) WindowInsets.statusBars else WindowInsets(0, 0, 0, 0),
                        ),
                ) {
                    Thi360App(
                        preferences = preferences,
                        brandPalette = brandPalette,
                        onSelectLocale = onSelectLocale,
                        onSelectThemeMode = onSelectThemeMode,
                        navController = shellNavController,
                        startDestination = startDestination ?: Destination.Home.route,
                        accountContent = resolvedAccountContent,
                        extraDestinations = extraDestinations,
                    )
                }
            }

            if (isAppBackgrounded && biometricState is BiometricState.On) {
                RecentsPrivacyShieldOverlay(preferences = preferences, brandPalette = brandPalette)
            }
        }
    }
}

@Composable
private fun RecentsPrivacyShieldOverlay(
    preferences: AppPreferences,
    brandPalette: BrandPalette,
) {
    Thi360Theme(
        preferences = preferences,
        brandPalette = brandPalette,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Thi360Theme.colors.bgPage)
                .testTag(LockTestTags.RECENTS_PRIVACY_SHIELD),
            contentAlignment = Alignment.Center,
        ) {
            BrandMark(sizeDp = 56)
        }
    }
}

@Composable
private fun OfflineBannerSlot(
    preferences: AppPreferences,
    brandPalette: BrandPalette,
    isRetrying: Boolean,
    onRetry: () -> Unit,
) {
    Thi360Theme(
        preferences = preferences,
        brandPalette = brandPalette,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Thi360Theme.colors.bgPage)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OfflineSessionBanner(
                isRetrying = isRetrying,
                onRetry = onRetry,
            )
        }
    }
}

private fun routeForSignedOut(reason: SignOutReason): String = when (reason) {
    is SignOutReason.StudentOnly -> LoginRoutes.ROLE_NOTICE
    SignOutReason.None, SignOutReason.Expired, SignOutReason.PasswordReset -> LoginRoutes.LOGIN
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    navLayout: NavLayout,
    isTwoPaneWidth: Boolean,
    preferences: AppPreferences,
    onSelectLocale: (AppLocale) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = Destination.Home.route,
    accountContent: (@Composable (isTwoPaneWidth: Boolean) -> Unit)? = null,
    extraDestinations: NavGraphBuilder.() -> Unit = {},
) {
    val showCompactBrandHeader = navLayout == NavLayout.BottomBar

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Destination.Home.route) {
            PlaceholderTabScreen(
                destination = Destination.Home,
                showCompactBrandHeader = showCompactBrandHeader,
            )
        }
        composable(Destination.MyExams.route) {
            PlaceholderTabScreen(
                destination = Destination.MyExams,
                showCompactBrandHeader = showCompactBrandHeader,
            )
        }
        composable(Destination.History.route) {
            PlaceholderTabScreen(
                destination = Destination.History,
                showCompactBrandHeader = showCompactBrandHeader,
            )
        }
        composable(Destination.Account.route) {
            if (accountContent != null) {
                accountContent(isTwoPaneWidth)
            } else {
                AccountShellPlaceholder(
                    isTwoPaneWidth = isTwoPaneWidth,
                    preferences = preferences,
                    onSelectLocale = onSelectLocale,
                    onSelectThemeMode = onSelectThemeMode,
                )
            }
        }
        extraDestinations()
    }
}
