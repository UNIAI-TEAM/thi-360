package vn.unicom.thi360.feature.richcontent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.feature.richcontent.formula.KatexGrammarGuard
import vn.unicom.thi360.feature.richcontent.formula.VietnameseGlyphShield
import vn.unicom.thi360.feature.richcontent.model.FormulaDisplay
import vn.unicom.thi360.feature.richcontent.model.FormulaNotation
import vn.unicom.thi360.feature.richcontent.model.RichBlock
import vn.unicom.thi360.feature.richcontent.model.RichInline
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser

class FormulaSplitTest {

    @Test
    fun parsesSpanMathWithAndWithoutDelimitersAndDetectsMhchemNotation() {
        val html = """
            <p><span class="math-inline">\(x^2 + 1\)</span> and <span class="math-inline">y = 2x</span></p>
            <span class="math-block">\[a^2 + b^2 = c^2\]</span>
            <span class="math-block">E = mc^2</span>
            <p><span class="math-inline">\(\ce{CaCO3(s) &lt;=&gt; CaO(s) + CO2(g)}\)</span> <span class="math-inline">\(\pu{179,2 kJ}\)</span></p>
        """.trimIndent()

        val doc = RichHtmlParser.parse(html)
        assertEquals(4, doc.blocks.size)

        val p1Formulas = (doc.blocks[0] as RichBlock.Paragraph).inlines.filterIsInstance<RichInline.Formula>()
        assertEquals(2, p1Formulas.size)
        assertEquals("x^2 + 1", p1Formulas[0].source)
        assertEquals(FormulaDisplay.Inline, p1Formulas[0].display)
        assertEquals(FormulaNotation.Tex, p1Formulas[0].notation)
        assertEquals("y = 2x", p1Formulas[1].source)

        val b1 = (doc.blocks[1] as RichBlock.FormulaBlock).formula
        assertEquals("a^2 + b^2 = c^2", b1.source)
        assertEquals(FormulaDisplay.Block, b1.display)

        val b2 = (doc.blocks[2] as RichBlock.FormulaBlock).formula
        assertEquals("E = mc^2", b2.source)
        assertEquals(FormulaDisplay.Block, b2.display)

        val chemFormulas = (doc.blocks[3] as RichBlock.Paragraph).inlines.filterIsInstance<RichInline.Formula>()
        assertEquals(2, chemFormulas.size)
        assertEquals(FormulaNotation.Ce, chemFormulas[0].notation)
        assertTrue(chemFormulas[0].source.contains("<=>"))
        assertEquals(FormulaNotation.Pu, chemFormulas[1].notation)
    }

    @Test
    fun splitsRawInlineAndBlockDelimitersInsideParagraph() {
        val html = """<p>Cho \(x^2 + 1\) và \[\int_0^1 f(x)\,dx\]</p>"""
        val doc = RichHtmlParser.parse(html)

        assertEquals(2, doc.blocks.size)
        val p = doc.blocks[0] as RichBlock.Paragraph
        val inlineFormula = p.inlines.filterIsInstance<RichInline.Formula>().single()
        assertEquals("x^2 + 1", inlineFormula.source)
        assertEquals(FormulaDisplay.Inline, inlineFormula.display)

        val blockFormula = (doc.blocks[1] as RichBlock.FormulaBlock).formula
        assertEquals("\\int_0^1 f(x)\\,dx", blockFormula.source)
        assertEquals(FormulaDisplay.Block, blockFormula.display)
    }

    @Test
    fun leavesUnmatchedDelimitersAsLiteralTextWithoutFormula() {
        val html = """<p>Giá trị \(x + 1 là</p>"""
        val doc = RichHtmlParser.parse(html)

        val p = doc.blocks.single() as RichBlock.Paragraph
        assertTrue(p.inlines.filterIsInstance<RichInline.Formula>().isEmpty())
        val text = p.inlines.filterIsInstance<RichInline.Text>().joinToString("") { it.text }
        assertEquals("Giá trị \\(x + 1 là", text)
    }

    @Test
    fun katexGrammarGuardRejectsDisplayOnlyAndNonKatexSyntaxWhileAllowingValidMath() {
        assertNotNull(KatexGrammarGuard.rejectReason("""${'$'}x^2 + 1${'$'}""", FormulaDisplay.Inline))
        assertNotNull(KatexGrammarGuard.rejectReason("""\ce{CH3COOH} \hfill (1)""", FormulaDisplay.Block))
        assertNotNull(KatexGrammarGuard.rejectReason("""E = mc^2 \tag{1}""", FormulaDisplay.Inline))
        assertNull(KatexGrammarGuard.rejectReason("""E = mc^2 \tag{1}""", FormulaDisplay.Block))
        assertNotNull(KatexGrammarGuard.rejectReason("""\begin{align} x &= 1 \end{align}""", FormulaDisplay.Inline))
        assertNull(KatexGrammarGuard.rejectReason("""\begin{align} x &= 1 \end{align}""", FormulaDisplay.Block))
        assertNotNull(KatexGrammarGuard.rejectReason("""x\limits_a""", FormulaDisplay.Block))
        assertNull(KatexGrammarGuard.rejectReason("""\lim\limits_{n \to \infty} a_n""", FormulaDisplay.Inline))
        assertNull(KatexGrammarGuard.rejectReason("""\ce{H2SO4 + ${'$'}n${'$'} SO3}""", FormulaDisplay.Block))
        assertNull(KatexGrammarGuard.rejectReason("""\%,\ \#,\ \${'$'}""", FormulaDisplay.Inline))
    }

    @Test
    fun vietnameseGlyphShieldRoundTripsAllExtendedVietnameseVowels() {
        val sample = """\text{Vận tốc, Diện tích, rộng, thời gian, đường, ử ữ ự}"""
        val (shielded, changed) = VietnameseGlyphShield.shield(sample)
        assertTrue(changed)
        assertTrue(!shielded.contains('ậ') && !shielded.contains('ệ') && !shielded.contains('ộ') && !shielded.contains('ờ'))
        val items = shielded.mapIndexed { i, ch ->
            io.ratex.DisplayItem.GlyphPath(
                x = i.toDouble(),
                y = 0.0,
                scale = 1.0,
                font = "Main-Regular",
                charCode = ch.code,
                commands = emptyList(),
                color = io.ratex.RaTeXColor(0f, 0f, 0f, 1f),
            )
        }
        val restored = VietnameseGlyphShield.restore(io.ratex.DisplayList(10.0, 1.0, 0.0, items))
        val rebuilt = restored.items
            .filterIsInstance<io.ratex.DisplayItem.GlyphPath>()
            .map { it.charCode.toChar() }
            .joinToString("")
        assertEquals(sample, rebuilt)
    }
}
