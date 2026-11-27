package vn.unicom.thi360.feature.richcontent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import vn.unicom.thi360.feature.richcontent.formula.FormulaRenderResult
import vn.unicom.thi360.feature.richcontent.formula.RaTeXFormulaEngine
import vn.unicom.thi360.feature.richcontent.model.FormulaDisplay
import vn.unicom.thi360.feature.richcontent.model.RichInline
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser

@RunWith(Parameterized::class)
class FormulaSheetInstrumentedTest(
    private val sheetGroup: String,
    private val entries: List<AndroidFormulaEntry>,
) {

    @Test
    fun renderSideBySideComparisonSheet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = RaTeXFormulaEngine(context = context, maxCacheEntries = 64)
        val sorted = entries.sortedBy { it.sheetItemNumber().orEmpty() }
        assertTrue("Sheet group $sheetGroup must have entries", sorted.isNotEmpty())
        assertTrue("Sheet group $sheetGroup must contain only katexRenders=true entries", sorted.all { it.katexRenders })

        val colWidth = 460
        val colGap = 16
        val outerPad = 16
        val headerHeight = 32
        val rowPad = 12
        val sheetWidth = outerPad * 2 + colWidth * 2 + colGap

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 18f
            typeface = Typeface.MONOSPACE
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4B5563")
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E5E7EB")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#F3F4F6")
            style = Paint.Style.FILL
        }
        val warningBgPaint = Paint().apply {
            color = Color.parseColor("#FEF3C7")
            style = Paint.Style.FILL
        }
        val warningFgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B45309")
            textSize = 16f
            typeface = Typeface.MONOSPACE
        }

        data class PreparedRow(
            val itemNum: String,
            val latex: String,
            val webBmp: Bitmap,
            val androidBmp: Bitmap,
            val contentHeight: Int,
        )

        val prepared = sorted.map { entry ->
            val key = requireNotNull(entry.sheetKey)
            val num = requireNotNull(entry.sheetItemNumber())
            val webBmp = RichContentAndroidTestSupport.loadWebRenderBitmap(key)

            val formula = RichInline.Formula(
                source = entry.latex,
                display = FormulaDisplay.Block,
                notation = RichHtmlParser.detectNotation(entry.latex),
            )
            val rendered = engine.render(formula, textSizePx = 38f, color = Color.parseColor("#111827"))
            val androidBmp = when (rendered) {
                is FormulaRenderResult.Rendered -> {
                    val w = rendered.width.coerceAtLeast(1)
                    val h = (rendered.ascent + rendered.descent).coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w + 16, h + 16, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    c.drawColor(Color.WHITE)
                    c.translate(8f, 8f)
                    rendered.drawable.draw(c)
                    bmp
                }

                is FormulaRenderResult.Unsupported -> {
                    val bmp = Bitmap.createBitmap(colWidth - 8, 48, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    c.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), warningBgPaint)
                    c.drawText(rendered.source.take(44), 8f, 30f, warningFgPaint)
                    bmp
                }
            }

            val webScaledH = scaledHeight(webBmp, colWidth - 16)
            val androidScaledH = scaledHeight(androidBmp, colWidth - 16)
            val contentH = max(48, max(webScaledH, androidScaledH)) + 24
            PreparedRow(num, entry.latex, webBmp, androidBmp, contentH)
        }

        val titleBarH = 44
        val totalHeight = outerPad * 2 + titleBarH + prepared.sumOf { headerHeight + it.contentHeight + rowPad }
        val sheet = Bitmap.createBitmap(sheetWidth, totalHeight.coerceAtLeast(100), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(
            "$sheetGroup (${prepared.size} formulas) — Left: Web KaTeX | Right: Android RaTeX",
            outerPad.toFloat(),
            (outerPad + 28).toFloat(),
            titlePaint,
        )

        var y = outerPad + titleBarH
        for (row in prepared) {
            val rowTop = y.toFloat()
            canvas.drawRect(
                outerPad.toFloat(),
                rowTop,
                (sheetWidth - outerPad).toFloat(),
                rowTop + headerHeight,
                headerBgPaint,
            )
            val headerText = "${row.itemNum}  ${row.latex}".let { if (it.length > 88) it.take(85) + "..." else it }
            canvas.drawText(headerText, (outerPad + 8).toFloat(), rowTop + 22f, headerPaint)

            val bodyTop = y + headerHeight
            val leftX = outerPad
            val rightX = outerPad + colWidth + colGap

            canvas.drawRect(
                leftX.toFloat(),
                bodyTop.toFloat(),
                (leftX + colWidth).toFloat(),
                (bodyTop + row.contentHeight).toFloat(),
                borderPaint,
            )
            canvas.drawRect(
                rightX.toFloat(),
                bodyTop.toFloat(),
                (rightX + colWidth).toFloat(),
                (bodyTop + row.contentHeight).toFloat(),
                borderPaint,
            )
            canvas.drawText("Web", (leftX + 6).toFloat(), (bodyTop + 16).toFloat(), labelPaint)
            canvas.drawText("Android", (rightX + 6).toFloat(), (bodyTop + 16).toFloat(), labelPaint)

            drawFitBitmap(canvas, row.webBmp, leftX + 8, bodyTop + 22, colWidth - 16, row.contentHeight - 26)
            drawFitBitmap(canvas, row.androidBmp, rightX + 8, bodyTop + 22, colWidth - 16, row.contentHeight - 26)

            row.webBmp.recycle()
            row.androidBmp.recycle()
            y += headerHeight + row.contentHeight + rowPad
        }

        val outFile = File(RichContentAndroidTestSupport.outputDir("formula-sheets"), "$sheetGroup.png")
        RichContentAndroidTestSupport.savePng(sheet, outFile)
        RichContentAndroidTestSupport.mirrorToHostTmp(outFile, "formula-sheets/$sheetGroup.png")
        sheet.recycle()

        val metaFile = File(RichContentAndroidTestSupport.outputDir("formula-sheets-meta"), "$sheetGroup.txt")
        metaFile.writeText("$sheetGroup=${prepared.size}\n", Charsets.UTF_8)
        RichContentAndroidTestSupport.mirrorToHostTmp(metaFile, "formula-sheets-meta/$sheetGroup.txt")

        assertTrue("Sheet PNG must exist and be non-empty", outFile.isFile && outFile.length() > 100L)
        assertEquals(entries.size, prepared.size)
    }

    private fun scaledHeight(bmp: Bitmap, maxWidth: Int): Int {
        if (bmp.width <= maxWidth) return bmp.height
        val ratio = maxWidth.toFloat() / bmp.width.toFloat()
        return (bmp.height * ratio).toInt().coerceAtLeast(1)
    }

    private fun drawFitBitmap(canvas: Canvas, bmp: Bitmap, left: Int, top: Int, maxW: Int, maxH: Int) {
        val scale = minOf(1f, maxW.toFloat() / bmp.width.coerceAtLeast(1), maxH.toFloat() / bmp.height.coerceAtLeast(1))
        val dstW = (bmp.width * scale).toInt().coerceAtLeast(1)
        val dstH = (bmp.height * scale).toInt().coerceAtLeast(1)
        val dst = Rect(left, top, left + dstW, top + dstH)
        canvas.drawBitmap(bmp, null, dst, null)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {
            val corpus = RichContentAndroidTestSupport.loadFormulaCorpus()
            val grouped = linkedMapOf<String, MutableList<AndroidFormulaEntry>>()
            corpus.entries.forEach { entry ->
                val group = entry.sheetGroup() ?: return@forEach
                grouped.getOrPut(group) { mutableListOf() }.add(entry)
            }
            return grouped.entries.map { (name, items) -> arrayOf(name, items) }
        }
    }
}
