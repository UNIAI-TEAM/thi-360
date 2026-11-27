import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ShortAnswerTests: XCTestCase {
    func testIntegerShortAnswerHintNoteKeyboardAndWhitespacePreservation() throws {
        let sample = try QuestionTestFixtures.sample(key: "short")
        var emitted: [AnswerValue] = []

        let (window, _) = QuestionViewTestHelper.host(
            QuestionView(
                item: sample.item,
                answer: nil,
                mode: .answering,
                onAnswer: { emitted.append($0) },
                locale: .vi,
                fixedWidth: 358
            )
            .padding(16),
            size: CGSize(width: 390, height: 640)
        )
        defer { window.isHidden = true }

        let allStrings = QuestionViewTestHelper.allAccessibilityStrings(in: window).joined(separator: "\n")
        XCTAssertTrue(allStrings.contains("Số nguyên, không ghi đơn vị"))
        XCTAssertTrue(allStrings.contains("Đáp số được so khớp đúng cách viết"))

        let fields = QuestionViewTestHelper.findViews(ofType: ExamUITextField.self, in: window)
        let field = try XCTUnwrap(fields.first, "Expected ExamUITextField in ShortAnswerQuestionView")

        XCTAssertEqual(field.keyboardType, .numbersAndPunctuation)
        XCTAssertEqual(field.autocorrectionType, .no)
        XCTAssertEqual(field.spellCheckingType, .no)
        XCTAssertEqual(field.smartQuotesType, .no)
        XCTAssertEqual(field.smartDashesType, .no)
        XCTAssertEqual(field.smartInsertDeleteType, .no)
        XCTAssertEqual(field.inlinePredictionType, .no)
        XCTAssertEqual(field.autocapitalizationType, .none)

        field.text = "  56 "
        field.sendActions(for: .editingChanged)
        QuestionViewTestHelper.pumpRunLoop()

        XCTAssertEqual(emitted.last?.shortAnswerText, "  56 ", "Must preserve exact leading/trailing spaces")
    }

    func testDecimalUsesDecimalPadAndFractionUsesNumbersAndPunctuation() throws {
        let cases: [(AnswerFormat, UIKeyboardType)] = [
            (.decimal, .decimalPad),
            (.fraction, .numbersAndPunctuation)
        ]
        for (format, expectedKeyboard) in cases {
            let item = QuestionItem.shortAnswer(
                id: "sa-\(format.rawValue)",
                displayNo: 1,
                points: 1,
                stem: "<p>Tính</p>",
                answerFormat: format,
                inputHint: nil,
                answers: []
            )
            let (window, _) = QuestionViewTestHelper.host(
                QuestionView(item: item, answer: nil, mode: .answering, onAnswer: { _ in }),
                size: CGSize(width: 390, height: 480)
            )
            defer { window.isHidden = true }

            let field = try XCTUnwrap(QuestionViewTestHelper.findViews(ofType: ExamUITextField.self, in: window).first)
            XCTAssertEqual(field.keyboardType, expectedKeyboard)
        }
    }
}
