package vn.unicom.thi360.feature.question

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.feature.question.model.AnswerFormat
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuestionSamplesScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun question_review_choice_light() {
        val sample = QuestionSamplesLoader.loadByKey("single")
        composeRule.setContent {
            QuestionTestHost(themeMode = ThemeMode.LIGHT) {
                QuestionView(
                    item = sample.item,
                    answer = sample.submitted,
                    mode = QuestionMode.Review(sample.review),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/question_review_choice_light.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun question_review_choice_dark() {
        val sample = QuestionSamplesLoader.loadByKey("single")
        composeRule.setContent {
            QuestionTestHost(themeMode = ThemeMode.DARK) {
                QuestionView(
                    item = sample.item,
                    answer = sample.submitted,
                    mode = QuestionMode.Review(sample.review),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/question_review_choice_dark.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun question_short_symbol_light() {
        val item = QuestionItem.ShortAnswer(
            id = "snap-chem",
            displayNo = 15,
            stem = "<p>Viết công thức ion đồng(II) trong dung dịch.</p>",
            answerFormat = AnswerFormat.CHEM_FORMULA,
            inputHint = "Viết công thức ion, ví dụ Fe2+",
        )
        composeRule.setContent {
            QuestionTestHost(themeMode = ThemeMode.LIGHT) {
                QuestionView(
                    item = item,
                    answer = null,
                    mode = QuestionMode.Answering,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/question_short_symbol_light.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun question_cloze_light() {
        val sample = QuestionSamplesLoader.loadByKey("cloze")
        composeRule.setContent {
            QuestionTestHost(themeMode = ThemeMode.LIGHT) {
                QuestionView(
                    item = sample.item,
                    answer = sample.submitted,
                    mode = QuestionMode.Answering,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/question_cloze_light.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun question_essay_dark() {
        val sample = QuestionSamplesLoader.loadByKey("essay")
        composeRule.setContent {
            QuestionTestHost(themeMode = ThemeMode.DARK) {
                QuestionView(
                    item = sample.item,
                    answer = sample.submitted,
                    mode = QuestionMode.Answering,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/question_essay_dark.png")
    }
}
