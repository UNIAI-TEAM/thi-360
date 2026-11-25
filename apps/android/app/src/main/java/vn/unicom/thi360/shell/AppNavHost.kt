package vn.unicom.thi360.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
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
    navController: NavHostController = rememberNavController(),
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
            navController = navController,
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

    val initialDestination = remember(activeSessionManager, startDestination) {
        routeForSessionState(
            state = activeSessionManager.state.value,
            authenticatedFallback = startDestination ?: Destination.Home.route,
        )
    }

    LaunchedEffect(sessionState, navController) {
        val currentRoute = navController.currentDestination?.route
        val targetRoute = when (sessionState) {
            is SessionState.SignedOut -> routeForSessionState(
                state = sessionState,
                authenticatedFallback = Destination.Home.route,
            )

            is SessionState.Active, is SessionState.MustChangePassword -> {
                if (currentRoute == LoginRoutes.LOGIN || currentRoute == LoginRoutes.ROLE_NOTICE) {
                    startDestination ?: Destination.Home.route
                } else {
                    null
                }
            }

            is SessionState.Locked -> null
        }

        if (targetRoute != null && currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val isOffline = when (val state = sessionState) {
        is SessionState.Active -> state.isOffline
        is SessionState.MustChangePassword -> state.isOffline
        is SessionState.SignedOut, is SessionState.Locked -> false
    }

    val authDestinations: NavGraphBuilder.() -> Unit = {
        composable(LoginRoutes.LOGIN) {
            val currentSignOutReason = (sessionStateHolder.value as? SessionState.SignedOut)?.reason
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
            val studentOnly = (sessionStateHolder.value as? SessionState.SignedOut)?.reason
                as? SignOutReason.StudentOnly
            RoleNoticeScreen(
                username = studentOnly?.username.orEmpty(),
                onSwitchAccount = { activeSessionManager.acknowledgeRoleNotice() },
                onSelectLocale = { latestSelectLocale(it) },
            )
        }
        extraDestinations()
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
                .fillMaxWidth(),
        ) {
            Thi360App(
                preferences = preferences,
                brandPalette = brandPalette,
                onSelectLocale = onSelectLocale,
                onSelectThemeMode = onSelectThemeMode,
                navController = navController,
                startDestination = initialDestination,
                accountContent = accountContent,
                extraDestinations = authDestinations,
            )
        }
    }
}

private fun routeForSessionState(
    state: SessionState,
    authenticatedFallback: String,
): String = when (state) {
    is SessionState.SignedOut -> when (state.reason) {
        is SignOutReason.StudentOnly -> LoginRoutes.ROLE_NOTICE
        SignOutReason.None, SignOutReason.Expired, SignOutReason.PasswordReset -> LoginRoutes.LOGIN
    }

    is SessionState.Active, is SessionState.MustChangePassword, is SessionState.Locked -> authenticatedFallback
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
