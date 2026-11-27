package vn.unicom.thi360.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme

@RunWith(RobolectricTestRunner::class)
class ListDetailLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shellWidthBreakpointsMatchSpec() {
        assertEquals(ShellWidth.Narrow, shellWidthForDp(360))
        assertEquals(ShellWidth.Narrow, shellWidthForDp(599))
        assertEquals(ShellWidth.Wide, shellWidthForDp(600))
        assertEquals(ShellWidth.Wide, shellWidthForDp(839))
        assertEquals(ShellWidth.TwoColumn, shellWidthForDp(840))
        assertEquals(ShellWidth.TwoColumn, shellWidthForDp(1280))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun resizesBetweenTwoColumnAndSingleColumnKeepingSelectionAndRestoringListScrollOnBack() {
        var widthDp by mutableStateOf(900.dp)
        var selected by mutableStateOf<String?>(null)
        var scrollRequest by mutableStateOf<Int?>(null)
        var observedFirstIndex = 0

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                val shellWidth = shellWidthForDp(widthDp.value.toInt())
                CompositionLocalProvider(LocalShellWidth provides shellWidth) {
                    Box(modifier = Modifier.size(width = widthDp, height = 720.dp)) {
                        ListDetailLayout(
                            selected = selected,
                            list = {
                                val listState = rememberLazyListState()
                                LaunchedEffect(scrollRequest) {
                                    val target = scrollRequest
                                    if (target != null) {
                                        listState.scrollToItem(target)
                                        scrollRequest = null
                                    }
                                }
                                observedFirstIndex = listState.firstVisibleItemIndex
                                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                    items(40) { idx ->
                                        Button(
                                            onClick = { selected = "ITEM-$idx" },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("list_item_$idx"),
                                        ) {
                                            Text("Item $idx")
                                        }
                                    }
                                }
                            },
                            detail = { item ->
                                Column {
                                    Text(text = "Detail:$item", modifier = Modifier.testTag("detail_title"))
                                    Button(
                                        onClick = { selected = null },
                                        modifier = Modifier.testTag("detail_back"),
                                    ) {
                                        Text("Back")
                                    }
                                }
                            },
                            emptyDetail = {
                                Text(text = "Select an item", modifier = Modifier.testTag("empty_detail_text"))
                            },
                        )
                    }
                }
            }
        }

        // At 900dp (TwoColumn), both list and empty detail are visible.
        composeRule.onNodeWithTag(ListDetailTestTags.TWO_COLUMN).assertIsDisplayed()
        composeRule.onNodeWithTag(ListDetailTestTags.LIST_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(ListDetailTestTags.EMPTY_DETAIL).assertIsDisplayed()

        // Scroll list down and select item 12.
        composeRule.runOnIdle { scrollRequest = 12 }
        composeRule.waitForIdle()
        assertTrue("List must be scrolled >= 5, was $observedFirstIndex", observedFirstIndex >= 5)
        composeRule.runOnIdle { selected = "ITEM-12" }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ListDetailTestTags.DETAIL_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag("detail_title").assertTextContains("Detail:ITEM-12")

        // Shrink below 840dp -> detail becomes standalone screen keeping selection ITEM-12.
        composeRule.runOnIdle { widthDp = 412.dp }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ListDetailTestTags.SINGLE_COLUMN).assertIsDisplayed()
        composeRule.onNodeWithTag(ListDetailTestTags.DETAIL_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(ListDetailTestTags.LIST_PANE).assertDoesNotExist()
        composeRule.onNodeWithTag("detail_title").assertTextContains("Detail:ITEM-12")

        // Expand back to 840dp -> both list and detail shown with ITEM-12 still selected.
        composeRule.runOnIdle { widthDp = 840.dp }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ListDetailTestTags.TWO_COLUMN).assertIsDisplayed()
        composeRule.onNodeWithTag("detail_title").assertTextContains("Detail:ITEM-12")

        // Shrink to 412dp and navigate back -> returns to list with saved scroll position intact.
        composeRule.runOnIdle { widthDp = 412.dp }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("detail_back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ListDetailTestTags.LIST_PANE).assertIsDisplayed()
        assertTrue("List scroll position must be preserved on back, was $observedFirstIndex", observedFirstIndex >= 5)
    }
}
