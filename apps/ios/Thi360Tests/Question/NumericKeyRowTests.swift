import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class NumericKeyRowTests: XCTestCase {
    func testInsertAtCursorBuildsNegativeFractionAndReplacesSelection() throws {
        let controller = ExamTextFieldController()
        let field = ExamUITextField(frame: CGRect(x: 0, y: 0, width: 240, height: 44))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 400))
        window.addSubview(field)
        window.makeKeyAndVisible()
        defer { window.isHidden = true }

        var emitted: [String] = []
        controller.textField = field
        controller.onCommittedChange = { emitted.append($0) }

        controller.insertAtCursor("-")
        XCTAssertEqual(field.text, "-")
        XCTAssertEqual(field.currentSelectionNSRange, NSRange(location: 1, length: 0))

        controller.insertAtCursor("3")
        controller.insertAtCursor("/")
        controller.insertAtCursor("4")
        XCTAssertEqual(field.text, "-3/4")
        XCTAssertEqual(emitted.last, "-3/4")
        XCTAssertEqual(field.currentSelectionNSRange, NSRange(location: 4, length: 0))

        if let start = field.position(from: field.beginningOfDocument, offset: 1),
           let end = field.position(from: field.beginningOfDocument, offset: 3)
        {
            field.selectedTextRange = field.textRange(from: start, to: end)
        }
        controller.insertAtCursor("5,")
        XCTAssertEqual(field.text, "-5,4")
        XCTAssertEqual(field.currentSelectionNSRange, NSRange(location: 3, length: 0))
    }

    func testNumericHelperRowAccessibilityLabelsAndTouchTargets() {
        let keys = SymbolInputRules.numericHelperKeys
        XCTAssertEqual(keys.map(\.insert), ["-", "/", ",", "."])
        XCTAssertEqual(
            keys.map { QuestionStrings.text($0.accessibilityKey, locale: .vi) },
            ["Dấu trừ (-)", "Dấu gạch chéo", "Dấu phẩy", "Dấu chấm"]
        )
        XCTAssertEqual(
            keys.map { QuestionStrings.text($0.accessibilityKey, locale: .en) },
            ["Minus sign (-)", "Slash", "Comma", "Full stop"]
        )

        let item = QuestionItem.shortAnswer(
            id: "num-row-1",
            displayNo: 3,
            points: 1,
            stem: "<p>Phân số</p>",
            answerFormat: .fraction,
            inputHint: nil,
            answers: []
        )
        let (window, _) = QuestionViewTestHelper.host(
            QuestionView(item: item, answer: nil, mode: .answering, onAnswer: { _ in }, fixedWidth: 358)
                .padding(16),
            size: CGSize(width: 390, height: 520)
        )
        defer { window.isHidden = true }

        for key in keys {
            let el = QuestionViewTestHelper.accessibilityElement(
                withIdentifier: "question.num-row-1.numericKey.\(key.id)",
                in: window
            )
            XCTAssertNotNil(el, "Missing numeric key \(key.id)")
            if let el {
                XCTAssertGreaterThanOrEqual(el.accessibilityFrame.width, 44)
                XCTAssertGreaterThanOrEqual(el.accessibilityFrame.height, 44)
            }
        }
    }
}
