import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class QuestionDynamicTypeTests: XCTestCase {
    func testAll11SamplesRenderAtAX3On390ptWithoutOverflowOrOverlap() throws {
        let samples = try QuestionTestFixtures.loadQuestionSamples()
        XCTAssertEqual(samples.count, 11, "Expected all 11 question-samples.json entries")

        for sample in samples {
            let (window, _) = QuestionViewTestHelper.host(
                ScrollView {
                    QuestionView(
                        item: sample.item,
                        answer: sample.submittedAnswer,
                        answers: sample.submittedAnswers,
                        mode: .answering,
                        onAnswer: { _, _ in },
                        locale: .vi,
                        fixedWidth: 358
                    )
                    .padding(16)
                },
                size: CGSize(width: 390, height: 844),
                sizeClass: .compact,
                contentSizeCategory: .accessibilityExtraExtraExtraLarge
            )
            defer { window.isHidden = true }

            let buttons = QuestionViewTestHelper.findViews(ofType: UIControl.self, in: window)
            for control in buttons where !control.isHidden && control.bounds.width > 0 {
                let frame = control.convert(control.bounds, to: window)
                XCTAssertLessThanOrEqual(
                    frame.maxX,
                    390 + 2,
                    "Sample \(sample.key) control exceeded 390pt width at AX3: \(frame)"
                )
            }
        }
    }

    func testDynamicTypeRescalePreservesDraftInputInShortAnswerClozeAndEssay() throws {
        let shortItem = QuestionItem.shortAnswer(id: "dt-short", displayNo: 1, points: 1, stem: "<p>Đề</p>", answerFormat: .text, inputHint: nil, answers: [])
        let shortDraft = AnswerValues.shortAnswer(text: "bản nháp ngắn")
        let (shortWindow, shortHost) = QuestionViewTestHelper.host(
            QuestionView(item: shortItem, answer: shortDraft, mode: .answering, onAnswer: { _, _ in }, fixedWidth: 358).padding(16),
            size: CGSize(width: 390, height: 420),
            contentSizeCategory: .large
        )
        defer { shortWindow.isHidden = true }

        shortHost.rootView = AnyView(
            QuestionView(item: shortItem, answer: shortDraft, mode: .answering, onAnswer: { _, _ in }, fixedWidth: 358)
                .padding(16)
                .environment(\.brandPalette, BrandPaletteSet.defaultThi360Green.palette(for: .light))
                .environment(\.statusPalette, StatusPalette.forMode(.light))
                .environment(\.dynamicTypeSize, .accessibility3)
        )
        QuestionViewTestHelper.pumpRunLoop()
        let shortField = try XCTUnwrap(QuestionViewTestHelper.findViews(ofType: ExamUITextField.self, in: shortWindow).first)
        XCTAssertEqual(shortField.text, "bản nháp ngắn")

        let clozeItem = QuestionItem.cloze(
            id: "dt-cloze",
            displayNo: 2,
            points: 1,
            stem: nil,
            passage: "<p>Điền [[b1]] vào đây.</p>",
            blanks: [ClozeBlank(id: "b1")],
            blankNumberStart: 2
        )
        let clozeDraft = AnswerValues.cloze(item: clozeItem, blanks: ["b1": "100"])
        let (clozeWindow, clozeHost) = QuestionViewTestHelper.host(
            QuestionView(item: clozeItem, answer: clozeDraft, mode: .answering, onAnswer: { _, _ in }, fixedWidth: 358).padding(16),
            size: CGSize(width: 390, height: 420),
            contentSizeCategory: .large
        )
        defer { clozeWindow.isHidden = true }

        clozeHost.rootView = AnyView(
            QuestionView(item: clozeItem, answer: clozeDraft, mode: .answering, onAnswer: { _, _ in }, fixedWidth: 358)
                .padding(16)
                .environment(\.brandPalette, BrandPaletteSet.defaultThi360Green.palette(for: .light))
                .environment(\.statusPalette, StatusPalette.forMode(.light))
                .environment(\.dynamicTypeSize, .accessibility3)
        )
        QuestionViewTestHelper.pumpRunLoop()
        let clozeField = try XCTUnwrap(QuestionViewTestHelper.findViews(ofType: ExamUITextField.self, in: clozeWindow).first)
        XCTAssertEqual(clozeField.text, "100")

        let essayController = EssayEditorController()
        let essayItem = QuestionItem.essay(id: "dt-essay", displayNo: 3, points: 5, stem: nil, wordLimit: nil)
        let essayDraft = AnswerValues.essay(html: "<p>Bài nháp tự luận</p>")
        let (essayWindow, essayHost) = QuestionViewTestHelper.host(
            QuestionView(item: essayItem, answer: essayDraft, mode: .answering, essayController: essayController, onAnswer: { _, _ in }, fixedWidth: 358).padding(16),
            size: CGSize(width: 390, height: 480),
            contentSizeCategory: .large
        )
        defer { essayWindow.isHidden = true }

        essayHost.rootView = AnyView(
            QuestionView(item: essayItem, answer: essayDraft, mode: .answering, essayController: essayController, onAnswer: { _, _ in }, fixedWidth: 358)
                .padding(16)
                .environment(\.brandPalette, BrandPaletteSet.defaultThi360Green.palette(for: .light))
                .environment(\.statusPalette, StatusPalette.forMode(.light))
                .environment(\.dynamicTypeSize, .accessibility3)
        )
        QuestionViewTestHelper.pumpRunLoop()
        XCTAssertEqual(essayController.committedHTML(), "<p>Bài nháp tự luận</p>")
    }
}
