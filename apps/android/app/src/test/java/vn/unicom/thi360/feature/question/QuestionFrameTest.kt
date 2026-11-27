package vn.unicom.thi360.feature.question

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.StatusPalette
import vn.unicom.thi360.feature.question.model.ItemOutcome
import vn.unicom.thi360.feature.question.model.ItemReview
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.model.ReviewLevel
import vn.unicom.thi360.feature.question.ui.QuestionFrame
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuestionFrameTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun answeringFrameRendersTitleTypeChipAttachmentsAndTogglesFlagWith48dpTarget() {
        val sample = QuestionSamplesLoader.loadByKey("single")
        var flagged by mutableStateOf(false)
        var toggleCount = 0

        composeRule.setContent {
            QuestionTestHost {
                QuestionFrame(
                    item = sample.item,
                    mode = QuestionMode.Answering,
                    flagged = flagged,
                    onFlag = {
                        flagged = !flagged
                        toggleCount += 1
                    },
                    attachments = {
                        Text("Audio attachment slot")
                    },
                ) {
                    Text("Question body slot")
                }
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.FRAME_TITLE).assertTextEquals("Câu 19")
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_TYPE_CHIP).assertTextEquals("Chọn một đáp án")
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_ATTACHMENTS).assertIsDisplayed()
        composeRule.onNodeWithText("Audio attachment slot").assertIsDisplayed()

        val flagNode = composeRule.onNodeWithTag(QuestionTestTags.FRAME_FLAG_BUTTON)
        flagNode.assertIsDisplayed()
        flagNode.assertWidthIsAtLeast(48.dp)
        flagNode.assertHeightIsAtLeast(48.dp)
        flagNode.assertContentDescriptionEquals("Đánh dấu xem lại")

        flagNode.performClick()
        composeRule.waitForIdle()
        assertTrue(flagged)
        assertEquals(1, toggleCount)
        flagNode.assertContentDescriptionEquals("Bỏ đánh dấu")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun lockedAndReviewModesHideFlagButtonAndShowOutcomeAndSolution() {
        val sample = QuestionSamplesLoader.loadByKey("single")
        var mode by mutableStateOf<QuestionMode>(QuestionMode.Locked)

        composeRule.setContent {
            QuestionTestHost {
                QuestionFrame(
                    item = sample.item,
                    mode = mode,
                    flagged = true,
                    onFlag = {},
                ) {
                    Text("Body")
                }
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.FRAME_FLAG_BUTTON).assertDoesNotExist()

        mode = QuestionMode.Review(
            ItemReview(
                outcome = ItemOutcome.WRONG,
                earned = BigDecimal.ZERO,
                max = BigDecimal.ONE,
                level = ReviewLevel.FULL_SOLUTION,
                solutionHtml = "<p>Lời giải chi tiết</p>",
            ),
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(QuestionTestTags.FRAME_FLAG_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_OUTCOME_CHIP).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_POINTS).assertTextEquals("0 / 1 điểm")
        composeRule.onNodeWithTag(QuestionTestTags.FRAME_SOLUTION_BLOCK).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun question_frame_flagged_light() {
        val sample = QuestionSamplesLoader.loadByKey("single")
        assertEquals(StatusPalette.Light.warning, StatusPalette.forMode(ThemeMode.LIGHT).warning)
        composeRule.setContent {
            QuestionTestHost(themeMode = ThemeMode.LIGHT) {
                QuestionFrame(
                    item = sample.item,
                    mode = QuestionMode.Answering,
                    flagged = true,
                    onFlag = {},
                ) {
                    Text("Nội dung phương án")
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/question_frame_flagged_light.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun question_frame_flagged_dark() {
        val sample = QuestionSamplesLoader.loadByKey("single")
        assertEquals(StatusPalette.Dark.warning, StatusPalette.forMode(ThemeMode.DARK).warning)
        composeRule.setContent {
            QuestionTestHost(themeMode = ThemeMode.DARK) {
                QuestionFrame(
                    item = sample.item,
                    mode = QuestionMode.Answering,
                    flagged = true,
                    onFlag = {},
                ) {
                    Text("Nội dung phương án")
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/question_frame_flagged_dark.png")
    }
}
