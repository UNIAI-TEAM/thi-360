package vn.unicom.thi360.shell.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import vn.unicom.thi360.shell.Destination

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
        RoutePlaceholderScreen(
            routeName = "prepare",
            params = mapOf("entitlementId" to entitlementId),
            onBack = { navigator.back() },
        )
    }
}
