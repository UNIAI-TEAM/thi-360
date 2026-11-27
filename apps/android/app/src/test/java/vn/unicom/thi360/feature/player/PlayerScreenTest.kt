package vn.unicom.thi360.feature.player

import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
import vn.unicom.thi360.core.network.generated.model.AttemptDisplaySegmentView
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.SingleChoice
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.attemptengine.AttemptClosedReason
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.EngineState
import vn.unicom.thi360.feature.attemptengine.SaveStatus
import vn.unicom.thi360.feature.attemptengine.UnsavedReason
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.shell.ShellTestTags

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PlayerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadStates() {
        var engineState by mutableStateOf(EngineState(load = LoadState.Loading))
        var retryClicks = 0
        var myExamsClicks = 0

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = UUID.randomUUID().toString(),
                    engineState = engineState,
                    localUi = PlayerLocalUi(),
                    onRetryLoad = { retryClicks += 1 },
                    onBackToMyExams = { myExamsClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.LOADING)
            .assertIsDisplayed()
            .assertTextContains("Đang chuẩn bị đề", substring = true)
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.NAV_RAIL).assertDoesNotExist()

        composeRule.runOnIdle {
            engineState = EngineState(load = LoadState.Failed(LoadFailureKind.NotFound, canRetry = false))
        }
        composeRule.onNodeWithTag(PlayerTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.ERROR_TITLE).assertTextContains("Không tìm thấy lượt thi")
        composeRule.onNodeWithTag(PlayerTestTags.ERROR_RETRY).assertDoesNotExist()
        composeRule.onNodeWithTag(PlayerTestTags.ERROR_MY_EXAMS).performClick()
        assertEquals(1, myExamsClicks)

        composeRule.runOnIdle {
            engineState = EngineState(load = LoadState.Failed(LoadFailureKind.Server, canRetry = true))
        }
        composeRule.onNodeWithTag(PlayerTestTags.ERROR_TITLE).assertTextContains("Không tải được lượt thi")
        composeRule.onNodeWithTag(PlayerTestTags.ERROR_RETRY).assertIsDisplayed().performClick()
        assertEquals(1, retryClicks)

        val loaded = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        composeRule.runOnIdle {
            engineState = PlayerTestFixtures.activeEngineState(loaded)
        }
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR).assertIsDisplayed()
    }

    @Test
    fun closedScreen() {
        val closedTimeUp = PlayerTestFixtures.attemptState("closed-time-up")
        var reason by mutableStateOf<AttemptClosedReason>(AttemptClosedReason.TimeUp)
        var viewResultClicks = 0
        var myExamsClicks = 0

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = closedTimeUp.attemptId.toString(),
                    engineState = EngineState(
                        load = LoadState.Loaded(Unit),
                        attempt = closedTimeUp,
                        closed = reason,
                    ),
                    localUi = PlayerLocalUi(),
                    onViewResult = { viewResultClicks += 1 },
                    onBackToMyExams = { myExamsClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.CLOSED_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.CLOSED_HEADING).assertTextContains("Đã hết giờ làm bài")

        composeRule.runOnIdle { reason = AttemptClosedReason.ChoiceTimeout }
        composeRule.onNodeWithTag(PlayerTestTags.CLOSED_HEADING).assertTextContains("Đã quá thời gian chọn phần 3")

        composeRule.runOnIdle { reason = AttemptClosedReason.Submitted }
        composeRule.onNodeWithTag(PlayerTestTags.CLOSED_HEADING).assertTextContains("Lượt thi đã đóng")

        composeRule.onNodeWithTag(PlayerTestTags.CLOSED_VIEW_RESULT).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.CLOSED_MY_EXAMS).performClick()
        assertEquals(1, viewResultClicks)
        assertEquals(1, myExamsClicks)
    }

    @Test
    fun beforeSession() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        var engineState by mutableStateOf(
            PlayerTestFixtures.activeEngineState(
                attempt = attempt,
                session = EditSessionState.Acquiring,
            ).copy(save = SaveStatus.Unsaved(UnsavedReason.ServerError)),
        )
        var answeredCalls = 0
        var flaggedCalls = 0

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = engineState,
                    localUi = PlayerLocalUi(),
                    onAnswer = { _, _ -> answeredCalls += 1 },
                    onFlag = { _, _ -> flaggedCalls += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_ACTION_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(PlayerTestTags.READ_ONLY_BANNER).assertDoesNotExist()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_SAVE_STATUS).assertTextContains("Máy chủ bận", substring = true)

        val firstSingleChoice = PlayerTestFixtures.leafItems(attempt)
            .firstOrNull { it.questionType == AttemptItemView.QuestionType.SINGLE_CHOICE }
        val firstOptId = (firstSingleChoice?.questionBody as? SingleChoice)?.options?.firstOrNull()?.id
        if (firstOptId != null) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(QuestionTestTags.choiceOption(firstOptId)))[0]
                .performClick()
            assertTrue("AC-4: answering must work before edit session is acquired", answeredCalls > 0)
        }
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(QuestionTestTags.FRAME_FLAG_BUTTON))[0].performClick()
        assertTrue("AC-4: flagging must work before edit session is acquired", flaggedCalls > 0)

        composeRule.runOnIdle {
            engineState = engineState.copy(save = SaveStatus.Unsaved(UnsavedReason.NetworkError))
        }
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_ACTION_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_SAVE_STATUS).assertTextContains("Lỗi kết nối", substring = true)

        composeRule.runOnIdle {
            engineState = engineState.copy(save = SaveStatus.Offline)
        }
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_ACTION_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_SAVE_STATUS).assertTextContains("Mất kết nối", substring = true)

        composeRule.runOnIdle {
            engineState = engineState.copy(
                session = EditSessionState.Editing(UUID.randomUUID()),
                save = SaveStatus.Saved,
            )
        }
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_ACTION_BUTTON).assertIsEnabled()
        composeRule.onNodeWithTag(PlayerTestTags.READ_ONLY_BANNER).assertDoesNotExist()
    }

    @Test
    fun grouping() {
        val base = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-shared"))
        val firstPart = base.parts!!.first()
        val firstLeaves = firstPart.items!!.filter { it.questionType != AttemptItemView.QuestionType.QUESTION_SET }
        val item1 = firstLeaves[0].copy(displayNo = 1, componentCode = "LY")
        val item2 = firstLeaves.getOrElse(1) { firstLeaves[0].copy(itemId = UUID.randomUUID()) }
            .copy(displayNo = 2, componentCode = "HOA")

        val segmentedPart = firstPart.copy(
            state = AttemptPartStateView.State.OPEN,
            displaySegments = listOf(
                AttemptDisplaySegmentView(
                    code = "S1",
                    name = "Đoạn đọc hiểu 1",
                    fromPosition = 1,
                    toPosition = 1,
                    commandCount = 1,
                    maxPoints = BigDecimal("2.5"),
                ),
                AttemptDisplaySegmentView(
                    code = "S2",
                    name = "Đoạn đọc hiểu 2",
                    fromPosition = 2,
                    toPosition = 2,
                    commandCount = 1,
                    maxPoints = BigDecimal("2.5"),
                ),
            ),
            items = listOf(item1, item2),
        )
        var attempt by mutableStateOf(base.copy(parts = listOf(segmentedPart)))

        composeRule.setContent {
            PlayerTestTheme {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(attempt),
                    localUi = PlayerLocalUi(),
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.segmentHeader("S1::0")).assertIsDisplayed()
            .assertTextContains("Đoạn đọc hiểu 1", substring = true)

        val subjectPart = firstPart.copy(
            state = AttemptPartStateView.State.OPEN,
            displaySegments = emptyList(),
            choice = mapOf(
                "optionCode" to JsonPrimitive("SCI"),
                "subjectCodes" to JsonArray(listOf(JsonPrimitive("LY"), JsonPrimitive("HOA"))),
            ),
            items = listOf(item1, item2),
        )
        composeRule.runOnIdle {
            attempt = base.copy(parts = listOf(subjectPart))
        }
        composeRule.onNodeWithTag(PlayerTestTags.subjectHeader("LY::0")).assertIsDisplayed()
            .assertTextContains("Vật lí", substring = true)
    }
}
