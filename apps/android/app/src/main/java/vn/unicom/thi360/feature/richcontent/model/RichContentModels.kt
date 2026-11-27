package vn.unicom.thi360.feature.richcontent.model

enum class RichTextStyle {
    Bold,
    Italic,
    Underline,
    Strike,
    Sub,
    Sup,
}

enum class FormulaDisplay {
    Inline,
    Block,
}

enum class FormulaNotation {
    Tex,
    Ce,
    Pu,
}

sealed interface AttemptAssetRef {
    data class Attempt(val assetId: String) : AttemptAssetRef
    data class Solution(val assetId: String) : AttemptAssetRef
    data object Blocked : AttemptAssetRef
}

enum class FigureWidth(val widthFraction: Float) {
    Small(0.40f),
    Medium(0.70f),
    Full(1.00f),
}

enum class QuoteKind {
    Poem,
    Prose,
    Source,
}

enum class TableHeaderScope {
    Col,
    Row,
}

enum class RichContentMode {
    Answering,
    Locked,
    Review,
}

sealed interface RichInline {
    data class Text(
        val text: String,
        val styles: Set<RichTextStyle> = emptySet(),
    ) : RichInline

    data object LineBreak : RichInline

    data class Formula(
        val source: String,
        val display: FormulaDisplay,
        val notation: FormulaNotation,
    ) : RichInline {
        fun delimitedSource(): String = when (display) {
            FormulaDisplay.Inline -> "\\($source\\)"
            FormulaDisplay.Block -> "\\[$source\\]"
        }
    }

    data class LabeledUnderline(
        val label: Char,
        val children: List<RichInline>,
    ) : RichInline

    data class Blank(
        val number: Int,
        val rawNumber: Int = number,
    ) : RichInline

    data class Image(
        val ref: AttemptAssetRef,
        val alt: String,
        val width: Int? = null,
        val height: Int? = null,
    ) : RichInline

    data class Slot(val id: String) : RichInline
}

data class RichTableCell(
    val isHeader: Boolean,
    val scope: TableHeaderScope? = null,
    val isNumeric: Boolean = false,
    val colspan: Int = 1,
    val rowspan: Int = 1,
    val blocks: List<RichBlock>,
)

data class RichTableRow(
    val cells: List<RichTableCell>,
    val isInHead: Boolean = false,
)

data class RichTable(
    val rows: List<RichTableRow>,
)

sealed interface RichBlock {
    data class Paragraph(val inlines: List<RichInline>) : RichBlock

    data class FormulaBlock(val formula: RichInline.Formula) : RichBlock

    data class BulletList(val items: List<List<RichBlock>>) : RichBlock

    data class OrderedList(val items: List<List<RichBlock>>) : RichBlock

    data class Table(val table: RichTable) : RichBlock

    data class DataTableFigure(
        val caption: List<RichInline>? = null,
        val table: RichTable,
        val notes: List<List<RichInline>> = emptyList(),
        val sources: List<List<RichInline>> = emptyList(),
    ) : RichBlock

    data class CrossFigure(val table: RichTable) : RichBlock

    data class ImageFigure(
        val image: RichInline.Image,
        val width: FigureWidth,
        val zoomable: Boolean,
        val caption: List<RichInline>? = null,
        val sources: List<List<RichInline>> = emptyList(),
    ) : RichBlock

    data class Quote(
        val kind: QuoteKind,
        val lineNumberStep: Int,
        val paragraphs: List<List<RichInline>>,
        val footer: List<RichInline>? = null,
        val source: List<RichInline>? = null,
    ) : RichBlock
}

data class RichDocument(
    val blocks: List<RichBlock>,
    val blankNumberStart: Int = 1,
)

data class RichParseOptions(
    val blankNumberStart: Int = 1,
    val slotMarkers: Boolean = false,
    val allowImages: Boolean = true,
)

data class RichSanitizeViolation(
    val tag: String,
    val attribute: String? = null,
    val value: String? = null,
)

data class RichParseResult(
    val document: RichDocument,
    val violations: List<RichSanitizeViolation>,
)
