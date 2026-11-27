package vn.unicom.thi360.shell.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.PlaceholderTabScreen

fun NavGraphBuilder.catalogGraph(
    tab: Destination,
    navigator: AppNavigator,
    showCompactBrandHeader: Boolean = false,
) {
    if (tab == Destination.Home) {
        composable(AppRoute.tabRoute(tab, AppRoute.CATALOG_PATH)) {
            PlaceholderTabScreen(
                destination = Destination.Home,
                showCompactBrandHeader = showCompactBrandHeader,
            )
        }
    }

    composable(
        route = AppRoute.tabRoute(tab, AppRoute.PRODUCT_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_CODE) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val rawCode = backStackEntry.arguments?.getString(AppRoute.ARG_CODE).orEmpty()
        val code = AppRoute.decodeSegment(rawCode).orEmpty()
        RoutePlaceholderScreen(
            routeName = "product",
            params = mapOf("code" to code),
            onBack = { navigator.back() },
        )
    }
}
