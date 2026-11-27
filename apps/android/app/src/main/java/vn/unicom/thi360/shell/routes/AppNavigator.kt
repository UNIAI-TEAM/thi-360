package vn.unicom.thi360.shell.routes

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import vn.unicom.thi360.core.legal.LegalId
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.shellDestination

val LocalAppNavigator = staticCompositionLocalOf<AppNavigator?> { null }

class AppNavigator(
    val navController: NavHostController,
    val linkStore: PaymentReturnLinkStore = PaymentReturnLinkStore.Default,
    private val authLegalOnly: Boolean = false,
) {
    private var attemptOriginTab: Destination? = null
    private var lastKnownTab: Destination = Destination.Home

    private val _reloadCounters = MutableStateFlow<Map<Destination, Long>>(emptyMap())
    val reloadCounters: StateFlow<Map<Destination, Long>> = _reloadCounters.asStateFlow()

    fun currentTab(): Destination {
        val resolved = navController.currentDestination.shellDestination()
            ?: inferOriginTabFromBackStack()
            ?: lastKnownTab
        lastKnownTab = resolved
        return resolved
    }

    fun openProduct(code: String) {
        val route = AppRoute.Product(code)
        navigateInTab(currentTab(), route)
    }

    fun openCheckout(
        code: String,
        seenVersionNo: Int? = null,
        seenPrice: Long? = null,
    ) {
        val route = AppRoute.Checkout(
            code = code,
            seenVersionNo = seenVersionNo,
            seenPrice = seenPrice,
        )
        navigateInTab(currentTab(), route)
    }

    fun openPaymentResult(result: AppRoute.PaymentResult) {
        navigateInTab(currentTab(), result)
    }

    fun openOrders() {
        navigateInTab(currentTab(), AppRoute.Orders)
    }

    fun openOrder(orderCode: String) {
        navigateInTab(currentTab(), AppRoute.Order(orderCode))
    }

    fun openPrepare(entitlementId: String) {
        navigateInTab(currentTab(), AppRoute.Prepare(entitlementId))
    }

    fun openResult(attemptId: String) {
        navigateInTab(currentTab(), AppRoute.Result(attemptId))
    }

    fun openReview(attemptId: String) {
        navigateInTab(currentTab(), AppRoute.Review(attemptId))
    }

    fun openProfile() {
        navigateInTab(Destination.Account, AppRoute.Profile)
    }

    fun openProfileVerifyEmail() {
        navigateInTab(Destination.Account, AppRoute.ProfileVerifyEmail)
    }

    fun openDeleteAccount() {
        navigateInTab(Destination.Account, AppRoute.DeleteAccount)
    }

    fun openLegal(id: LegalId) {
        val route = AppRoute.Legal(id)
        if (authLegalOnly) {
            navController.navigate(AppRoute.buildSubPath(route, linkStore)) {
                launchSingleTop = true
            }
        } else {
            navigateInTab(Destination.Account, route)
        }
    }

    fun openAttempt(attemptId: String) {
        val route = AppRoute.Attempt(attemptId)
        attemptOriginTab = currentTab()
        navController.navigate(AppRoute.buildSubPath(route, linkStore)) {
            launchSingleTop = false
        }
    }

    fun openAttemptAfterStart(attemptId: String) {
        val route = AppRoute.Attempt(attemptId)
        val originTab = currentTab()
        attemptOriginTab = originTab
        popPrepareRoutesInCurrentTab(originTab)
        navController.navigate(AppRoute.buildSubPath(route, linkStore)) {
            launchSingleTop = false
        }
    }

    fun replaceAttempt(attemptId: String) {
        val route = AppRoute.Attempt(attemptId)
        if (attemptOriginTab == null) {
            attemptOriginTab = inferOriginTabFromBackStack()
        }
        val target = AppRoute.buildSubPath(route, linkStore)
        if (isCurrentRouteAttempt()) {
            navController.navigate(target) {
                popUpTo(AppRoute.ATTEMPT_PATTERN) {
                    inclusive = true
                }
            }
        } else {
            navController.navigate(target)
        }
    }

    fun closeAttempt() {
        while (isCurrentRouteAttempt()) {
            if (!navController.popBackStack()) {
                break
            }
        }
        attemptOriginTab = null
    }

    fun openResultFromAttempt(attemptId: String) {
        val route = AppRoute.Result(attemptId)
        val originTab = attemptOriginTab ?: inferOriginTabFromBackStack() ?: Destination.MyExams
        while (isCurrentRouteAttempt()) {
            if (!navController.popBackStack()) {
                break
            }
        }
        popPrepareRoutesInCurrentTab(originTab)
        attemptOriginTab = null
        navigateInTab(originTab, route)
    }

    fun switchTab(destination: Destination, reload: Boolean = false) {
        lastKnownTab = destination
        val activeTab = navController.currentDestination.shellDestination()
        val isOnAttempt = isCurrentRouteAttempt()
        if (isOnAttempt) {
            closeAttempt()
        }

        if (reload) {
            val targetRoot = AppRoute.rootRouteFor(destination)
            if (activeTab == destination && !isOnAttempt) {
                if (destination == Destination.History) {
                    navController.popBackStack(destination.route, inclusive = true)
                    navController.navigate(AppRoute.tabRoute(destination, "history?${AppRoute.ARG_RESET_FILTERS}=true")) {
                        launchSingleTop = true
                    }
                } else {
                    navController.popBackStack(targetRoot, inclusive = false)
                }
            } else {
                navController.clearBackStack(destination.route)
                navController.navigate(destination.route) {
                    popUpTo(navController.graph.id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = false
                }
                if (destination == Destination.History) {
                    val currentHistoryRoute = navController.currentDestination?.route
                    if (currentHistoryRoute != null) {
                        navController.navigate(
                            AppRoute.tabRoute(destination, "history?${AppRoute.ARG_RESET_FILTERS}=true"),
                        ) {
                            popUpTo(currentHistoryRoute) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }
            _reloadCounters.update { current ->
                current + (destination to ((current[destination] ?: 0L) + 1L))
            }
        } else {
            if (activeTab != destination) {
                navController.navigate(destination.route) {
                    popUpTo(navController.graph.id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    fun back(): Boolean = navController.popBackStack()

    private fun navigateInTab(tab: Destination, route: AppRoute) {
        if (navController.currentDestination.shellDestination() != tab) {
            switchTab(tab, reload = false)
        }
        lastKnownTab = tab
        val target = AppRoute.tabRoute(tab, AppRoute.buildSubPath(route, linkStore))
        navController.navigate(target)
    }

    private fun isCurrentRouteAttempt(): Boolean =
        navController.currentDestination?.route == AppRoute.ATTEMPT_PATTERN

    private fun popPrepareRoutesInCurrentTab(tab: Destination) {
        val preparePattern = AppRoute.tabRoute(tab, AppRoute.PREPARE_PATTERN)
        while (navController.currentDestination?.route == preparePattern) {
            if (!navController.popBackStack()) {
                break
            }
        }
    }

    private fun inferOriginTabFromBackStack(): Destination? =
        navController.previousBackStackEntry?.destination.shellDestination() ?: lastKnownTab
}
