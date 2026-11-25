package vn.unicom.thi360.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode

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
    accountContent: @Composable (isTwoPaneWidth: Boolean) -> Unit = { twoPane ->
        AccountShellPlaceholder(
            isTwoPaneWidth = twoPane,
            preferences = preferences,
            onSelectLocale = onSelectLocale,
            onSelectThemeMode = onSelectThemeMode,
        )
    },
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
            accountContent(isTwoPaneWidth)
        }
        extraDestinations()
    }
}
