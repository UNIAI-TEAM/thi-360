package vn.unicom.thi360.feature.richcontent

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.ContractFixtureLoader

class FormulaCorpusGuardTest {

    private val json = Json { ignoreUnknownKeys = false }

    private fun repoRoot(): File =
        ContractFixtureLoader.openApiSpecFile().canonicalFile.parentFile!!.parentFile!!.parentFile!!

    private fun richTextDir(): File = File(repoRoot(), "contracts/mobile/rich-text")

    private fun acceptanceFile(): File = File(repoRoot(), "docs/acceptance/app-hoc-vien-cong-thi.md")

    internal data class ApprovalRow(
        val code: String,
        val group: String?,
        val kind: String?,
        val latex: String,
        val display: String,
        val app: String,
        val approvalQuote: String,
        val approver: String,
        val date: String,
    )

    internal fun parseApprovalTable(markdown: String): List<ApprovalRow> {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val headingIdx = lines.indexOfFirst { Regex("""^##\s+Miễn trừ công thức \(D-8\)\s*$""").matches(it.trim()) }
        if (headingIdx == -1) return emptyList()
        val endIdx = ((headingIdx + 1) until lines.size)
            .firstOrNull { lines[it].trim().startsWith("## ") }
            ?: lines.size
        val tableLines = lines.subList(headingIdx + 1, endIdx).filter { it.trim().startsWith("|") }
        if (tableLines.size <= 2) return emptyList()

        fun unwrap(cell: String): String {
            val t = cell.trim()
            return if (t.length >= 2 && t.startsWith("`") && t.endsWith("`")) t.substring(1, t.length - 1).trim() else t
        }
        fun nullable(cell: String): String? {
            val u = unwrap(cell)
            return if (u.isEmpty() || u == "-" || u == "—" || u == "null") null else u
        }

        return tableLines.drop(2).mapNotNull { line ->
            val trimmed = line.trim().removePrefix("|").removeSuffix("|")
            val cells = mutableListOf<String>()
            val current = StringBuilder()
            var i = 0
            while (i < trimmed.length) {
                val ch = trimmed[i]
                if (ch == '\\' && i + 1 < trimmed.length && trimmed[i + 1] == '|') {
                    current.append('|')
                    i += 2
                    continue
                }
                if (ch == '|') {
                    cells += current.toString().trim()
                    current.clear()
                } else {
                    current.append(ch)
                }
                i++
            }
            cells += current.toString().trim()
            if (cells.size != 11) return@mapNotNull null
            ApprovalRow(
                code = unwrap(cells[0]),
                group = nullable(cells[1]),
                kind = nullable(cells[2]),
                latex = unwrap(cells[3]),
                display = unwrap(cells[4]),
                app = unwrap(cells[5]),
                approvalQuote = unwrap(cells[8]),
                approver = unwrap(cells[9]),
                date = unwrap(cells[10]),
            )
        }
    }

