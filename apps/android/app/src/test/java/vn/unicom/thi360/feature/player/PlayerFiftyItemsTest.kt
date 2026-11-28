package vn.unicom.thi360.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.feature.attemptengine.EnginePosition
import vn.unicom.thi360.feature.attemptengine.PartSelection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PlayerFiftyItemsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersFiftyItemsLazilyAndJumpsToLastItemViaGrid() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attempt50())
        val firstPart = PartSelection.sortedParts(attempt).first()
        val leaves = PartSelection.leafItems(firstPart)
        assertTrue("Expected >= 50 items in attempt-50.json, found ${leaves.size}", leaves.size >= 50)

        val firstId = leaves.first().itemId!!
        val lastId = leaves.last().itemId!!

        var engineState by mutableStateOf(PlayerTestFixtures.activeEngineState(attempt))
        var localUi by mutableStateOf(PlayerLocalUi())
        var scrollReq by mutableStateOf<ScrollToItemRequest?>(null)

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = engineState,
                    localUi = localUi,
                    scrollRequest = scrollReq,
                    onOpenGridSheet = { localUi = localUi.copy(gridSheetOpen = true) },
                    onCloseGridSheet = { localUi = localUi.copy(gridSheetOpen = false) },
                    onSelectGridItem = { targetId ->
                        engineState = engineState.copy(position = EnginePosition(firstPart.partId!!, targetId))
                        scrollReq = ScrollToItemRequest(targetId, (scrollReq?.nonce ?: 0L) + 1L)
                    },
                )
            }
        }

        // LazyColumn must compose near-viewport items and NOT compose item 50 eagerly.
        composeRule.onNodeWithTag(PlayerTestTags.contentItem(firstId.toString())).assertIsDisplayed()
        assertEquals(
            "Item 50 must not be eagerly composed before scrolling",
            0,
            composeRule.onAllNodes(hasTestTag(PlayerTestTags.contentItem(lastId.toString())))
                .fetchSemanticsNodes().size,
        )

        // Jump to item 50 via QuestionGridSheet
        composeRule.onNodeWithTag(PlayerTestTags.NARROW_GRID_BUTTON).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.GRID_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.GRID_LIST)
            .performScrollToNode(hasTestTag(PlayerTestTags.gridCell(lastId.toString())))
        composeRule.onNodeWithTag(PlayerTestTags.gridCell(lastId.toString())).performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasTestTag(PlayerTestTags.contentItem(lastId.toString())))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(PlayerTestTags.contentItem(lastId.toString())).assertIsDisplayed()
    }
}
