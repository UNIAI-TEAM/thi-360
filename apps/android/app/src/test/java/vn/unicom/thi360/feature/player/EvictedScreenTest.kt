package vn.unicom.thi360.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.feature.attemptengine.AttemptCommandKind
import vn.unicom.thi360.feature.attemptengine.CommandState
import vn.unicom.thi360.feature.attemptengine.EditSessionState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class EvictedScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysEvictedScreenAndPreventsDoubleTapOnTakeover() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        var command by mutableStateOf<CommandState>(CommandState.Idle)
        var takeoverCalls = 0
        var myExamsCalls = 0

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(
                        attempt = attempt,
                        session = EditSessionState.Evicted,
                    ).copy(command = command),
                    localUi = PlayerLocalUi(),
                    onTakeover = {
                        takeoverCalls += 1
                        command = CommandState.Sending(AttemptCommandKind.Takeover)
                    },
                    onBackToMyExams = { myExamsCalls += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.EVICTED_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.EVICTED_HEADING)
            .assertTextContains("Lượt thi đã được tiếp tục ở thiết bị khác.")

        composeRule.onNodeWithTag(PlayerTestTags.EVICTED_MY_EXAMS).performClick()
        assertEquals(1, myExamsCalls)

        composeRule.onNodeWithTag(PlayerTestTags.EVICTED_TAKEOVER).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.EVICTED_TAKEOVER).assertIsNotEnabled()
        composeRule.onNodeWithTag(PlayerTestTags.EVICTED_TAKEOVER).performClick()
        assertEquals(1, takeoverCalls)
    }
}
