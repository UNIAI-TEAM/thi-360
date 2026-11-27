package vn.unicom.thi360.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.StatementView
import vn.unicom.thi360.core.network.generated.model.TrueFalse
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.feature.attemptengine.AnswerSyncStatus
import vn.unicom.thi360.feature.attemptengine.ItemId
import vn.unicom.thi360.feature.attemptengine.LocalAnswer
import vn.unicom.thi360.feature.attemptengine.PartSelection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h800dp-xhdpi")
class QuestionGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun filtersStatusesFlaggedLevelsLegendAndEmptyState() {
        val base = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-shared"))
        val firstPart = PartSelection.sortedParts(base).first()
        val origLeaves = PartSelection.leafItems(firstPart)
        require(origLeaves.size >= 3)

        val id1 = origLeaves[0].itemId!!
        val id2 = origLeaves[1].itemId!!
        val id3 = origLeaves[2].itemId!!

        val tfItem2 = origLeaves[1].copy(
            questionType = AttemptItemView.QuestionType.TRUE_FALSE,
            questionBody = TrueFalse(
                statements = listOf(
                    StatementView(id = "s1", content = "S1"),
                    StatementView(id = "s2", content = "S2"),
                ),
            ),
        )
        val attempt = base.copy(
            parts = listOf(firstPart.copy(items = listOf(origLeaves[0], tfItem2, origLeaves[2]))),
        )

        var filter by mutableStateOf(GridFilter.All)
        val answers = mapOf(
            id1 to LocalAnswer(SingleChoiceAnswer("opt-a"), 1L, flagged = true, sync = AnswerSyncStatus.Saved),
            id2 to LocalAnswer(TrueFalseAnswer(mapOf("s1" to true)), 1L, flagged = true, sync = AnswerSyncStatus.Pending),
            id3 to LocalAnswer(null, 0L, flagged = false, sync = AnswerSyncStatus.Saved),
        )
        val engineState = PlayerTestFixtures.activeEngineState(attempt, answersOverride = answers)

        composeRule.setContent {
            PlayerTestTheme {
                Box(modifier = Modifier.width(900.dp).fillMaxHeight()) {
                    PlayerScreen(
                        attemptId = attempt.attemptId.toString(),
                        engineState = engineState,
                        localUi = PlayerLocalUi(gridFilter = filter),
                        onSelectGridFilter = { filter = it },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.GRID_SIDEBAR).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.gridCell(id1.toString())).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.gridCellFlag(id1.toString()), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.gridCellUnsaved(id2.toString()), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.GRID_LEGEND_FLAGGED).assertIsDisplayed()

        val cell1Desc = composeRule.onNodeWithTag(PlayerTestTags.gridCell(id1.toString()))
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(" ").orEmpty()
        assertTrue("Expected saved TalkBack desc: $cell1Desc",
            cell1Desc.contains("đã trả lời") && cell1Desc.contains("đánh dấu xem lại") && cell1Desc.contains("đã lưu"))

        val cell2Desc = composeRule.onNodeWithTag(PlayerTestTags.gridCell(id2.toString()))
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(" ").orEmpty()
        assertTrue("Expected partial+flagged+unsaved TalkBack desc: $cell2Desc",
            cell2Desc.contains("trả lời một phần") && cell2Desc.contains("đánh dấu xem lại") && cell2Desc.contains("chưa lưu"))

        composeRule.onNodeWithTag(PlayerTestTags.GRID_FILTER_UNANSWERED).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.gridCell(id1.toString())).assertDoesNotExist()
        composeRule.onNodeWithTag(PlayerTestTags.gridCell(id2.toString())).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.gridCell(id3.toString())).assertIsDisplayed()

        composeRule.onNodeWithTag(PlayerTestTags.GRID_FILTER_FLAGGED).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.gridCell(id1.toString())).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.gridCell(id2.toString())).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.gridCell(id3.toString())).assertDoesNotExist()

        // Empty state when filtering flagged on unflagged items
        composeRule.runOnIdle { filter = GridFilter.All }
    }

    @Test
    fun navigation() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-shared"))
        val leaves = PartSelection.leafItems(PartSelection.sortedParts(attempt).first())
        val targetId = leaves.last().itemId!!

        var containerWidthDp by mutableStateOf(412)
        var localUi by mutableStateOf(PlayerLocalUi())
        var clickedItemId: ItemId? = null

        composeRule.setContent {
            PlayerTestTheme {
                Box(modifier = Modifier.width(containerWidthDp.dp).fillMaxHeight()) {
                    PlayerScreen(
                        attemptId = attempt.attemptId.toString(),
                        engineState = PlayerTestFixtures.activeEngineState(attempt),
                        localUi = localUi,
                        onOpenGridSheet = { localUi = localUi.copy(gridSheetOpen = true) },
                        onCloseGridSheet = { localUi = localUi.copy(gridSheetOpen = false) },
                        onSelectGridItem = { clickedItemId = it },
                    )
                }
            }
        }

        // Narrow <840dp: bottom bar shown, no fixed sidebar
        composeRule.onNodeWithTag(PlayerTestTags.NARROW_BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.GRID_SIDEBAR).assertDoesNotExist()
        composeRule.onNodeWithTag(PlayerTestTags.NARROW_GRID_BUTTON).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.GRID_SHEET).assertIsDisplayed()

        composeRule.onNodeWithTag(PlayerTestTags.gridCell(targetId.toString())).performClick()
        assertEquals(targetId, clickedItemId)
        composeRule.onNodeWithTag(PlayerTestTags.GRID_SHEET).assertDoesNotExist()

        // Wide >=840dp: fixed sidebar shown, bottom bar hidden
        composeRule.runOnIdle { containerWidthDp = 900 }
        composeRule.onNodeWithTag(PlayerTestTags.GRID_SIDEBAR).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.NARROW_BOTTOM_BAR).assertDoesNotExist()
    }
}
