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
        XCTAssertTrue(compactText.contains("Điểm:"))

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
        XCTAssertTrue(countText.contains("Điểm:"), "COUNT_TABLE / backend-mapped TRUE_FALSE review must still show Điểm:")
        XCTAssertTrue(countText.contains("—"), "COUNT_TABLE / backend-mapped TRUE_FALSE rows without per-statement points must show —")
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
        XCTAssertTrue(text.contains("Mục"), "First review table header must use question.itemCol (Mục)")
        XCTAssertFalse(text.contains("Cột trái"), "Review table header must not use question.leftColumn (Cột trái)")
        XCTAssertTrue(text.contains("Bạn chọn"))
        XCTAssertTrue(text.contains("Đáp án"))
        XCTAssertTrue(text.contains("Điểm"))
        XCTAssertTrue(text.contains("Chưa ghép"), "Unmatched left row l4 must display Chưa ghép")

        let blankReview = ItemReview(
            outcome: .blank,
            earned: 0,
            max: 2,
            level: .scoreAndAnswers,
            showAnswers: true,
            detail: [:]
        )
        let (blankWindow, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: matchSample.item,
                    answer: nil,
                    mode: .review(blankReview),
                    onAnswer: { _, _ in },
                    locale: .vi,
                    fixedWidth: 960
                )
                .padding(16)
            },
            size: CGSize(width: 1024, height: 768),
            sizeClass: .regular
        )
        defer { blankWindow.isHidden = true }

        let blankText = QuestionViewTestHelper.allAccessibilityStrings(in: blankWindow).joined(separator: "\n")
        XCTAssertTrue(blankText.contains("Mục"))
        XCTAssertTrue(blankText.contains("Bạn chọn"))
        XCTAssertTrue(blankText.contains("Đáp án"))
        XCTAssertTrue(blankText.contains("Điểm"), "BLANK matching review must still render 4th Điểm column")
        XCTAssertTrue(blankText.contains("—"), "BLANK matching rows without per-pair earned points must show —")
        XCTAssertTrue(blankText.contains("Chưa ghép"))
    }
}
