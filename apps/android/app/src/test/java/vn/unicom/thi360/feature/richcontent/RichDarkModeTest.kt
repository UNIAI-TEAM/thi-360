package vn.unicom.thi360.feature.richcontent

import android.text.Spanned
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlin.math.max
import kotlin.math.min
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
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.core.theme.StatusPalette
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxDrawable
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.BlankChipSpan
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.RaTeXSpan
import vn.unicom.thi360.feature.richcontent.ui.RichContentPalette
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RichDarkModeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = fg.luminance().toDouble()
        val l2 = bg.luminance().toDouble()
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun tokensAndContrast() {
        val html = """
            <p>Tính <span class="math-inline">\(x^2 + 1\)</span></p>
            <span class="math-block">\[a^2 + b^2 = c^2\]</span>
            <figure class="data-table"><table><thead><tr><th scope="col">Năm</th></tr></thead><tbody><tr><td class="num">2026</td></tr></tbody></table></figure>
            <p>Chỗ trống <span class="blank" data-blank="1"></span></p>
        """.trimIndent()
        val doc = RichHtmlParser.parse(html)
        lateinit var darkPalette: RichContentPalette

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.DARK)) {
                darkPalette = RichContentPalette.current()
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                    RichContentView(document = doc, assets = null)
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(BrandPalette.DefaultDarkColors.text, darkPalette.text)
        assertEquals(BrandPalette.DefaultDarkColors.borderStrong, darkPalette.borderStrong)
        assertEquals(RichContentPalette.DarkTableHead, darkPalette.tableHead)
        assertEquals(StatusPalette.Dark.info, darkPalette.blankFg)
        assertEquals(StatusPalette.Dark.infoBg, darkPalette.blankBg)
        assertEquals(StatusPalette.Dark.warning, darkPalette.warningFg)
        assertEquals(StatusPalette.Dark.warningBg, darkPalette.warningBg)

        assertTrue(contrastRatio(darkPalette.text, darkPalette.surface) >= 4.5)
        assertTrue(contrastRatio(darkPalette.text, darkPalette.tableHead) >= 4.5)
        assertTrue(contrastRatio(darkPalette.text, darkPalette.quoteBackground) >= 4.5)
        assertTrue(contrastRatio(darkPalette.blankFg, darkPalette.blankBg) >= 4.5)
        assertTrue(contrastRatio(darkPalette.warningFg, darkPalette.warningBg) >= 4.5)

        val tvs = collectTextViews(composeRule.activity.window.decorView)
        val mathTv = tvs.first { it.text.toString().contains("Tính") }
        assertEquals(BrandPalette.DefaultDarkColors.text.toArgb(), mathTv.currentTextColor)

        val spanned = mathTv.text as Spanned
        val formulaSpan = spanned.getSpans(0, spanned.length, RaTeXSpan::class.java).single()
        val drawable = formulaSpan.rendered.drawable as SourceBoxDrawable
        assertEquals("Formula color must match dark text token", BrandPalette.DefaultDarkColors.text.toArgb(), drawable.color)

        val blankTv = tvs.first { it.text.toString().contains("(1) ____") }
        val blankSpan = (blankTv.text as Spanned).getSpans(0, blankTv.text.length, BlankChipSpan::class.java).single()
        assertEquals(StatusPalette.Dark.info.toArgb(), blankSpan.fgColor)
        assertEquals(StatusPalette.Dark.infoBg.toArgb(), blankSpan.bgColor)
    }
}

