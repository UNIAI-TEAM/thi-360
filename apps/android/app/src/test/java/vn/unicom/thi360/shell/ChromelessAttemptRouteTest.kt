package vn.unicom.thi360.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.RoutePlaceholderTestTags

@RunWith(RobolectricTestRunner::class)
class ChromelessAttemptRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun compactHidesBottomBarDuringAttemptAndRestoresOriginTabOnCloseOrResult() {
        var navigator: AppNavigator? = null

        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navigator = appNav
            Thi360App(
                preferences = AppPreferences(locale = AppLocale.VI),
                navController = navController,
                navigator = appNav,
            )
        }

        val nav = checkNotNull(navigator)
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()

        // Navigate to MyExams, open attempt -> bottom bar and rail disappear.
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { nav.openAttempt("att-compact-1") }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(vn.unicom.thi360.feature.player.PlayerTestTags.SCREEN).assertIsDisplayed()
        org.junit.Assert.assertEquals(
            "att-compact-1",
            nav.navController.currentBackStackEntry?.arguments?.getString(vn.unicom.thi360.shell.routes.AppRoute.ARG_ATTEMPT_ID),
        )
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertDoesNotExist()

        // Closing attempt brings bottom bar back on MyExams.
        composeRule.runOnIdle { nav.closeAttempt() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).assertIsSelected()

        // Re-open attempt and finish via openResultFromAttempt -> shows result in MyExams with bottom bar visible.
        composeRule.runOnIdle { nav.openAttempt("att-compact-2") }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()

        composeRule.runOnIdle { nav.openResultFromAttempt("att-compact-2") }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).assertIsSelected()
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.result.ResultTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.result.ResultTestTags.screenFor("att-compact-2")).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletHidesNavRailDuringAttemptAndRestoresOriginTabOnClose() {
        var navigator: AppNavigator? = null

        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navigator = appNav
            Thi360App(
                preferences = AppPreferences(locale = AppLocale.VI),
                navController = navController,
                navigator = appNav,
            )
        }

        val nav = checkNotNull(navigator)
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertIsDisplayed()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { nav.openAttempt("att-tablet-1") }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(vn.unicom.thi360.feature.player.PlayerTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()

        composeRule.runOnIdle { nav.closeAttempt() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).assertIsSelected()
    }
}
