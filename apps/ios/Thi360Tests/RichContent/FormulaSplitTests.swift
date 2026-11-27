import XCTest
@testable import Thi360

final class FormulaSplitTests: XCTestCase {
    func testSplitsRawInlineAndBlockDelimiters() {
        let html = #"<p>Cho \(x^2 + 1\) và \[\int_0^1 f(x)\,dx\]</p>"#
        let doc = RichHTMLParser.parse(html)

        XCTAssertEqual(doc.blocks.count, 2)
        if case let .paragraph(inlines) = doc.blocks[0] {
            XCTAssertEqual(inlines, [
                .text("Cho ", style: []),
                .formula(Formula(source: "x^2 + 1", display: .inline, notation: .tex)),
                .text(" và ", style: [])
            ])
        } else {
            XCTFail("Expected paragraph with inline formula first")
        }

        if case let .formulaBlock(blockFormula) = doc.blocks[1] {
            XCTAssertEqual(blockFormula.source, #"\int_0^1 f(x)\,dx"#)
            XCTAssertEqual(blockFormula.display, .block)
            XCTAssertEqual(blockFormula.notation, .tex)
        } else {
            XCTFail("Expected formulaBlock second")
        }
    }

    func testUnmatchedDelimiterStaysPlainTextWithoutFormula() {
        let html = #"<p>Giá trị \(x + 1 là</p>"#
        let doc = RichHTMLParser.parse(html)

        XCTAssertEqual(doc.blocks.count, 1)
        if case let .paragraph(inlines) = doc.blocks[0] {
            XCTAssertEqual(inlines, [.text(#"Giá trị \(x + 1 là"#, style: [])])
            XCTAssertFalse(inlines.contains { if case .formula = $0 { return true }; return false })
        } else {
            XCTFail("Expected single plain text paragraph")
        }
    }

    func testSpanWithAndWithoutDelimitersAndMhchemNotation() {
        let html = #"<p><span class="math-inline">\ce{H2O}</span> and <span class="math-inline">\(\pu{123 kJ/mol}\)</span></p><span class="math-block">E = mc^2</span>"#
        let doc = RichHTMLParser.parse(html)

        XCTAssertEqual(doc.blocks.count, 2)
        if case let .paragraph(inlines) = doc.blocks[0] {
            let formulas = inlines.compactMap { inline -> Formula? in
                if case let .formula(f) = inline { return f }
                return nil
            }
            XCTAssertEqual(formulas.count, 2)
            XCTAssertEqual(formulas[0], Formula(source: #"\ce{H2O}"#, display: .inline, notation: .ce))
            XCTAssertEqual(formulas[1], Formula(source: #"\pu{123 kJ/mol}"#, display: .inline, notation: .pu))
        } else {
            XCTFail("Expected paragraph first")
        }

        if case let .formulaBlock(block) = doc.blocks[1] {
            XCTAssertEqual(block, Formula(source: "E = mc^2", display: .block, notation: .tex))
        } else {
            XCTFail("Expected block formula second")
        }
    }
}
