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
import vn.unicom.thi360.feature.attemptengine.AttemptCommandKind
import vn.unicom.thi360.feature.attemptengine.CommandState
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
}
