package vn.unicom.thi360.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import vn.unicom.thi360.R
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.core.theme.Thi360Theme

@Composable
fun Thi360App(
    preferences: AppPreferences = AppPreferences(),
    brandPalette: BrandPalette = BrandPalette.Default,
    onSelectLocale: (AppLocale) -> Unit = {},
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    navController: NavHostController = rememberNavController(),
    startDestination: String = Destination.Home.route,
    accountContent: (@Composable (isTwoPaneWidth: Boolean) -> Unit)? = null,
) {
    Thi360Theme(
        preferences = preferences,
        brandPalette = brandPalette,
    ) {
        Thi360Shell(
            navController = navController,
            preferences = preferences,
            onSelectLocale = onSelectLocale,
            onSelectThemeMode = onSelectThemeMode,
            startDestination = startDestination,
            accountContent = accountContent,
        )
    }
}

private data class ShellPaneInput(
    val layout: NavLayout,
    val isTwoPane: Boolean,
    val preferences: AppPreferences,
    val onSelectLocale: (AppLocale) -> Unit,
    val onSelectThemeMode: (ThemeMode) -> Unit,
    val startDestination: String,
    val accountContent: (@Composable (Boolean) -> Unit)?,
)

@Composable
fun Thi360Shell(
    navController: NavHostController,
    preferences: AppPreferences,
    onSelectLocale: (AppLocale) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = Destination.Home.route,
    accountContent: (@Composable (isTwoPaneWidth: Boolean) -> Unit)? = null,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = Destination.fromRoute(backStackEntry?.destination?.route ?: startDestination)

    val navigateTo: (Destination) -> Unit = { target ->
        if (target != currentDestination) {
            navController.navigate(target.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val navContent = remember(navController) {
        movableContentOf<ShellPaneInput> { input ->
            if (input.accountContent != null) {
                AppNavHost(
                    navController = navController,
                    navLayout = input.layout,
                    isTwoPaneWidth = input.isTwoPane,
                    preferences = input.preferences,
                    onSelectLocale = input.onSelectLocale,
                    onSelectThemeMode = input.onSelectThemeMode,
                    startDestination = input.startDestination,
                    accountContent = input.accountContent,
                )
            } else {
                AppNavHost(
                    navController = navController,
                    navLayout = input.layout,
                    isTwoPaneWidth = input.isTwoPane,
                    preferences = input.preferences,
                    onSelectLocale = input.onSelectLocale,
                    onSelectThemeMode = input.onSelectThemeMode,
                    startDestination = input.startDestination,
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Thi360Theme.colors.bgPage),
    ) {
        val availableWidthDp = maxWidth.value.toInt()
        val navLayout = navLayoutForWidthDp(availableWidthDp)
        val isTwoPane = isTwoPaneWidthDp(availableWidthDp)
        val colors = Thi360Theme.colors
        val navDescription = stringResource(R.string.nav_primary_description)
        val paneInput = ShellPaneInput(
            layout = navLayout,
            isTwoPane = isTwoPane,
            preferences = preferences,
            onSelectLocale = onSelectLocale,
            onSelectThemeMode = onSelectThemeMode,
            startDestination = startDestination,
            accountContent = accountContent,
        )

        when (navLayout) {
            NavLayout.BottomBar -> {
                Scaffold(
                    containerColor = colors.bgPage,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        NavigationBar(
                            containerColor = colors.bgSurface,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .testTag(ShellTestTags.BOTTOM_BAR)
                                .semantics { contentDescription = navDescription },
                        ) {
                            Destination.entries.forEach { destination ->
                                val selected = destination == currentDestination
                                val label = stringResource(destination.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(destination) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(destination.iconRes),
                                            contentDescription = null,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colors.primaryText,
                                        selectedTextColor = colors.primaryText,
                                        indicatorColor = colors.primaryContainer,
                                        unselectedIconColor = colors.textMuted,
                                        unselectedTextColor = colors.textMuted,
                                    ),
                                    modifier = Modifier.testTag(ShellTestTags.navItem(destination)),
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        navContent(paneInput)
                    }
                }
            }

            NavLayout.Rail -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    NavigationRail(
                        containerColor = colors.bgPage,
                        header = {
                            Spacer(modifier = Modifier.height(12.dp))
                            BrandMark(sizeDp = 36)
                            Spacer(modifier = Modifier.height(8.dp))
                        },
                        modifier = Modifier
                            .width(96.dp)
                            .fillMaxHeight()
                            .testTag(ShellTestTags.NAV_RAIL)
                            .semantics { contentDescription = navDescription },
                    ) {
                        Destination.entries.forEach { destination ->
                            val selected = destination == currentDestination
                            val label = stringResource(destination.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(destination) },
                                icon = {
                                    Icon(
                                        painter = painterResource(destination.iconRes),
                                        contentDescription = null,
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 2,
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = colors.primaryText,
                                    selectedTextColor = colors.primaryText,
                                    indicatorColor = colors.primaryContainer,
                                    unselectedIconColor = colors.textMuted,
                                    unselectedTextColor = colors.textMuted,
                                ),
                                modifier = Modifier.testTag(ShellTestTags.navItem(destination)),
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = 16.dp, end = 16.dp, bottom = 16.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = colors.bgSurface,
                    ) {
                        navContent(paneInput)
                    }
                }
            }
        }
    }
}
