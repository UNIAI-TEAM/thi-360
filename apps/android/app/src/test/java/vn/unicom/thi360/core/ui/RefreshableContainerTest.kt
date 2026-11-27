package vn.unicom.thi360.core.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class RefreshableContainerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun pullToRefreshTriggersCallbackReplacesDataAndShowsStaleBannerOnError() {
        var refreshCalls = 0
        var state by mutableStateOf<LoadState<List<String>>>(LoadState.Loaded(listOf("Initial Exam"), stale = false))
        var isRefreshing by mutableStateOf(false)
        var failNext by mutableStateOf(false)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                RefreshableContainer(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        refreshCalls++
                        if (failNext) {
                            val existing = (state as LoadState.Loaded).value
                            state = LoadState.Loaded(existing, stale = true)
                        } else {
                            state = LoadState.Loaded(listOf("Updated Exam #$refreshCalls"), stale = false)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LoadStateView(
                        state = state,
                        onRetry = {},
                        emptyContent = {},
                        staleBannerMessage = "Không thể cập nhật dữ liệu mới.",
                        modifier = Modifier.fillMaxSize(),
                    ) { items ->
                        LazyColumn(modifier = Modifier.fillMaxSize().testTag("refresh_list")) {
                            items(items.size) { index ->
                                Text(text = items[index], modifier = Modifier.testTag("list_row_$index"))
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag(RefreshableContainerTestTags.CONTAINER).assertIsDisplayed()
        composeRule.onNodeWithTag("list_row_0").assertTextContains("Initial Exam")

        // Pull down triggers refresh and replaces visible data.
        composeRule.onNodeWithTag("refresh_list").performTouchInput {
            swipeDown(startY = top + 20f, endY = bottom - 20f)
        }
        composeRule.waitForIdle()
        assertEquals(1, refreshCalls)
        composeRule.onNodeWithTag("list_row_0").assertTextContains("Updated Exam #1")
        composeRule.onNodeWithTag(StaleBannerTestTags.BANNER).assertDoesNotExist()

        // Failed pull-to-refresh preserves existing data and displays StaleBanner.
        composeRule.runOnIdle { failNext = true }
        composeRule.onNodeWithTag("refresh_list").performTouchInput {
            swipeDown(startY = top + 20f, endY = bottom - 20f)
        }
        composeRule.waitForIdle()
        assertEquals(2, refreshCalls)
        composeRule.onNodeWithTag("list_row_0").assertTextContains("Updated Exam #1")
        composeRule.onNodeWithTag(StaleBannerTestTags.BANNER)
            .assertIsDisplayed()
            .assertTextContains("Không thể cập nhật dữ liệu mới.")
    }
}
