package vn.unicom.thi360.feature.richcontent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import vn.unicom.thi360.R
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichContentMode
import vn.unicom.thi360.feature.richcontent.model.RichTable
import vn.unicom.thi360.feature.richcontent.model.RichTableCell

internal data class PlacedTableCell(
    val row: Int,
    val col: Int,
    val colspan: Int,
    val rowspan: Int,
    val cell: RichTableCell,
)

internal data class TableGridPlan(
    val rowCount: Int,
    val colCount: Int,
    val placements: List<PlacedTableCell>,
)

internal object TableGridLayoutPlanner {
    fun plan(table: RichTable): TableGridPlan {
        val occupied = mutableMapOf<Pair<Int, Int>, Boolean>()
        val placements = mutableListOf<PlacedTableCell>()
        var maxRow = table.rows.size
        var maxCol = 0

        table.rows.forEachIndexed { rowIdx, row ->
            var colIdx = 0
            for (cell in row.cells) {
                while (occupied[rowIdx to colIdx] == true) {
                    colIdx++
                }
                val cSpan = cell.colspan.coerceAtLeast(1)
                val rSpan = cell.rowspan.coerceAtLeast(1)
                placements += PlacedTableCell(
                    row = rowIdx,
                    col = colIdx,
                    colspan = cSpan,
                    rowspan = rSpan,
                    cell = cell,
                )
                for (r in rowIdx until (rowIdx + rSpan)) {
                    for (c in colIdx until (colIdx + cSpan)) {
                        occupied[r to c] = true
                    }
                }
                maxRow = max(maxRow, rowIdx + rSpan)
                maxCol = max(maxCol, colIdx + cSpan)
                colIdx += cSpan
            }
        }
        return TableGridPlan(
            rowCount = maxRow,
            colCount = maxCol,
            placements = placements,
        )
    }
}

internal object ConsumeHorizontalNestedScroll : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = Offset(x = available.x, y = 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(x = available.x, y = 0f)
}

