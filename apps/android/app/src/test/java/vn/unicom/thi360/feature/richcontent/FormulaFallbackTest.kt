package vn.unicom.thi360.feature.richcontent

import android.graphics.Color
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.formula.FormulaRenderResult
import vn.unicom.thi360.feature.richcontent.formula.GuardedFormulaEngine
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.model.FormulaDisplay
import vn.unicom.thi360.feature.richcontent.model.FormulaNotation
import vn.unicom.thi360.feature.richcontent.model.RichInline

@RunWith(RobolectricTestRunner::class)
class FormulaFallbackTest {

    @Test
    fun singleThrowingFormulaAmongTwentyFallsBackWhileNineteenRender() {
        val delegate = SourceBoxFormulaEngine()
        val throwingEngine = GuardedFormulaEngine(
            delegate = object : FormulaEngine {
                override fun render(
                    formula: RichInline.Formula,
                    textSizePx: Float,
                    color: Int,
                ): FormulaRenderResult {
                    if (formula.source == "f_7") {
                        throw IllegalStateException("Simulated native crash in formula 7")
                    }
                    return delegate.render(formula, textSizePx, color)
                }
            },
            timeoutMs = 500L,
        )

        val formulas = (1..20).map { i ->
            RichInline.Formula("f_$i", FormulaDisplay.Inline, FormulaNotation.Tex)
        }
        val results = formulas.map { throwingEngine.render(it, 32f, Color.BLACK) }

        val unsupported = results.filterIsInstance<FormulaRenderResult.Unsupported>()
        val rendered = results.filterIsInstance<FormulaRenderResult.Rendered>()
        assertEquals(1, unsupported.size)
        assertEquals("\\(f_7\\)", unsupported.single().source)
        assertEquals(19, rendered.size)
    }

    @Test
    fun singleHangingFormulaAmongTwentyTimesOutWhileNineteenRender() {
        val delegate = SourceBoxFormulaEngine()
        val unblockLatch = CountDownLatch(1)
        val hangingEngine = GuardedFormulaEngine(
            delegate = object : FormulaEngine {
                override fun render(
                    formula: RichInline.Formula,
                    textSizePx: Float,
                    color: Int,
                ): FormulaRenderResult {
                    if (formula.source == "hang_13") {
                        unblockLatch.await(10, TimeUnit.SECONDS)
                    }
                    return delegate.render(formula, textSizePx, color)
                }
            },
            timeoutMs = 150L,
        )

        try {
            val formulas = (1..20).map { i ->
                val src = if (i == 13) "hang_13" else "ok_$i"
                RichInline.Formula(src, FormulaDisplay.Inline, FormulaNotation.Tex)
            }
            val results = formulas.map { hangingEngine.render(it, 32f, Color.BLACK) }

            val unsupported = results.filterIsInstance<FormulaRenderResult.Unsupported>()
            val rendered = results.filterIsInstance<FormulaRenderResult.Rendered>()
            assertEquals(1, unsupported.size)
            assertEquals("\\(hang_13\\)", unsupported.single().source)
            assertEquals(19, rendered.size)
        } finally {
            unblockLatch.countDown()
        }
    }

    @Test
    fun fiveThousandCharFormulaDoesNotCrashEngine() {
        val hugeLatex = (1..500).joinToString(" + ") { "x_{$it}" }.padEnd(5000, ' ')
        assertTrue(hugeLatex.length >= 5000)

        val engine = GuardedFormulaEngine(
            delegate = SourceBoxFormulaEngine(unsupportedPredicate = { it.source.length > 4000 }),
            timeoutMs = 500L,
        )
        val result = engine.render(
            RichInline.Formula(hugeLatex, FormulaDisplay.Block, FormulaNotation.Tex),
            32f,
            Color.BLACK,
        )
        assertTrue(result is FormulaRenderResult.Unsupported)
        assertEquals("\\[$hugeLatex\\]", (result as FormulaRenderResult.Unsupported).source)
    }
}
