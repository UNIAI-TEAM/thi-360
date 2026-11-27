package vn.unicom.thi360.feature.richcontent

import android.graphics.Paint
import android.text.Spanned
import android.text.style.UnderlineSpan
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichParseOptions
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.BlankChipSpan
import vn.unicom.thi360.feature.richcontent.ui.LabeledUnderlineSpan
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RichUnderlineAndBlankTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun underlineLabelsRenderAUnderHasBeenAndBUnderToWithExpandedLineDescent() {
        val html = """<p>For millennia, the coconuts <u data-label="A">has been</u> central <u data-label="B">to</u> the lives of Polynesian peoples. <u>Plain underline</u> stays plain.</p>"""
        val doc = RichHtmlParser.parse(html)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                    RichContentView(document = doc, assets = null)
                }
            }
        }
        composeRule.waitForIdle()

        val tv = collectTextViews(composeRule.activity.window.decorView)
            .first { it.text.toString().contains("has been") }
        val spanned = tv.text as Spanned
        val labeledSpans = spanned.getSpans(0, spanned.length, LabeledUnderlineSpan::class.java)
            .sortedBy { spanned.getSpanStart(it) }

        assertEquals(2, labeledSpans.size)
        assertEquals('A', labeledSpans[0].label)
        assertEquals("has been", spanned.subSequence(spanned.getSpanStart(labeledSpans[0]), spanned.getSpanEnd(labeledSpans[0])).toString())
        assertEquals('B', labeledSpans[1].label)
        assertEquals("to", spanned.subSequence(spanned.getSpanStart(labeledSpans[1]), spanned.getSpanEnd(labeledSpans[1])).toString())

        val plainUnderlines = spanned.getSpans(0, spanned.length, UnderlineSpan::class.java)
        assertEquals(1, plainUnderlines.size)
        assertEquals(
            "Plain underline",
            spanned.subSequence(spanned.getSpanStart(plainUnderlines[0]), spanned.getSpanEnd(plainUnderlines[0])).toString(),
        )

        val baseFm = tv.paint.fontMetricsInt
        val expandedFm = Paint.FontMetricsInt()
        labeledSpans[0].getSize(tv.paint, spanned, spanned.getSpanStart(labeledSpans[0]), spanned.getSpanEnd(labeledSpans[0]), expandedFm)
        assertTrue(
            "LabeledUnderlineSpan descent (${expandedFm.descent}) must exceed base descent (${baseFm.descent}) so label clears next line",
            expandedFm.descent > baseFm.descent + 12,
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun numberedBlankRespectsBlankNumberStartAndDoesNotBreakMidChip() {
        val html = """<p>The Holocene began <span class="blank" data-blank="2"></span> years ago.</p>"""
        val doc = RichHtmlParser.parse(html, RichParseOptions(blankNumberStart = 31))

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                    RichContentView(document = doc, assets = null, blankNumberStart = 31)
                }
            }
        }
        composeRule.waitForIdle()

        val tv = collectTextViews(composeRule.activity.window.decorView)
            .first { it.text.toString().contains("(32) ____") }
        val spanned = tv.text as Spanned
        val chips = spanned.getSpans(0, spanned.length, BlankChipSpan::class.java)
        assertEquals(1, chips.size)
        assertEquals("(32) ____", chips.single().chipText)

        val layout = tv.layout
        val start = spanned.getSpanStart(chips.single())
        val end = spanned.getSpanEnd(chips.single()) - 1
        assertEquals(layout.getLineForOffset(start), layout.getLineForOffset(end))
    }
}

