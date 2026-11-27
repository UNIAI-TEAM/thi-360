package vn.unicom.thi360.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.Essay
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.feature.attemptengine.AnswerSyncStatus
import vn.unicom.thi360.feature.attemptengine.AttemptCommandKind
import vn.unicom.thi360.feature.attemptengine.CommandState
import vn.unicom.thi360.feature.attemptengine.LocalAnswer
import vn.unicom.thi360.feature.attemptengine.PartSelection
import vn.unicom.thi360.feature.attemptengine.UnconfirmedCommandKind

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class SubmitDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysFourCountsPartBreakdownEssayWarningsAndReconcile() {
        val base = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-shared"))
        val firstPart = PartSelection.sortedParts(base).first()
        val essayId = UUID.randomUUID()
        val essayItem = AttemptItemView(
            itemId = essayId,
            displayNo = 99,
            content = "<p>Bài luận</p>",
            questionType = AttemptItemView.QuestionType.ESSAY,
            questionBody = Essay(wordLimit = 200),
        )
        val attempt = base.copy(
            parts = listOf(firstPart.copy(items = firstPart.items.orEmpty() + essayItem)) +
                PartSelection.sortedParts(base).drop(1),
        )

        var command by mutableStateOf<CommandState>(CommandState.Idle)
        var submitCalls = 0
        var reconcileCalls = 0

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(
                        attempt = attempt,
                        answersOverride = mapOf(
                            essayId to LocalAnswer(
                                value = EssayAnswer(html = "<p>Mười chữ ngắn gọn</p>", wordCount = 4),
                                seq = 1L,
                                flagged = true,
                                sync = AnswerSyncStatus.Pending,
                            ),
                        ),
                    ).copy(command = command),
                    localUi = PlayerLocalUi(dialog = PlayerDialog.Submit),
                    onConfirmSubmit = {
                        submitCalls += 1
                        command = CommandState.Sending(AttemptCommandKind.Submit)
                    },
                    onReconcile = { reconcileCalls += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.SUBMIT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.SUBMIT_PART_BREAKDOWN).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.SUBMIT_ESSAY_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.SUBMIT_ESSAY_WARNING).assertIsDisplayed()

        // Under-limit warning MUST NOT disable submit CTA
        composeRule.onNodeWithTag(PlayerTestTags.SUBMIT_CONFIRM).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(PlayerTestTags.SUBMIT_CONFIRM).assertIsNotEnabled()
        composeRule.onNodeWithTag(PlayerTestTags.SUBMIT_CONFIRM).performClick()
        assertEquals(1, submitCalls)

        composeRule.runOnIdle {
            command = CommandState.Unconfirmed(UnconfirmedCommandKind.Submit)
        }
        composeRule.onNodeWithText("Chưa xác nhận nộp bài").assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.SUBMIT_RECONCILE).assertIsDisplayed().performClick()
        assertEquals(1, reconcileCalls)
    }
}
