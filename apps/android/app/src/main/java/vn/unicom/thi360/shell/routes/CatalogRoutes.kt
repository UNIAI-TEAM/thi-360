package vn.unicom.thi360.shell.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import vn.unicom.thi360.feature.catalog.CatalogRoute
import vn.unicom.thi360.feature.catalog.ProductDetailRoute
import vn.unicom.thi360.shell.Destination

fun NavGraphBuilder.catalogGraph(
    tab: Destination,
    navigator: AppNavigator,
    showCompactBrandHeader: Boolean = false,
) {
    if (tab == Destination.Home) {
        composable(AppRoute.tabRoute(tab, AppRoute.CATALOG_PATH)) {
            CatalogRoute(
                navigator = navigator,
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
        ProductDetailRoute(
            code = code,
            navigator = navigator,
        )
    }
}
