import XCTest
@testable import Thi360

final class OptionOrderTests: XCTestCase {
    func testLetterMapsToServerIdFromOptionOrder() throws {
        let sample = try QuestionTestFixtures.sample(key: "single")
        guard case let .singleChoice(_, _, _, _, optionOrder, options) = sample.item else {
            return XCTFail("Expected single choice sample")
        }
        XCTAssertEqual(optionOrder, ["s3", "s1", "s4", "s2"])

        let ordered = OptionOrder.order(options, by: optionOrder)
        XCTAssertEqual(ordered.map(\.id), ["s3", "s1", "s4", "s2"])

        XCTAssertEqual(OptionOrder.optionLetter(at: 0), "A")
        XCTAssertEqual(OptionOrder.optionLetter(at: 1), "B")
        XCTAssertEqual(OptionOrder.optionLetter(at: 2), "C")
        XCTAssertEqual(OptionOrder.optionLetter(at: 3), "D")

        let tappedA = AnswerValues.singleChoice(item: sample.item, optionId: ordered[0].id)
        XCTAssertEqual(tappedA.singleChoiceOptionId, "s3")

        let tappedB = AnswerValues.singleChoice(item: sample.item, optionId: ordered[1].id)
        XCTAssertEqual(tappedB.singleChoiceOptionId, "s1")
    }

    func testPartialOptionOrderKeepsUnlistedAtTailInOriginalOrder() {
        let options = [
            ChoiceOption(id: "o1", content: "1"),
            ChoiceOption(id: "o2", content: "2"),
            ChoiceOption(id: "o3", content: "3"),
            ChoiceOption(id: "o4", content: "4")
        ]
        let ordered = OptionOrder.order(options, by: ["o3", "o1"])
        XCTAssertEqual(ordered.map(\.id), ["o3", "o1", "o2", "o4"])
    }

    func testEmptyOrNilOptionOrderPreservesServerOrderAndIsStableAcrossRebuilds() {
        let options = [
            ChoiceOption(id: "x", content: "X"),
            ChoiceOption(id: "y", content: "Y"),
            ChoiceOption(id: "z", content: "Z")
        ]
        let first = OptionOrder.order(options, by: nil)
        let second = OptionOrder.order(options, by: [])
        XCTAssertEqual(first.map(\.id), ["x", "y", "z"])
        XCTAssertEqual(second.map(\.id), ["x", "y", "z"])

        let shuffledFirst = OptionOrder.order(options, by: ["z", "x", "y"])
        let shuffledSecond = OptionOrder.order(options, by: ["z", "x", "y"])
        XCTAssertEqual(shuffledFirst, shuffledSecond)
    }

    func testDuplicateAndUnknownIdsInOptionOrderIgnoredGracefully() {
        let options = [
            ChoiceOption(id: "a", content: "A"),
            ChoiceOption(id: "b", content: "B"),
            ChoiceOption(id: "c", content: "C")
        ]
        let ordered = OptionOrder.order(options, by: ["unknown", "c", "a", "c", "b"])
        XCTAssertEqual(ordered.map(\.id), ["c", "a", "b"])
    }
}
