package vn.unicom.thi360.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.attemptengine.CommandState
import vn.unicom.thi360.feature.attemptengine.Countdown
import vn.unicom.thi360.feature.attemptengine.CountdownTarget
import vn.unicom.thi360.feature.attemptengine.OpenApiAttemptApi
import vn.unicom.thi360.feature.attemptengine.PartSelection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PartChoicePanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectsOptionAndSubjectsWithLimitLockAndTimerExpiryAndErrors() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("awaiting-choice"))
        val metadata = PlayerTestFixtures.partChoiceMetadata("per-part")
        val choicePart = PartSelection.sortedParts(attempt)
            .first { it.state == vn.unicom.thi360.core.network.generated.model.AttemptPartStateView.State.AWAITING_CHOICE }
        val matchedChoice = metadata.choiceForPart(choicePart)!!
        val subjectOption = matchedChoice.options!!.first { it.kind == "SUBJECT_CHOICE" }
        val required = subjectOption.chooseSubjects ?: 3
        val subjects = subjectOption.subjects!!.mapNotNull { it.code }

        var localUi by mutableStateOf(PlayerLocalUi())
        var remainingMs by mutableStateOf(25_000L)
        var command by mutableStateOf<CommandState>(CommandState.Idle)
        var confirmCalls = 0
        var lastConfirmedCode: String? = null
        var lastConfirmedSubjects: List<String> = emptyList()

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(attempt).copy(
                        clock = Countdown(CountdownTarget.Choice(choicePart.partId!!), remainingMs),
                        command = command,
                    ),
                    localUi = localUi,
                    partChoiceLoad = LoadState.Loaded(metadata),
                    onSelectChoiceOption = { partId, code ->
                        localUi = localUi.copy(
                            choiceDraftPartId = partId.toString(),
                            choiceDraft = PartChoiceDraft(optionCode = code, subjectCodes = emptyList()),
                        )
                    },
                    onToggleChoiceSubject = { partId, subjCode, maxSelect ->
                        val cur = localUi.choiceDraft.subjectCodes
                        val next = if (subjCode in cur) cur - subjCode else if (cur.size < maxSelect) cur + subjCode else cur
                        localUi = localUi.copy(
                            choiceDraftPartId = partId.toString(),
                            choiceDraft = localUi.choiceDraft.copy(subjectCodes = next),
                        )
                    },
                    onConfirmPartChoice = { _, code, subjs ->
                        confirmCalls += 1
                        lastConfirmedCode = code
                        lastConfirmedSubjects = subjs
                    },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_TIMER).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_DEADLINE_WARNING).assertIsDisplayed()

        composeRule.onNodeWithTag(PlayerTestTags.choiceOption(subjectOption.code!!)).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_CONFIRM).assertIsNotEnabled()

        for (i in 0 until required) {
            composeRule.onNodeWithTag(PlayerTestTags.choiceSubject(subjects[i]))
                .performScrollTo()
                .performClick()
        }
        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_SUBJECT_PROGRESS)
            .performScrollTo()
            .assertTextContains("$required/$required", substring = true)
        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_SUBJECT_LIMIT).performScrollTo().assertIsDisplayed()

        // Extra subject must be disabled once limit reached
        if (subjects.size > required) {
            composeRule.onNodeWithTag(PlayerTestTags.choiceSubject(subjects[required]))
                .performScrollTo()
                .assertIsNotEnabled()
        }

        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_CONFIRM)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertEquals(1, confirmCalls)
        assertEquals(subjectOption.code, lastConfirmedCode)
        assertEquals(subjects.take(required), lastConfirmedSubjects)

        // Error mapping
        composeRule.runOnIdle {
            command = CommandState.Refused(OpenApiAttemptApi.CODE_CHOICE_LOCKED)
        }
        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_ERROR_BANNER)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("đã xác nhận lựa chọn khác", substring = true)

        // Timer expiry disables confirm and displays expired text
        composeRule.runOnIdle { remainingMs = 0L }
        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_DEADLINE_EXPIRED).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_CONFIRM).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun loadOptionsFailureShowsA109ErrorWhileKeepingCountdownRunningAndBlockingConfirm() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("awaiting-choice"))
        val choicePart = PartSelection.sortedParts(attempt)
            .first { it.state == vn.unicom.thi360.core.network.generated.model.AttemptPartStateView.State.AWAITING_CHOICE }
        var retryClicks = 0
        var myExamsClicks = 0
        var confirmCalls = 0

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(attempt).copy(
                        clock = Countdown(CountdownTarget.Choice(choicePart.partId!!), 420_000L),
                    ),
                    localUi = PlayerLocalUi(),
                    partChoiceLoad = LoadState.Failed(LoadFailureKind.NotFound, canRetry = true),
                    onRetryLoadChoiceOptions = { retryClicks += 1 },
                    onBackToMyExams = { myExamsClicks += 1 },
                    onConfirmPartChoice = { _, _, _ -> confirmCalls += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_TIMER).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_LOAD_ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_CONFIRM).assertDoesNotExist()
        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_LOAD_RETRY).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.CHOICE_LOAD_MY_EXAMS).performClick()

        assertEquals(1, retryClicks)
        assertEquals(1, myExamsClicks)
        assertEquals(0, confirmCalls)
    }
}
