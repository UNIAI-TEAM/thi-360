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
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.attemptengine.AttemptCommandKind
import vn.unicom.thi360.feature.attemptengine.CommandState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class GenerationPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsBusyNoticeAfterDelayAndGuardsRetryDoubleTapOnGenerationFailed() {
        val generating = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("generating"))
        val failed = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("generation-failed"))
        val metadata = PlayerTestFixtures.partChoiceMetadata("per-part")

        var engineState by mutableStateOf(PlayerTestFixtures.activeEngineState(generating))
        var retryCalls = 0

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = generating.attemptId.toString(),
                    engineState = engineState,
                    localUi = PlayerLocalUi(),
                    partChoiceLoad = LoadState.Loaded(metadata),
                    busyNoticeDelayMs = 100L,
                    onRetryGeneration = {
                        retryCalls += 1
                        engineState = engineState.copy(command = CommandState.Sending(AttemptCommandKind.Retry))
                    },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.GENERATION_PANEL).assertIsDisplayed()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(PlayerTestTags.GENERATION_BUSY_NOTICE))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(PlayerTestTags.GENERATION_BUSY_NOTICE)
            .assertIsDisplayed()
            .assertTextContains("Ứng dụng tự kiểm tra lại", substring = true)

        composeRule.runOnIdle {
            engineState = PlayerTestFixtures.activeEngineState(failed)
        }
        composeRule.onNodeWithTag(PlayerTestTags.GENERATION_FAILED_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.GENERATION_RETRY).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.GENERATION_RETRY).assertIsNotEnabled()
        composeRule.onNodeWithTag(PlayerTestTags.GENERATION_RETRY).performClick()
        assertEquals(1, retryCalls)
    }
}
