import Observation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@Observable
@MainActor
private final class ClozeTestState {
    var answer: AnswerValue?
    var emitted: [AnswerValue] = []
}

@MainActor
final class ClozeQuestionTests: XCTestCase {
    func testDemoClozeInlineSlotsPlaceholdersFormulaAndCounterUpdates() throws {
        let sample = try QuestionTestFixtures.sample(key: "cloze")
        let state = ClozeTestState()

        struct Harness: View {
            let item: QuestionItem
            @Bindable var state: ClozeTestState

            var body: some View {
                QuestionView(
                    item: item,
                    answer: state.answer,
                    mode: .answering,
                    onAnswer: { next in
                        state.answer = next
                        state.emitted.append(next)
                    },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            }
        }

        let (window, _) = QuestionViewTestHelper.host(
            Harness(item: sample.item, state: state),
            size: CGSize(width: 390, height: 640)
        )
        defer { window.isHidden = true }

        let initialStrings = QuestionViewTestHelper.allAccessibilityStrings(in: window).joined(separator: "\n")
        XCTAssertTrue(initialStrings.contains("Đã điền 0/2 ô"), "Initial counter must show Đã điền 0/2 ô")

        let fields = QuestionViewTestHelper.findViews(ofType: ExamUITextField.self, in: window)
        XCTAssertEqual(fields.count, 2, "demo-cloze must render 2 inline slot text fields")

        let fieldB1 = try XCTUnwrap(fields.first { $0.accessibilityIdentifier == "question.demo-cloze.cloze.b1" })
        let fieldB2 = try XCTUnwrap(fields.first { $0.accessibilityIdentifier == "question.demo-cloze.cloze.b2" })
        XCTAssertEqual(fieldB1.attributedPlaceholder?.string, "Ô 1")
        XCTAssertEqual(fieldB2.attributedPlaceholder?.string, "Ô 2")

        if case let .cloze(_, _, _, _, passage, _, _) = sample.item {
            let parsed = RichHTMLParser.parse(passage, options: RichParseOptions(slotMarkers: true))
            let hasChemMath = parsed.blocks.contains { block in
                if case let .paragraph(runs) = block {
                    return runs.contains {
                        if case let .formula(formula) = $0 { return formula.source.contains("\\ce{2H2 + O2 -> 2H2O}") }
                        return false
                    }
                }
                return false
            }
            XCTAssertTrue(hasChemMath, "Cloze passage must parse mhchem inline formula")
        }

        fieldB1.text = "100"
        fieldB1.sendActions(for: .editingChanged)
        QuestionViewTestHelper.pumpRunLoop(0.12)

        let firstPayload = try XCTUnwrap(state.emitted.last)
        let firstJSON = try XCTUnwrap(
            JSONSerialization.jsonObject(with: JSONEncoder().encode(firstPayload)) as? [String: Any]
        )
        XCTAssertEqual(firstJSON["type"] as? String, "CLOZE")
        XCTAssertEqual(firstJSON["blanks"] as? [String: String], ["b1": "100"])

        let midStrings = QuestionViewTestHelper.allAccessibilityStrings(in: window).joined(separator: "\n")
        XCTAssertTrue(midStrings.contains("Đã điền 1/2 ô"), "Counter must update to Đã điền 1/2 ô")

        fieldB1.text = ""
        fieldB1.sendActions(for: .editingChanged)
        QuestionViewTestHelper.pumpRunLoop(0.12)

        let clearedPayload = try XCTUnwrap(state.emitted.last)
        let clearedJSON = try XCTUnwrap(
            JSONSerialization.jsonObject(with: JSONEncoder().encode(clearedPayload)) as? [String: Any]
        )
        XCTAssertEqual(clearedJSON["type"] as? String, "CLOZE")
        XCTAssertEqual(clearedJSON["blanks"] as? [String: String], ["b1": ""])

        let finalStrings = QuestionViewTestHelper.allAccessibilityStrings(in: window).joined(separator: "\n")
        XCTAssertTrue(finalStrings.contains("Đã điền 0/2 ô"), "Counter must return to Đã điền 0/2 ô")
    }
}
