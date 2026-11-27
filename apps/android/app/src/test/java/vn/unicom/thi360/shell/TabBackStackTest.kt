package vn.unicom.thi360.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class TabBackStackTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun tabSwitchPreservesSubRouteScrollAndInputAndSameRouteIsScopedPerTab() {
        var navigator: AppNavigator? = null
        var scrollRequest by mutableStateOf<Int?>(null)
        var observedFirstVisible = 0

        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navigator = appNav
            Thi360App(
                preferences = AppPreferences(locale = AppLocale.VI),
                navController = navController,
                navigator = appNav,
                homeExtraRoutes = {
                    composable("home/test_form/{code}") { entry ->
                        val code = entry.arguments?.getString("code").orEmpty()
                        var note by rememberSaveable { mutableStateOf("") }
                        val listState = rememberLazyListState()
                        LaunchedEffect(scrollRequest) {
                            val idx = scrollRequest
                            if (idx != null) {
                                listState.scrollToItem(idx)
                                scrollRequest = null
                            }
                        }
                        observedFirstVisible = listState.firstVisibleItemIndex
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(text = "form:$code", modifier = Modifier.testTag("home_form_code"))
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier.testTag("home_form_input"),
                            )
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(30) { i ->
                                    Text("Row $i", modifier = Modifier.fillMaxWidth().height(48.dp))
                                }
                            }
                        }
                    }
                },
            )
        }

        val nav = checkNotNull(navigator)

        // Open sub-route inside Home, type input and scroll.
        composeRule.runOnIdle { nav.navController.navigate("home/test_form/X") }
        composeRule.onNodeWithTag("home_form_code").assertTextContains("form:X")
        composeRule.onNodeWithTag("home_form_input").performTextInput("saved-home-draft")
        composeRule.runOnIdle { scrollRequest = 10 }
        composeRule.waitForIdle()
        assertTrue("Expected scrolled list index >= 5, was $observedFirstVisible", observedFirstVisible >= 5)

        // Switch to MyExams and open result/ATT-1 inside MyExams.
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { nav.openResult("ATT-1") }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.result.ResultTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.result.ResultTestTags.screenFor("ATT-1")).assertIsDisplayed()

        // Switch to History and open result/ATT-2 inside History.
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { nav.openResult("ATT-2") }
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.result.ResultTestTags.screenFor("ATT-2")).assertIsDisplayed()

        // Back from History's result returns to History root.
        composeRule.runOnIdle { nav.back() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History)).assertIsDisplayed()

        // Switch back to MyExams -> still on ATT-1; back returns to MyExams root.
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.result.ResultTestTags.screenFor("ATT-1")).assertIsDisplayed()
        composeRule.runOnIdle { nav.back() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.MyExams)).assertIsDisplayed()

        // Switch back to Home -> sub-route X restored with exact typed text & scroll position.
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_form_code").assertIsDisplayed().assertTextContains("form:X")
        composeRule.onNodeWithTag("home_form_input").assertTextContains("saved-home-draft")
        assertTrue("Home scroll index must be restored, was $observedFirstVisible", observedFirstVisible >= 5)

        // switchTab(Home, reload = true) pops Home stack to root and sends exactly one reload signal.
        assertEquals(null, nav.reloadCounters.value[Destination.Home])
        composeRule.runOnIdle { nav.switchTab(Destination.Home, reload = true) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).assertIsSelected()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()
        assertEquals(1L, nav.reloadCounters.value[Destination.Home])
    }
}
