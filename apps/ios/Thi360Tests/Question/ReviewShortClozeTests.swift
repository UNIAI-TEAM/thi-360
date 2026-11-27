import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ReviewShortClozeTests: XCTestCase {
    func testShortAnswerReviewShowsAcceptedAnswersAndHidesWhenShowAnswersFalse() throws {
        let sample = try QuestionTestFixtures.sample(key: "short")
        let (shownWindow, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: sample.item,
                    answer: sample.submittedAnswer,
                    mode: .review(sample.review),
                    onAnswer: { _, _ in },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 700)
        )
        defer { shownWindow.isHidden = true }

        let shownText = QuestionViewTestHelper.allAccessibilityStrings(in: shownWindow).joined(separator: "\n")
        XCTAssertTrue(shownText.contains("Đúng"))
        XCTAssertTrue(shownText.contains("Đáp án:"))
        XCTAssertTrue(shownText.contains("56 · 56 phút"))

        let hiddenReview = ItemReview(
            outcome: .correct,
            earned: 1,
            max: 1,
            level: .scoreOnly,
            showAnswers: false,
            solutionHtml: sample.review.solutionHtml
        )
        let (hiddenWindow, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: sample.item,
                    answer: sample.submittedAnswer,
                    mode: .review(hiddenReview),
                    onAnswer: { _, _ in },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 700)
        )
        defer { hiddenWindow.isHidden = true }

        let hiddenText = QuestionViewTestHelper.allAccessibilityStrings(in: hiddenWindow).joined(separator: "\n")
        XCTAssertFalse(hiddenText.contains("Đáp án:"), "showAnswers=false must hide Đáp án:")
        XCTAssertFalse(hiddenText.contains("56 phút"), "showAnswers=false must hide accepted answers")
        XCTAssertFalse(hiddenText.contains("Lời giải"), "showAnswers=false must hide solution")
    }

    func testClozeReviewDisplaysSlotsAndTableAndRespectsShowAnswersFalse() throws {
        let sample = try QuestionTestFixtures.sample(key: "cloze")
        let (window, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: sample.item,
                    answer: sample.submittedAnswer,
                    mode: .review(sample.review),
                    onAnswer: { _, _ in },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 844)
        )
        defer { window.isHidden = true }

        let text = QuestionViewTestHelper.allAccessibilityStrings(in: window).joined(separator: "\n")
        XCTAssertTrue(text.contains("Đáp án được chấp nhận"))
        XCTAssertTrue(text.contains("100"))
        XCTAssertNotNil(QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.demo-cloze.cloze.b1", in: window))
        XCTAssertNotNil(QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.demo-cloze.cloze.b2", in: window))

        let hiddenReview = ItemReview(
            outcome: .partial,
            earned: 1,
            max: 2,
            level: .scoreOnly,
            showAnswers: false,
            detail: sample.review.detail
        )
        let (hiddenWindow, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: sample.item,
                    answer: sample.submittedAnswer,
                    mode: .review(hiddenReview),
                    onAnswer: { _, _ in },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 844)
        )
        defer { hiddenWindow.isHidden = true }

        let hiddenText = QuestionViewTestHelper.allAccessibilityStrings(in: hiddenWindow).joined(separator: "\n")
        XCTAssertFalse(hiddenText.contains("Đáp án được chấp nhận"), "showAnswers=false must hide accepted answers column/rows")
    }
}
