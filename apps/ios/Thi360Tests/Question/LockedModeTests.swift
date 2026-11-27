import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class LockedModeTests: XCTestCase {
    func testLockedSingleChoiceDisablesAllOptionsAndNeverEmits() throws {
        let sample = try QuestionTestFixtures.sample(key: "single")
        var emitted: [AnswerValue] = []

        let (window, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: sample.item,
                    answer: AnswerValues.singleChoice(item: sample.item, optionId: "s1"),
                    mode: .locked,
                    onAnswer: { _, val in emitted.append(val) },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 640)
        )
        defer { window.isHidden = true }

        let optionC = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.demo-single.option.s4", in: window)
        )
        XCTAssertFalse(optionC.accessibilityTraits.contains(.button), "Locked choice option must not be an interactive button")
        XCTAssertTrue(emitted.isEmpty)

        let scrollViews = QuestionViewTestHelper.findViews(ofType: UIScrollView.self, in: window)
        XCTAssertTrue(scrollViews.contains(where: \.isScrollEnabled), "Scrolling must remain enabled in locked mode")
    }

    func testLockedEssayIsReadOnlySelectableNoKeyboardAndDisabledToolbar() throws {
        let controller = EssayEditorController()
        let item = QuestionItem.essay(id: "locked-essay", displayNo: 1, points: 5, stem: "<p>Đề</p>", wordLimit: nil)
        var emitted: [AnswerValue] = []

        let (window, _) = QuestionViewTestHelper.host(
            QuestionView(
                item: item,
                answer: AnswerValues.essay(html: "<p>Bài viết đã lưu.</p>"),
                mode: .locked,
                essayController: controller,
                onAnswer: { _, val in emitted.append(val) },
                locale: .vi,
                fixedWidth: 358
            )
            .padding(16),
            size: CGSize(width: 390, height: 540)
        )
        defer { window.isHidden = true }

        let textView = try XCTUnwrap(controller.textView)
        XCTAssertFalse(textView.isEditable, "Locked essay editor must not be editable")
        XCTAssertTrue(textView.isSelectable, "Locked essay editor must remain selectable for copy")
        XCTAssertFalse(textView.isFirstResponder, "Locked essay editor must not open keyboard")

        controller.toggleBold()
        controller.pasteHTML("<p>Injected</p>")
        QuestionViewTestHelper.pumpRunLoop()
        XCTAssertTrue(emitted.isEmpty, "Locked essay must not emit changes")
        XCTAssertEqual(controller.committedHTML(), "<p>Bài viết đã lưu.</p>")

        for suffix in ["bold", "italic", "bulletList", "orderedList", "undo", "redo"] {
            let btn = try XCTUnwrap(
                QuestionViewTestHelper.accessibilityElement(
                    withIdentifier: "question.locked-essay.essayToolbar.\(suffix)",
                    in: window
                )
            )
            XCTAssertTrue(btn.accessibilityTraits.contains(.notEnabled), "Toolbar button \(suffix) must be disabled in locked mode")
        }
    }

    func testLockedShortAnswerDisablesFieldNumericRowAndSymbolBar() throws {
        let chemItem = QuestionItem.shortAnswer(
            id: "locked-chem",
            displayNo: 2,
            points: 1,
            stem: "<p>Viết công thức ion</p>",
            answerFormat: .chemFormula,
            inputHint: nil,
            answers: []
        )
        let (chemWindow, _) = QuestionViewTestHelper.host(
            QuestionView(item: chemItem, answer: AnswerValues.shortAnswer(text: "SO42-"), mode: .locked, onAnswer: { _, _ in }, fixedWidth: 358)
                .padding(16),
            size: CGSize(width: 390, height: 520)
        )
        defer { chemWindow.isHidden = true }

        let chemField = try XCTUnwrap(QuestionViewTestHelper.findViews(ofType: ExamUITextField.self, in: chemWindow).first)
        XCTAssertTrue(chemField.isReadOnly)
        let symBtn = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.locked-chem.symbolKey.charge-2minus", in: chemWindow)
        )
        XCTAssertTrue(symBtn.accessibilityTraits.contains(.notEnabled))

        let fracItem = QuestionItem.shortAnswer(
            id: "locked-frac",
            displayNo: 3,
            points: 1,
            stem: "<p>Phân số</p>",
            answerFormat: .fraction,
            inputHint: nil,
            answers: []
        )
        let (fracWindow, _) = QuestionViewTestHelper.host(
            QuestionView(item: fracItem, answer: AnswerValues.shortAnswer(text: "-3/4"), mode: .locked, onAnswer: { _, _ in }, fixedWidth: 358)
                .padding(16),
            size: CGSize(width: 390, height: 520)
        )
        defer { fracWindow.isHidden = true }

        let numBtn = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.locked-frac.numericKey.num-minus", in: fracWindow)
        )
        XCTAssertTrue(numBtn.accessibilityTraits.contains(.notEnabled))
    }
}
