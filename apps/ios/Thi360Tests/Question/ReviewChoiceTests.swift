import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ReviewChoiceTests: XCTestCase {
    func testSingleChoiceWrongReviewDisplaysOutcomePointsTagsSolutionAndNoFlag() throws {
        let sample = try QuestionTestFixtures.sample(key: "single")
        let (window, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: sample.item,
                    answer: sample.submittedAnswer,
                    mode: .review(sample.review),
                    flagged: true,
                    onAnswer: { _, _ in },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 844)
        )
        defer { window.isHidden = true }

        let allText = QuestionViewTestHelper.allAccessibilityStrings(in: window).joined(separator: "\n")
        XCTAssertTrue(allText.contains("Sai"))
        XCTAssertTrue(allText.contains("0 / 1 điểm"))
        XCTAssertTrue(allText.contains("Bạn chọn · Sai"))
        XCTAssertTrue(allText.contains("Đáp án đúng"))
        XCTAssertTrue(allText.contains("Lời giải"))
        XCTAssertNil(QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.demo-single.flagButton", in: window))
    }

    func testSingleChoiceNoKeyMarksWrongPickWithoutCorrectAnswerTag() throws {
        let sample = try QuestionTestFixtures.sample(key: "singleNoKey")
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

        let allText = QuestionViewTestHelper.allAccessibilityStrings(in: window).joined(separator: "\n")
        XCTAssertTrue(allText.contains("Bạn chọn"), "singleNoKey must mark student's choice neutrally as Bạn chọn")
        XCTAssertFalse(allText.contains("Bạn chọn · Sai"), "singleNoKey must not show Bạn chọn · Sai when no key is available")
        XCTAssertFalse(allText.contains("Đáp án đúng"), "singleNoKey must not fabricate a correct answer tag")
    }

    func testMultipleChoicePartialReviewMarksAllFourRowStatesDistinctly() throws {
        let sample = try QuestionTestFixtures.sample(key: "multiple")
        let answer = AnswerValues.multipleChoice(item: sample.item, optionIds: ["m1", "m2"])
        let (window, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: sample.item,
                    answer: answer,
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

        let allText = QuestionViewTestHelper.allAccessibilityStrings(in: window).joined(separator: "\n")
        XCTAssertTrue(allText.contains("Bạn chọn"))
        XCTAssertTrue(allText.contains("Bạn chọn · Sai"))
        XCTAssertTrue(allText.contains("Đáp án đúng"))
        XCTAssertTrue(allText.contains("Bạn không chọn"))
    }
}
