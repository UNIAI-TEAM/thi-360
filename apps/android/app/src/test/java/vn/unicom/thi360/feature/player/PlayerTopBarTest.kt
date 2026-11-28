package vn.unicom.thi360.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.feature.attemptengine.Countdown
import vn.unicom.thi360.feature.attemptengine.CountdownTarget
import vn.unicom.thi360.feature.attemptengine.NetworkStatus
import vn.unicom.thi360.feature.attemptengine.SaveStatus
import vn.unicom.thi360.feature.attemptengine.UnsavedReason

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PlayerTopBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun togglesCollapseAndDisplaysOfflineBannerAndSaveStatuses() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        var localUi by mutableStateOf(PlayerLocalUi())
        var engineState by mutableStateOf(PlayerTestFixtures.activeEngineState(attempt))

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = engineState,
                    localUi = localUi,
                    onToggleCollapse = { localUi = localUi.copy(headerCollapsed = !localUi.headerCollapsed) },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_EXPANDED).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_TIMER).assertTextContains("Phần 1 còn", substring = true)
        composeRule.onNodeWithTag(PlayerTestTags.OFFLINE_BANNER).assertDoesNotExist()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_SAVE_STATUS).assertTextContains("Đã lưu")

        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_COLLAPSE_TOGGLE).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_COLLAPSED).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_TIMER).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_ACTION_BUTTON).assertIsDisplayed()

        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_COLLAPSE_TOGGLE).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_EXPANDED).assertIsDisplayed()

        composeRule.runOnIdle {
            engineState = engineState.copy(
                network = NetworkStatus.Offline,
                save = SaveStatus.Offline,
            )
        }
        composeRule.onNodeWithTag(PlayerTestTags.OFFLINE_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_SAVE_STATUS).assertTextContains("Mất kết nối", substring = true)

        composeRule.runOnIdle {
            engineState = engineState.copy(
                network = NetworkStatus.Online,
                save = SaveStatus.Unsaved(UnsavedReason.RateLimited(12L)),
            )
        }
        composeRule.onNodeWithTag(PlayerTestTags.OFFLINE_BANNER).assertDoesNotExist()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_SAVE_STATUS).assertTextContains("Chờ 12 giây", substring = true)
    }

    @Test
    fun timer() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-shared"))
        var remainingMs by mutableStateOf(3_661_000L)

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(attempt).copy(
                        clock = Countdown(target = CountdownTarget.Attempt, remainingMs = remainingMs),
                    ),
                    localUi = PlayerLocalUi(),
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_TIMER_VALUE, useUnmergedTree = true)
            .assertTextContains("1:01:01")
        val a11yHour = composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_TIMER)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        assertTrue("Expected hours in TalkBack label: $a11yHour", a11yHour.contains("1 giờ 1 phút 1 giây"))

        composeRule.runOnIdle { remainingMs = 45_000L }
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_TIMER_VALUE, useUnmergedTree = true)
            .assertTextContains("00:45")
        val a11yUrgent = composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_TIMER)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        assertTrue("Expected short TalkBack label: $a11yUrgent", a11yUrgent.contains("0 phút 45 giây"))
    }

    @Test
    fun perPartTimerHiddenWhenActivePartNotOpen() {
        val generating = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("generating"))
        val genPart = vn.unicom.thi360.feature.attemptengine.PartSelection.sortedParts(generating)
            .first { it.state == vn.unicom.thi360.core.network.generated.model.AttemptPartStateView.State.GENERATING }

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = generating.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(generating).copy(
                        clock = Countdown(target = CountdownTarget.Part(genPart.partId!!), remainingMs = 900_000L),
                    ),
                    localUi = PlayerLocalUi(),
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_TIMER).assertDoesNotExist()
    }
}
