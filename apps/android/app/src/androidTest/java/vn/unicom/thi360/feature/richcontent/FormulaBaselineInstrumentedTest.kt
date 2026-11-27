package vn.unicom.thi360.feature.richcontent

import android.graphics.Color
import android.text.Spanned
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.richcontent.formula.RaTeXFormulaEngine
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.RaTeXSpan
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

@RunWith(AndroidJUnit4::class)
class FormulaBaselineInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun inlineFractionAlignsBaselineFractionBarAndDoesNotOverlapAdjacentLines() {
        val html = """<p>Dòng trên tham chiếu<br>Biết \(x + \frac{a}{b}\) thì<br>Dòng dưới tham chiếu</p>"""
        val doc = RichHtmlParser.parse(html)
        val engine = RaTeXFormulaEngine(composeRule.activity)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides engine) {
                    Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                        RichContentView(document = doc, assets = null, formulaEngine = engine)
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val tvs = RichContentAndroidTestSupport.collectTextViews(composeRule.activity.window.decorView)
            val tv = tvs.first { it.text.toString().contains("Biết") && it.text.toString().contains("thì") }
            val layout = requireNotNull(tv.layout) { "TextView layout must be measured" }
            val spanned = tv.text as Spanned
            val span = spanned.getSpans(0, spanned.length, RaTeXSpan::class.java).single()

            val textStr = tv.text.toString()
            val bietStart = textStr.indexOf("Biết")
            val spanStart = spanned.getSpanStart(span)
            val thiStart = textStr.indexOf("thì")

            val bietLine = layout.getLineForOffset(bietStart)
            val spanLine = layout.getLineForOffset(spanStart)
            val thiLine = layout.getLineForOffset(thiStart)
            assertEquals("Biết and formula must be on same line", bietLine, spanLine)
            assertEquals("Formula and thì must be on same line", spanLine, thiLine)
            assertTrue("Expected reference line above and below", spanLine >= 1 && layout.lineCount >= spanLine + 2)

            val lineBaseline = layout.getLineBaseline(spanLine)
            val prevLineBottom = layout.getLineBottom(spanLine - 1)
            val nextLineTop = layout.getLineTop(spanLine + 1)
            val spanTopY = lineBaseline - span.rendered.ascent
            val spanBottomY = lineBaseline + span.rendered.descent

            assertTrue(
                "Numerator top ($spanTopY) must not touch previous line bottom ($prevLineBottom)",
                spanTopY >= prevLineBottom,
            )
            assertTrue(
                "Denominator bottom ($spanBottomY) must not touch next line top ($nextLineTop)",
                spanBottomY <= nextLineTop,
            )

            val bmp = RichContentAndroidTestSupport.captureViewBitmap(tv, Color.WHITE)
            val baselineFile = File(RichContentAndroidTestSupport.outputDir("baseline"), "formula_baseline_ac15.png")
            RichContentAndroidTestSupport.savePng(bmp, baselineFile)
            RichContentAndroidTestSupport.mirrorToHostTmp(baselineFile, "baseline/formula_baseline_ac15.png")

            val spanLeftX = layout.getPrimaryHorizontal(spanStart).toInt().coerceIn(0, bmp.width - 1)
            val spanRightX = (spanLeftX + span.rendered.width).coerceAtMost(bmp.width)
            val spanWidth = (spanRightX - spanLeftX).coerceAtLeast(1)

            val xColEnd = (spanLeftX + (spanWidth * 0.24f).toInt()).coerceAtMost(spanRightX)
            var xInkTop = Int.MAX_VALUE
            var xInkBottom = Int.MIN_VALUE
            for (y in spanTopY.coerceAtLeast(0) until spanBottomY.coerceAtMost(bmp.height)) {
                for (x in spanLeftX until xColEnd) {
                    val px = bmp.getPixel(x, y)
                    val lum = (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
                    if (lum < 200) {
                        if (y < xInkTop) xInkTop = y
                        if (y > xInkBottom) xInkBottom = y
                    }
                }
            }
            assertTrue("Formula 'x' must have visible ink", xInkTop < xInkBottom)
            assertTrue(
                "Baseline of 'x' ($xInkBottom) must align within 1px of TextView baseline ($lineBaseline)",
                abs(xInkBottom - lineBaseline) <= 1,
            )

            val fracStartX = (spanLeftX + (spanWidth * 0.55f).toInt()).coerceAtMost(spanRightX - 1)
            var bestBarY = -1
            var bestBarDarkCols = 0
            for (y in spanTopY.coerceAtLeast(0) until spanBottomY.coerceAtMost(bmp.height)) {
                var darkCols = 0
                for (x in fracStartX until spanRightX) {
                    val px = bmp.getPixel(x, y)
                    val lum = (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
                    if (lum < 200) darkCols++
                }
                if (darkCols > bestBarDarkCols) {
                    bestBarDarkCols = darkCols
                    bestBarY = y
                }
            }
            assertTrue("Expected visible fraction bar in formula", bestBarY > 0 && bestBarDarkCols >= 6)
            assertTrue(
                "Fraction bar ($bestBarY) must lie between top of 'x' ($xInkTop) and baseline ($lineBaseline)",
                bestBarY in (xInkTop + 1) until lineBaseline,
            )
        }
    }
}

