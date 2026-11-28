import Foundation
import XCTest
@testable import Thi360

final class CompletionTests: XCTestCase {
    func testAllQuestionTypesCompletionAndEssayWordLimit() {
        func makeItem(_ type: Components.Schemas.AttemptItemView.questionTypePayload) -> Components.Schemas.AttemptItemView {
            Components.Schemas.AttemptItemView(itemId: "item-1", questionType: type)
        }

        XCTAssertEqual(Completion.of(item: makeItem(.SINGLE_CHOICE), value: nil), .blank)
        XCTAssertEqual(Completion.of(item: makeItem(.SINGLE_CHOICE), value: .singleChoice(optionId: "  ")), .blank)
        XCTAssertEqual(Completion.of(item: makeItem(.SINGLE_CHOICE), value: .singleChoice(optionId: "a")), .answered)

        XCTAssertEqual(Completion.of(item: makeItem(.MULTIPLE_CHOICE), value: .multipleChoice(optionIds: [])), .blank)
        XCTAssertEqual(Completion.of(item: makeItem(.MULTIPLE_CHOICE), value: .multipleChoice(optionIds: ["a"])), .answered)

        XCTAssertEqual(Completion.of(item: makeItem(.SHORT_ANSWER), value: .shortAnswer(text: "   ")), .blank)
        XCTAssertEqual(Completion.of(item: makeItem(.SHORT_ANSWER), value: .shortAnswer(text: "42")), .answered)

        let clozeBody = AttemptQuestionBodyValue.cloze(
            Components.Schemas.Cloze(
                value1: .init(_type: "CLOZE"),
                value2: .init(blanks: [.init(id: "b1"), .init(id: "b2")], passage: "")
            )
        )
        XCTAssertEqual(Completion.of(item: makeItem(.CLOZE), value: nil, body: clozeBody), .blank)
        XCTAssertEqual(Completion.of(item: makeItem(.CLOZE), value: .cloze(blanks: ["b1": "x", "b2": "  "]), body: clozeBody), .partial)
        XCTAssertEqual(Completion.of(item: makeItem(.CLOZE), value: .cloze(blanks: ["b1": "x", "b2": "y"]), body: clozeBody), .answered)

        let tfBody = AttemptQuestionBodyValue.trueFalse(
            Components.Schemas.TrueFalse(
                value1: .init(_type: "TRUE_FALSE"),
                value2: .init(statements: [.init(content: "s1", id: "s1"), .init(content: "s2", id: "s2")])
            )
        )
        XCTAssertEqual(Completion.of(item: makeItem(.TRUE_FALSE), value: nil, body: tfBody), .blank)
        XCTAssertEqual(Completion.of(item: makeItem(.TRUE_FALSE), value: .trueFalse(statements: ["s1": true]), body: tfBody), .partial)
        XCTAssertEqual(Completion.of(item: makeItem(.TRUE_FALSE), value: .trueFalse(statements: ["s1": true, "s2": false]), body: tfBody), .answered)

        let matchBody = AttemptQuestionBodyValue.matching(
            Components.Schemas.Matching(
                value1: .init(_type: "MATCHING"),
                value2: .init(left: [.init(content: "L1", id: "l1"), .init(content: "L2", id: "l2")], right: [])
            )
        )
        XCTAssertEqual(Completion.of(item: makeItem(.MATCHING), value: nil, body: matchBody), .blank)
        XCTAssertEqual(Completion.of(item: makeItem(.MATCHING), value: .matching(pairs: ["l1": "r1"]), body: matchBody), .partial)
        XCTAssertEqual(Completion.of(item: makeItem(.MATCHING), value: .matching(pairs: ["l1": "r1", "l2": "r2"]), body: matchBody), .answered)

        let essayBody = AttemptQuestionBodyValue.essay(
            Components.Schemas.Essay(
                value1: .init(_type: "ESSAY"),
                value2: .init(wordLimit: 5)
            )
        )
        XCTAssertEqual(Completion.of(item: makeItem(.ESSAY), value: .essay(html: "<p>   </p>"), body: essayBody), .blank)
        XCTAssertEqual(Completion.of(item: makeItem(.ESSAY), value: .essay(html: "<p>Một hai</p>"), body: essayBody), .answered)

        let under = Completion.essayLimit(item: makeItem(.ESSAY), value: .essay(html: "<p>Một hai ba</p>"), body: essayBody)
        XCTAssertTrue(under.isEssay)
        XCTAssertEqual(under.wordCount, 3)
        XCTAssertEqual(under.wordLimit, 5)
        XCTAssertTrue(under.underLimit)

        let met = Completion.essayLimit(item: makeItem(.ESSAY), value: .essay(html: "<p>Một hai ba bốn năm</p>"), body: essayBody)
        XCTAssertFalse(met.underLimit)
    }
}
