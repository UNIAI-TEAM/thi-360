import Foundation
import XCTest
@testable import Thi360

final class RichHTMLParserTests: XCTestCase {
    private struct SampleFixtureFile: Decodable {
        let assetId: String
        let samples: [SampleFixture]
    }

    private struct SampleFixture: Decodable {
        let name: String
        let valid: Bool
        let allowImages: Bool
        let html: String
        let violations: [ExpectedViolation]?
    }

    private struct ExpectedViolation: Decodable, Hashable {
        let tag: String
        let attribute: String?
    }

    private func loadSamples() throws -> SampleFixtureFile {
        let data = try Data(contentsOf: FormulaCorpusLoader.richTextSamplesURL)
        return try JSONDecoder().decode(SampleFixtureFile.self, from: data)
    }

    func testValidSamplesKeepEverything() throws {
        let file = try loadSamples()
        let validSamples = file.samples.filter(\.valid)
        XCTAssertEqual(validSamples.count, 12, "Expected 12 valid samples in rich-text-samples.json")

        for sample in validSamples {
            let doc = RichHTMLParser.parse(
                sample.html,
                options: RichParseOptions(allowImages: sample.allowImages)
            )
            XCTAssertTrue(
                doc.violations.isEmpty,
                "Valid sample '\(sample.name)' unexpectedly had violations: \(doc.violations)"
            )
            XCTAssertFalse(doc.blocks.isEmpty, "Valid sample '\(sample.name)' must produce non-empty blocks")
        }

        let byName = Dictionary(uniqueKeysWithValues: validSamples.map { ($0.name, $0) })

        let mathDoc = RichHTMLParser.parse(try XCTUnwrap(byName["math-inline-and-block"]).html)
        XCTAssertEqual(mathDoc.blocks.count, 2)
        if case let .paragraph(inlines) = mathDoc.blocks[0] {
            XCTAssertTrue(inlines.contains { if case let .formula(f) = $0 { return f.display == .inline }; return false })
        } else {
            XCTFail("Expected paragraph block first")
        }
        if case let .formulaBlock(formula) = mathDoc.blocks[1] {
            XCTAssertEqual(formula.source, "x^2 + y^2 = z^2")
            XCTAssertEqual(formula.display, .block)
        } else {
            XCTFail("Expected formulaBlock second")
        }

        let chemDoc = RichHTMLParser.parse(try XCTUnwrap(byName["chemistry-mhchem"]).html)
        if case let .paragraph(inlines) = chemDoc.blocks.first {
            let formulas = inlines.compactMap { inline -> Formula? in
                if case let .formula(f) = inline { return f }
                return nil
            }
            XCTAssertEqual(formulas.count, 2)
            XCTAssertEqual(formulas[0].notation, .ce)
            XCTAssertTrue(formulas[0].source.contains("<=>"))
        } else {
            XCTFail("Expected chemistry paragraph")
        }

        let tableDoc = RichHTMLParser.parse(try XCTUnwrap(byName["data-table"]).html)
        if case let .dataTableFigure(caption, table, notes, sources) = tableDoc.blocks.first {
            XCTAssertNotNil(caption)
            XCTAssertEqual(table.rows.count, 3)
            XCTAssertEqual(notes.count, 1)
            XCTAssertEqual(sources.count, 1)
        } else {
            XCTFail("Expected dataTableFigure")
        }

        let imageDoc = RichHTMLParser.parse(try XCTUnwrap(byName["image-figure"]).html)
        if case let .imageFigure(image, width, zoomable, caption, sources) = imageDoc.blocks.first {
            XCTAssertEqual(image.ref, .attempt(assetId: file.assetId))
            XCTAssertEqual(width, .medium)
            XCTAssertTrue(zoomable)
            XCTAssertNotNil(caption)
            XCTAssertEqual(sources.count, 1)
        } else {
            XCTFail("Expected imageFigure")
        }

        let poemDoc = RichHTMLParser.parse(try XCTUnwrap(byName["quote-poem"]).html)
        if case let .quote(quote) = poemDoc.blocks.first {
            XCTAssertEqual(quote.kind, .poem)
            XCTAssertEqual(quote.lineNumberStep, 5)
            XCTAssertEqual(quote.paragraphs.count, 2)
            XCTAssertNotNil(quote.footer)
        } else {
            XCTFail("Expected poem quote")
        }
    }

