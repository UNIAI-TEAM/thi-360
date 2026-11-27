package vn.unicom.thi360.feature.richcontent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import vn.unicom.thi360.feature.richcontent.formula.FormulaRenderResult
import vn.unicom.thi360.feature.richcontent.formula.RaTeXFormulaEngine
import vn.unicom.thi360.feature.richcontent.model.FormulaDisplay
import vn.unicom.thi360.feature.richcontent.model.FormulaNotation
import vn.unicom.thi360.feature.richcontent.model.RichInline
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.RichContentTestTags
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

@RunWith(AndroidJUnit4::class)
class FormulaRenderInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mathInlineAndBlockRenderWithRaTeXAndNoFallback() {
        val sample = RichContentAndroidTestSupport.loadRichTextSamples()
            .first { it.name == "math-inline-and-block" && it.valid }
        val doc = RichHtmlParser.parse(sample.html)
        val engine = RaTeXFormulaEngine(composeRule.activity)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides engine) {
                    Box(modifier = Modifier.width(360.dp)) {
                        RichContentView(document = doc, assets = null, formulaEngine = engine)
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(
            "math-inline-and-block must not show any fallback warning",
            0,
            composeRule.onAllNodesWithTag(RichContentTestTags.FORMULA_FALLBACK).fetchSemanticsNodes().size,
        )
        val blockNode = composeRule.onNodeWithTag(RichContentTestTags.FORMULA_BLOCK).assertIsDisplayed()
        val rootBounds = composeRule.onNodeWithTag(RichContentTestTags.ROOT).getUnclippedBoundsInRoot()
        val blockBounds = blockNode.getUnclippedBoundsInRoot()
        val rootCenter = (rootBounds.left.value + rootBounds.right.value) / 2f
        val blockCenter = (blockBounds.left.value + blockBounds.right.value) / 2f
        assertTrue("Block formula must be centered", abs(rootCenter - blockCenter) <= 6f)

        composeRule.runOnIdle {
            val tvs = RichContentAndroidTestSupport.collectTextViews(composeRule.activity.window.decorView)
            val ratexSpans = tvs.flatMap { RichContentAndroidTestSupport.raTeXSpans(it) }
            val fallbackSpans = tvs.flatMap { RichContentAndroidTestSupport.fallbackSpans(it) }
            assertTrue("Expected inline RaTeXSpan(s) in math-inline-and-block", ratexSpans.isNotEmpty())
            assertTrue("Expected zero inline fallback spans", fallbackSpans.isEmpty())
            ratexSpans.forEach { span ->
                val bmp = Bitmap.createBitmap(span.rendered.width.coerceAtLeast(1), (span.rendered.ascent + span.rendered.descent).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                span.rendered.drawable.draw(c)
                assertTrue("Inline RaTeX drawable must paint visible ink", RichContentAndroidTestSupport.countDarkPixels(bmp) > 10)
            }
        }
    }

    @Test
    fun chemistryMhchemRendersTwoEquationsWithoutFallback() {
        val sample = RichContentAndroidTestSupport.loadRichTextSamples()
            .first { it.name == "chemistry-mhchem" && it.valid }
        val doc = RichHtmlParser.parse(sample.html)
        val engine = RaTeXFormulaEngine(composeRule.activity)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides engine) {
                    Box(modifier = Modifier.width(360.dp)) {
                        RichContentView(document = doc, assets = null, formulaEngine = engine)
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(
            "chemistry-mhchem must render without fallback warnings",
            0,
            composeRule.onAllNodesWithTag(RichContentTestTags.FORMULA_FALLBACK).fetchSemanticsNodes().size,
        )
        composeRule.runOnIdle {
            val tvs = RichContentAndroidTestSupport.collectTextViews(composeRule.activity.window.decorView)
            val ratexSpans = tvs.flatMap { RichContentAndroidTestSupport.raTeXSpans(it) }
            val fallbackSpans = tvs.flatMap { RichContentAndroidTestSupport.fallbackSpans(it) }
            assertEquals("chemistry-mhchem must render 2 inline RaTeXSpans", 2, ratexSpans.size)
            assertTrue("Expected zero inline fallback spans", fallbackSpans.isEmpty())
            ratexSpans.forEach { span ->
                val bmp = Bitmap.createBitmap(
                    span.rendered.width.coerceAtLeast(1),
                    (span.rendered.ascent + span.rendered.descent).coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                span.rendered.drawable.draw(c)
                assertTrue("Chemistry span must paint visible ink", RichContentAndroidTestSupport.countDarkPixels(bmp) > 20)
            }
        }

        val eq1 = engine.render(
            RichInline.Formula("""\ce{CaCO3(s) <=> CaO(s) + CO2(g)}""", FormulaDisplay.Block, FormulaNotation.Ce),
            44f,
            Color.BLACK,
        )
        val eq2 = engine.render(
            RichInline.Formula("""\Delta_r H^\circ_{298} = 179,2\ \mathrm{kJ}""", FormulaDisplay.Block, FormulaNotation.Tex),
            44f,
            Color.BLACK,
        )
        assertTrue("Equation 1 must render", eq1 is FormulaRenderResult.Rendered)
        assertTrue("Equation 2 must render", eq2 is FormulaRenderResult.Rendered)
    }

    @Test
    fun rawDelimitersVietnameseTextAndUnmatchedDelimiter() {
        val html = """
            <p>Tính \(x^2 + 1\) và \[\text{Vận tốc } v = \frac{s}{t}\] với \(x + 1 là chữ thường</p>
        """.trimIndent()
        val doc = RichHtmlParser.parse(html)
        val engine = RaTeXFormulaEngine(composeRule.activity)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides engine) {
                    Box(modifier = Modifier.width(360.dp)) {
                        RichContentView(document = doc, assets = null, formulaEngine = engine)
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(
            "Unmatched delimiter must not produce a formula warning box",
            0,
            composeRule.onAllNodesWithTag(RichContentTestTags.FORMULA_FALLBACK).fetchSemanticsNodes().size,
        )
        assertEquals(
            "Raw block formula must render as FORMULA_BLOCK",
            1,
            composeRule.onAllNodesWithTag(RichContentTestTags.FORMULA_BLOCK).fetchSemanticsNodes().size,
        )

        composeRule.runOnIdle {
            val tvs = RichContentAndroidTestSupport.collectTextViews(composeRule.activity.window.decorView)
            val ratexSpans = tvs.flatMap { RichContentAndroidTestSupport.raTeXSpans(it) }
            val fallbackSpans = tvs.flatMap { RichContentAndroidTestSupport.fallbackSpans(it) }
            assertEquals("Expected 1 inline RaTeXSpan for \\(x^2 + 1\\)", 1, ratexSpans.size)
            assertTrue("Expected zero fallback spans", fallbackSpans.isEmpty())
            val combinedText = tvs.joinToString(" ") { it.text.toString() }
            assertTrue(
                "Unmatched delimiter must stay literal plain text, got: $combinedText",
                combinedText.contains("\\(x + 1 là chữ thường"),
            )
        }

        val vnResult = engine.render(
            RichInline.Formula("""\text{Vận tốc } v = \frac{s}{t}""", FormulaDisplay.Block, FormulaNotation.Tex),
            44f,
            Color.BLACK,
        )
        assertTrue("Vietnamese \\text{Vận tốc } formula must render", vnResult is FormulaRenderResult.Rendered)
        val rendered = vnResult as FormulaRenderResult.Rendered
        val ratexDrawable = rendered.drawable as vn.unicom.thi360.feature.richcontent.formula.RaTeXDrawable
        val glyphCodes = ratexDrawable.renderer.displayList.items
            .filterIsInstance<io.ratex.DisplayItem.GlyphPath>()
            .map { it.charCode }
            .toSet()
        assertTrue("Vietnamese 'ậ' (U+1EAD) must be preserved intact in DisplayList", 'ậ'.code in glyphCodes)
        assertTrue("Vietnamese 'ố' (U+1ED1) must be preserved intact in DisplayList", 'ố'.code in glyphCodes)
        val bmp = Bitmap.createBitmap(rendered.width, rendered.ascent + rendered.descent, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        rendered.drawable.draw(canvas)
        assertTrue("Vietnamese formula must draw ink", RichContentAndroidTestSupport.countDarkPixels(bmp) > 30)
    }

    @Test
    fun brokenSources() {
        val html = """
            <p>Đoạn trước công thức hỏng</p>
            <p>Biết \(\broken{syntax}\) thì</p>
            <p>\[\frac{1}{\]</p>
            <p>Đoạn sau công thức hỏng</p>
        """.trimIndent()
        val doc = RichHtmlParser.parse(html)
        val engine = RaTeXFormulaEngine(composeRule.activity)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides engine) {
                    Box(modifier = Modifier.width(360.dp)) {
                        RichContentView(document = doc, assets = null, formulaEngine = engine)
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Đoạn trước công thức hỏng").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Đoạn sau công thức hỏng").assertIsDisplayed()
        composeRule.onNodeWithTag(RichContentTestTags.FORMULA_FALLBACK).assertIsDisplayed()
        composeRule.onNodeWithText("""\[\frac{1}{\]""").assertIsDisplayed()

        composeRule.runOnIdle {
            val tvs = RichContentAndroidTestSupport.collectTextViews(composeRule.activity.window.decorView)
            val inlineTv = tvs.first { it.text.toString().contains("Biết") && it.text.toString().contains("thì") }
            val fallbacks = RichContentAndroidTestSupport.fallbackSpans(inlineTv)
            assertEquals("Inline broken syntax must render 1 FormulaFallbackSpan", 1, fallbacks.size)
            assertEquals("""\(\broken{syntax}\)""", fallbacks.single().source)
            assertEquals("Inline broken formula must stay on the same line between Biết and thì", 1, inlineTv.lineCount)
        }
    }
}
