import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ImeCompositionTests: XCTestCase {
    func testEssayImeCompositionDoesNotEmitUncommittedTelexAndCommitsCleanly() throws {
        let controller = EssayEditorController()
        let item = QuestionItem.essay(id: "ime-essay", displayNo: 1, points: 5, stem: nil, wordLimit: nil)
        var emitted: [AnswerValue] = []

        let (window, _) = QuestionViewTestHelper.host(
            QuestionView(
                item: item,
                answer: nil,
                mode: .answering,
                essayController: controller,
                onAnswer: { _, val in emitted.append(val) },
                locale: .vi
            )
            .padding(16),
            size: CGSize(width: 390, height: 500)
        )
        defer { window.isHidden = true }

        let textView = try XCTUnwrap(controller.textView)
        textView.becomeFirstResponder()

        textView.insertText("Bài làm tiếng ")
        QuestionViewTestHelper.pumpRunLoop()
        XCTAssertEqual(emitted.last?.essayHtml, "<p>Bài làm tiếng </p>")
        let emitCountBeforeCompose = emitted.count

        textView.setMarkedText("Vieej", selectedRange: NSRange(location: 5, length: 0))
        QuestionViewTestHelper.pumpRunLoop()

        XCTAssertNotNil(textView.markedTextRange, "Marked text range must be active during IME composition")
        XCTAssertEqual(emitted.count, emitCountBeforeCompose, "Must not emit onAnswer while IME markedTextRange is active")
        XCTAssertFalse(
            (emitted.last?.essayHtml ?? "").contains("Vieej"),
            "Uncommitted Telex fragment must never appear in onAnswer"
        )
        XCTAssertEqual(controller.committedHTML(), "<p>Bài làm tiếng </p>")

        textView.setMarkedText("Việt", selectedRange: NSRange(location: 4, length: 0))
        XCTAssertEqual(emitted.count, emitCountBeforeCompose)
        textView.unmarkText()
        QuestionViewTestHelper.pumpRunLoop()

        XCTAssertNil(textView.markedTextRange)
        XCTAssertEqual(emitted.last?.essayHtml, "<p>Bài làm tiếng Việt</p>")
        XCTAssertEqual(emitted.last?.essayWordCount, 4)
    }

    func testEssayComposedVietnameseSentenceAndUndoRedo() throws {
        let controller = EssayEditorController()
        let item = QuestionItem.essay(id: "ime-undo", displayNo: 2, points: 5, stem: nil, wordLimit: nil)
        var emitted: [AnswerValue] = []

        let (window, _) = QuestionViewTestHelper.host(
            QuestionView(
                item: item,
                answer: nil,
                mode: .answering,
                essayController: controller,
                onAnswer: { _, val in emitted.append(val) },
                locale: .vi
            )
            .padding(16),
            size: CGSize(width: 390, height: 500)
        )
        defer { window.isHidden = true }

        let textView = try XCTUnwrap(controller.textView)
        textView.becomeFirstResponder()

        textView.setMarkedText("Tieengs", selectedRange: NSRange(location: 7, length: 0))
        textView.setMarkedText("Tiếng", selectedRange: NSRange(location: 5, length: 0))
        textView.unmarkText()
        textView.insertText(" ")
        textView.setMarkedText("Vieetj", selectedRange: NSRange(location: 6, length: 0))
        textView.setMarkedText("Việt", selectedRange: NSRange(location: 4, length: 0))
        textView.unmarkText()
        textView.insertText(" có dấu")
        QuestionViewTestHelper.pumpRunLoop()

        XCTAssertEqual(emitted.last?.essayHtml, "<p>Tiếng Việt có dấu</p>")
        XCTAssertEqual(emitted.last?.essayWordCount, 4)
        XCTAssertEqual(QuestionStrings.essayWordCount(count: emitted.last?.essayWordCount ?? 0, min: nil, locale: .vi), "4 chữ")

        controller.pastePlainText(" Đường thẳng")
        QuestionViewTestHelper.pumpRunLoop()
        XCTAssertEqual(emitted.last?.essayHtml, "<p>Tiếng Việt có dấu Đường thẳng</p>")
        XCTAssertTrue(controller.canUndo)

        controller.undo()
        QuestionViewTestHelper.pumpRunLoop()
        XCTAssertEqual(emitted.last?.essayHtml, "<p>Tiếng Việt có dấu</p>")
        XCTAssertTrue(controller.canRedo)

        controller.redo()
        QuestionViewTestHelper.pumpRunLoop()
        XCTAssertEqual(emitted.last?.essayHtml, "<p>Tiếng Việt có dấu Đường thẳng</p>")
    }

    func testClozeBlankImeCompositionEmitsOnlyCommittedText() throws {
        let item = QuestionItem.cloze(
            id: "ime-cloze",
            displayNo: 1,
            points: 1,
            stem: nil,
            passage: "<p>Tiếng [[b1]]</p>",
            blanks: [ClozeBlank(id: "b1")],
            blankNumberStart: 1
        )
        var emitted: [AnswerValue] = []

        let (window, _) = QuestionViewTestHelper.host(
            QuestionView(item: item, answer: nil, mode: .answering, onAnswer: { _, val in emitted.append(val) }, fixedWidth: 358)
                .padding(16),
            size: CGSize(width: 390, height: 340)
        )
        defer { window.isHidden = true }

        let field = try XCTUnwrap(QuestionViewTestHelper.findViews(ofType: ExamUITextField.self, in: window).first)
        field.becomeFirstResponder()

        field.setMarkedText("Vieej", selectedRange: NSRange(location: 5, length: 0))
        field.sendActions(for: .editingChanged)
        QuestionViewTestHelper.pumpRunLoop()
        XCTAssertTrue(emitted.isEmpty, "Cloze must not emit onAnswer while composing Vieej")

        field.setMarkedText("Việt", selectedRange: NSRange(location: 4, length: 0))
        field.unmarkText()
        QuestionViewTestHelper.pumpRunLoop()

        XCTAssertEqual(emitted.count, 1)
        XCTAssertEqual(emitted.last?.clozeBlanks["b1"], "Việt")
    }
}
