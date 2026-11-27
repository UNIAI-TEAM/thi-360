package vn.unicom.thi360.feature.richcontent

import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.serialization.encodeToString
import org.junit.AfterClass
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import vn.unicom.thi360.feature.richcontent.formula.DetailedFormulaOutcome
import vn.unicom.thi360.feature.richcontent.formula.FormulaRenderDefaults
import vn.unicom.thi360.feature.richcontent.formula.RaTeXFormulaEngine
import vn.unicom.thi360.feature.richcontent.model.FormulaDisplay
import vn.unicom.thi360.feature.richcontent.model.RichInline
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser

@RunWith(Parameterized::class)
class FormulaCorpusInstrumentedTest(
    private val caseName: String,
    private val indexedEntries: List<Pair<Int, AndroidFormulaEntry>>,
) {

    @Test
    fun verifyGroupFormulas() {
        val engine = sharedEngine ?: run {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            RaTeXFormulaEngine(
                context = context,
                timeoutMs = FormulaRenderDefaults.RENDER_TIMEOUT_MS,
                maxCacheEntries = 64,
            ).also { sharedEngine = it }
        }
        val exemptions = RichContentAndroidTestSupport.loadFormulaExemptions().rows
            .filter { it.app == "android" || it.app == "both" }
            .associateBy { it.latex to it.display }

        val rows = mutableListOf<AndroidFormulaCheckRow>()
        val proposed = mutableListOf<AndroidFormulaExemption>()
        val failures = mutableListOf<String>()

        for ((index, entry) in indexedEntries) {
            val displayEnum = if (entry.display == "block") FormulaDisplay.Block else FormulaDisplay.Inline
            val notation = RichHtmlParser.detectNotation(entry.latex)
            val formula = RichInline.Formula(
                source = entry.latex,
                display = displayEnum,
                notation = notation,
            )

            val detailed = engine.renderDetailed(
                formula = formula,
                textSizePx = 44f,
                color = Color.BLACK,
            )

            val (outcomeName, detailMsg) = when (detailed) {
                is DetailedFormulaOutcome.Rendered -> "Rendered" to ""
                is DetailedFormulaOutcome.Fallback -> "Fallback" to detailed.detail
                is DetailedFormulaOutcome.Timeout -> "Timeout" to detailed.detail
                is DetailedFormulaOutcome.Crash -> "Crash" to detailed.detail
            }

            rows += AndroidFormulaCheckRow(
                index = index,
                latex = entry.latex,
                sheetKey = entry.sheetKey,
                group = entry.group,
                kind = entry.kind,
                form = entry.display,
                outcome = outcomeName,
            )

            val idLabel = entry.sheetKey ?: entry.rejectedArea ?: "idx=$index"
            if (entry.katexRenders) {
                if (outcomeName != "Rendered") {
                    val exempt = exemptions[entry.latex to entry.display]
                    if (exempt == null) {
                        proposed += AndroidFormulaExemption(
                            latex = entry.latex,
                            display = entry.display,
                            app = "android",
                            reason = "RaTeX 0.1.16 $outcomeName: ${detailMsg.ifBlank { "unsupported syntax" }}",
                            beyondCurriculum = (entry.kind == "syntax"),
                            origin = entry.origin ?: "catalogue",
                            group = entry.group,
                            kind = entry.kind,
                            approvalRef = "",
                        )
                        failures += "[$idLabel | group=${entry.group} | kind=${entry.kind} | form=${entry.display}] expected Rendered, got $outcomeName ($detailMsg) :: ${entry.latex}"
                    }
                }
            } else {
                if (outcomeName != "Fallback") {
                    failures += "[$idLabel | rejectedArea=${entry.rejectedArea} | group=${entry.group} | kind=${entry.kind} | form=${entry.display}] expected Fallback for katexRenders=false, got $outcomeName :: ${entry.latex}"
                }
            }
        }

        val deviceTag = RichContentAndroidTestSupport.deviceTag()
        val partsDir = RichContentAndroidTestSupport.outputDir("formula-check/$deviceTag-parts")
        val partFile = File(partsDir, "$caseName.json")
        partFile.writeText(
            RichContentAndroidTestSupport.json.encodeToString(rows),
            Charsets.UTF_8,
        )

        val proposedDir = RichContentAndroidTestSupport.outputDir("formula-check/$deviceTag-proposed-parts")
        val proposedFile = File(proposedDir, "$caseName.json")
        if (proposed.isNotEmpty()) {
            proposedFile.writeText(
                RichContentAndroidTestSupport.json.encodeToString(proposed),
                Charsets.UTF_8,
            )
        } else if (proposedFile.exists()) {
            proposedFile.delete()
        }

        if (failures.isNotEmpty()) {
            fail(
                "FormulaCorpusInstrumentedTest[$caseName] failed ${failures.size}/${indexedEntries.size} entries:\n" +
                    failures.joinToString("\n"),
            )
        }
    }

    companion object {
        const val NO_SHEET_CASE = "__no_sheet__"

        @Volatile
        private var sharedEngine: RaTeXFormulaEngine? = null

        @JvmStatic
        @BeforeClass
        fun warmUpEngine() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val engine = RaTeXFormulaEngine(
                context = context,
                timeoutMs = FormulaRenderDefaults.RENDER_TIMEOUT_MS,
                maxCacheEntries = 64,
            )
            engine.renderDetailed(
                RichInline.Formula("x^2 + 1", FormulaDisplay.Inline, vn.unicom.thi360.feature.richcontent.model.FormulaNotation.Tex),
                44f,
                Color.BLACK,
            )
            sharedEngine = engine
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {
            val corpus = RichContentAndroidTestSupport.loadFormulaCorpus()
            val grouped = linkedMapOf<String, MutableList<Pair<Int, AndroidFormulaEntry>>>()
            corpus.entries.forEachIndexed { index, entry ->
                val bucket = entry.sheetGroup() ?: NO_SHEET_CASE
                grouped.getOrPut(bucket) { mutableListOf() }.add(index to entry)
            }
            return grouped.entries.map { (name, items) -> arrayOf(name, items) }
        }

        @JvmStatic
        @AfterClass
        fun mergeShardOutputs() {
            val deviceTag = RichContentAndroidTestSupport.deviceTag()
            val outRoot = RichContentAndroidTestSupport.outputDir("formula-check")
            val partsDir = File(outRoot, "$deviceTag-parts")
            val allRows = partsDir.listFiles { f -> f.isFile && f.extension == "json" }
                .orEmpty()
                .flatMap { f ->
                    RichContentAndroidTestSupport.json.decodeFromString<List<AndroidFormulaCheckRow>>(
                        f.readText(Charsets.UTF_8),
                    )
                }
                .sortedBy { it.index }
            val mergedFile = File(outRoot, "$deviceTag.json")
            mergedFile.writeText(
                RichContentAndroidTestSupport.json.encodeToString(allRows),
                Charsets.UTF_8,
            )
            RichContentAndroidTestSupport.mirrorToHostTmp(mergedFile, "formula-check/$deviceTag.json")

            val proposedDir = File(outRoot, "$deviceTag-proposed-parts")
            val allProposed = proposedDir.listFiles { f -> f.isFile && f.extension == "json" }
                .orEmpty()
                .sortedBy { it.name }
                .flatMap { f ->
                    RichContentAndroidTestSupport.json.decodeFromString<List<AndroidFormulaExemption>>(
                        f.readText(Charsets.UTF_8),
                    )
                }
            val mergedProposed = File(outRoot, "proposed-exemptions-$deviceTag.json")
            val proposedJson = RichContentAndroidTestSupport.json.encodeToString(
                AndroidFormulaExemptionsRoot(version = 1, rows = allProposed),
            )
            mergedProposed.writeText(proposedJson, Charsets.UTF_8)
            RichContentAndroidTestSupport.mirrorToHostTmp(mergedProposed, "formula-check/proposed-exemptions-$deviceTag.json")
            if (!deviceTag.endsWith("-shard")) {
                val defaultProposed = File(outRoot, "proposed-exemptions.json")
                defaultProposed.writeText(proposedJson, Charsets.UTF_8)
                RichContentAndroidTestSupport.mirrorToHostTmp(defaultProposed, "formula-check/proposed-exemptions.json")
            }
        }
    }
}
