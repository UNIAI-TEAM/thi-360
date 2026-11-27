package vn.unicom.thi360.feature.richcontent

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.feature.richcontent.model.AttemptAssetRef
import vn.unicom.thi360.feature.richcontent.model.RichBlock
import vn.unicom.thi360.feature.richcontent.model.RichDocument
import vn.unicom.thi360.feature.richcontent.model.RichInline
import vn.unicom.thi360.feature.richcontent.model.RichParseOptions
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser

class RichHtmlParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ExpectedViolation(
        val tag: String,
        val attribute: String? = null,
    )

    @Serializable
    private data class SampleFixture(
        val name: String,
        val valid: Boolean,
        val allowImages: Boolean = true,
        val html: String,
        val violations: List<ExpectedViolation> = emptyList(),
    )

    @Serializable
    private data class SamplesRoot(
        val assetId: String,
        val samples: List<SampleFixture>,
    )

    private fun loadSamples(): SamplesRoot {
        val repoRoot = ContractFixtureLoader.openApiSpecFile().canonicalFile.parentFile!!.parentFile!!.parentFile!!
        val file = File(repoRoot, "docs/specs/fixtures/rich-text-samples.json")
        require(file.isFile) { "Missing rich-text-samples.json at ${file.canonicalPath}" }
        return json.decodeFromString(file.readText(Charsets.UTF_8))
    }

    private fun flattenDocumentText(doc: RichDocument): String = buildString {
        fun walkInlines(inlines: List<RichInline>) {
            for (i in inlines) {
                when (i) {
                    is RichInline.Text -> append(i.text)
                    RichInline.LineBreak -> append('\n')
                    is RichInline.Formula -> append(i.delimitedSource())
                    is RichInline.LabeledUnderline -> {
                        walkInlines(i.children)
                        append("[").append(i.label).append("]")
                    }
                    is RichInline.Blank -> append("(${i.number}) ____")
                    is RichInline.Image -> append(i.alt)
                    is RichInline.Slot -> append("[[${i.id}]]")
                }
            }
        }
        fun walkBlocks(blocks: List<RichBlock>) {
            for (b in blocks) {
                when (b) {
                    is RichBlock.Paragraph -> walkInlines(b.inlines)
                    is RichBlock.FormulaBlock -> append(b.formula.delimitedSource())
                    is RichBlock.BulletList -> b.items.forEach(::walkBlocks)
                    is RichBlock.OrderedList -> b.items.forEach(::walkBlocks)
                    is RichBlock.Table -> b.table.rows.forEach { r -> r.cells.forEach { walkBlocks(it.blocks) } }
                    is RichBlock.DataTableFigure -> {
                        b.caption?.let(::walkInlines)
                        b.table.rows.forEach { r -> r.cells.forEach { walkBlocks(it.blocks) } }
                        b.notes.forEach(::walkInlines)
                        b.sources.forEach(::walkInlines)
                    }
                    is RichBlock.CrossFigure -> b.table.rows.forEach { r -> r.cells.forEach { walkBlocks(it.blocks) } }
                    is RichBlock.ImageFigure -> {
                        append(b.image.alt)
                        b.caption?.let(::walkInlines)
                        b.sources.forEach(::walkInlines)
                    }
                    is RichBlock.Quote -> {
                        b.paragraphs.forEach(::walkInlines)
                        b.footer?.let(::walkInlines)
                        b.source?.let(::walkInlines)
                    }
                }
            }
        }
        walkBlocks(doc.blocks)
    }

    @Test
    fun validSamplesKeepEverything() {
        val root = loadSamples()
        val validSamples = root.samples.filter { it.valid }
        assertEquals(12, validSamples.size)

        for (sample in validSamples) {
            val result = RichHtmlParser.parseWithViolations(
                sample.html,
                RichParseOptions(allowImages = sample.allowImages),
            )
            assertTrue(
                "Valid sample '${sample.name}' produced unexpected violations: ${result.violations}",
                result.violations.isEmpty(),
            )
            assertTrue(
                "Valid sample '${sample.name}' must produce non-empty blocks",
                result.document.blocks.isNotEmpty(),
            )
        }

        val byName = validSamples.associateBy { it.name }

        val mathDoc = RichHtmlParser.parse(byName.getValue("math-inline-and-block").html)
        assertTrue(mathDoc.blocks.any { it is RichBlock.Paragraph })
        assertTrue(mathDoc.blocks.any { it is RichBlock.FormulaBlock })

        val tableDoc = RichHtmlParser.parse(byName.getValue("data-table").html)
        val dt = tableDoc.blocks.single() as RichBlock.DataTableFigure
        assertEquals(3, dt.table.rows.size)
        assertEquals(1, dt.notes.size)
        assertEquals(1, dt.sources.size)

        val imgDoc = RichHtmlParser.parse(byName.getValue("image-figure").html)
        val imgFig = imgDoc.blocks.single() as RichBlock.ImageFigure
        assertEquals(AttemptAssetRef.Attempt(root.assetId), imgFig.image.ref)
        assertTrue(imgFig.zoomable)

        val quoteDoc = RichHtmlParser.parse(byName.getValue("quote-poem").html)
        val quote = quoteDoc.blocks.single() as RichBlock.Quote
        assertEquals(5, quote.lineNumberStep)
        assertEquals(2, quote.paragraphs.size)
        assertTrue(quote.footer != null)
    }

    @Test
    fun invalidSamplesDropListedViolations() {
        val root = loadSamples()
        val invalidSamples = root.samples.filter { !it.valid }
        assertEquals(31, invalidSamples.size)

        for (sample in invalidSamples) {
            val result = RichHtmlParser.parseWithViolations(
                sample.html,
                RichParseOptions(allowImages = sample.allowImages),
            )
            val actualSet = result.violations.map { ExpectedViolation(it.tag, it.attribute) }.toSet()
            val expectedSet = sample.violations.toSet()
            assertEquals(
                "Violation set mismatch for invalid sample '${sample.name}'",
                expectedSet,
                actualSet,
            )

            if (sample.violations.any { it.tag == "figure" }) {
                assertTrue(
                    "Invalid figure sample '${sample.name}' must not retain any figure block",
                    result.document.blocks.none {
                        it is RichBlock.DataTableFigure || it is RichBlock.ImageFigure || it is RichBlock.CrossFigure
                    },
                )
            }
            if (sample.violations.any { it.tag == "blockquote" }) {
                assertTrue(
                    "Invalid blockquote sample '${sample.name}' must not retain any quote block",
                    result.document.blocks.none { it is RichBlock.Quote },
                )
            }
        }

        val badBlankWithText = invalidSamples.first { it.name == "bad-blank-with-text" }
        val blankDoc = RichHtmlParser.parse(badBlankWithText.html)
        val blankText = flattenDocumentText(blankDoc)
        assertFalse("bad-blank-with-text must not contain 'text'", blankText.contains("text"))
        assertTrue("bad-blank-with-text must produce no blank chip", blankDoc.blocks.isEmpty())

        val mixed = RichHtmlParser.parse("""<p>before</p><figure class="gallery"><figcaption>x</figcaption></figure><p>after</p>""")
        assertEquals(2, mixed.blocks.size)
        assertEquals("beforeafter", flattenDocumentText(mixed))
    }

    @Test
    fun dropsScriptStyleAndEvents() {
        val html = """<p>a <font>b</font> c</p><script>x()</script><style>.x{color:red}</style><p style="color:red" onclick="x()">d</p>"""
        val result = RichHtmlParser.parseWithViolations(html)
        val text = flattenDocumentText(result.document)
        assertEquals("a b cd", text)
        assertFalse(text.contains("x()"))
        assertFalse(text.contains("color:red"))
        assertEquals(2, result.document.blocks.size)
        assertTrue(result.violations.any { it.tag == "font" })
        assertTrue(result.violations.any { it.tag == "script" })
        assertTrue(result.violations.any { it.tag == "style" })
        assertTrue(result.violations.any { it.tag == "p" && it.attribute == "style" })
        assertTrue(result.violations.any { it.tag == "p" && it.attribute == "onclick" })
    }

    @Test
    fun deepNesting200LevelsAndBoundaryBlanksAndSlots() {
        val deepDivs = "<div>".repeat(200) + "deep-text" + "</div>".repeat(200)
        val divDoc = RichHtmlParser.parse(deepDivs)
        assertTrue(flattenDocumentText(divDoc).contains("deep-text"))

        val deepLists = "<ul><li>".repeat(200) + "leaf" + "</li></ul>".repeat(200)
        val listDoc = RichHtmlParser.parse(deepLists)
        assertTrue(flattenDocumentText(listDoc).contains("leaf"))

        val blanksHtml = """<p><span class="blank" data-blank="1"></span> <span class="blank" data-blank="20"></span> <span class="blank" data-blank="21"></span> <span class="blank" data-blank="0"></span></p>"""
        val blanksDoc = RichHtmlParser.parse(blanksHtml, RichParseOptions(blankNumberStart = 10))
        val blanks = (blanksDoc.blocks.single() as RichBlock.Paragraph).inlines.filterIsInstance<RichInline.Blank>()
        assertEquals(listOf(10, 29), blanks.map { it.number })

        val slotHtml = "<p>Water boils at [[b1]] C and freezes at [[b2]] C.</p>"
        val withSlots = RichHtmlParser.parse(slotHtml, RichParseOptions(slotMarkers = true))
        val slots = (withSlots.blocks.single() as RichBlock.Paragraph).inlines.filterIsInstance<RichInline.Slot>()
        assertEquals(listOf("b1", "b2"), slots.map { it.id })

        val withoutSlots = RichHtmlParser.parse(slotHtml, RichParseOptions(slotMarkers = false))
        val noSlots = (withoutSlots.blocks.single() as RichBlock.Paragraph).inlines.filterIsInstance<RichInline.Slot>()
        assertTrue(noSlots.isEmpty())
    }
}

