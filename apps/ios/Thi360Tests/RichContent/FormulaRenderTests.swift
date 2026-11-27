import UIKit
import XCTest
@testable import Thi360

final class FormulaRenderTests: XCTestCase {
    private let engine = SwaTexFormulaEngine(timeoutSeconds: 1.5, displayScale: 2.0, useCache: false)

    func testMathInlineAndBlockAndChemistryMhchemAndVietnameseText() {
        let inlineLimit = Formula(source: #"\lim_{x \to 1} \frac{2x - \sqrt{3x + 1}}{x^2 - 1}"#, display: .inline)
        let blockPythagoras = Formula(source: #"x^2 + y^2 = z^2"#, display: .block)
        let mhchem1 = Formula(source: #"\ce{CaCO3(s) <=> CaO(s) + CO2(g)}"#, display: .inline)
        let mhchem2 = Formula(source: #"\Delta_r H^\circ_{298} = 179,2\ \mathrm{kJ}"#, display: .inline)
        let vietnamese = Formula(source: #"\text{Vận tốc } v = \frac{s}{t}"#, display: .inline)

        for formula in [inlineLimit, blockPythagoras, mhchem1, mhchem2, vietnamese] {
            let result = engine.render(formula, pointSize: 17, color: .black)
            switch result {
            case let .rendered(image, width, ascent, descent):
                XCTAssertGreaterThan(width, 4, "Formula \(formula.source) must render with positive width")
                XCTAssertGreaterThan(ascent + descent, 4, "Formula \(formula.source) must render with positive height")
                XCTAssertNotNil(image.cgImage)
            case let .unsupported(source):
                XCTFail("Expected SwaTex to render \(formula.source), got unsupported(\(source))")
            }
        }
    }

    func testBrokenSourcesReturnUnsupportedWithExactDelimitedSource() {
        let brokenInline = Formula(source: #"\broken{syntax}"#, display: .inline)
        if case let .unsupported(source) = engine.render(brokenInline, pointSize: 17, color: .black) {
            XCTAssertEqual(source, #"\(\broken{syntax}\)"#)
        } else {
            XCTFail("Expected broken inline LaTeX to return .unsupported")
        }

        let brokenBlock = Formula(source: #"\frac{1}{"#, display: .block)
        if case let .unsupported(source) = engine.render(brokenBlock, pointSize: 17, color: .black) {
            XCTAssertEqual(source, #"\[\frac{1}{\]"#)
        } else {
            XCTFail("Expected broken block LaTeX to return .unsupported")
        }
    }

    func testFiveThousandCharFormulaDoesNotCrash() {
        let terms = (1...1000).map { "x_{\($0)}" }.joined(separator: "+")
        XCTAssertGreaterThan(terms.count, 5000)
        let huge = Formula(source: terms, display: .block)
        let result = engine.render(huge, pointSize: 17, color: .black)
        switch result {
        case let .rendered(_, width, ascent, descent):
            XCTAssertGreaterThan(width, 0)
            XCTAssertGreaterThan(ascent + descent, 0)
        case let .unsupported(source):
            XCTAssertEqual(source, huge.delimitedSource)
        }
    }
}
