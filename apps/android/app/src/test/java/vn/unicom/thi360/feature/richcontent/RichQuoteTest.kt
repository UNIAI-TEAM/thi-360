package vn.unicom.thi360.feature.richcontent

import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.RichContentTestTags
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RichQuoteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun twoStanzaPoemShowsNumberFiveOnlyOnLineFive() {
        val html = """
            <blockquote class="quote" data-kind="poem" data-line-numbers="5">
                <p>Dòng một<br>Dòng hai<br>Dòng ba<br>Dòng bốn</p>
                <p>Dòng năm đầu khổ hai<br>Dòng sáu<br>Dòng bảy<br>Dòng tám</p>
            </blockquote>
        """.trimIndent()
        val doc = RichHtmlParser.parse(html)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                    RichContentView(document = doc, assets = null)
                }
            }
        }
        composeRule.waitForIdle()

        for (line in 1..8) {
            val expected = if (line == 5) "5" else ""
            composeRule.onNodeWithTag("${RichContentTestTags.QUOTE_LINE_NUMBER}_$line")
                .assertTextEquals(expected)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun poemWithLineNumbersZeroHasNoNumberColumn() {
        val html = """
            <blockquote class="quote" data-kind="poem" data-line-numbers="0">
                <p>Dòng một<br>Dòng hai<br>Dòng ba<br>Dòng bốn<br>Dòng năm</p>
            </blockquote>
        """.trimIndent()
        val doc = RichHtmlParser.parse(html)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                    RichContentView(document = doc, assets = null)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("${RichContentTestTags.QUOTE_LINE_NUMBER}_1").assertDoesNotExist()
        composeRule.onNodeWithTag("${RichContentTestTags.QUOTE_LINE_NUMBER}_5").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun quoteFooterThenSourceAlignsFooterEndAndPlacesSourceLast() {
        val html = """<blockquote class="quote" data-kind="prose" data-line-numbers="0"><p>body</p><footer>author</footer><p class="figure-source">source</p></blockquote>"""
        val doc = RichHtmlParser.parse(html)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                    RichContentView(document = doc, assets = null)
                }
            }
        }
        composeRule.waitForIdle()

        val bodyBounds = composeRule.onNodeWithContentDescription("body").assertIsDisplayed().getUnclippedBoundsInRoot()
        val footerBounds = composeRule.onNodeWithContentDescription("author").assertIsDisplayed().getUnclippedBoundsInRoot()
        val sourceBounds = composeRule.onNodeWithContentDescription("source").assertIsDisplayed().getUnclippedBoundsInRoot()

        assertTrue("Footer must be below body", footerBounds.top >= bodyBounds.bottom - 2.dp)
        assertTrue("Source must be below footer", sourceBounds.top >= footerBounds.bottom - 2.dp)

        val tvs = collectTextViews(composeRule.activity.window.decorView)
        val footerTv = tvs.first { it.text.toString() == "author" }
        val sourceTv = tvs.first { it.text.toString() == "source" }
        assertEquals(Gravity.END, footerTv.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
        assertTrue("Source text size must be smaller than body text", sourceTv.textSize < footerTv.textSize)
    }
}
