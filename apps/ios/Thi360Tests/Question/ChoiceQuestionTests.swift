import Foundation
import XCTest
@testable import Thi360

final class ChoiceQuestionTests: XCTestCase {
    private let singleItem = QuestionItem.singleChoice(
        id: "sc-1",
        displayNo: 1,
        points: 1,
        stem: "<p>Chọn một</p>",
        optionOrder: ["o3", "o1", "o4", "o2"],
        options: [
            ChoiceOption(id: "o1", content: "<p>1</p>", correct: true),
            ChoiceOption(id: "o2", content: "<p>2</p>"),
            ChoiceOption(id: "o3", content: "<p>3</p>"),
            ChoiceOption(id: "o4", content: "<p>4</p>")
        ]
    )

    private let multiItem = QuestionItem.multipleChoice(
        id: "mc-1",
        displayNo: 2,
        points: 1,
        stem: "<p>Chọn nhiều</p>",
        optionOrder: ["m1", "m2", "m3", "m4"],
        options: [
            ChoiceOption(id: "m1", content: "<p>A</p>", correct: true),
            ChoiceOption(id: "m2", content: "<p>B</p>"),
            ChoiceOption(id: "m3", content: "<p>C</p>", correct: true),
            ChoiceOption(id: "m4", content: "<p>D</p>")
        ],
        scoringRule: .allOrNothing
    )

    func testLetterMapsToServerId() {
        guard case let .singleChoice(_, _, _, _, optionOrder, options) = singleItem else {
            return XCTFail("Expected single choice")
        }
        let ordered = OptionOrder.order(options, by: optionOrder)
        XCTAssertEqual(ordered.map(\.id), ["o3", "o1", "o4", "o2"])
        XCTAssertEqual(OptionOrder.optionLetter(at: 0), "A")
        let emitted = AnswerValues.singleChoice(item: singleItem, optionId: ordered[0].id)
        XCTAssertEqual(emitted.singleChoiceOptionId, "o3")
    }

    func testSingleSelectSwitchAndDeselectWireJSON() throws {
        let pickB = AnswerValues.singleChoice(item: singleItem, optionId: "o1")
        XCTAssertEqual(pickB.singleChoiceOptionId, "o1")

        let switchToC = AnswerValues.singleChoice(item: singleItem, optionId: "o4")
        XCTAssertEqual(switchToC.singleChoiceOptionId, "o4")

        let cleared = AnswerValues.singleChoice(item: singleItem, optionId: nil)
        XCTAssertNil(cleared.singleChoiceOptionId)

        let data = try JSONEncoder().encode(cleared)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(json["type"] as? String, "SINGLE_CHOICE")
        XCTAssertNil(json["optionId"])
    }

    func testSingleChoiceRejectsUnknownOptionId() {
        let invalid = AnswerValues.singleChoice(item: singleItem, optionId: "nonexistent")
        XCTAssertNil(invalid.singleChoiceOptionId)
    }

    func testMultipleSelectToggleOffAndEmptyArrayWireJSON() throws {
        let two = AnswerValues.multipleChoice(item: multiItem, optionIds: ["m1", "m3", "m1", "unknown"])
        XCTAssertEqual(two.multipleChoiceOptionIds, ["m1", "m3"])

        let one = AnswerValues.multipleChoice(item: multiItem, optionIds: ["m3"])
        XCTAssertEqual(one.multipleChoiceOptionIds, ["m3"])

        let empty = AnswerValues.multipleChoice(item: multiItem, optionIds: [])
        XCTAssertEqual(empty.multipleChoiceOptionIds, [])

        let data = try JSONEncoder().encode(empty)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(json["type"] as? String, "MULTIPLE_CHOICE")
        XCTAssertEqual(json["optionIds"] as? [String], [])
    }
}

final class AnswerValueJSONTests: XCTestCase {
    func testAllSevenAnswerValueTypesEncodeAndDecodeExactWireShapes() throws {
        let singleItem = QuestionItem.singleChoice(
            id: "j-sc",
            displayNo: 1,
            points: 1,
            stem: nil,
            optionOrder: nil,
            options: [ChoiceOption(id: "s1", content: "1")]
        )
        let multiItem = QuestionItem.multipleChoice(
            id: "j-mc",
            displayNo: 2,
            points: 1,
            stem: nil,
            optionOrder: nil,
            options: [ChoiceOption(id: "m1", content: "1"), ChoiceOption(id: "m3", content: "3")],
            scoringRule: .partial
        )
        let clozeItem = QuestionItem.cloze(
            id: "j-cl",
            displayNo: 3,
            points: 2,
            stem: nil,
            passage: "[[b1]]",
            blanks: [ClozeBlank(id: "b1")],
            blankNumberStart: 3
        )
        let tfItem = QuestionItem.trueFalse(
            id: "j-tf",
            displayNo: 4,
            points: 1,
            stem: nil,
            statements: [TrueFalseStatement(id: "a", content: "A"), TrueFalseStatement(id: "c", content: "C")],
            scoringMethod: .perStatement
        )
        let matchItem = QuestionItem.matching(
            id: "j-ma",
            displayNo: 5,
            points: 1,
            stem: nil,
            optionOrder: nil,
            left: [MatchingSideItem(id: "l1", content: "L1")],
            right: [MatchingSideItem(id: "r1", content: "R1")],
            reuseRight: false,
            pairs: nil
        )

        let payloads: [(AnswerValue, String)] = [
            (AnswerValues.singleChoice(item: singleItem, optionId: nil), "SINGLE_CHOICE"),
            (AnswerValues.singleChoice(item: singleItem, optionId: "s1"), "SINGLE_CHOICE"),
            (AnswerValues.multipleChoice(item: multiItem, optionIds: []), "MULTIPLE_CHOICE"),
            (AnswerValues.multipleChoice(item: multiItem, optionIds: ["m1", "m3"]), "MULTIPLE_CHOICE"),
            (AnswerValues.shortAnswer(text: "  56 "), "SHORT_ANSWER"),
            (AnswerValues.cloze(item: clozeItem, blanks: ["b1": "100"]), "CLOZE"),
            (AnswerValues.trueFalse(item: tfItem, statements: ["a": true, "c": false]), "TRUE_FALSE"),
            (AnswerValues.matching(item: matchItem, pairs: ["l1": "r1"]), "MATCHING"),
            (AnswerValues.essay(html: "", wordCount: 0), "ESSAY"),
            (AnswerValues.essay(html: "<p>Mở bài</p>", wordCount: 2), "ESSAY")
        ]

        let encoder = JSONEncoder()
        let decoder = JSONDecoder()
        for (value, expectedType) in payloads {
            let encoded = try encoder.encode(value)
            let obj = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
            XCTAssertEqual(obj["type"] as? String, expectedType)
            let decoded = try decoder.decode(AnswerValue.self, from: encoded)
            XCTAssertEqual(decoded, value)
        }
    }
}
