import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class FormulaFallbackTests: XCTestCase {
    func testSingleThrowingOrTimedOutFormulaIn20FormulaQuestionFallsBackIndividually() {
        let html = (1...20).map { i in
            #"<p>Câu \#(i): <span class="math-inline">\(x_{\#(i)} + 1\)</span></p>"#
        }.joined()
        let doc = RichHTMLParser.parse(html)
        XCTAssertEqual(doc.blocks.count, 20)

        let faultEngine = FaultInjectingFormulaEngine(
            base: SwaTexFormulaEngine.shared,
            timeoutSeconds: 0.08
        ) { formula in
            if formula.source == "x_{7} + 1" {
                return .throwError
            }
            if formula.source == "x_{13} + 1" {
                return .delay(0.4)
            }
            return .delegate
        }

        var renderedCount = 0
        var fallbackSources: [String] = []
        for block in doc.blocks {
            guard case let .paragraph(inlines) = block else { continue }
            for inline in inlines {
                guard case let .formula(formula) = inline else { continue }
                switch faultEngine.render(formula, pointSize: 17, color: .black) {
                case .rendered:
                    renderedCount += 1
                case let .unsupported(source):
                    fallbackSources.append(source)
                }
            }
        }

        XCTAssertEqual(renderedCount, 18, "All non-faulty formulas must still render")
        XCTAssertEqual(Set(fallbackSources), [#" \(x_{7} + 1\)"#.trimmingCharacters(in: .whitespaces), #"\(x_{13} + 1\)"#])

        let view = ScrollView {
            RichContentView(
                document: doc,
                formulaEngine: faultEngine,
                fixedWidth: 358
            )
            .padding(16)
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
        XCTAssertEqual(textViews.count, 20, "All 20 paragraphs must stay rendered without crashing")
    }

    func testBrokenInlineAndBlockFormulasKeepSurroundingText() {
        let inlineHTML = #"<p>Biết <span class="math-inline">\(\broken{syntax}\)</span> thì</p>"#
        let inlineDoc = RichHTMLParser.parse(inlineHTML)
        let inlineView = RichContentView(document: inlineDoc, fixedWidth: 358)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (w1, c1) = RecoveryViewTestHelper.host(
            inlineView,
            size: CGSize(width: 390, height: 200),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { w1.isHidden = true }
        let tv1 = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: c1.view)
        XCTAssertEqual(tv1.count, 1, "Broken inline formula must remain on the same paragraph as 'Biết' and 'thì'")
        XCTAssertTrue(tv1[0].customAccessibilityDescription.contains("Biết"))
        XCTAssertTrue(tv1[0].customAccessibilityDescription.contains(#"\(\broken{syntax}\)"#))
        XCTAssertTrue(tv1[0].customAccessibilityDescription.contains("thì"))

        let blockHTML = #"<p>Đoạn trước</p><span class="math-block">\[\frac{1}{\]</span><p>Đoạn sau</p>"#
        let blockDoc = RichHTMLParser.parse(blockHTML)
        XCTAssertEqual(blockDoc.blocks.count, 3)
        let blockView = RichContentView(document: blockDoc, fixedWidth: 358)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (w2, c2) = RecoveryViewTestHelper.host(
            blockView,
            size: CGSize(width: 390, height: 300),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { w2.isHidden = true }
        let tv2 = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: c2.view)
        let texts = tv2.map(\.customAccessibilityDescription).joined(separator: " ")
        XCTAssertTrue(texts.contains("Đoạn trước"))
        XCTAssertTrue(texts.contains("Đoạn sau"))
    }

    func testFiveThousandCharFormulaDoesNotCrashAndRespectsTimeout() {
        let longBody = String(repeating: "x + ", count: 1250) + "1"
        XCTAssertGreaterThanOrEqual(longBody.count, 5000)
        let formula = Formula(source: longBody, display: .block)

        let engine = SwaTexFormulaEngine(
            timeoutSeconds: SwaTexFormulaEngine.defaultTimeoutSeconds,
            displayScale: 2.0,
            useCache: false
        )
        let start = CFAbsoluteTimeGetCurrent()
        let result = engine.render(formula, pointSize: 17, color: .black)
        let elapsed = CFAbsoluteTimeGetCurrent() - start

        XCTAssertLessThan(elapsed, SwaTexFormulaEngine.defaultTimeoutSeconds + 1.0)
        switch result {
        case let .rendered(image, width, ascent, descent):
            XCTAssertGreaterThan(width, 0)
            XCTAssertGreaterThan(ascent + descent, 0)
            XCTAssertGreaterThan(image.size.width, 0)
        case let .unsupported(source):
            XCTAssertEqual(source, formula.delimitedSource)
        }
    }
}
