import SwiftUI
import UIKit

struct PlacedTableCell: Identifiable {
    let id: String
    let cell: RichTableCell
    let row: Int
    let column: Int
    let rowSpan: Int
    let colSpan: Int
}

struct ResolvedTableGrid {
    let rowCount: Int
    let columnCount: Int
    let cells: [PlacedTableCell]

    static func resolve(_ table: RichTable) -> ResolvedTableGrid {
        var occupied = Set<GridCoord>()
        var placed: [PlacedTableCell] = []
        var maxCols = 0
        var maxRows = table.rows.count

        for (rIdx, row) in table.rows.enumerated() {
            var col = 0
            for (cIdx, cell) in row.cells.enumerated() {
                while occupied.contains(GridCoord(row: rIdx, col: col)) {
                    col += 1
                }
                let rSpan = max(1, cell.rowspan)
                let cSpan = max(1, cell.colspan)
                for dr in 0..<rSpan {
                    for dc in 0..<cSpan {
                        occupied.insert(GridCoord(row: rIdx + dr, col: col + dc))
                    }
                }
                placed.append(
                    PlacedTableCell(
                        id: "r\(rIdx)c\(col)_\(cIdx)",
                        cell: cell,
                        row: rIdx,
                        column: col,
                        rowSpan: rSpan,
                        colSpan: cSpan
                    )
                )
                maxCols = max(maxCols, col + cSpan)
                maxRows = max(maxRows, rIdx + rSpan)
                col += cSpan
            }
        }

        return ResolvedTableGrid(rowCount: maxRows, columnCount: maxCols, cells: placed)
    }

    private struct GridCoord: Hashable {
        let row: Int
        let col: Int
    }
}