    internal fun validateCorpus(
        formulasFile: FormulasFileModel,
        exemptionsFile: FormulaExemptionsFileModel,
        webRendersDir: File,
        acceptanceMarkdown: String,
    ): List<String> {
        val errors = mutableListOf<String>()

        if (formulasFile.katexVersion != EXPECTED_KATEX_VERSION) {
            errors += "katexVersion mismatch: expected $EXPECTED_KATEX_VERSION, got ${formulasFile.katexVersion}"
        }

        val catalogueEntries = formulasFile.entries.filter { it.origin == "catalogue" }
        formulasFile.entries.forEachIndexed { idx, entry ->
            if (entry.origin !in VALID_ORIGINS) {
                errors += "entry[$idx] missing or invalid origin: ${entry.origin}"
            }
            if (entry.display !in VALID_DISPLAYS) {
                errors += "entry[$idx] invalid display: ${entry.display}"
            }
            if (entry.origin == "catalogue" && entry.katexRenders && entry.rejectedArea == null) {
                if (entry.group.isNullOrBlank()) {
                    errors += "entry[$idx] catalogue renderable entry missing group (${entry.latex})"
                }
                if (entry.kind !in VALID_KINDS) {
                    errors += "entry[$idx] catalogue renderable entry missing or invalid kind (${entry.latex})"
                }
            }
        }

        val grouped = catalogueEntries.filter { it.group != null }
        val uniqueGroups = grouped.mapNotNull { it.group }.toSet()
        val renderableFormulas = grouped.count { it.display == "block" && it.katexRenders }
        val rejectedFormulas = catalogueEntries.count { it.group == null && it.display == "inline" && !it.katexRenders }

        if (uniqueGroups.size != EXPECTED_GROUPS) {
            errors += "Catalogue group count mismatch: expected $EXPECTED_GROUPS, got ${uniqueGroups.size}"
        }
        if (renderableFormulas != EXPECTED_RENDERABLE_FORMULAS) {
            errors += "Catalogue renderable formula count mismatch: expected $EXPECTED_RENDERABLE_FORMULAS, got $renderableFormulas"
        }
        if (rejectedFormulas != EXPECTED_REJECTED_FORMULAS) {
            errors += "Catalogue rejected formula count mismatch: expected $EXPECTED_REJECTED_FORMULAS, got $rejectedFormulas"
        }

        val expectedSheetKeys = formulasFile.entries.mapNotNull { it.sheetKey }
        val expectedSheetGroups = expectedSheetKeys.map { it.substringBefore('/') }.toSet()
        if (expectedSheetGroups.size != EXPECTED_SHEET_GROUPS) {
            errors += "Expected $EXPECTED_SHEET_GROUPS sheetKey groups, got ${expectedSheetGroups.size}"
        }
        if (!webRendersDir.isDirectory) {
            errors += "Missing web-renders directory: ${webRendersDir.canonicalPath}"
        } else {
            val actualDirs = webRendersDir.listFiles { f -> f.isDirectory }?.map { it.name }?.toSet().orEmpty()
            if (actualDirs.size != EXPECTED_SHEET_GROUPS) {
                errors += "Expected $EXPECTED_SHEET_GROUPS directories under web-renders/, got ${actualDirs.size}"
            }
            for (sheetKey in expectedSheetKeys) {
                val pngFile = File(webRendersDir, "$sheetKey.png")
                if (!pngFile.isFile || pngFile.length() == 0L) {
                    errors += "Missing or empty web-render PNG for sheetKey '$sheetKey'"
                }
            }
        }

        val approvalsByCode = parseApprovalTable(acceptanceMarkdown).associateBy { it.code }
        exemptionsFile.rows.forEachIndexed { idx, row ->
            if (row.reason.isBlank()) {
                errors += "exemption[$idx] (${row.latex}) missing reason"
            }
            if (row.beyondCurriculum == null) {
                errors += "exemption[$idx] (${row.latex}) missing beyondCurriculum"
            }
            if (row.display !in VALID_DISPLAYS) {
                errors += "exemption[$idx] invalid display: ${row.display}"
            }
            if (row.app !in VALID_APPS) {
                errors += "exemption[$idx] invalid app: ${row.app}"
            }
            if (row.origin !in VALID_ORIGINS) {
                errors += "exemption[$idx] invalid origin: ${row.origin}"
            }
            val needsPerFormulaApproval = row.kind == "curriculum" || row.origin == "repo-sample" || row.group == null
            if (needsPerFormulaApproval) {
                val approval = approvalsByCode[row.approvalRef]
                if (row.approvalRef.isBlank() || approval == null) {
                    errors += "exemption[$idx] (${row.latex}) requires per-formula D-8 approvalRef in acceptance report"
                } else {
                    if (approval.latex != row.latex) {
                        errors += "exemption[$idx] approvalRef '${row.approvalRef}' formula mismatch"
                    }
                    if (approval.approvalQuote.isBlank() || approval.approver.isBlank() || approval.date.isBlank()) {
                        errors += "exemption[$idx] approvalRef '${row.approvalRef}' incomplete in acceptance report"
                    }
                }
            } else if (row.approvalRef.isBlank()) {
                errors += "exemption[$idx] (${row.latex}) missing list approvalRef"
            }
        }

        return errors
    }

    @Test
    fun repositoryFormulaCorpusExemptionsAndWebRendersAreValid() {
        val dir = richTextDir()
        val formulasFile = json.decodeFromString<FormulasFileModel>(File(dir, "formulas.json").readText(Charsets.UTF_8))
        val exemptionsFile = json.decodeFromString<FormulaExemptionsFileModel>(
            File(dir, "formula-exemptions.json").readText(Charsets.UTF_8),
        )
        val acceptanceMd = acceptanceFile().readText(Charsets.UTF_8)

        val errors = validateCorpus(formulasFile, exemptionsFile, File(dir, "web-renders"), acceptanceMd)
        assertTrue("Formula corpus guard errors:\n${errors.joinToString("\n")}", errors.isEmpty())
    }

