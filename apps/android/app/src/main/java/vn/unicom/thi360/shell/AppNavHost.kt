package vn.unicom.thi360.shell

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.login.LoginRoutes
import vn.unicom.thi360.feature.login.LoginScreen
import vn.unicom.thi360.feature.login.OfflineSessionBanner
import vn.unicom.thi360.feature.login.RoleNoticeScreen

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
    onForgotPasswordClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
) {
    // Preserve direct shell entry when callers pin an explicit startDestination without a SessionManager.
    if (startDestination != null && sessionManager == null) {
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
    val activeSessionManager = remember(sessionManager, context) {
        sessionManager ?: AppSessionContainer.get(context).sessionManager
    }

    SideEffect {
        AppSessionContainer.setLocale(preferences.effectiveLocale)
    }

    val sessionStateHolder = activeSessionManager.state.collectAsStateWithLifecycle()
    val sessionState = sessionStateHolder.value
    val latestSelectLocale by rememberUpdatedState(onSelectLocale)
    val latestForgotPasswordClick by rememberUpdatedState(onForgotPasswordClick)
    val latestRegisterClick by rememberUpdatedState(onRegisterClick)
    val coroutineScope = rememberCoroutineScope()
    var isRetryingOffline by remember { mutableStateOf(false) }

    LaunchedEffect(activeSessionManager, validateOnLaunch) {
        if (validateOnLaunch) {
            activeSessionManager.validateSessionOnLaunch()
        }
    }

    // Separate signed-out auth NavHost from authenticated Thi360App so shell graph startDestination
    // stays pinned to Home (preserving tab saveState/restoreState) and sign-out disposes prior tab state.
    if (sessionState is SessionState.SignedOut) {
        val authNavController = rememberNavController()
        val initialAuthRoute = remember(activeSessionManager) {
            routeForSignedOut(sessionState.reason)
        }
        LaunchedEffect(sessionState.reason, authNavController) {
            val targetRoute = routeForSignedOut(sessionState.reason)
            if (authNavController.currentDestination?.route != targetRoute) {
                authNavController.navigate(targetRoute) {
                    popUpTo(0) { inclusive = true }
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
                        val currentSignOutReason =
                            (sessionStateHolder.value as? SessionState.SignedOut)?.reason
                                ?: SignOutReason.None
                        LoginScreen(
                            sessionManager = activeSessionManager,
                            signOutReason = currentSignOutReason,
                            onSelectLocale = { latestSelectLocale(it) },
                            onForgotPasswordClick = { latestForgotPasswordClick() },
                            onRegisterClick = { latestRegisterClick() },
                        )
                    }
                    composable(LoginRoutes.ROLE_NOTICE) {
                        val studentOnly =
                            (sessionStateHolder.value as? SessionState.SignedOut)?.reason
                                as? SignOutReason.StudentOnly
                        RoleNoticeScreen(
                            username = studentOnly?.username.orEmpty(),
                            onSwitchAccount = { activeSessionManager.acknowledgeRoleNotice() },
                            onSelectLocale = { latestSelectLocale(it) },
                        )
                    }
                    extraDestinations()
                }
            }
        }
        return
    }

    val shellNavController = navController ?: rememberNavController()
    DisposableEffect(shellNavController) {
        onDispose {
            if (navController != null) {
                Destination.entries.forEach { destination ->
                    shellNavController.clearBackStack(destination.route)
                }
                shellNavController.popBackStack(
                    destinationId = shellNavController.graph.id,
                    inclusive = true,
                )
            }
        }
    }

    val isOffline = when (sessionState) {
        is SessionState.Active -> sessionState.isOffline
        is SessionState.MustChangePassword -> sessionState.isOffline
        is SessionState.Locked -> false
    }

    // Keep Thi360App at a stable slot in Column so toggling isOffline never unmounts the NavHost.
    Column(modifier = Modifier.fillMaxSize()) {
        if (isOffline) {
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
                        isRetrying = isRetryingOffline,
                        onRetry = {
                            if (!isRetryingOffline) {
                                isRetryingOffline = true
                                coroutineScope.launch {
                                    try {
                                        activeSessionManager.retryConnection()
                                    } finally {
                                        isRetryingOffline = false
                                    }
                                }
                            }
                        },
                    )
                }
            }
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
                accountContent = accountContent,
                extraDestinations = extraDestinations,
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
