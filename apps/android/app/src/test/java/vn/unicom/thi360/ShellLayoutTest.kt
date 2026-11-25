package vn.unicom.thi360

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
    fun breakpointMathMatchesSpec() {
        assertEquals(NavLayout.BottomBar, navLayoutForWidthDp(360))
        assertEquals(NavLayout.BottomBar, navLayoutForWidthDp(599))
        assertEquals(NavLayout.Rail, navLayoutForWidthDp(600))
        assertEquals(NavLayout.Rail, navLayoutForWidthDp(840))
        assertEquals(NavLayout.Rail, navLayoutForWidthDp(1280))

        assertFalse(isTwoPaneWidthDp(599))
        assertFalse(isTwoPaneWidthDp(839))
        assertTrue(isTwoPaneWidthDp(840))
        assertTrue(isTwoPaneWidthDp(1280))
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
}
