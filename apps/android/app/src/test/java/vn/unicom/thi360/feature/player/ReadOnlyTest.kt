package vn.unicom.thi360.feature.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.SingleChoice
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.ReadOnlyReason
import vn.unicom.thi360.feature.question.ui.QuestionTestTags

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class ReadOnlyTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readOnlyLocksEditingAndDisablesTopActions() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        var answersEmitted = 0

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(
                        attempt = attempt,
                        session = EditSessionState.ReadOnly(ReadOnlyReason.SessionUnavailable),
                    ),
                    localUi = PlayerLocalUi(),
                    onAnswer = { _, _ -> answersEmitted += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.READ_ONLY_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_ACTION_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_FLAG_BUTTON).assertDoesNotExist()

        val firstSc = PlayerTestFixtures.leafItems(attempt)
            .firstOrNull { it.questionType == AttemptItemView.QuestionType.SINGLE_CHOICE }
        val optId = (firstSc?.questionBody as? SingleChoice)?.options?.firstOrNull()?.id
        if (optId != null) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(QuestionTestTags.choiceOption(optId)))[0]
                .performClick()
            assertEquals(0, answersEmitted)
        }
    }
}