@Composable
internal fun RichTableView(
    table: RichTable,
    assets: AttemptAssetSource?,
    mode: RichContentMode,
    slots: Map<String, @Composable () -> Unit>,
    effectiveBlankStart: Int,
    formulaEngine: FormulaEngine,
    palette: RichContentPalette,
    textSizeSp: Float,
    modifier: Modifier = Modifier,
) {
    val grid = remember(table) { TableGridLayoutPlanner.plan(table) }
    if (grid.placements.isEmpty() || grid.colCount == 0) return

    val scrollState = rememberScrollState()
    val scrollRegionDesc = stringResource(R.string.richcontent_table_scroll_region)
    val density = LocalDensity.current
    val minColPx = with(density) { 48.dp.roundToPx() }
    val maxSingleColProsePx = with(density) { 260.dp.roundToPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = scrollRegionDesc }
            .testTag(RichContentTestTags.TABLE_SCROLL),
    ) {
        val availableWidthPx = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: 0

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(ConsumeHorizontalNestedScroll)
                .horizontalScroll(scrollState),
        ) {
            Layout(
            content = {
                grid.placements.forEach { placed ->
                    val cell = placed.cell
                    val cellBg = if (cell.isHeader) palette.tableHead else palette.surface
                    Box(
                        modifier = Modifier
                            .background(cellBg)
                            .border(width = 1.dp, color = palette.borderStrong)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            RichBlocksColumn(
                                blocks = cell.blocks,
                                assets = assets,
                                mode = mode,
                                slots = slots,
                                effectiveBlankStart = effectiveBlankStart,
                                formulaEngine = formulaEngine,
                                palette = palette,
                                textSizeSp = textSizeSp,
                                forceBold = cell.isHeader,
                                alignEnd = cell.isNumeric,
                                tabularNums = cell.isNumeric,
                                insideTableCell = true,
                            )
                        }
                    }
                }
            },
        ) { measurables, _ ->
            val colWidths = IntArray(grid.colCount) { minColPx }

            val naturalWidths = IntArray(grid.placements.size)
            grid.placements.forEachIndexed { index, placed ->
                val rawIntrinsic = measurables[index].maxIntrinsicWidth(Constraints.Infinity)
                val cap = if (placed.cell.isNumeric || placed.cell.isHeader) {
                    Int.MAX_VALUE
                } else {
                    maxSingleColProsePx * placed.colspan
                }
                val natW = rawIntrinsic.coerceIn(minColPx * placed.colspan, cap)
                naturalWidths[index] = natW
                if (placed.colspan == 1) {
                    colWidths[placed.col] = max(colWidths[placed.col], natW)
                }
            }

            grid.placements.forEachIndexed { index, placed ->
                if (placed.colspan > 1) {
                    var spanSum = 0
                    for (c in placed.col until placed.col + placed.colspan) {
                        spanSum += colWidths[c]
                    }
                    if (naturalWidths[index] > spanSum) {
                        val extra = naturalWidths[index] - spanSum
                        val perCol = ceil(extra.toDouble() / placed.colspan).toInt()
                        for (c in placed.col until placed.col + placed.colspan) {
                            colWidths[c] += perCol
                        }
                    }
                }
            }

            val rawTotalWidth = colWidths.sum()
            if (availableWidthPx > 0 && rawTotalWidth < availableWidthPx) {
                val scale = availableWidthPx.toDouble() / rawTotalWidth.toDouble()
                var assigned = 0
                for (c in 0 until grid.colCount) {
                    val w = if (c == grid.colCount - 1) {
                        max(colWidths[c], availableWidthPx - assigned)
                    } else {
                        (colWidths[c] * scale).roundToInt()
                    }
                    colWidths[c] = w
                    assigned += w
                }
            }

            val rowHeights = IntArray(grid.rowCount) { 0 }
            val cellHeights = IntArray(grid.placements.size)

            grid.placements.forEachIndexed { index, placed ->
                var spanW = 0
                for (c in placed.col until placed.col + placed.colspan) spanW += colWidths[c]
                val intrinsicH = measurables[index].minIntrinsicHeight(spanW).coerceAtLeast(1)
                cellHeights[index] = intrinsicH
                if (placed.rowspan == 1) {
                    rowHeights[placed.row] = max(rowHeights[placed.row], intrinsicH)
                }
            }

            grid.placements.forEachIndexed { index, placed ->
                if (placed.rowspan > 1) {
                    var spanH = 0
                    for (r in placed.row until placed.row + placed.rowspan) spanH += rowHeights[r]
                    if (cellHeights[index] > spanH) {
                        val extra = cellHeights[index] - spanH
                        val perRow = ceil(extra.toDouble() / placed.rowspan).toInt()
                        for (r in placed.row until placed.row + placed.rowspan) {
                            rowHeights[r] += perRow
                        }
                    }
                }
            }

            val finalPlaceables = grid.placements.mapIndexed { index, placed ->
                var spanW = 0
                for (c in placed.col until placed.col + placed.colspan) spanW += colWidths[c]
                var spanH = 0
                for (r in placed.row until placed.row + placed.rowspan) spanH += rowHeights[r]
                measurables[index].measure(Constraints.fixed(spanW, spanH))
            }

            val colOffsets = IntArray(grid.colCount + 1)
            for (c in 0 until grid.colCount) colOffsets[c + 1] = colOffsets[c] + colWidths[c]
            val rowOffsets = IntArray(grid.rowCount + 1)
            for (r in 0 until grid.rowCount) rowOffsets[r + 1] = rowOffsets[r] + rowHeights[r]

            val totalW = colOffsets[grid.colCount]
            val totalH = rowOffsets[grid.rowCount]

            layout(totalW, totalH) {
                grid.placements.forEachIndexed { index, placed ->
                    finalPlaceables[index].placeRelative(
                        x = colOffsets[placed.col],
                        y = rowOffsets[placed.row],
                    )
                }
            }
        }
        }
    }
}
