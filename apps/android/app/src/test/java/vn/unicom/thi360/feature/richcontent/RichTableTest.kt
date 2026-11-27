package vn.unicom.thi360.feature.richcontent

import android.graphics.Typeface
import android.text.Spanned
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichBlock
import vn.unicom.thi360.feature.richcontent.model.RichInline
import vn.unicom.thi360.feature.richcontent.model.RichTable
import vn.unicom.thi360.feature.richcontent.model.RichTableCell
import vn.unicom.thi360.feature.richcontent.model.RichTableRow
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.RaTeXSpan
import vn.unicom.thi360.feature.richcontent.ui.RichContentTestTags
import vn.unicom.thi360.feature.richcontent.ui.RichContentView
import vn.unicom.thi360.feature.richcontent.ui.TableGridLayoutPlanner

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RichTableTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Sample(val name: String, val valid: Boolean, val html: String)

    @Serializable
    private data class SamplesRoot(val samples: List<Sample>)

    private fun sampleHtml(name: String): String {
        val repoRoot = ContractFixtureLoader.openApiSpecFile().canonicalFile.parentFile!!.parentFile!!.parentFile!!
        val root = json.decodeFromString<SamplesRoot>(
            File(repoRoot, "docs/specs/fixtures/rich-text-samples.json").readText(Charsets.UTF_8),
        )
        return root.samples.first { it.name == name && it.valid }.html
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun dataTableRendersCaptionHeadersNumericCellsNoteAndSource() {
        val doc = RichHtmlParser.parse(sampleHtml("data-table"))

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                    RichContentView(document = doc, assets = null)
                }
            }
        }
        composeRule.waitForIdle()

        val captionNode = composeRule.onNodeWithContentDescription("TRỊ GIÁ XUẤT, NHẬP KHẨU CỦA HOA KÌ").assertIsDisplayed()
        val tableNode = composeRule.onNodeWithTag(RichContentTestTags.TABLE_SCROLL).assertIsDisplayed()
        val noteNode = composeRule.onNodeWithContentDescription("Đơn vị: Tỉ USD").assertIsDisplayed()
        val sourceNode = composeRule.onNodeWithContentDescription("Nguồn: World Bank, 2022").assertIsDisplayed()

        val captionBounds = captionNode.getUnclippedBoundsInRoot()
        val tableBounds = tableNode.getUnclippedBoundsInRoot()
        val noteBounds = noteNode.getUnclippedBoundsInRoot()
        val sourceBounds = sourceNode.getUnclippedBoundsInRoot()
        assertTrue("Caption must be above table", captionBounds.bottom <= tableBounds.top + 4.dp)
        assertTrue("Note must be below table", noteBounds.top >= tableBounds.bottom - 4.dp)
        assertTrue("Source must be below note", sourceBounds.top >= noteBounds.bottom - 4.dp)

        val tvs = collectTextViews(composeRule.activity.window.decorView)
        val headers = listOf("Năm", "2000", "2020").map { label -> tvs.first { it.text.toString() == label } }
        headers.forEach { tv ->
            assertEquals("Header '${tv.text}' must be bold", Typeface.BOLD, tv.typeface.style)
        }

        val numValues = listOf("1096,1", "2148,6", "1477,2", "2776,1").map { v -> tvs.first { it.text.toString() == v } }
        numValues.forEach { tv ->
            assertEquals("Numeric cell '${tv.text}' must align END", Gravity.END, tv.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
            assertEquals("tnum", tv.fontFeatureSettings)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun mergedHeaderCellSpansTwoColumnsAndRendersMathInCell() {
        val doc = RichHtmlParser.parse(sampleHtml("data-table-merged-cell-with-math"))

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                    RichContentView(document = doc, assets = null)
                }
            }
        }
        composeRule.waitForIdle()

        val tvs = collectTextViews(composeRule.activity.window.decorView)
        val headerTv = tvs.first { it.text.toString() == "Giá trị" }
        val mathTv = tvs.first { it.contentDescription?.toString()?.contains("x_1") == true }
        val chemTv = tvs.first { it.text.toString() == "H2O" }

        assertTrue(
            "Merged header 'Giá trị' (width=${headerTv.width}) must span wider than each single column below (${mathTv.width}, ${chemTv.width})",
            headerTv.width > mathTv.width && headerTv.width > chemTv.width,
        )
        val mathSpanned = mathTv.text as Spanned
        assertTrue(
            "Inline formula in merged table cell must render RaTeXSpan",
            mathSpanned.getSpans(0, mathSpanned.length, RaTeXSpan::class.java).isNotEmpty(),
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun wideEightColumnTableScrollsInOwnContainerWhileAboveAndBelowStayStill() {
        val headerCols = (1..8).joinToString("") { """<th scope="col">Cột $it</th>""" }
        val dataCols = (1..8).joinToString("") { """<td class="num">${1_000_000 + it * 111_111}</td>""" }
        val html = """
            <p>Đề bài phía trên bảng rộng</p>
            <figure class="data-table"><table><thead><tr>$headerCols</tr></thead><tbody><tr>$dataCols</tr></tbody></table></figure>
            <p>Phương án phía dưới bảng rộng</p>
        """.trimIndent()
        val doc = RichHtmlParser.parse(html)

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

        val above = composeRule.onNodeWithContentDescription("Đề bài phía trên bảng rộng").getUnclippedBoundsInRoot()
        val below = composeRule.onNodeWithContentDescription("Phương án phía dưới bảng rộng").getUnclippedBoundsInRoot()
        val tableContainer = composeRule.onNodeWithTag(RichContentTestTags.TABLE_SCROLL).getUnclippedBoundsInRoot()

        assertTrue("Paragraph above must fit inside 360dp", (above.right - above.left) <= 360.dp + 2.dp)
        assertTrue("Paragraph below must fit inside 360dp", (below.right - below.left) <= 360.dp + 2.dp)
        assertTrue("Table container viewport must stay within 360dp", (tableContainer.right - tableContainer.left) <= 360.dp + 2.dp)

        val tvs = collectTextViews(composeRule.activity.window.decorView)
        val totalCellsWidth = (1..8).sumOf { i ->
            val tv = tvs.first { it.text.toString() == "${1_000_000 + i * 111_111}" }
            assertEquals("7-digit numeric cell must not wrap", 1, tv.lineCount)
            tv.width
        }
        val viewportPx = (360f * composeRule.activity.resources.displayMetrics.density).toInt()
        assertTrue(
            "8-column 7-digit table content ($totalCellsWidth px) must exceed 360dp ($viewportPx px) so it scrolls inside its own frame",
            totalCellsWidth > viewportPx,
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun extremeColspanAndRowspanAreBoundedAndRenderWithoutCrash() {
        val html = """
            <figure class="data-table">
                <table>
                    <tbody>
                        <tr><td colspan="999999" rowspan="999999">x</td></tr>
                        <tr><td colspan="4500000">y</td></tr>
                    </tbody>
                </table>
            </figure>
        """.trimIndent()
        val parsed = RichHtmlParser.parseWithViolations(html)
        assertTrue("Valid numeric span attributes should not raise sanitize violations", parsed.violations.isEmpty())

        val programmatic = RichTable(
            rows = listOf(
                RichTableRow(
                    cells = listOf(
                        RichTableCell(
                            isHeader = false,
                            scope = null,
                            isNumeric = false,
                            colspan = Int.MAX_VALUE,
                            rowspan = Int.MAX_VALUE,
                            blocks = listOf(RichBlock.Paragraph(listOf(RichInline.Text("z")))),
                        ),
                    ),
                ),
            ),
        )
        val plan = TableGridLayoutPlanner.plan(programmatic)
        assertEquals(TableGridLayoutPlanner.MAX_COLSPAN, plan.colCount)
        assertEquals(1, plan.rowCount)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
                    Box(modifier = Modifier.width(360.dp)) {
                        RichContentView(document = parsed.document, assets = null)
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RichContentTestTags.TABLE_SCROLL).assertIsDisplayed()
        val tvs = collectTextViews(composeRule.activity.window.decorView)
        assertTrue(tvs.any { it.text.toString() == "x" })
        assertTrue(tvs.any { it.text.toString() == "y" })
    }
}
