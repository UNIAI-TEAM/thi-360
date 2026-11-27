import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ReviewEssaySetTests: XCTestCase {
    func testPendingEssayShowsNoteNoPointsYetManualTagAndCollapsible1200CharBody() throws {
        let longText = String(repeating: "Hình tượng người lính trong thơ ca kháng chiến mang vẻ đẹp giản dị mà kiên cường. ", count: 16)
        XCTAssertGreaterThan(longText.count, 1200)
        XCTAssertTrue(EssayReviewMetrics.shouldCollapse(html: "<p>\(longText)</p>"))

        let item = QuestionItem.essay(id: "essay-pending", displayNo: 27, points: 25, stem: "<p>Phân tích</p>", wordLimit: 200)
        let answer = AnswerValues.essay(html: "<p>\(longText)</p>")
        let pendingReview = ItemReview(
            outcome: .pendingReview,
            earned: nil,
            max: 25,
            level: .scoreAndAnswers,
            essay: EssayReviewDetail(state: .pending, wordCount: answer.essayWordCount)
        )

        let (window, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: item,
                    answer: answer,
                    mode: .review(pendingReview),
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
        XCTAssertTrue(text.contains("Chấm tay"))
        XCTAssertTrue(text.contains("Chờ chấm"))
        XCTAssertTrue(text.contains("Chưa có điểm · tối đa 25 điểm"))
        XCTAssertTrue(text.contains("Câu này do người chấm chấm sau khi bạn nộp bài. Điểm tổng của lượt hiện khi người chấm chấm xong."))
        XCTAssertTrue(text.contains("Xem toàn bộ bài viết"))
        XCTAssertNil(QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.essay-pending.criteriaTable", in: window))

        let toggleBtn = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.essay-pending.essayToggle", in: window)
        )
        XCTAssertEqual(toggleBtn.accessibilityLabel, "Xem toàn bộ bài viết")
        XCTAssertTrue(QuestionViewTestHelper.triggerAction(identifier: "question.essay-pending.essayToggle", in: window))
        let expandedToggle = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.essay-pending.essayToggle", in: window)
        )
        XCTAssertEqual(expandedToggle.accessibilityLabel, "Thu gọn bài viết")
    }

    func testGradedEssayCriteriaTableShownOnlyWhenNonEmptyAndRegradingBannerShown() throws {
        let sample = try QuestionTestFixtures.sample(key: "essay")
        let (withCriteriaWindow, _) = QuestionViewTestHelper.host(
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
        defer { withCriteriaWindow.isHidden = true }

        XCTAssertNotNil(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.demo-essay.criteriaTable", in: withCriteriaWindow)
        )
        let gradedText = QuestionViewTestHelper.allAccessibilityStrings(in: withCriteriaWindow).joined(separator: "\n")
        XCTAssertTrue(gradedText.contains("Điểm theo tiêu chí"))
        XCTAssertTrue(gradedText.contains("Nêu đúng luận điểm"))
        XCTAssertTrue(gradedText.contains("Bài viết mạch lạc"))

        let regradingReview = ItemReview(
            outcome: .pendingReview,
            earned: nil,
            max: 25,
            level: .scoreAndAnswers,
            essay: EssayReviewDetail(state: .regrading, wordCount: 86, criteria: [], comment: nil)
        )
        let (emptyCriteriaWindow, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: sample.item,
                    answer: sample.submittedAnswer,
                    mode: .review(regradingReview),
                    onAnswer: { _, _ in },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 844)
        )
        defer { emptyCriteriaWindow.isHidden = true }

        XCTAssertNil(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.demo-essay.criteriaTable", in: emptyCriteriaWindow),
            "Criteria table must be omitted when criteria is empty"
        )
        let regradingText = QuestionViewTestHelper.allAccessibilityStrings(in: emptyCriteriaWindow).joined(separator: "\n")
        XCTAssertTrue(regradingText.contains("Đang chấm lại"))
    }

    func testQuestionSetReviewMissingChildOmitsOutcomeChipAndRespectsShowAnswersFalse() throws {
        let setSample = try QuestionTestFixtures.sample(key: "set")
        let partialSetReview = ItemReview(
            outcome: .partial,
            earned: 1,
            max: 2,
            level: .scoreOnly,
            showAnswers: false,
            children: [
                "demo-set-a": ItemReview(outcome: .correct, earned: 1, max: 1, level: .scoreOnly, showAnswers: false)
            ]
        )

        let inheritedB = partialSetReview.childReview(for: "demo-set-b")
        XCTAssertNil(inheritedB.outcome, "Missing child result must omit outcome chip")
        XCTAssertFalse(inheritedB.showAnswers, "Missing child must inherit showAnswers=false")

        let (window, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: setSample.item,
                    answers: setSample.submittedAnswers,
                    mode: .review(partialSetReview),
                    onAnswer: { _, _ in },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 1000),
            sizeClass: .compact
        )
        defer { window.isHidden = true }

        XCTAssertNotNil(QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.demo-set-a.outcomeChip", in: window))
        XCTAssertNil(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.demo-set-b.outcomeChip", in: window),
            "Missing child demo-set-b must not show an outcome chip"
        )
        let text = QuestionViewTestHelper.allAccessibilityStrings(in: window).joined(separator: "\n")
        XCTAssertFalse(text.contains("Đáp án:"), "showAnswers=false must hide child accepted answers")
    }
}
