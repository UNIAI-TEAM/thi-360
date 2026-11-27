package vn.unicom.thi360.shell.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import vn.unicom.thi360.shell.Destination

fun NavGraphBuilder.resultGraph(
    tab: Destination,
    navigator: AppNavigator,
) {
    composable(
        route = AppRoute.tabRoute(tab, AppRoute.RESULT_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_ATTEMPT_ID) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val attemptId = AppRoute.decodeSegment(
            backStackEntry.arguments?.getString(AppRoute.ARG_ATTEMPT_ID).orEmpty(),
        ).orEmpty()
        RoutePlaceholderScreen(
            routeName = "result",
            params = mapOf("attemptId" to attemptId),
            onBack = { navigator.back() },
        )
    }

    composable(
        route = AppRoute.tabRoute(tab, AppRoute.REVIEW_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_ATTEMPT_ID) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val attemptId = AppRoute.decodeSegment(
            backStackEntry.arguments?.getString(AppRoute.ARG_ATTEMPT_ID).orEmpty(),
        ).orEmpty()
        RoutePlaceholderScreen(
            routeName = "review",
            params = mapOf("attemptId" to attemptId),
            onBack = { navigator.back() },
        )
    }
}