    @Test
    fun guardTurnsRedOnEveryAC18ViolationCondition() {
        val dir = richTextDir()
        val baseFormulas = json.decodeFromString<FormulasFileModel>(File(dir, "formulas.json").readText(Charsets.UTF_8))
        val baseExemptions = json.decodeFromString<FormulaExemptionsFileModel>(
            File(dir, "formula-exemptions.json").readText(Charsets.UTF_8),
        )
        val webDir = File(dir, "web-renders")
        val acceptanceMd = acceptanceFile().readText(Charsets.UTF_8)

        val missingOrigin = baseFormulas.copy(
            entries = listOf(baseFormulas.entries.first().copy(origin = null)) + baseFormulas.entries.drop(1),
        )
        assertTrue(validateCorpus(missingOrigin, baseExemptions, webDir, acceptanceMd).any { it.contains("origin") })

        val missingGroup = baseFormulas.copy(
            entries = listOf(baseFormulas.entries.first().copy(group = null)) + baseFormulas.entries.drop(1),
        )
        assertTrue(validateCorpus(missingGroup, baseExemptions, webDir, acceptanceMd).any { it.contains("group") })

        val missingKind = baseFormulas.copy(
            entries = listOf(baseFormulas.entries.first().copy(kind = null)) + baseFormulas.entries.drop(1),
        )
        assertTrue(validateCorpus(missingKind, baseExemptions, webDir, acceptanceMd).any { it.contains("kind") })

        val droppedFormula = baseFormulas.copy(entries = baseFormulas.entries.drop(1))
        assertTrue(validateCorpus(droppedFormula, baseExemptions, webDir, acceptanceMd).any { it.contains("count mismatch") })

        val wrongKatex = baseFormulas.copy(katexVersion = "0.19.2")
        assertTrue(validateCorpus(wrongKatex, baseExemptions, webDir, acceptanceMd).any { it.contains("katexVersion") })

        val sampleEntry = baseFormulas.entries.first { it.kind == "curriculum" }
        val noReason = FormulaExemptionsFileModel(
            version = 1,
            rows = listOf(
                FormulaExemption(
                    latex = sampleEntry.latex,
                    display = sampleEntry.display,
                    app = "android",
                    reason = "",
                    beyondCurriculum = false,
                    origin = "catalogue",
                    group = sampleEntry.group,
                    kind = sampleEntry.kind,
                    approvalRef = "EX-01",
                ),
            ),
        )
        assertTrue(validateCorpus(baseFormulas, noReason, webDir, acceptanceMd).any { it.contains("missing reason") })

        val noBeyond = FormulaExemptionsFileModel(
            version = 1,
            rows = listOf(noReason.rows.single().copy(reason = "Unsupported macro", beyondCurriculum = null)),
        )
        assertTrue(validateCorpus(baseFormulas, noBeyond, webDir, acceptanceMd).any { it.contains("beyondCurriculum") })

        val unapprovedCurriculum = FormulaExemptionsFileModel(
            version = 1,
            rows = listOf(noReason.rows.single().copy(reason = "Unsupported macro", beyondCurriculum = false, approvalRef = "MISSING-REF")),
        )
        assertTrue(validateCorpus(baseFormulas, unapprovedCurriculum, webDir, acceptanceMd).any { it.contains("D-8 approvalRef") })

        val repoSample = baseFormulas.entries.first { it.origin == "repo-sample" && it.katexRenders }
        val unapprovedRepoSample = FormulaExemptionsFileModel(
            version = 1,
            rows = listOf(
                FormulaExemption(
                    latex = repoSample.latex,
                    display = repoSample.display,
                    app = "android",
                    reason = "Unsupported",
                    beyondCurriculum = false,
                    origin = "repo-sample",
                    group = null,
                    kind = null,
                    approvalRef = "",
                ),
            ),
        )
        assertTrue(validateCorpus(baseFormulas, unapprovedRepoSample, webDir, acceptanceMd).any { it.contains("D-8 approvalRef") })
        assertEquals(225, baseFormulas.catalogue.counts.groups)
    }

    companion object {
        private const val EXPECTED_KATEX_VERSION = "0.19.1"
        private const val EXPECTED_GROUPS = 225
        private const val EXPECTED_RENDERABLE_FORMULAS = 1915
        private const val EXPECTED_REJECTED_FORMULAS = 93
        private const val EXPECTED_SHEET_GROUPS = 226
        private val VALID_ORIGINS = setOf("catalogue", "repo-sample")
        private val VALID_DISPLAYS = setOf("inline", "block")
        private val VALID_KINDS = setOf("curriculum", "syntax")
        private val VALID_APPS = setOf("android", "ios", "both")
    }
}

