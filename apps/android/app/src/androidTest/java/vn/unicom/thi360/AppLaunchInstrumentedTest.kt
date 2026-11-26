package vn.unicom.thi360

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App

@RunWith(AndroidJUnit4::class)
class AppLaunchInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldLaunchWithoutSessionShowsLoginScreenWithExpectedPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("vn.unicom.thi360", context.packageName)

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertDoesNotExist()
    }
}

@RunWith(AndroidJUnit4::class)
class ShellNavigationInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun authenticatedShellNavigatesAllFourDestinations() {
        composeRule.setContent { Thi360App() }

        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.MyExams)).assertIsDisplayed()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History)).assertIsDisplayed()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Account)).assertIsDisplayed()
    }
}