struct RichTableView: View {
    let table: RichTable
    let tokens: RichContentTokens
    let assets: AttemptAssetSource?
    let mode: RichContentMode
    let slots: [String: () -> AnyView]
    let blankNumberStart: Int
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let availableWidth: CGFloat

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let grid = ResolvedTableGrid.resolve(table)
        let traits = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(dynamicTypeSize))
        let colWidths = computeColumnWidths(grid: grid, traits: traits)
        let totalWidth = colWidths.reduce(0, +)
        let needsHorizontalScroll = totalWidth > availableWidth + 0.5

        if grid.rowCount == 0 || grid.columnCount == 0 {
            EmptyView()
        } else if needsHorizontalScroll {
            ScrollView(.horizontal, showsIndicators: true) {
                tableGridView(grid: grid, columnWidths: colWidths)
            }
            .frame(maxWidth: availableWidth, alignment: .leading)
            .accessibilityIdentifier("richContent.table.horizontalScroll")
        } else {
            let expandedWidths = expandColumnWidths(colWidths, toFill: availableWidth)
            tableGridView(grid: grid, columnWidths: expandedWidths)
                .frame(maxWidth: availableWidth, alignment: .leading)
                .accessibilityIdentifier("richContent.table.static")
        }
    }

    private func tableGridView(
        grid: ResolvedTableGrid,
        columnWidths: [CGFloat]
    ) -> some View {
        TableSpanGridLayout(
            rowCount: grid.rowCount,
            columnWidths: columnWidths,
            placements: grid.cells
        ) {
            ForEach(grid.cells) { placed in
                RichTableCellView(
                    cell: placed.cell,
                    tokens: tokens,
                    assets: assets,
                    mode: mode,
                    slots: slots,
                    blankNumberStart: blankNumberStart,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    cellWidth: cellWidth(for: placed, columnWidths: columnWidths)
                )
            }
        }
        .overlay(
            Rectangle()
                .strokeBorder(tokens.brand.borderStrongColor, lineWidth: 1)
        )
    }

    private func cellWidth(for placed: PlacedTableCell, columnWidths: [CGFloat]) -> CGFloat {
        let end = min(placed.column + placed.colSpan, columnWidths.count)
        guard placed.column < end else { return 48 }
        return columnWidths[placed.column..<end].reduce(0, +)
    }

    private func computeColumnWidths(
        grid: ResolvedTableGrid,
        traits: UITraitCollection
    ) -> [CGFloat] {
        guard grid.columnCount > 0 else { return [] }
        let baseFont = RichDynamicTypeFont.scaledFont(
            baseSize: 15,
            style: [],
            textStyle: .callout,
            tabularDigits: true,
            traitCollection: traits
        )
        let boldFont = RichDynamicTypeFont.scaledFont(
            baseSize: 15,
            style: .bold,
            textStyle: .callout,
            tabularDigits: true,
            traitCollection: traits
        )

        let minColumnWidth: CGFloat = max(44, ceil(baseFont.pointSize * 2.6))
        let maxSingleColumnWidth: CGFloat = max(min(availableWidth * 0.65, 240), 120)
        var widths = [CGFloat](repeating: minColumnWidth, count: grid.columnCount)

        for placed in grid.cells {
            let font = placed.cell.isHeader ? boldFont : baseFont
            let measuredText = measureCellNaturalWidth(
                placed.cell,
                font: font,
                pointSize: baseFont.pointSize
            )
            let padded = ceil(measuredText) + 20
            let capped = placed.cell.isNumeric ? max(padded, minColumnWidth) : min(max(padded, minColumnWidth), maxSingleColumnWidth)

            if placed.colSpan == 1 {
                widths[placed.column] = max(widths[placed.column], capped)
            } else {
                let end = min(placed.column + placed.colSpan, grid.columnCount)
                let existing = widths[placed.column..<end].reduce(0, +)
                if capped > existing {
                    let delta = (capped - existing) / CGFloat(end - placed.column)
                    for col in placed.column..<end {
                        widths[col] += delta
                    }
                }
            }
        }

        return widths.map { ceil($0) }
    }

    private func expandColumnWidths(_ widths: [CGFloat], toFill targetWidth: CGFloat) -> [CGFloat] {
        let sum = widths.reduce(0, +)
        guard sum > 0, sum < targetWidth else { return widths }
        let scale = targetWidth / sum
        return widths.map { floor($0 * scale) }
    }

    private func measureCellNaturalWidth(
        _ cell: RichTableCell,
        font: UIFont,
        pointSize: CGFloat
    ) -> CGFloat {
        var maxLineWidth: CGFloat = 0
        for block in cell.blocks {
            switch block {
            case let .paragraph(inlines):
                var lineWidth: CGFloat = 0
                for inline in inlines {
                    switch inline {
                    case let .text(s, _):
                        lineWidth += (s as NSString).size(withAttributes: [.font: font]).width
                    case let .formula(f):
                        if case let .rendered(_, width, _, _) = formulaEngine.render(f, pointSize: pointSize, color: tokens.textUIColor) {
                            lineWidth += width
                        } else {
                            lineWidth += RichInlineBuilder.measureFallbackBadgeWidth(source: f.delimitedSource, pointSize: pointSize)
                        }
                    case .lineBreak:
                        maxLineWidth = max(maxLineWidth, lineWidth)
                        lineWidth = 0
                    case let .blank(ordinal):
                        lineWidth += ("(\(blankNumberStart + ordinal - 1)) ____" as NSString).size(withAttributes: [.font: font]).width + 18
                    case let .labeledUnderline(_, children):
                        let text = children.compactMap { if case let .text(s, _) = $0 { return s }; return nil }.joined()
                        lineWidth += (text as NSString).size(withAttributes: [.font: font]).width + 4
                    case .slot:
                        lineWidth += 72
                    case .image:
                        lineWidth += 80
                    }
                }
                maxLineWidth = max(maxLineWidth, lineWidth)

            case let .formulaBlock(f):
                if case let .rendered(_, width, _, _) = formulaEngine.render(f, pointSize: pointSize, color: tokens.textUIColor) {
                    maxLineWidth = max(maxLineWidth, width)
                } else {
                    maxLineWidth = max(maxLineWidth, RichInlineBuilder.measureFallbackBadgeWidth(source: f.delimitedSource, pointSize: pointSize))
                }

            default:
                maxLineWidth = max(maxLineWidth, 120)
            }
        }
        return maxLineWidth
    }
}

private struct RichTableCellView: View {
    let cell: RichTableCell
    let tokens: RichContentTokens
    let assets: AttemptAssetSource?
    let mode: RichContentMode
    let slots: [String: () -> AnyView]
    let blankNumberStart: Int
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let cellWidth: CGFloat

