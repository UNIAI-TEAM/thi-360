import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ReviewTrueFalseMatchingTests: XCTestCase {
    func testTrueFalseCompactStacksWithoutHorizontalScrollAndNoKeyHidesAnswerColumn() throws {
        let tfSample = try QuestionTestFixtures.sample(key: "trueFalse")
        let (compactWindow, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: tfSample.item,
                    answer: tfSample.submittedAnswer,
                    mode: .review(tfSample.review),
                    onAnswer: { _, _ in },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact
        )
        defer { compactWindow.isHidden = true }

        let compactText = QuestionViewTestHelper.allAccessibilityStrings(in: compactWindow).joined(separator: "\n")
        XCTAssertTrue(compactText.contains("Bạn chọn"))
        XCTAssertTrue(compactText.contains("Đáp án"))

        let scrollViews = QuestionViewTestHelper.findViews(ofType: UIScrollView.self, in: compactWindow)
        for sv in scrollViews {
            XCTAssertLessThanOrEqual(sv.contentSize.width, 390 + 2, "Compact TF review must not require horizontal scroll")
        }

        let noKeySample = try QuestionTestFixtures.sample(key: "trueFalseNoKey")
        let (noKeyWindow, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: noKeySample.item,
                    answer: noKeySample.submittedAnswer,
                    mode: .review(noKeySample.review),
                    onAnswer: { _, _ in },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 700),
            sizeClass: .compact
        )
        defer { noKeyWindow.isHidden = true }

        let noKeyText = QuestionViewTestHelper.allAccessibilityStrings(in: noKeyWindow).joined(separator: "\n")
        XCTAssertFalse(noKeyText.contains("Đáp án:"), "trueFalseNoKey must hide Đáp án")

        let countItem = QuestionItem.trueFalse(
            id: "tf-count",
            displayNo: 24,
            points: 1,
            stem: "<p>Tính điểm theo số ý đúng</p>",
            statements: [
                TrueFalseStatement(id: "s1", content: "<p>Ý 1</p>", answer: true),
                TrueFalseStatement(id: "s2", content: "<p>Ý 2</p>", answer: false)
            ],
            scoringMethod: .countTable
        )
        let countReview = ItemReview(
            outcome: .partial,
            earned: 0.25,
            max: 1,
            level: .scoreAndAnswers,
            detail: [
                "s1": PartResult(key: "s1", outcome: .correct),
                "s2": PartResult(key: "s2", outcome: .wrong)
            ]
        )
        let (countWindow, _) = QuestionViewTestHelper.host(
            QuestionView(
                item: countItem,
                answer: AnswerValues.trueFalse(item: countItem, statements: ["s1": true, "s2": true]),
                mode: .review(countReview),
                onAnswer: { _, _ in },
                locale: .vi,
                fixedWidth: 358
            )
            .padding(16),
            size: CGSize(width: 390, height: 600)
        )
        defer { countWindow.isHidden = true }

        let countText = QuestionViewTestHelper.allAccessibilityStrings(in: countWindow).joined(separator: "\n")
        XCTAssertTrue(countText.contains("Đúng 1/2 ý → 0,25 điểm"))
    }

    func testMatchingRegularReviewShowsFourColumnTableAndNotMatchedLabel() throws {
        let matchSample = try QuestionTestFixtures.sample(key: "matching")
        let (regularWindow, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: matchSample.item,
                    answer: matchSample.submittedAnswer,
                    mode: .review(matchSample.review),
                    onAnswer: { _, _ in },
                    locale: .vi,
                    fixedWidth: 960
                )
                .padding(16)
            },
            size: CGSize(width: 1024, height: 768),
            sizeClass: .regular
        )
        defer { regularWindow.isHidden = true }

        let text = QuestionViewTestHelper.allAccessibilityStrings(in: regularWindow).joined(separator: "\n")
        XCTAssertTrue(text.contains("Cột trái"))
        XCTAssertTrue(text.contains("Bạn chọn"))
        XCTAssertTrue(text.contains("Đáp án"))
        XCTAssertTrue(text.contains("Điểm"))
        XCTAssertTrue(text.contains("Chưa ghép"), "Unmatched left row l4 must display Chưa ghép")
    }
}
