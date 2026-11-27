package vn.unicom.thi360.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.feature.attemptengine.EnginePosition
import vn.unicom.thi360.feature.attemptengine.ItemId
import vn.unicom.thi360.feature.attemptengine.PartSelection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h640dp-xhdpi")
class PlayerScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scrollingQuestionListUpdatesActivePosition() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attempt50())
        val firstPart = PartSelection.sortedParts(attempt).first()
        val firstLeaf = PartSelection.leafItems(firstPart).first().itemId!!

        var engineState by mutableStateOf(PlayerTestFixtures.activeEngineState(attempt))
        var lastVisibleItem: ItemId? = null

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = engineState,
                    localUi = PlayerLocalUi(),
                    onVisibleItemChanged = { partId, itemId ->
                        lastVisibleItem = itemId
                        engineState = engineState.copy(position = EnginePosition(partId, itemId))
                    },
                )
            }
        }

        repeat(4) {
            composeRule.onNodeWithTag(PlayerTestTags.CONTENT_LIST).performTouchInput {
                swipeUp(startY = bottom * 0.85f, endY = top + 40f, durationMillis = 150L)
            }
            composeRule.waitForIdle()
        }

        assertNotNull("Expected scroll tracking to report visible item", lastVisibleItem)
        assertNotEquals("Expected visible item to advance past first question", firstLeaf, lastVisibleItem)
    }

    @Test
    fun programmaticInitialScrollDoesNotOverwriteRestoredLastQuestionPosition() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        val firstPart = PartSelection.sortedParts(attempt).first()
        val lastLeaf = PartSelection.leafItems(firstPart).last().itemId!!
        val initialPosition = EnginePosition(firstPart.partId!!, lastLeaf)

        var engineState by mutableStateOf(
            PlayerTestFixtures.activeEngineState(attempt).copy(position = initialPosition),
        )
        var passiveReport: ItemId? = null

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = engineState,
                    localUi = PlayerLocalUi(),
                    onVisibleItemChanged = { partId, itemId ->
                        passiveReport = itemId
                        engineState = engineState.copy(position = EnginePosition(partId, itemId))
                    },
                )
            }
        }

        composeRule.waitForIdle()
        assertNull("Programmatic initial scroll must not emit passive visible-item overwrite", passiveReport)
        assertEquals(initialPosition, engineState.position)
    }
}
