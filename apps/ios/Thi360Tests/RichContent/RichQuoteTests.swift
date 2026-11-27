import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class RichQuoteTests: XCTestCase {
    func testPoemTwoStanzasLineNumberFiveOnly() throws {
        let html = """
        <blockquote class="quote" data-kind="poem" data-line-numbers="5">
          <p>Dòng một của khổ đầu<br>Dòng hai của khổ đầu<br>Dòng ba của khổ đầu<br>Dòng bốn của khổ đầu</p>
          <p>Dòng năm đầu khổ sau<br>Dòng sáu của khổ sau<br>Dòng bảy của khổ sau<br>Dòng tám của khổ sau</p>
        </blockquote>
        """
        let doc = RichHTMLParser.parse(html)
        guard case let .quote(quote) = doc.blocks.first else {
            XCTFail("Expected quote block")
            return
        }

        let stanzas = RichQuoteView.numberedPoemStanzas(paragraphs: quote.paragraphs, step: quote.lineNumberStep)
        XCTAssertEqual(stanzas.count, 2)
        XCTAssertEqual(stanzas[0].count, 4)
        XCTAssertEqual(stanzas[1].count, 4)

        let allLines = stanzas.flatMap { $0 }
        let numbered = allLines.filter { $0.displayedNumber != nil }
        XCTAssertEqual(numbered.count, 1, "Only line 5 should have a line number")
        XCTAssertEqual(numbered[0].number, 5)
        XCTAssertEqual(numbered[0].displayedNumber, "5")
        XCTAssertEqual(stanzas[1][0].displayedNumber, "5", "Line 5 must be the first line of the second stanza")

        let view = RichContentView(document: doc, fixedWidth: 358)
            .padding(16)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, _) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 420),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }
        _ = try RecoveryViewTestHelper.captureSnapshot(
            window: window,
            size: CGSize(width: 390, height: 420),
            name: "richcontent-quote-poem-line5",
            in: self
        )
    }

    func testPoemLineNumbersZeroHasNoNumberColumn() {
        let html = """
        <blockquote class="quote" data-kind="poem" data-line-numbers="0">
          <p>Dòng một<br>Dòng hai<br>Dòng ba<br>Dòng bốn<br>Dòng năm</p>
        </blockquote>
        """
        let doc = RichHTMLParser.parse(html)
        guard case let .quote(quote) = doc.blocks.first else {
            XCTFail("Expected quote block")
            return
        }
        XCTAssertEqual(quote.lineNumberStep, 0)
        let stanzas = RichQuoteView.numberedPoemStanzas(paragraphs: quote.paragraphs, step: quote.lineNumberStep)
        XCTAssertTrue(stanzas.flatMap { $0 }.allSatisfy { $0.displayedNumber == nil })

        let view = RichContentView(document: doc, fixedWidth: 358)
            .padding(16)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 260),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let lineNumLabels = RecoveryViewTestHelper.findViews(ofType: UILabel.self, in: controller.view)
            .filter { ($0.accessibilityIdentifier ?? "").hasPrefix("richContent.quote.lineNumber.") }
        XCTAssertTrue(lineNumLabels.isEmpty, "data-line-numbers='0' must not render a line-number gutter")
    }

    func testQuoteFooterThenSourceAlignmentAndOrder() throws {
        let html = #"<blockquote class="quote" data-kind="prose" data-line-numbers="0"><p>body</p><footer>author</footer><p class="figure-source">source</p></blockquote>"#
        let doc = RichHTMLParser.parse(html)

        let view = RichContentView(document: doc, fixedWidth: 358)
            .padding(16)
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
        let bodyTV = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription == "body" })
        let footerTV = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription == "author" })
        let sourceTV = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription == "source" })

        let footerStyle = footerTV.attributedText.attribute(.paragraphStyle, at: 0, effectiveRange: nil) as? NSParagraphStyle
        XCTAssertEqual(footerStyle?.alignment, .right, "Quote footer must be right-aligned")

        let bodyFont = try XCTUnwrap(bodyTV.attributedText.attribute(.font, at: 0, effectiveRange: nil) as? UIFont)
        let sourceFont = try XCTUnwrap(sourceTV.attributedText.attribute(.font, at: 0, effectiveRange: nil) as? UIFont)
        XCTAssertLessThan(sourceFont.pointSize, bodyFont.pointSize, "Source line must use smaller font than body")

        let bodyFrame = bodyTV.convert(bodyTV.bounds, to: window)
        let footerFrame = footerTV.convert(footerTV.bounds, to: window)
        let sourceFrame = sourceTV.convert(sourceTV.bounds, to: window)
        XCTAssertLessThan(bodyFrame.maxY, footerFrame.minY + 1)
        XCTAssertLessThan(footerFrame.maxY, sourceFrame.minY + 1)
    }
}
