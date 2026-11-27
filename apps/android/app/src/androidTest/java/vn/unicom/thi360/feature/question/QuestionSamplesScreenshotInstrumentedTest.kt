package vn.unicom.thi360.feature.question

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
        lateinit var scrollState: ScrollState
        lateinit var scope: CoroutineScope
        var viewportTopPx = 0
        var viewportHeightPx = 0

        composeRule.setContent {
            val sample = samples[activeIndex.intValue]
            scrollState = rememberScrollState()
            scope = rememberCoroutineScope()
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
                            .onGloballyPositioned { coords ->
                                val bounds = coords.boundsInRoot()
                                viewportTopPx = bounds.top.roundToInt().coerceAtLeast(0)
                                viewportHeightPx = bounds.height.roundToInt().coerceAtLeast(1)
                            }
                            .background(Thi360Theme.colors.bgPage)
                            .verticalScroll(scrollState)
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
                    scope.launch { scrollState.scrollTo(0) }
                }
                composeRule.waitForIdle()
                Thread.sleep(80)
                composeRule.waitForIdle()

                val bmp = captureFullScrolledSample(
                    scrollState = scrollState,
                    scope = scope,
                    viewportTopPx = viewportTopPx,
                    viewportHeightPx = viewportHeightPx,
                    bgColor = bgColor,
                )
                val outFile = File(outDir, "question_${sample.key}_$suffix.png")
                QuestionAndroidTestSupport.savePng(bmp, outFile)
                QuestionAndroidTestSupport.mirrorToHostTmp(
                    outFile,
                    "question-samples/question_${sample.key}_$suffix.png",
                )
                if (sample.key == "set") {
                    assertTrue(
                        "Expected full unstitched height for demo-set (${bmp.height} > $viewportHeightPx)",
                        bmp.height > viewportHeightPx,
                    )
                }
                bmp.recycle()
                assertTrue("PNG ${outFile.name} must be non-empty", outFile.length() > 100L)
                writtenCount += 1
            }
        }

        assertEquals(22, writtenCount)
    }

    private fun captureFullScrolledSample(
        scrollState: ScrollState,
        scope: CoroutineScope,
        viewportTopPx: Int,
        viewportHeightPx: Int,
        bgColor: Int,
    ): Bitmap {
        val contentView = composeRule.activity.window.decorView
            .findViewById<android.view.View>(android.R.id.content)
        val maxScroll = scrollState.maxValue
        if (maxScroll <= 0) {
            var single: Bitmap? = null
            composeRule.runOnIdle {
                single = QuestionAndroidTestSupport.captureViewBitmap(contentView, bgColor)
            }
            return single!!
        }

        val sliceHeight = viewportHeightPx.coerceAtMost(contentView.height - viewportTopPx).coerceAtLeast(1)
        val totalHeight = sliceHeight + maxScroll
        val result = Bitmap.createBitmap(contentView.width.coerceAtLeast(1), totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(bgColor)

        var firstSlice: Bitmap? = null
        composeRule.runOnIdle {
            firstSlice = QuestionAndroidTestSupport.captureViewBitmap(contentView, bgColor)
        }
        val initialBitmap = checkNotNull(firstSlice)
        canvas.drawBitmap(
            initialBitmap,
            Rect(0, viewportTopPx, initialBitmap.width, viewportTopPx + sliceHeight),
            Rect(0, 0, initialBitmap.width, sliceHeight),
            null,
        )
        initialBitmap.recycle()

        var prevScroll = 0
        while (prevScroll < maxScroll) {
            val nextScroll = (prevScroll + sliceHeight).coerceAtMost(maxScroll)
            val delta = nextScroll - prevScroll
            composeRule.runOnIdle {
                scope.launch { scrollState.scrollTo(nextScroll) }
            }
            composeRule.waitForIdle()
            Thread.sleep(40)
            composeRule.waitForIdle()

            var slice: Bitmap? = null
            composeRule.runOnIdle {
                slice = QuestionAndroidTestSupport.captureViewBitmap(contentView, bgColor)
            }
            val sliceBitmap = checkNotNull(slice)
            val srcTop = viewportTopPx + sliceHeight - delta
            val srcBottom = viewportTopPx + sliceHeight
            val dstTop = prevScroll + sliceHeight
            canvas.drawBitmap(
                sliceBitmap,
                Rect(0, srcTop, sliceBitmap.width, srcBottom),
                Rect(0, dstTop, sliceBitmap.width, dstTop + delta),
                null,
            )
            sliceBitmap.recycle()
            prevScroll = nextScroll
        }

        composeRule.runOnIdle {
            scope.launch { scrollState.scrollTo(0) }
        }
        composeRule.waitForIdle()
        return result
    }
}
