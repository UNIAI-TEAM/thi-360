package vn.unicom.thi360.feature.question

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.feature.question.logic.WordCounter
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

class WordCounterTest {

    @Test
    fun matchesAllThirtyContractWordCountCasesAndSpecEdgeCases() {
        assertEquals(2, WordCounter.count("<p>a</p><p>b</p>"))
        assertEquals(4, WordCounter.count("<p>Xin chào – thế giới !</p>"))

        val raw = QuestionSamplesLoader.richTextFixtureFile("word-count.json").readText(Charsets.UTF_8)
        val root = Json.parseToJsonElement(raw).jsonObject
        val cases = root["cases"]!!.jsonArray
        assertEquals(30, cases.size)

        for (entry in cases) {
            val obj = entry.jsonObject
            val id = obj["id"]!!.jsonPrimitive.content
            val html = obj["html"]!!.jsonPrimitive.content
            val expected = obj["expected"]!!.jsonPrimitive.int
            assertEquals("Word count mismatch on $id", expected, WordCounter.count(html))
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class EssayWordCountTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsWarningUnderWordLimitAndMetStateAtOrAboveWordLimit() {
        val item = QuestionItem.Essay(
            id = "essay-200",
            displayNo = 27,
            stem = "<p>Viết bài nghị luận</p>",
            wordLimit = 200,
        )
        val words150 = "<p>${List(150) { "chữ" }.joinToString(" ")}</p>"
        val words205 = "<p>${List(205) { "chữ" }.joinToString(" ")}</p>"
        var answer by mutableStateOf(EssayAnswer(html = words150, wordCount = 150))

        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = item,
                    answer = answer,
                    mode = QuestionMode.Answering,
                )
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_WORD_COUNT)
            .assertTextEquals("150 / tối thiểu 200 chữ")
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_WORD_STATUS)
            .assertTextEquals("Còn 50 chữ nữa để đạt yêu cầu.")

        answer = EssayAnswer(html = words205, wordCount = 205)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_WORD_COUNT)
            .assertTextEquals("205 / tối thiểu 200 chữ")
        composeRule.onNodeWithTag(QuestionTestTags.ESSAY_WORD_STATUS)
            .assertTextEquals("Đã đạt yêu cầu số chữ")
    }
}
