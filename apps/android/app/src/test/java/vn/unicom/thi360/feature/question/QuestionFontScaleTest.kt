package vn.unicom.thi360.feature.question

import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.feature.question.mapper.QuestionItemMapper
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionSetView
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class QuestionFontScaleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allElevenSamplesRenderCleanlyAt200PercentFontScaleOn360dp() {
        val samples = QuestionSamplesLoader.loadAll()
        assertEquals(11, samples.size)
        var currentIndex by mutableStateOf(0)

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 2.0f),
            ) {
                QuestionTestHost {
                    val sample = samples[currentIndex]
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (sample.item is QuestionItem.QuestionSet) {
                            QuestionSetView(
                                item = sample.item,
                                answers = sample.submittedChildren,
                                mode = QuestionMode.Answering,
                            )
                        } else {
                            QuestionView(
                                item = sample.item,
                                answer = sample.submitted,
                                mode = QuestionMode.Answering,
                            )
                        }
                    }
                }
            }
        }

        for (index in samples.indices) {
            currentIndex = index
            composeRule.waitForIdle()
            val expectedTag = if (samples[index].item is QuestionItem.QuestionSet) {
                QuestionTestTags.SET_ROOT
            } else {
                QuestionTestTags.FRAME_ROOT
            }
            composeRule.onNodeWithTag(expectedTag).assertIsDisplayed()
        }
    }

    @Test
    fun switchingFontScalePreservesInProgressInputForShortClozeAndEssay() {
        val shortSample = QuestionSamplesLoader.loadByKey("short")
        val clozeSample = QuestionSamplesLoader.loadByKey("cloze")
        val essaySample = QuestionSamplesLoader.loadByKey("essay")
        var scale by mutableFloatStateOf(1.0f)
        var shortAns by mutableStateOf<AnswerValue>(ShortAnswerValue("56"))
        var clozeAns by mutableStateOf<AnswerValue>(ClozeAnswer(linkedMapOf("b1" to "100", "b2" to "0")))
        var essayAns by mutableStateOf<AnswerValue>(EssayAnswer("<p>Đoạn mở bài đang dở</p>", 5))
        var rootView: View? = null

        composeRule.setContent {
            rootView = LocalView.current
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = scale),
            ) {
                QuestionTestHost {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        QuestionView(
                            item = shortSample.item,
                            answer = shortAns,
                            mode = QuestionMode.Answering,
                            onAnswer = { shortAns = it },
                        )
                        QuestionView(
                            item = clozeSample.item,
                            answer = clozeAns,
                            mode = QuestionMode.Answering,
                            onAnswer = { clozeAns = it },
                        )
                        QuestionView(
                            item = essaySample.item,
                            answer = essayAns,
                            mode = QuestionMode.Answering,
                            onAnswer = { essayAns = it },
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        scale = 2.0f
        composeRule.waitForIdle()

        val allTexts = findAllEditTexts(rootView!!).map { it.text.toString() }
        assertTrue(allTexts.contains("56"))
        assertTrue(allTexts.contains("100"))
        assertTrue(allTexts.contains("0"))
        assertTrue(allTexts.any { it.contains("Đoạn mở bài đang dở") })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FiftyItemPartRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fiftyItemFixtureRendersLazilyWithoutComposingOffscreenItems() {
        val raw = QuestionSamplesLoader.richTextFixtureFile("attempt-50.json").readText(Charsets.UTF_8)
        val attempt = Serializer.kotlinxSerializationJson.decodeFromString(
            AttemptStateResponse.serializer(),
            raw,
        )
        val rawItems = attempt.parts.orEmpty().flatMap { it.items.orEmpty() }
        val items = QuestionItemMapper.mapAttemptItems(rawItems)
        assertTrue("Expected at least 20 mapped top-level items from 50-item fixture, got ${items.size}", items.size >= 20)

        composeRule.setContent {
            QuestionTestHost {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = items, key = { it.id }) { item ->
                        QuestionView(
                            item = item,
                            answer = null,
                            mode = QuestionMode.Answering,
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val initiallyComposed = composeRule.onAllNodesWithTag(QuestionTestTags.FRAME_ROOT)
            .fetchSemanticsNodes().size
        assertTrue("Expected lazy composition (< 15 visible frames), got $initiallyComposed", initiallyComposed in 1..15)
    }
}
