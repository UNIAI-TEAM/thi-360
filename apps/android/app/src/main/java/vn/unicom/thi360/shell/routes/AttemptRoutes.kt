package vn.unicom.thi360.shell.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

fun NavGraphBuilder.attemptGraph(
    navigator: AppNavigator,
) {
    composable(
        route = AppRoute.ATTEMPT_PATTERN,
        arguments = listOf(
            navArgument(AppRoute.ARG_ATTEMPT_ID) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val attemptId = AppRoute.decodeSegment(
            backStackEntry.arguments?.getString(AppRoute.ARG_ATTEMPT_ID).orEmpty(),
        ).orEmpty()
        RoutePlaceholderScreen(
            routeName = "attempt",
            params = mapOf("attemptId" to attemptId),
            onBack = { navigator.closeAttempt() },
        )
    }
}
