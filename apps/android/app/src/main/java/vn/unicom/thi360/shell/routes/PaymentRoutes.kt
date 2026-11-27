package vn.unicom.thi360.shell.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import vn.unicom.thi360.shell.Destination

fun NavGraphBuilder.paymentGraph(
    tab: Destination,
    navigator: AppNavigator,
) {
    composable(
        route = AppRoute.tabRoute(tab, AppRoute.CHECKOUT_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_CODE) { type = NavType.StringType },
            navArgument(AppRoute.ARG_SEEN_VERSION_NO) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(AppRoute.ARG_SEEN_PRICE) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val args = backStackEntry.arguments
        val code = AppRoute.decodeSegment(args?.getString(AppRoute.ARG_CODE).orEmpty()).orEmpty()
        val seenVersionNo = args?.getString(AppRoute.ARG_SEEN_VERSION_NO)?.toIntOrNull()
        val seenPrice = args?.getString(AppRoute.ARG_SEEN_PRICE)?.toLongOrNull()
        val params = buildMap {
            put("code", code)
            seenVersionNo?.let { put("seenVersionNo", it.toString()) }
            seenPrice?.let { put("seenPrice", it.toString()) }
        }
        RoutePlaceholderScreen(
            routeName = "checkout",
            params = params,
            onBack = { navigator.back() },
        )
    }

    composable(
        route = AppRoute.tabRoute(tab, AppRoute.PAYMENT_RESULT_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_ORDER_CODE) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(AppRoute.ARG_LINK_TOKEN) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val args = backStackEntry.arguments
        val orderCode = args?.getString(AppRoute.ARG_ORDER_CODE)?.let { AppRoute.decodeSegment(it) }
        val linkToken = args?.getString(AppRoute.ARG_LINK_TOKEN)?.let { AppRoute.decodeSegment(it) }
        val hasReturnLink = !linkToken.isNullOrBlank() && navigator.linkStore.get(linkToken) != null
        val params = buildMap {
            if (!orderCode.isNullOrBlank()) {
                put("orderCode", orderCode)
            } else if (hasReturnLink) {
                put("source", "returnLink")
            }
        }
        RoutePlaceholderScreen(
            routeName = "payment-result",
            params = params,
            onBack = { navigator.back() },
        )
    }

    composable(AppRoute.tabRoute(tab, AppRoute.ORDERS_PATH)) {
        RoutePlaceholderScreen(
            routeName = "orders",
            onBack = { navigator.back() },
        )
    }

    composable(
        route = AppRoute.tabRoute(tab, AppRoute.ORDER_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_ORDER_CODE) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val orderCode = AppRoute.decodeSegment(
            backStackEntry.arguments?.getString(AppRoute.ARG_ORDER_CODE).orEmpty(),
        ).orEmpty()
        RoutePlaceholderScreen(
            routeName = "order",
            params = mapOf("orderCode" to orderCode),
            onBack = { navigator.back() },
        )
    }
}
