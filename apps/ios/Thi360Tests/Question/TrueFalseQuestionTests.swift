import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class TrueFalseQuestionTests: XCTestCase {
    private let tfItem = QuestionItem.trueFalse(
        id: "tf-ac10",
        displayNo: 24,
        points: 1,
        stem: "<p>Mỗi phát biểu sau đúng hay sai?</p>",
        statements: [
            TrueFalseStatement(id: "a", content: "<p>Hai đường thẳng song song thì đồng phẳng.</p>", answer: true, points: 0.25),
            TrueFalseStatement(id: "b", content: "<p>Hai đường thẳng chéo nhau có điểm chung.</p>", answer: false, points: 0.25),
            TrueFalseStatement(id: "c", content: "<p>Ba điểm bất kỳ luôn thẳng hàng.</p>", answer: false, points: 0.25),
            TrueFalseStatement(id: "d", content: "<p>Qua hai điểm phân biệt có duy nhất một đường thẳng.</p>", answer: true, points: 0.25)
        ],
        scoringMethod: .perStatement
    )

    func testTrueFalseSelectionAndRetapOmitsClearedStatement() throws {
        var map: [String: Bool] = [:]
        map["a"] = true
        map["c"] = false
        let first = AnswerValues.trueFalse(item: tfItem, statements: map)
        let firstJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(first)) as? [String: Any])
        XCTAssertEqual(firstJSON["type"] as? String, "TRUE_FALSE")
        XCTAssertEqual(firstJSON["statements"] as? [String: Bool], ["a": true, "c": false])

        map.removeValue(forKey: "a")
        let afterClear = AnswerValues.trueFalse(item: tfItem, statements: map)
        let clearedJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(afterClear)) as? [String: Any])
        XCTAssertEqual(clearedJSON["type"] as? String, "TRUE_FALSE")
        let statementsDict = try XCTUnwrap(clearedJSON["statements"] as? [String: Bool])
        XCTAssertNil(statementsDict["a"], "Cleared statement ID must be omitted from statements map")
        XCTAssertEqual(statementsDict, ["c": false])
    }

    func testNarrowScreenAndAX3ButtonsWrapBelowTextWithoutClipping() throws {
        for category in [UIContentSizeCategory.large, .accessibilityExtraExtraExtraLarge] {
            let (window, _) = QuestionViewTestHelper.host(
                ScrollView {
                    QuestionView(
                        item: tfItem,
                        answer: AnswerValues.trueFalse(item: tfItem, statements: ["a": true, "c": false]),
                        mode: .answering,
                        onAnswer: { _ in },
                        locale: .vi,
                        fixedWidth: 320
                    )
                    .padding(12)
                },
                size: CGSize(width: 344, height: 844),
                sizeClass: .compact,
                contentSizeCategory: category
            )
            defer { window.isHidden = true }

            let trueBtn = try XCTUnwrap(
                QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.tf-ac10.tf.a.true", in: window)
            )
            let falseBtn = try XCTUnwrap(
                QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.tf-ac10.tf.a.false", in: window)
            )

            XCTAssertGreaterThanOrEqual(trueBtn.accessibilityFrame.width, 44)
            XCTAssertGreaterThanOrEqual(trueBtn.accessibilityFrame.height, 44)
            XCTAssertGreaterThanOrEqual(falseBtn.accessibilityFrame.width, 44)
            XCTAssertGreaterThanOrEqual(falseBtn.accessibilityFrame.height, 44)
            XCTAssertLessThanOrEqual(falseBtn.accessibilityFrame.maxX, 344 + 1, "Buttons must stay within 344pt viewport")
            XCTAssertFalse(trueBtn.accessibilityFrame.intersects(falseBtn.accessibilityFrame), "True/False buttons must not overlap")
        }
    }
}
