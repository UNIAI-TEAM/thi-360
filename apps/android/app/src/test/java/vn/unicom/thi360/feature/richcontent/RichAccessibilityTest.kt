package vn.unicom.thi360.feature.richcontent

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
class RichAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun formulaLabelIsSource() {
        val html = """
            <p>Biết <span class="math-inline">\(x^2 + 1\)</span> và <span class="math-inline">\(\broken{syntax}\)</span> thì</p>
            <span class="math-block">\[\frac{a}{b}\]</span>
            <span class="math-block">\[\broken{block}\]</span>
        """.trimIndent()
        val doc = RichHtmlParser.parse(html)
        val engine = SourceBoxFormulaEngine(unsupportedPredicate = { it.source.contains("\\broken") })

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides engine) {
                    RichContentView(document = doc, assets = null)
                }
            }
        }
        composeRule.waitForIdle()

        val inlineExpected = """Biết \(x^2 + 1\) và \(\broken{syntax}\) thì"""
        composeRule.onNodeWithContentDescription(inlineExpected).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("""\[\frac{a}{b}\]""").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("""\[\broken{block}\]""").assertIsDisplayed()
        composeRule.onNodeWithTag(RichContentTestTags.FORMULA_BLOCK).assertIsDisplayed()
        composeRule.onNodeWithTag(RichContentTestTags.FORMULA_FALLBACK).assertIsDisplayed()

        val tv = collectTextViews(composeRule.activity.window.decorView).first { it.text.toString().contains("Biết") }
        assertTrue(tv.contentDescription.toString().contains("""\(x^2 + 1\)"""))
        assertTrue(tv.contentDescription.toString().contains("""\(\broken{syntax}\)"""))
    }
}

