import Foundation
import XCTest
@testable import Thi360

final class AnswerLimitTests: XCTestCase {
    func testDoesNotTruncate70000CharLatinInputInShortAnswerOrEssay() {
        let large = String(repeating: "a", count: 70_000)

        let shortVal = AnswerValues.shortAnswer(text: large)
        XCTAssertEqual(shortVal.shortAnswerText.count, 70_000, "Client must not truncate short answer input")

        let essayHTML = "<p>\(large)</p>"
        let essayVal = AnswerValues.essay(html: essayHTML)
        XCTAssertEqual(essayVal.essayHtml.count, essayHTML.count, "Client must not truncate essay HTML")
        XCTAssertEqual(essayVal.essayWordCount, 1)
    }

    func testStripsNullBytesInShortAnswerClozeAndEssay() {
        let shortVal = AnswerValues.shortAnswer(text: "abc\u{0000}def\u{0000}")
        XCTAssertEqual(shortVal.shortAnswerText, "abcdef")
        XCTAssertFalse(shortVal.shortAnswerText.contains("\u{0000}"))

        let clozeItem = QuestionItem.cloze(
            id: "c-null",
            displayNo: 1,
            points: 1,
            stem: nil,
            passage: "[[b1]]",
            blanks: [ClozeBlank(id: "b1")],
            blankNumberStart: 1
        )
        let clozeVal = AnswerValues.cloze(item: clozeItem, blanks: ["b1": "10\u{0000}0"])
        XCTAssertEqual(clozeVal.clozeBlanks["b1"], "100")

        let essayVal = AnswerValues.essay(html: "<p>Xin\u{0000} chào</p>")
        XCTAssertEqual(essayVal.essayHtml, "<p>Xin chào</p>")
        XCTAssertFalse(essayVal.essayHtml.contains("\u{0000}"))
    }
}
