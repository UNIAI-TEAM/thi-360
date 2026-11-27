package vn.unicom.thi360.feature.richcontent

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import vn.unicom.thi360.feature.richcontent.ui.RichContentTestTags
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

@RunWith(AndroidJUnit4::class)
class FormulaOverflowInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longInlineFormulaMovesToOwnLineAndScrollsAt360dp() {
        val terms = (1..40).joinToString(" + ") { "a_{$it}" }
        val html = """<p>Trước \($terms = S\) sau</p>"""
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

        val rootBounds = composeRule.onNodeWithTag(RichContentTestTags.ROOT).getUnclippedBoundsInRoot()
        val beforeBounds = composeRule.onNodeWithContentDescription("Trước").assertIsDisplayed().getUnclippedBoundsInRoot()
        val scrollNode = composeRule.onNodeWithTag(RichContentTestTags.FORMULA_SCROLL).assertIsDisplayed()
        val scrollBounds = scrollNode.getUnclippedBoundsInRoot()
        val afterBounds = composeRule.onNodeWithContentDescription("sau").assertIsDisplayed().getUnclippedBoundsInRoot()

        assertTrue("Page root must stay within 360dp", (rootBounds.right - rootBounds.left) <= 360.dp + 2.dp)
        assertTrue("Formula scroll frame must stay within 360dp", (scrollBounds.right - scrollBounds.left) <= 360.dp + 2.dp)
        assertTrue("Overflow inline formula must move onto its own line below 'Trước'", scrollBounds.top >= beforeBounds.bottom - 2.dp)
        assertTrue("Text after overflow formula must appear below it", afterBounds.top >= scrollBounds.bottom - 2.dp)

        composeRule.runOnIdle {
            val viewportPx = (360f * composeRule.activity.resources.displayMetrics.density).toInt()
            val wideViews = RichContentAndroidTestSupport.collectViews(composeRule.activity.window.decorView) { v ->
                v.javaClass.simpleName == "FormulaDrawableView"
            }
            assertTrue("Expected rendered FormulaDrawableView", wideViews.isNotEmpty())
            assertTrue(
                "Long inline formula (${wideViews.first().width}px) must exceed viewport (${viewportPx}px) without wrapping",
                wideViews.first().width > viewportPx,
            )
        }
    }

    @Test
    fun matrixFormulaSwipeDoesNotTurnHorizontalPagerPage() {
        val cols = (1..12).joinToString(" & ") { "x_{1,$it} + ${100 + it}" }
        val html = """<p>Ma trận 1x12:</p><p>\[\begin{pmatrix} $cols \end{pmatrix}\]</p>"""
        val doc = RichHtmlParser.parse(html)
        val engine = RaTeXFormulaEngine(composeRule.activity)
        var observedPage = -1

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides engine) {
                    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
                    observedPage = pagerState.currentPage
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.width(360.dp),
                    ) { page ->
                        if (page == 0) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                RichContentView(document = doc, assets = null, formulaEngine = engine)
                            }
                        } else {
                            Text("Trang hai")
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val scrollNode = composeRule.onNodeWithTag(RichContentTestTags.FORMULA_SCROLL).assertIsDisplayed()
        val beforeLeft = composeRule.runOnIdle {
            val v = RichContentAndroidTestSupport.collectViews(composeRule.activity.window.decorView) {
                it.javaClass.simpleName == "FormulaDrawableView"
            }.first()
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            loc[0]
        }

        scrollNode.performTouchInput {
            swipeLeft(startX = right * 0.85f, endX = right * 0.15f, durationMillis = 300)
        }
        composeRule.waitForIdle()

        assertEquals("Swiping horizontally on wide formula must NOT flip HorizontalPager", 0, observedPage)
        val afterLeft = composeRule.runOnIdle {
            val v = RichContentAndroidTestSupport.collectViews(composeRule.activity.window.decorView) {
                it.javaClass.simpleName == "FormulaDrawableView"
            }.first()
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            loc[0]
        }
        assertTrue(
            "Formula must scroll left inside its own frame (beforeX=$beforeLeft, afterX=$afterLeft)",
            afterLeft < beforeLeft,
        )
    }
}

