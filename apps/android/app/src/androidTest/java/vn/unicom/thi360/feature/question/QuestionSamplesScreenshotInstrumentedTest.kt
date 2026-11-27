package vn.unicom.thi360.feature.question

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionSetView
import vn.unicom.thi360.feature.question.ui.QuestionView
import vn.unicom.thi360.feature.richcontent.formula.RaTeXFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine

@RunWith(AndroidJUnit4::class)
class QuestionSamplesScreenshotInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersAllElevenQuestionSamplesInLightAndDarkModesWithRaTeX() {
        val samples = QuestionAndroidTestSupport.loadSamples()
        assertEquals(11, samples.size)

        val engine = RaTeXFormulaEngine(composeRule.activity)
        val activeIndex = mutableIntStateOf(0)
        val activeTheme = mutableStateOf(ThemeMode.LIGHT)

        composeRule.setContent {
            val sample = samples[activeIndex.intValue]
            Thi360Theme(
                preferences = AppPreferences(
                    locale = AppLocale.VI,
                    themeMode = activeTheme.value,
                ),
            ) {
                CompositionLocalProvider(LocalFormulaEngine provides engine) {
                    Box(
                        modifier = Modifier
                            .width(390.dp)
                            .background(Thi360Theme.colors.bgPage)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        if (sample.item is QuestionItem.QuestionSet) {
                            QuestionSetView(
                                item = sample.item,
                                answers = sample.submittedChildren,
                                mode = QuestionMode.Review(sample.review),
                                formulaEngine = engine,
                            )
                        } else {
                            QuestionView(
                                item = sample.item,
                                answer = sample.submitted,
                                mode = QuestionMode.Review(sample.review),
                                formulaEngine = engine,
                            )
                        }
                    }
                }
            }
        }

        val outDir = QuestionAndroidTestSupport.outputDir("question-samples")
        var writtenCount = 0

        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            val suffix = if (mode == ThemeMode.LIGHT) "light" else "dark"
            val bgColor = if (mode == ThemeMode.LIGHT) Color.WHITE else Color.parseColor("#0F1418")
            for (i in samples.indices) {
                val sample = samples[i]
                composeRule.runOnIdle {
                    activeTheme.value = mode
                    activeIndex.intValue = i
                }
                composeRule.waitForIdle()
                Thread.sleep(80)
                composeRule.waitForIdle()

                composeRule.runOnIdle {
                    val contentView = composeRule.activity.window.decorView
                        .findViewById<android.view.View>(android.R.id.content)
                    val bmp = QuestionAndroidTestSupport.captureViewBitmap(contentView, bgColor)
                    val outFile = File(outDir, "question_${sample.key}_$suffix.png")
                    QuestionAndroidTestSupport.savePng(bmp, outFile)
                    QuestionAndroidTestSupport.mirrorToHostTmp(
                        outFile,
                        "question-samples/question_${sample.key}_$suffix.png",
                    )
                    bmp.recycle()
                    assertTrue("PNG ${outFile.name} must be non-empty", outFile.length() > 100L)
                    writtenCount += 1
                }
            }
        }

        assertEquals(22, writtenCount)
    }
}
