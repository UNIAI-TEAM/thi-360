package vn.unicom.thi360

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.LocalAppNavigator

@RunWith(RobolectricTestRunner::class)
class StateRestorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun rotationAndInstanceStateRestorePreservesDestinationAndDraftInput() {
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            Thi360App(
                accountContent = { isTwoPane ->
                    var draft by rememberSaveable { mutableStateOf("") }
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = if (isTwoPane) "two-pane" else "single-pane")
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.testTag(DRAFT_INPUT_TAG),
                        )
                    }
                },
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.onNodeWithTag(DRAFT_INPUT_TAG).performTextInput("matkhau-dang-nhap")
        composeRule.onNodeWithTag(DRAFT_INPUT_TAG).assertTextContains("matkhau-dang-nhap")

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).assertIsSelected()
        composeRule.onNodeWithTag(DRAFT_INPUT_TAG)
            .assertIsDisplayed()
            .assertTextContains("matkhau-dang-nhap")
    }

    @Test
    @Config(qualifiers = "w1280dp-h915dp")
    fun foldUnfoldAndWindowResizePreserveDestinationAndDraftInput() {
        var windowWidthDp by mutableStateOf(412.dp)
        var windowHeightDp by mutableStateOf(915.dp)

        composeRule.setContent {
            Box(modifier = Modifier.size(width = windowWidthDp, height = windowHeightDp)) {
                Thi360App(
                    accountContent = { isTwoPane ->
                        var draft by rememberSaveable { mutableStateOf("") }
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (isTwoPane) "two-pane" else "single-pane",
                                modifier = Modifier.testTag(PANE_MODE_TAG),
                            )
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier.testTag(DRAFT_INPUT_TAG),
                            )
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.onNodeWithTag(DRAFT_INPUT_TAG).performTextInput("ghi-chu-gap-mo")

        composeRule.runOnIdle {
            windowWidthDp = 840.dp
            windowHeightDp = 720.dp
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).assertIsSelected()
        composeRule.onNodeWithTag(PANE_MODE_TAG).assertTextContains("two-pane")
        composeRule.onNodeWithTag(DRAFT_INPUT_TAG).assertTextContains("ghi-chu-gap-mo")

        composeRule.runOnIdle {
            windowWidthDp = 1280.dp
            windowHeightDp = 800.dp
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(DRAFT_INPUT_TAG).assertTextContains("ghi-chu-gap-mo")

        composeRule.runOnIdle {
            windowWidthDp = 360.dp
            windowHeightDp = 640.dp
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).assertIsSelected()
        composeRule.onNodeWithTag(PANE_MODE_TAG).assertTextContains("single-pane")
        composeRule.onNodeWithTag(DRAFT_INPUT_TAG).assertTextContains("ghi-chu-gap-mo")
    }

    @Test
    @Config(qualifiers = "w1280dp-h915dp")
    fun portalRouteSurvivesRotationFoldResize() {
        var windowWidthDp by mutableStateOf(412.dp)
        var windowHeightDp by mutableStateOf(915.dp)
        var observedNavigator: AppNavigator? = null
        var scrollToIndex by mutableStateOf<Int?>(null)
        var lastVisibleFirstIndex = 0
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            Box(modifier = Modifier.size(width = windowWidthDp, height = windowHeightDp)) {
                Thi360App(
                    extraDestinations = {
                        composable(
                            route = "portal_test_sub/{code}",
                            arguments = listOf(navArgument("code") { type = NavType.StringType }),
                        ) { entry ->
                            observedNavigator = LocalAppNavigator.current
                            val code = entry.arguments?.getString("code").orEmpty()
                            var draft by rememberSaveable { mutableStateOf("") }
                            val listState = rememberLazyListState()
                            LaunchedEffect(scrollToIndex) {
                                val target = scrollToIndex
                                if (target != null) {
                                    listState.scrollToItem(target)
                                    scrollToIndex = null
                                }
                            }
                            lastVisibleFirstIndex = listState.firstVisibleItemIndex
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Text(text = "code=$code", modifier = Modifier.testTag(PORTAL_PARAM_TAG))
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    modifier = Modifier.testTag(PORTAL_DRAFT_TAG),
                                )
                                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                    items(40) { idx ->
                                        Text(
                                            text = "Item #$idx",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("portal_item_$idx"),
                                        )
                                    }
                                }
                            }
                        }
                    },
                    startDestination = "portal_test_sub/EXAM-2026",
                )
            }
        }

        composeRule.onNodeWithTag(PORTAL_PARAM_TAG).assertTextContains("code=EXAM-2026")
        composeRule.onNodeWithTag(PORTAL_DRAFT_TAG).performTextInput("ghi-chu-de-thi")
        composeRule.runOnIdle { scrollToIndex = 12 }
        composeRule.waitForIdle()
        assertTrue("Expected list scrolled past index 5, was $lastVisibleFirstIndex", lastVisibleFirstIndex >= 5)

        // Emulate rotation / instance state restore.
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PORTAL_PARAM_TAG).assertTextContains("code=EXAM-2026")
        composeRule.onNodeWithTag(PORTAL_DRAFT_TAG).assertTextContains("ghi-chu-de-thi")
        assertTrue("Scroll position must survive rotation, was $lastVisibleFirstIndex", lastVisibleFirstIndex >= 5)

        // Unfold to 840dp + resize to 1280dp + fold back to 360dp.
        for ((w, h) in listOf(840.dp to 720.dp, 1280.dp to 800.dp, 360.dp to 640.dp)) {
            composeRule.runOnIdle {
                windowWidthDp = w
                windowHeightDp = h
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PORTAL_PARAM_TAG).assertTextContains("code=EXAM-2026")
            composeRule.onNodeWithTag(PORTAL_DRAFT_TAG).assertTextContains("ghi-chu-de-thi")
            assertTrue(
                "Scroll position must survive resize to ${w}x${h}, was $lastVisibleFirstIndex",
                lastVisibleFirstIndex >= 5,
            )
        }
    }

    private companion object {
        const val DRAFT_INPUT_TAG = "state_restoration_draft_input"
        const val PANE_MODE_TAG = "state_restoration_pane_mode"
        const val PORTAL_PARAM_TAG = "portal_state_param"
        const val PORTAL_DRAFT_TAG = "portal_state_draft"
    }
}
