import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class RichTableTests: XCTestCase {
    private struct SampleFixtureFile: Decodable {
        let samples: [Sample]
        struct Sample: Decodable { let name: String; let html: String }
    }

    private func sampleHTML(_ name: String) throws -> String {
        let data = try Data(contentsOf: FormulaCorpusLoader.richTextSamplesURL)
        let file = try JSONDecoder().decode(SampleFixtureFile.self, from: data)
        return try XCTUnwrap(file.samples.first { $0.name == name }?.html)
    }

    func testDataTableStructureHeadersNumericCellsAndCaptionNotes() throws {
        let doc = RichHTMLParser.parse(try sampleHTML("data-table"))
        guard case let .dataTableFigure(caption, table, notes, sources) = doc.blocks.first else {
            XCTFail("Expected dataTableFigure")
            return
        }

        XCTAssertNotNil(caption)
        XCTAssertEqual(notes.count, 1)
        XCTAssertEqual(sources.count, 1)

        let headerCells = table.rows.flatMap(\.cells).filter { $0.isHeader && $0.scope == .col }
        XCTAssertEqual(headerCells.count, 3, "data-table must have 3 column header cells")

        let numericCells = table.rows.flatMap(\.cells).filter(\.isNumeric)
        XCTAssertEqual(numericCells.count, 4, "data-table must have 4 right-aligned numeric cells")

        let view = RichContentView(document: doc, fixedWidth: 358)
            .padding(16)
            .background(BrandPalette.defaultLight.bgSurfaceColor)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 420),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
        let captionTV = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription.contains("TRỊ GIÁ XUẤT") })
        let noteTV = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription.contains("Đơn vị") })
        let sourceTV = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription.contains("Nguồn") })

        let captionFrame = captionTV.convert(captionTV.bounds, to: window)
        let noteFrame = noteTV.convert(noteTV.bounds, to: window)
        let sourceFrame = sourceTV.convert(sourceTV.bounds, to: window)
        XCTAssertLessThan(captionFrame.maxY, noteFrame.minY, "Caption must appear above table notes")
        XCTAssertLessThanOrEqual(noteFrame.maxY, sourceFrame.minY + 2, "Note must appear above source")

        let numTVs = textViews.filter { $0.customAccessibilityDescription.contains("1096,1") || $0.customAccessibilityDescription.contains("2776,1") }
        XCTAssertEqual(numTVs.count, 2)
        for numTV in numTVs {
            let pStyle = numTV.attributedText.attribute(.paragraphStyle, at: 0, effectiveRange: nil) as? NSParagraphStyle
            XCTAssertEqual(pStyle?.alignment, .right, "Numeric td.num cell must be right-aligned")
        }

        _ = try RecoveryViewTestHelper.captureSnapshot(
            window: window,
            size: CGSize(width: 390, height: 420),
            name: "richcontent-data-table",
            in: self
        )
    }

    func testMergedCellWithMathSpansTwoColumnsAndRendersFormula() throws {
        let doc = RichHTMLParser.parse(try sampleHTML("data-table-merged-cell-with-math"))
        guard case let .dataTableFigure(_, table, _, _) = doc.blocks.first else {
            XCTFail("Expected dataTableFigure")
            return
        }

        let grid = ResolvedTableGrid.resolve(table)
        XCTAssertEqual(grid.columnCount, 2)
        let merged = try XCTUnwrap(grid.cells.first { $0.cell.colspan == 2 })
        XCTAssertEqual(merged.column, 0)
        XCTAssertEqual(merged.colSpan, 2)

        let view = RichContentView(document: doc, fixedWidth: 358)
            .padding(16)
            .background(BrandPalette.defaultLight.bgSurfaceColor)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 260),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
        let headerTV = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription.contains("Giá trị") })
        let mathTV = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription.contains(#"\(x_1\)"#) })

        let headerFrame = headerTV.convert(headerTV.bounds, to: window)
        let mathFrame = mathTV.convert(mathTV.bounds, to: window)
        XCTAssertGreaterThan(headerFrame.width, mathFrame.width * 1.4, "Colspan=2 header cell must span both columns")

        var foundFormulaAttachment = false
        mathTV.attributedText.enumerateAttribute(
            .attachment,
            in: NSRange(location: 0, length: mathTV.attributedText.length)
        ) { value, _, _ in
            if value is FormulaTextAttachment {
                foundFormulaAttachment = true
            }
        }
        XCTAssertTrue(foundFormulaAttachment, "Formula inside merged table cell must render via FormulaTextAttachment")
    }

    func testEightColumnSevenDigitTableScrollsHorizontallyInOwnFrameAt390pt() throws {
        let headers = (1...8).map { #"<th scope="col">Cột \#($0)</th>"# }.joined()
        let cells = (1...8).map { i in #"<td class="num">\#(1_000_000 + i * 111_111)</td>"# }.joined()
        let html = """
        <p>Đề bài phía trên bảng rộng</p>
        <figure class="data-table">
          <table><thead><tr>\(headers)</tr></thead><tbody><tr>\(cells)</tr></tbody></table>
        </figure>
        <p>Phương án phía dưới bảng rộng</p>
        """
        let doc = RichHTMLParser.parse(html)
        let view = ScrollView {
            RichContentView(document: doc, fixedWidth: 358)
                .padding(16)
        }
        .background(BrandPalette.defaultLight.bgSurfaceColor)
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 480),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let allScrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
            .filter { !($0 is UITextView) }

        let horizontalTableScroll = allScrollViews.first { $0.contentSize.width > $0.bounds.width + 20 }
        XCTAssertNotNil(horizontalTableScroll, "8-column 7-digit table must scroll horizontally inside its own ScrollView")
        if let tableScroll = horizontalTableScroll {
            XCTAssertLessThanOrEqual(tableScroll.bounds.width, 358, "Table scroll viewport must fit inside column width")
        }

        let outerVerticalScroll = allScrollViews.first { $0 !== horizontalTableScroll }
        if let outer = outerVerticalScroll {
            XCTAssertLessThanOrEqual(outer.contentSize.width, outer.bounds.width + 1, "Outer page must NOT scroll horizontally")
        }

        let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
        let above = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription.contains("Đề bài phía trên") })
        let below = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription.contains("Phương án phía dưới") })
        XCTAssertLessThanOrEqual(above.bounds.width, 358)
        XCTAssertLessThanOrEqual(below.bounds.width, 358)

        _ = try RecoveryViewTestHelper.captureSnapshot(
            window: window,
            size: CGSize(width: 390, height: 480),
            name: "richcontent-wide-table-8col-390pt",
            in: self
        )
    }

    func testPlainTableCellRendersInlineImageAndScrollsWideInlineFormula() async throws {
        let assetId = "9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f"
        let png = UIGraphicsImageRenderer(size: CGSize(width: 120, height: 80)).image { ctx in
            UIColor.systemTeal.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 120, height: 80))
        }.pngData()!

        RecordingAssetURLProtocol.reset()
        defer { RecordingAssetURLProtocol.reset() }
        RecordingAssetURLProtocol.handler = { req in
            let resp = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png"]
            )!
            return (resp, png)
        }

        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "tbl-token",
                refreshToken: "tbl-refresh",
                accessExpiresIn: 3600,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "student1",
                accountId: "acc-tbl"
            )
        )
        let refresher = TokenRefresher(
            tokenStore: tokenStore,
            refreshExecutor: { _ in throw MobileApiError.unexpectedResponse(statusCode: 401) }
        )
        let source = AttemptAssetSource(
            attemptId: "44444444-4444-4444-8444-444444444444",
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            refresher: refresher,
            session: AttemptAssetSource.makeNoDiskCacheSession(protocolClasses: [RecordingAssetURLProtocol.self])
        )
        defer { source.purgeAll() }
        _ = try await source.load(.attempt(assetId: assetId))

        let wideTerms = (1...25).map { "x_{\($0)}" }.joined(separator: " + ")
        let html = """
        <table>
          <tbody>
            <tr>
              <td><img src="" alt="Ảnh trong ô bảng" data-asset-id="\(assetId)"></td>
              <td>Biểu thức rộng \\(\(wideTerms) = S\\)</td>
            </tr>
          </tbody>
        </table>
        """
        let doc = RichHTMLParser.parse(html)
        let view = RichContentView(document: doc, assets: source, fixedWidth: 358)
            .padding(16)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 320),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let tableImages = RecoveryViewTestHelper.findViews(ofType: UIImageView.self, in: controller.view)
            .filter { $0.accessibilityLabel == "Ảnh trong ô bảng" }
        XCTAssertFalse(tableImages.isEmpty, "Legacy plain <table> cell must mount UIImageView for inline <img>")

        let formulaScrolls = RecoveryViewTestHelper.findViews(ofType: HorizontalFormulaScrollView.self, in: controller.view)
        let wideCellFormulaScroll = try XCTUnwrap(
            formulaScrolls.first,
            "Wide inline formula inside <td> must segment into HorizontalFormulaScrollView instead of clipping"
        )
        XCTAssertTrue(wideCellFormulaScroll.isScrollEnabled)
        XCTAssertGreaterThan(wideCellFormulaScroll.contentSize.width, wideCellFormulaScroll.bounds.width + 10)
    }
}
