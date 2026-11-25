package vn.unicom.thi360

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.NavLayout
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.shell.isTwoPaneWidthDp
import vn.unicom.thi360.shell.navLayoutForWidthDp

@RunWith(RobolectricTestRunner::class)
class ShellLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun breakpointMathAndRouteResolutionMatchSpec() {
        assertEquals(NavLayout.BottomBar, navLayoutForWidthDp(360))
        assertEquals(NavLayout.BottomBar, navLayoutForWidthDp(599))
        assertEquals(NavLayout.Rail, navLayoutForWidthDp(600))
        assertEquals(NavLayout.Rail, navLayoutForWidthDp(840))
        assertEquals(NavLayout.Rail, navLayoutForWidthDp(1280))

        assertFalse(isTwoPaneWidthDp(599))
        assertFalse(isTwoPaneWidthDp(839))
        assertTrue(isTwoPaneWidthDp(840))
        assertTrue(isTwoPaneWidthDp(1280))

        assertEquals(Destination.Home, Destination.fromRouteOrNull("home"))
        assertNull(Destination.fromRouteOrNull("login"))
        assertNull(Destination.fromRouteOrNull("lock"))
        assertNull(Destination.fromRouteOrNull(null))
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun compactPhoneWidthShowsBottomBarWithFourDestinations() {
        composeRule.setContent { Thi360App() }

        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertDoesNotExist()
        Destination.entries.forEach { destination ->
            composeRule.onNodeWithTag(ShellTestTags.navItem(destination)).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun unfoldedWidthShowsNavigationRailAndTwoPaneAccountSlot() {
        composeRule.setContent { Thi360App() }

        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_TWO_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_SINGLE_PANE).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletWidthShowsNavigationRail() {
        composeRule.setContent { Thi360App() }

        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "sw600dp-w1280dp-h800dp")
    fun splitScreenCompact() {
        composeRule.setContent {
            Box(modifier = Modifier.size(width = 400.dp, height = 800.dp)) {
                Thi360App()
            }
        }

        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertDoesNotExist()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).assertIsSelected()
        composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_SINGLE_PANE).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun chromelessExtraDestinationHidesNavigationChromeOnCompactAndTransitionsToHome() {
        composeRule.setContent {
            val navController = rememberNavController()
            Thi360App(
                navController = navController,
                startDestination = "login",
                extraDestinations = {
                    composable("login") {
                        Button(
                            onClick = { navController.navigate(Destination.Home.route) },
                            modifier = Modifier.testTag("standalone_login_enter"),
                        ) {
                            Text("Sign in")
                        }
                    }
                },
            )
        }

        composeRule.onNodeWithTag("standalone_login_enter").assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertDoesNotExist()

        composeRule.onNodeWithTag("standalone_login_enter").performClick()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun chromelessExtraDestinationHidesNavigationRailOnExpandedWidth() {
        composeRule.setContent {
            Thi360App(
                startDestination = "lock",
                extraDestinations = {
                    composable("lock") {
                        Text(
                            text = "Locked",
                            modifier = Modifier.testTag("standalone_lock_screen"),
                        )
                    }
                },
            )
        }

        composeRule.onNodeWithTag("standalone_lock_screen").assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun navigatingFromNonHomeTabToHomeNeverDropsClick() {
        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(),
                onSelectLocale = {},
                onSelectThemeMode = {},
                startDestination = Destination.Account.route,
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).assertIsSelected()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).assertIsNotSelected()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).assertIsSelected()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()
    }
}
