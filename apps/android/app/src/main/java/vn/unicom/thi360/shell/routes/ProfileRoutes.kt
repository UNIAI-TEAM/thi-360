package vn.unicom.thi360.shell.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import vn.unicom.thi360.shell.Destination

fun NavGraphBuilder.profileGraph(
    tab: Destination,
    navigator: AppNavigator,
) {
    composable(AppRoute.tabRoute(tab, AppRoute.PROFILE_PATH)) {
        RoutePlaceholderScreen(
            routeName = "profile",
            onBack = { navigator.back() },
        )
    }

    composable(AppRoute.tabRoute(tab, AppRoute.PROFILE_VERIFY_EMAIL_PATH)) {
        RoutePlaceholderScreen(
            routeName = "profile/verify-email",
            onBack = { navigator.back() },
        )
    }

    composable(AppRoute.tabRoute(tab, AppRoute.DELETE_ACCOUNT_PATH)) {
        RoutePlaceholderScreen(
            routeName = "account/delete",
            onBack = { navigator.back() },
        )
    }
}
