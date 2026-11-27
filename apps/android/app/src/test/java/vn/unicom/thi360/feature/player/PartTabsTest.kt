package vn.unicom.thi360.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.feature.attemptengine.AnswerSyncStatus
import vn.unicom.thi360.feature.attemptengine.EnginePosition
import vn.unicom.thi360.feature.attemptengine.LocalAnswer
import vn.unicom.thi360.feature.attemptengine.PartId
import vn.unicom.thi360.feature.attemptengine.PartSelection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PartTabsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sequentialEnforcesD10LabelsAndBlocksPendingClosedClicks() {
        val base = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        val parts = PartSelection.sortedParts(base)
        require(parts.size >= 3)
        val closedPart = parts[0].copy(state = AttemptPartStateView.State.CLOSED)
        val openPart = parts[1].copy(state = AttemptPartStateView.State.OPEN)
        val pendingPart = parts[2].copy(state = AttemptPartStateView.State.PENDING)
        val attempt = base.copy(
            navigationMode = AttemptStateResponse.NavigationMode.SEQUENTIAL,
            parts = listOf(closedPart, openPart, pendingPart),
        )
        val selectedPartIds = mutableListOf<PartId>()

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(
                        attempt = attempt,
                        positionOverride = EnginePosition(openPart.partId!!, null),
                    ),
                    localUi = PlayerLocalUi(),
                    onSelectPart = { selectedPartIds.add(it) },
                )
            }
        }

        // D-10: CLOSED tab does NOT visibly display "Đã hoàn thành" text, but TalkBack reads it.
        assertEquals(0, composeRule.onAllNodesWithText("Đã hoàn thành").fetchSemanticsNodes().size)
        val closedNode = composeRule.onNodeWithTag(PlayerTestTags.partTab(closedPart.partId!!.toString()))
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val closedA11y = closedNode.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(" ").orEmpty()
        assertTrue("Expected TalkBack to announce closed state: $closedA11y", closedA11y.contains("Đã hoàn thành"))

        // D-10: OPEN tab shows answered/total count and does NOT visibly show "Đang làm".
        assertEquals(0, composeRule.onAllNodesWithText("Đang làm").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(PlayerTestTags.partTabProgress(openPart.partId!!.toString()), useUnmergedTree = true)
            .assertTextContains("câu", substring = true)

        // PENDING tab shows locked label and is NOT clickable in SEQUENTIAL mode.
        val pendingId = pendingPart.partId!!
        val closedId = closedPart.partId
        composeRule.onNodeWithTag(PlayerTestTags.partTabState(pendingId.toString()), useUnmergedTree = true)
            .assertTextContains("Đã khóa")
        composeRule.onNodeWithTag(PlayerTestTags.partTab(pendingId.toString())).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.partTab(closedId.toString())).performClick()
        assertTrue("Sequential mode must block clicks on CLOSED/PENDING parts", selectedPartIds.isEmpty())
    }

    @Test
    fun freeNavigationSwitchesPartsAndUpdatesProgressLive() {
        val base = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        val parts = PartSelection.sortedParts(base)
        val p1 = parts[0].copy(state = AttemptPartStateView.State.OPEN)
        val p2 = parts[1].copy(state = AttemptPartStateView.State.OPEN)
        val attempt = base.copy(
            navigationMode = AttemptStateResponse.NavigationMode.FREE,
            parts = listOf(p1, p2),
        )
        val p1Leaves = PartSelection.leafItems(p1)
        val firstItemId = p1Leaves.first().itemId!!

        var engineState by mutableStateOf(
            PlayerTestFixtures.activeEngineState(
                attempt = attempt,
                positionOverride = EnginePosition(p1.partId!!, firstItemId),
            ),
        )

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = engineState,
                    localUi = PlayerLocalUi(),
                    onSelectPart = { targetPartId ->
                        engineState = engineState.copy(position = EnginePosition(targetPartId, null))
                    },
                )
            }
        }

        val initialTotal = p1Leaves.size
        val p1Id = p1.partId
        composeRule.onNodeWithTag(PlayerTestTags.partTabProgress(p1Id.toString()), useUnmergedTree = true)
            .assertTextContains("0/$initialTotal câu")

        composeRule.runOnIdle {
            engineState = engineState.copy(
                answers = engineState.answers + (
                    firstItemId to LocalAnswer(
                        value = SingleChoiceAnswer("opt-1"),
                        seq = 1L,
                        flagged = false,
                        sync = AnswerSyncStatus.Saved,
                    )
                    ),
            )
        }
        composeRule.onNodeWithTag(PlayerTestTags.partTabProgress(p1Id.toString()), useUnmergedTree = true)
            .assertTextContains("1/$initialTotal câu")

        composeRule.onNodeWithTag(PlayerTestTags.partTab(p2.partId!!.toString())).performClick()
        assertEquals(p2.partId, engineState.position?.partId)
    }
}
