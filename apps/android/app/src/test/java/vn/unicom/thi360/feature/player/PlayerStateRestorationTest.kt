package vn.unicom.thi360.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.OptionView
import vn.unicom.thi360.core.network.generated.model.QuestionSet
import vn.unicom.thi360.core.network.generated.model.SingleChoice
import vn.unicom.thi360.feature.attemptengine.PartSelection
import vn.unicom.thi360.feature.question.ui.QuestionTestTags

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class PlayerStateRestorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchesBetweenCompactAndFoldThreeColumnAndTabletPreservingUiState() {
        val base = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-shared"))
        val firstPart = PartSelection.sortedParts(base).first()
        val setId = UUID.randomUUID()
        val child1 = UUID.randomUUID()
        val child2 = UUID.randomUUID()
        val setItems = listOf(
            AttemptItemView(
                itemId = setId,
                displayNo = 1,
                questionType = AttemptItemView.QuestionType.QUESTION_SET,
                questionBody = QuestionSet(
                    passage = "<p>Ngữ liệu bài đọc hiểu mẫu cho máy gập mở ba cột theo D-9.</p>",
                ),
            ),
            AttemptItemView(
                itemId = child1,
                parentItemId = setId,
                displayNo = 1,
                content = "<p>Câu hỏi con 1</p>",
                questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
                questionBody = SingleChoice(
                    options = listOf(
                        OptionView(id = "a", content = "Phương án A"),
                        OptionView(id = "b", content = "Phương án B"),
                    ),
                ),
            ),
            AttemptItemView(
                itemId = child2,
                parentItemId = setId,
                displayNo = 2,
                content = "<p>Câu hỏi con 2</p>",
                questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
                questionBody = SingleChoice(
                    options = listOf(
                        OptionView(id = "c", content = "Phương án C"),
                        OptionView(id = "d", content = "Phương án D"),
                    ),
                ),
            ),
        )
        val attempt = base.copy(
            parts = listOf(firstPart.copy(state = AttemptPartStateView.State.OPEN, items = setItems)),
        )

        var widthDp by mutableStateOf(412)
        val engineState = PlayerTestFixtures.activeEngineState(attempt)

        composeRule.setContent {
            PlayerTestTheme {
                Box(modifier = Modifier.width(widthDp.dp).fillMaxHeight()) {
                    PlayerScreen(
                        attemptId = attempt.attemptId.toString(),
                        engineState = engineState,
                        localUi = PlayerLocalUi(headerCollapsed = true, gridFilter = GridFilter.Flagged),
                    )
                }
            }
        }

        // Compact phone (<840dp): bottom bar + single-column QuestionSetView
        composeRule.onNodeWithTag(PlayerTestTags.NARROW_BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.SET_SINGLE_COLUMN).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_COLLAPSED).assertIsDisplayed()

        // Fold 840dp (D-9 / A-Fold-Player): 3 columns -> Passage | Questions | QuestionGrid sidebar
        composeRule.runOnIdle { widthDp = 840 }
        composeRule.onNodeWithTag(PlayerTestTags.GRID_SIDEBAR).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.NARROW_BOTTOM_BAR).assertDoesNotExist()
        composeRule.onNodeWithTag(QuestionTestTags.SET_TWO_COLUMN).assertIsDisplayed()

        val passageBounds = composeRule.onNodeWithTag(QuestionTestTags.SET_PASSAGE_PANE)
            .fetchSemanticsNode().boundsInRoot
        val childrenBounds = composeRule.onNodeWithTag(QuestionTestTags.SET_CHILDREN_PANE)
            .fetchSemanticsNode().boundsInRoot
        val sidebarBounds = composeRule.onNodeWithTag(PlayerTestTags.GRID_SIDEBAR)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Fold 840dp passage (${passageBounds.right}) must be left of questions (${childrenBounds.left})",
            passageBounds.right <= childrenBounds.left + 2f,
        )
        assertTrue(
            "Fold 840dp questions (${childrenBounds.right}) must be left of grid sidebar (${sidebarBounds.left})",
            childrenBounds.right <= sidebarBounds.left + 2f,
        )

        // Tablet 1280dp keeps 3-column wide layout and collapsed top bar
        composeRule.runOnIdle { widthDp = 1280 }
        composeRule.onNodeWithTag(PlayerTestTags.GRID_SIDEBAR).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.SET_TWO_COLUMN).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_COLLAPSED).assertIsDisplayed()
    }

    @Test
    fun localUiSaverRestoresCollapseFilterAndChoiceDraftAcrossRecreation() {
        val restorationTester = StateRestorationTester(composeRule)
        var captured: PlayerLocalUi? = null

        restorationTester.setContent {
            var localUi by rememberSaveable(stateSaver = PlayerLocalUi.Saver) {
                mutableStateOf(
                    PlayerLocalUi(
                        headerCollapsed = true,
                        gridSheetOpen = false,
                        gridFilter = GridFilter.Unanswered,
                        dialog = PlayerDialog.None,
                        choiceDraft = PartChoiceDraft(
                            optionCode = "SCIENCE",
                            subjectCodes = listOf("LY", "HOA"),
                        ),
                        choiceDraftPartId = "part-3",
                    ),
                )
            }
            captured = localUi
        }

        restorationTester.emulateSavedInstanceStateRestore()

        assertEquals(true, captured?.headerCollapsed)
        assertEquals(GridFilter.Unanswered, captured?.gridFilter)
        assertEquals("SCIENCE", captured?.choiceDraft?.optionCode)
        assertEquals(listOf("LY", "HOA"), captured?.choiceDraft?.subjectCodes)
        assertEquals("part-3", captured?.choiceDraftPartId)
    }
}
