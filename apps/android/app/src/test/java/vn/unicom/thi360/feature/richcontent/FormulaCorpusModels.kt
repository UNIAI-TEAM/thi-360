package vn.unicom.thi360.feature.richcontent

import kotlinx.serialization.Serializable

@Serializable
data class FormulaEntry(
    val latex: String,
    val display: String,
    val origin: String? = null,
    val group: String? = null,
    val kind: String? = null,
    val rejectedArea: String? = null,
    val katexRenders: Boolean,
    val sheetKey: String? = null,
)

@Serializable
data class FormulaCatalogueCounts(
    val groups: Int,
    val formulas: Int,
    val curriculumFormulas: Int,
    val syntaxFormulas: Int,
    val mhchemFormulas: Int,
    val blockOnly: Int,
    val rejected: Int,
)

@Serializable
data class FormulaCatalogueMeta(
    val path: String,
    val date: String,
    val counts: FormulaCatalogueCounts,
)

@Serializable
data class FormulasFileModel(
    val katexVersion: String,
    val mhchem: Boolean,
    val catalogue: FormulaCatalogueMeta,
    val entries: List<FormulaEntry>,
)

@Serializable
data class FormulaExemption(
    val latex: String,
    val display: String,
    val app: String,
    val reason: String,
    val beyondCurriculum: Boolean? = null,
    val origin: String,
    val group: String? = null,
    val kind: String? = null,
    val approvalRef: String,
)

@Serializable
data class FormulaExemptionsFileModel(
    val version: Int,
    val rows: List<FormulaExemption>,
)

enum class FormulaCheckOutcome {
    Rendered,
    Fallback,
    Crash,
    Timeout,
}

@Serializable
data class FormulaCheckRow(
    val index: Int,
    val latex: String,
    val sheetKey: String? = null,
    val group: String? = null,
    val kind: String? = null,
    val form: String,
    val outcome: FormulaCheckOutcome,
)
