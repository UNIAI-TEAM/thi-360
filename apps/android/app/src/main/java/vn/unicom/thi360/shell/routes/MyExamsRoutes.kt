package vn.unicom.thi360.shell.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.PlaceholderTabScreen

fun NavGraphBuilder.myExamsGraph(
    tab: Destination,
    navigator: AppNavigator,
    showCompactBrandHeader: Boolean = false,
) {
    if (tab == Destination.MyExams) {
        composable(AppRoute.tabRoute(tab, AppRoute.MY_EXAMS_PATH)) {
            PlaceholderTabScreen(
                destination = Destination.MyExams,
                showCompactBrandHeader = showCompactBrandHeader,
            )
        }
    }

    if (tab == Destination.History) {
        composable(
            route = AppRoute.tabRoute(tab, AppRoute.HISTORY_PATTERN),
            arguments = listOf(
                navArgument(AppRoute.ARG_RESET_FILTERS) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) {
            PlaceholderTabScreen(
                destination = Destination.History,
                showCompactBrandHeader = showCompactBrandHeader,
            )
        }
    }
}
