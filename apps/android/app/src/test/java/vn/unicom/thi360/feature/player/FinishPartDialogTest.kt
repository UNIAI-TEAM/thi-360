package vn.unicom.thi360.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.StatementView
import vn.unicom.thi360.core.network.generated.model.TrueFalse
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.feature.attemptengine.AnswerSyncStatus
import vn.unicom.thi360.feature.attemptengine.AttemptCommandKind
import vn.unicom.thi360.feature.attemptengine.CommandState
import vn.unicom.thi360.feature.attemptengine.LocalAnswer
import vn.unicom.thi360.feature.attemptengine.PartSelection
import vn.unicom.thi360.feature.attemptengine.UnconfirmedCommandKind

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class FinishPartDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysSummaryGuardsDoubleTapAndHandlesUnconfirmedReconcile() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        var localUi by mutableStateOf(PlayerLocalUi(dialog = PlayerDialog.FinishPart))
        var command by mutableStateOf<CommandState>(CommandState.Idle)
        var confirmCalls = 0
        var reconcileCalls = 0

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(attempt).copy(command = command),
                    localUi = localUi,
                    onConfirmFinishPart = {
                        confirmCalls += 1
                        command = CommandState.Sending(AttemptCommandKind.FinishPart)
                    },
                    onReconcile = { reconcileCalls += 1 },
                    onDismissDialog = { localUi = localUi.copy(dialog = PlayerDialog.None) },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.FINISH_PART_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Hoàn thành phần?").assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.FINISH_PART_CONFIRM).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.FINISH_PART_CONFIRM).assertIsNotEnabled()
        composeRule.onNodeWithTag(PlayerTestTags.FINISH_PART_CONFIRM).performClick()
        assertEquals(1, confirmCalls)

        composeRule.runOnIdle {
            command = CommandState.Unconfirmed(UnconfirmedCommandKind.FinishPart)
        }
        composeRule.onNodeWithText("Chưa xác nhận hoàn thành phần").assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.FINISH_PART_RECONCILE).assertIsDisplayed().performClick()
        assertEquals(1, reconcileCalls)
    }

    @Test
    fun countsOnlyBlankAsMissingSeparatesPartialAndDistinguishesPendingVsChoiceNextPart() {
        val base = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        val parts = PartSelection.sortedParts(base)
        val p1 = parts[0]
        val leaves = PartSelection.leafItems(p1)
        require(leaves.size >= 3)
        val partialItem = leaves[1].copy(
            questionType = AttemptItemView.QuestionType.TRUE_FALSE,
            questionBody = TrueFalse(
                statements = listOf(
                    StatementView(id = "s1", content = "S1"),
                    StatementView(id = "s2", content = "S2"),
                ),
            ),
        )
        val trimmedP1 = p1.copy(items = listOf(leaves[0], partialItem, leaves[2]))
        val pendingNext = parts[1].copy(
            state = AttemptPartStateView.State.PENDING,
            questionCount = null,
            items = emptyList(),
        )
        var attempt by mutableStateOf(base.copy(parts = listOf(trimmedP1, pendingNext)))
        var answers by mutableStateOf(
            mapOf(
                leaves[0].itemId!! to LocalAnswer(SingleChoiceAnswer("a"), 1L, false, AnswerSyncStatus.Saved),
                partialItem.itemId!! to LocalAnswer(TrueFalseAnswer(mapOf("s1" to true)), 1L, false, AnswerSyncStatus.Saved),
            ),
        )

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(attempt, answersOverride = answers),
                    localUi = PlayerLocalUi(dialog = PlayerDialog.FinishPart),
                )
            }
        }

        composeRule.onNodeWithText("Còn 1/3 câu chưa trả lời").assertIsDisplayed()
        composeRule.onNodeWithText("Có 1 câu đang làm dở").assertIsDisplayed()
        composeRule.onNodeWithText("Phần tiếp theo sẽ mở ngay và không thể quay lại phần này.").assertIsDisplayed()

        composeRule.runOnIdle {
            answers = answers + (leaves[2].itemId!! to LocalAnswer(SingleChoiceAnswer("a"), 1L, false, AnswerSyncStatus.Saved))
            attempt = base.copy(
                parts = listOf(
                    trimmedP1,
                    parts[1].copy(state = AttemptPartStateView.State.AWAITING_CHOICE, items = emptyList()),
                ),
            )
        }
        composeRule.onNodeWithText("Còn 0/3 câu chưa trả lời").assertIsDisplayed()
        composeRule.onNodeWithText("Có 1 câu đang làm dở").assertIsDisplayed()
        composeRule.onNodeWithText("Tiếp theo là bước chọn phần 3", substring = true).assertIsDisplayed()
    }
}
