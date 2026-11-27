package vn.unicom.thi360.feature.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.SingleChoice
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.EnginePosition
import vn.unicom.thi360.feature.attemptengine.PartSelection
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

    @Test
    fun readOnlyBlocksFreePartTabsAndPartChoiceSelectionWhileKeepingGridNavigation() {
        val base = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        val parts = PartSelection.sortedParts(base)
        val p1 = parts[0].copy(state = AttemptPartStateView.State.OPEN)
        val p2 = parts[1].copy(state = AttemptPartStateView.State.OPEN)
        val freeAttempt = base.copy(
            navigationMode = AttemptStateResponse.NavigationMode.FREE,
            parts = listOf(p1, p2),
        )
        var partClicks = 0
        var gridClicks = 0
        var localUi = PlayerLocalUi(gridSheetOpen = true)
        val firstLeaf = PartSelection.leafItems(p1).first().itemId!!

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = freeAttempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(
                        attempt = freeAttempt,
                        session = EditSessionState.ReadOnly(ReadOnlyReason.SessionUnavailable),
                        positionOverride = EnginePosition(p1.partId!!, firstLeaf),
                    ),
                    localUi = localUi,
                    onSelectPart = { partClicks += 1 },
                    onSelectGridItem = { gridClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.partTab(p2.partId!!.toString()))
            .assertIsNotEnabled()
            .performClick()
        assertEquals(0, partClicks)

        composeRule.onNodeWithTag(PlayerTestTags.gridCell(firstLeaf.toString())).performClick()
        assertEquals(1, gridClicks)
    }

    @Test
    fun takeoverReadOnlyBlocksPartChoiceSelection() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("awaiting-choice"))
        val metadata = PlayerTestFixtures.partChoiceMetadata("per-part")
        val choicePart = PartSelection.sortedParts(attempt)
            .first { it.state == AttemptPartStateView.State.AWAITING_CHOICE }
        val firstOption = metadata.choiceForPart(choicePart)!!.options!!.first()
        var choiceSelectCalls = 0

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(
                        attempt = attempt,
                        session = EditSessionState.OpenElsewhere(OffsetDateTime.parse("2026-11-20T10:30:45Z")),
                    ),
                    localUi = PlayerLocalUi(),
                    partChoiceLoad = LoadState.Loaded(metadata),
                    onSelectChoiceOption = { _, _ -> choiceSelectCalls += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.choiceOption(firstOption.code!!))
            .assertIsNotEnabled()
            .performClick()
        assertEquals(0, choiceSelectCalls)
    }
}