    func testInvalidSamplesDropListedViolations() throws {
        let file = try loadSamples()
        let invalidSamples = file.samples.filter { !$0.valid }
        XCTAssertEqual(invalidSamples.count, 31, "Expected 31 invalid samples in rich-text-samples.json")

        for sample in invalidSamples {
            let doc = RichHTMLParser.parse(
                sample.html,
                options: RichParseOptions(allowImages: sample.allowImages)
            )
            let expected = (sample.violations ?? []).map { ExpectedViolation(tag: $0.tag, attribute: $0.attribute) }
            let actual = doc.violations.map { ExpectedViolation(tag: $0.tag, attribute: $0.attribute) }
            XCTAssertEqual(
                countedSet(actual),
                countedSet(expected),
                "Invalid sample '\(sample.name)' violation mismatch. Actual=\(actual), Expected=\(expected)"
            )
        }

        let badBlank = try XCTUnwrap(invalidSamples.first { $0.name == "bad-blank-with-text" })
        let badBlankDoc = RichHTMLParser.parse(badBlank.html)
        XCTAssertTrue(badBlankDoc.blocks.isEmpty, "bad-blank-with-text must drop the chip and the text 'text'")
        XCTAssertFalse(extractAllText(from: badBlankDoc).contains("text"))

        let badFig = RichHTMLParser.parse(#"<p>before</p><figure class="gallery"><figcaption>drop</figcaption></figure><p>after</p>"#)
        XCTAssertEqual(extractAllText(from: badFig), "beforeafter")
        XCTAssertFalse(extractAllText(from: badFig).contains("drop"))

        let badQuote = RichHTMLParser.parse(#"<p>keep</p><blockquote class="quote" data-kind="prose" data-line-numbers="5"><p>dropped</p></blockquote>"#)
        XCTAssertEqual(extractAllText(from: badQuote), "keep")
        XCTAssertFalse(extractAllText(from: badQuote).contains("dropped"))
    }

    func testDropsScriptStyleAndEvents() {
        let html = #"<p>a <font>b</font> c</p><script>x()</script><p style="color:red" onclick="x()">d</p>"#
        let doc = RichHTMLParser.parse(html)

        XCTAssertEqual(doc.blocks.count, 2)
        if case let .paragraph(firstInlines) = doc.blocks[0] {
            XCTAssertEqual(plainText(firstInlines), "a b c")
        } else {
            XCTFail("Expected first paragraph")
        }
        if case let .paragraph(secondInlines) = doc.blocks[1] {
            XCTAssertEqual(plainText(secondInlines), "d")
        } else {
            XCTFail("Expected second paragraph")
        }

        let allText = extractAllText(from: doc)
        XCTAssertFalse(allText.contains("x()"))
        XCTAssertTrue(doc.violations.contains { $0.tag == "script" })
        XCTAssertTrue(doc.violations.contains { $0.tag == "p" && $0.attribute == "style" })
        XCTAssertTrue(doc.violations.contains { $0.tag == "p" && $0.attribute == "onclick" })
    }

    func testDeeplyNested200LevelHtmlDoesNotCrash() {
        let open = String(repeating: "<div>", count: 200)
        let close = String(repeating: "</div>", count: 200)
        let html = "\(open)<p>deep-ok</p>\(close)"
        let doc = RichHTMLParser.parse(html)
        XCTAssertEqual(extractAllText(from: doc), "deep-ok")
    }

    func testSlotMarkersExtractedWhenEnabledOnly() {
        let html = "<p>Điền [[b1]], [[ b2 ]], [[ô_1]], [[blank:1]], [[câu 1]] và [[   ]]</p>"
        let withoutSlots = RichHTMLParser.parse(html, options: RichParseOptions(slotMarkers: false))
        XCTAssertEqual(extractAllText(from: withoutSlots), "Điền [[b1]], [[ b2 ]], [[ô_1]], [[blank:1]], [[câu 1]] và [[   ]]")

        let withSlots = RichHTMLParser.parse(html, options: RichParseOptions(slotMarkers: true))
        if case let .paragraph(inlines) = withSlots.blocks.first {
            XCTAssertEqual(inlines, [
                .text("Điền ", style: []),
                .slot(id: "b1"),
                .text(", ", style: []),
                .slot(id: "b2"),
                .text(", ", style: []),
                .slot(id: "ô_1"),
                .text(", ", style: []),
                .slot(id: "blank:1"),
                .text(", ", style: []),
                .slot(id: "câu 1"),
                .text(" và [[   ]]", style: [])
            ])
        } else {
            XCTFail("Expected paragraph with slots")
        }
    }

    private func countedSet<T: Hashable>(_ items: [T]) -> [T: Int] {
        var map: [T: Int] = [:]
        for item in items { map[item, default: 0] += 1 }
        return map
    }

    private func extractAllText(from doc: RichDocument) -> String {
        doc.blocks.map(extractText(from:)).joined()
    }

    private func extractText(from block: RichBlock) -> String {
        switch block {
        case let .paragraph(inlines):
            return plainText(inlines)
        case let .formulaBlock(f):
            return f.delimitedSource
        case let .list(_, items):
            return items.flatMap(\.blocks).map(extractText(from:)).joined()
        case let .table(table), let .crossFigure(table):
            return table.rows.flatMap(\.cells).flatMap(\.blocks).map(extractText(from:)).joined()
        case let .dataTableFigure(caption, table, notes, sources):
            return (caption.map(plainText) ?? "")
                + table.rows.flatMap(\.cells).flatMap(\.blocks).map(extractText(from:)).joined()
                + notes.map(plainText).joined()
                + sources.map(plainText).joined()
        case let .imageFigure(img, _, _, caption, sources):
            return img.alt + (caption.map(plainText) ?? "") + sources.map(plainText).joined()
        case let .quote(quote):
            return quote.paragraphs.map(plainText).joined()
                + (quote.footer.map(plainText) ?? "")
                + (quote.source.map(plainText) ?? "")
        }
    }

    private func plainText(_ inlines: [RichInline]) -> String {
        inlines.map { inline -> String in
            switch inline {
            case let .text(s, _): s
            case .lineBreak: "\n"
            case let .formula(f): f.delimitedSource
            case let .labeledUnderline(_, children): plainText(children)
            case let .blank(ordinal): "(\(ordinal)) ____"
            case let .image(img): img.alt
            case let .slot(id): "[[\(id)]]"
            }
        }.joined()
    }
}