    var body: some View {
        let innerWidth = max(cellWidth - 16, 24)
        VStack(alignment: cell.isNumeric ? .trailing : .leading, spacing: 4) {
            ForEach(Array(cell.blocks.enumerated()), id: \.offset) { _, block in
                cellBlockView(block, innerWidth: innerWidth)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: cell.isNumeric ? .topTrailing : .topLeading)
        .background(cell.isHeader ? tokens.tableHeadColor : Color.clear)
        .overlay(
            Rectangle()
                .strokeBorder(tokens.brand.borderStrongColor, lineWidth: 0.5)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(
            cell.isHeader
                ? "richContent.table.headerCell"
                : (cell.isNumeric ? "richContent.table.numCell" : "richContent.table.cell")
        )
    }

    @ViewBuilder
    private func cellBlockView(_ block: RichBlock, innerWidth: CGFloat) -> some View {
        switch block {
        case let .paragraph(inlines):
            let styledInlines: [RichInline] = cell.isHeader
                ? inlines.map { applyBold($0) }
                : inlines
            RichParagraphView(
                inlines: styledInlines,
                baseSize: 15,
                textStyle: .callout,
                textColor: tokens.textUIColor,
                alignment: cell.isNumeric ? .right : .left,
                lineSpacing: 3,
                tabularDigits: cell.isNumeric,
                blankNumberStart: blankNumberStart,
                tokens: tokens,
                assets: assets,
                mode: mode,
                formulaEngine: formulaEngine,
                slots: slots,
                locale: locale,
                availableWidth: innerWidth
            )
        case let .formulaBlock(formula):
            RichFormulaBlockView(
                formula: formula,
                tokens: tokens,
                formulaEngine: formulaEngine,
                availableWidth: innerWidth,
                centered: true
            )
        default:
            RichBlockView(
                block: block,
                tokens: tokens,
                assets: assets,
                mode: mode,
                slots: slots,
                blankNumberStart: blankNumberStart,
                formulaEngine: formulaEngine,
                locale: locale,
                availableWidth: innerWidth
            )
        }
    }

    private func applyBold(_ inline: RichInline) -> RichInline {
        switch inline {
        case let .text(text, style):
            .text(text, style: style.union(.bold))
        case let .labeledUnderline(label, children):
            .labeledUnderline(label: label, children: children.map { applyBold($0) })
        default:
            inline
        }
    }
}

private struct TableSpanGridLayout: Layout {
    let rowCount: Int
    let columnWidths: [CGFloat]
    let placements: [PlacedTableCell]

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        let rowHeights = computeRowHeights(subviews: subviews)
        let totalWidth = columnWidths.reduce(0, +)
        let totalHeight = rowHeights.reduce(0, +)
        return CGSize(width: max(totalWidth, 1), height: max(totalHeight, 1))
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let rowHeights = computeRowHeights(subviews: subviews)
        var colX: [CGFloat] = [0]
        for w in columnWidths {
            colX.append((colX.last ?? 0) + w)
        }
        var rowY: [CGFloat] = [0]
        for h in rowHeights {
            rowY.append((rowY.last ?? 0) + h)
        }

        for (index, subview) in subviews.enumerated() where index < placements.count {
            let p = placements[index]
            let x = bounds.minX + colX[min(p.column, colX.count - 1)]
            let endCol = min(p.column + p.colSpan, colX.count - 1)
            let w = max(colX[endCol] - colX[min(p.column, colX.count - 1)], 1)

            let y = bounds.minY + rowY[min(p.row, rowY.count - 1)]
            let endRow = min(p.row + p.rowSpan, rowY.count - 1)
            let h = max(rowY[endRow] - rowY[min(p.row, rowY.count - 1)], 1)

            subview.place(
                at: CGPoint(x: x, y: y),
                anchor: .topLeading,
                proposal: ProposedViewSize(width: w, height: h)
            )
        }
    }

    private func computeRowHeights(subviews: Subviews) -> [CGFloat] {
        var heights = [CGFloat](repeating: 28, count: max(rowCount, 0))
        for (index, subview) in subviews.enumerated() where index < placements.count {
            let p = placements[index]
            let endCol = min(p.column + p.colSpan, columnWidths.count)
            let cellWidth = columnWidths[p.column..<endCol].reduce(0, +)
            let measured = subview.sizeThatFits(ProposedViewSize(width: max(cellWidth, 1), height: nil)).height
            if p.rowSpan == 1, p.row < heights.count {
                heights[p.row] = max(heights[p.row], ceil(measured))
            }
        }
        for (index, subview) in subviews.enumerated() where index < placements.count {
            let p = placements[index]
            guard p.rowSpan > 1 else { continue }
            let endCol = min(p.column + p.colSpan, columnWidths.count)
            let cellWidth = columnWidths[p.column..<endCol].reduce(0, +)
            let measured = ceil(subview.sizeThatFits(ProposedViewSize(width: max(cellWidth, 1), height: nil)).height)
            let endRow = min(p.row + p.rowSpan, heights.count)
            guard p.row < endRow else { continue }
            let current = heights[p.row..<endRow].reduce(0, +)
            if measured > current {
                let extra = ceil((measured - current) / CGFloat(endRow - p.row))
                for r in p.row..<endRow {
                    heights[r] += extra
                }
            }
        }
        return heights
    }
}
