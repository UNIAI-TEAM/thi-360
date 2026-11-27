package vn.unicom.thi360.feature.richcontent

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

internal fun collectTextViews(root: View): List<TextView> {
    val result = mutableListOf<TextView>()
    fun walk(v: View) {
        if (v is TextView) result += v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
    }
    walk(root)
    return result
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RichContentViewTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun tapHasNoAction() {
        val html = """<p>a <font>b</font> c</p><script>x()</script><p style="color:red" onclick="x()">d</p>"""
        val doc = RichHtmlParser.parse(html)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                    RichContentView(document = doc, assets = null)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("a b c").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("d").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        val tvs = collectTextViews(composeRule.activity.window.decorView)
        val combinedText = tvs.joinToString("\n") { it.text.toString() }
        assertTrue(combinedText.contains("a b c"))
        assertTrue(combinedText.contains("d"))
        assertFalse("Script content x() must not be rendered", combinedText.contains("x()"))

        val dTextView = tvs.first { it.text.toString() == "d" }
        assertEquals(BrandPalette.DefaultLightColors.text.toArgb(), dTextView.currentTextColor)
        assertFalse("Paragraph TextView must not be clickable", dTextView.isClickable)
        assertFalse("Paragraph TextView must not handle links", dTextView.linksClickable)
        assertFalse("Tapping 'd' must not invoke any click action", dTextView.performClick())
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun inlineStylesAndOrderedList() {
        val html = """<p>H<sub>2</sub>O và x<sup>2</sup>, <strong>đậm</strong> <em>nghiêng</em> <u>gạch chân</u> <s>gạch ngang</s></p><ol><li>một</li><li>hai</li></ol>"""
        val doc = RichHtmlParser.parse(html)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                    RichContentView(document = doc, assets = null)
                }
            }
        }
        composeRule.waitForIdle()

        val tvs = collectTextViews(composeRule.activity.window.decorView)
        val styledTv = tvs.first { it.text.toString().contains("H2O") }
        val spanned = styledTv.text as Spanned

        assertTrue(spanned.getSpans(0, spanned.length, SubscriptSpan::class.java).isNotEmpty())
        assertTrue(spanned.getSpans(0, spanned.length, SuperscriptSpan::class.java).isNotEmpty())
        val styleSpans = spanned.getSpans(0, spanned.length, StyleSpan::class.java)
        assertTrue(styleSpans.any { it.style == Typeface.BOLD })
        assertTrue(styleSpans.any { it.style == Typeface.ITALIC })
        assertTrue(spanned.getSpans(0, spanned.length, UnderlineSpan::class.java).isNotEmpty())
        assertTrue(spanned.getSpans(0, spanned.length, StrikethroughSpan::class.java).isNotEmpty())

        composeRule.onNodeWithText("1.").assertIsDisplayed()
        composeRule.onNodeWithText("2.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("một").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("hai").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun longWordWrapsAt360dp() {
        val longToken = "a".repeat(300)
        val doc = RichHtmlParser.parse("<p>$longToken</p>")

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                    Box(modifier = Modifier.width(360.dp)) {
                        RichContentView(document = doc, assets = null)
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val tvs = collectTextViews(composeRule.activity.window.decorView)
        val longTv = tvs.first { it.text.toString() == longToken }
        val density = composeRule.activity.resources.displayMetrics.density
        val maxPx = (360f * density).toInt() + 2
        assertTrue(
            "Long unspaced word width (${longTv.width}px) must fit inside 360dp column (${maxPx}px)",
            longTv.width <= maxPx,
        )
        assertTrue(
            "Long 300-char word must wrap across multiple lines, got lineCount=${longTv.lineCount}",
            longTv.lineCount > 1,
        )
    }
}
