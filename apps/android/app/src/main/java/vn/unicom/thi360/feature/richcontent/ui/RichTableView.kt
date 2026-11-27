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
    internal const val MAX_COLSPAN = 64
    internal const val MAX_ROWSPAN = 200

    fun plan(table: RichTable): TableGridPlan {
        val occupied = mutableMapOf<Pair<Int, Int>, Boolean>()
        val placements = mutableListOf<PlacedTableCell>()
        var maxRow = table.rows.size
        var maxCol = 0

        table.rows.forEachIndexed { rowIdx, row ->
            var colIdx = 0
            val remainingRows = (table.rows.size - rowIdx).coerceAtLeast(1)
            val maxRowSpan = minOf(remainingRows, MAX_ROWSPAN)
            for (cell in row.cells) {
                while (occupied[rowIdx to colIdx] == true) {
                    colIdx++
                }
                val cSpan = cell.colspan.coerceIn(1, MAX_COLSPAN)
                val rSpan = cell.rowspan.coerceIn(1, maxRowSpan)
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

private const val MAX_COMPOSE_LAYOUT_PX = 200_000

private fun clampTrackSizes(tracks: IntArray, maxTotal: Int) {
    val sum = tracks.sumOf { it.toLong() }
    if (sum <= maxTotal.toLong() || tracks.isEmpty()) return
    val scale = maxTotal.toDouble() / sum.toDouble()
    var running = 0
    for (i in tracks.indices) {
        val remaining = (maxTotal - running).coerceAtLeast(0)
        val scaled = (tracks[i] * scale).toInt().coerceIn(0, remaining)
        tracks[i] = scaled
        running += scaled
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
                val minSpanW = (minColPx.toLong() * placed.colspan)
                    .coerceAtMost(MAX_COMPOSE_LAYOUT_PX.toLong())
                    .toInt()
                val cap = if (placed.cell.isNumeric || placed.cell.isHeader) {
                    MAX_COMPOSE_LAYOUT_PX
                } else {
                    (maxSingleColProsePx.toLong() * placed.colspan)
                        .coerceIn(minSpanW.toLong(), MAX_COMPOSE_LAYOUT_PX.toLong())
                        .toInt()
                }
                val natW = rawIntrinsic.coerceIn(minSpanW, max(minSpanW, cap))
                naturalWidths[index] = natW
                if (placed.colspan == 1) {
                    colWidths[placed.col] = max(colWidths[placed.col], natW)
                }
            }

            grid.placements.forEachIndexed { index, placed ->
                if (placed.colspan > 1) {
                    var spanSum = 0L
                    for (c in placed.col until placed.col + placed.colspan) {
                        spanSum += colWidths[c]
                    }
                    if (naturalWidths[index].toLong() > spanSum) {
                        val extra = naturalWidths[index].toLong() - spanSum
                        val perCol = ceil(extra.toDouble() / placed.colspan).toInt()
                        for (c in placed.col until placed.col + placed.colspan) {
                            colWidths[c] = (colWidths[c].toLong() + perCol)
                                .coerceAtMost(MAX_COMPOSE_LAYOUT_PX.toLong())
                                .toInt()
                        }
                    }
                }
            }

            clampTrackSizes(colWidths, MAX_COMPOSE_LAYOUT_PX)

            val rawTotalWidth = colWidths.sumOf { it.toLong() }
            if (availableWidthPx > 0 && rawTotalWidth > 0L && rawTotalWidth < availableWidthPx.toLong()) {
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
                val intrinsicH = measurables[index].minIntrinsicHeight(spanW)
                    .coerceIn(1, MAX_COMPOSE_LAYOUT_PX)
                cellHeights[index] = intrinsicH
                if (placed.rowspan == 1) {
                    rowHeights[placed.row] = max(rowHeights[placed.row], intrinsicH)
                }
            }

            grid.placements.forEachIndexed { index, placed ->
                if (placed.rowspan > 1) {
                    var spanH = 0L
                    for (r in placed.row until placed.row + placed.rowspan) spanH += rowHeights[r]
                    if (cellHeights[index].toLong() > spanH) {
                        val extra = cellHeights[index].toLong() - spanH
                        val perRow = ceil(extra.toDouble() / placed.rowspan).toInt()
                        for (r in placed.row until placed.row + placed.rowspan) {
                            rowHeights[r] = (rowHeights[r].toLong() + perRow)
                                .coerceAtMost(MAX_COMPOSE_LAYOUT_PX.toLong())
                                .toInt()
                        }
                    }
                }
            }

            clampTrackSizes(rowHeights, MAX_COMPOSE_LAYOUT_PX)

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
