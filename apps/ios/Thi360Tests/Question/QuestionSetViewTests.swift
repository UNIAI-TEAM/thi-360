import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class QuestionSetViewTests: XCTestCase {
    func testDemoSetCompactVsRegularLayoutAndChildIdRoutingAndDraftPreservation() throws {
        let sample = try QuestionTestFixtures.sample(key: "set")
        var answers: [String: AnswerValue] = [:]
        var emittedIds: [String] = []

        let (compactWindow, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: sample.item,
                    answers: answers,
                    mode: .answering,
                    onAnswer: { itemId, value in
                        emittedIds.append(itemId)
                        answers[itemId] = value
                    },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact
        )
        defer { compactWindow.isHidden = true }

        let compactIds = QuestionViewTestHelper.allAccessibilityIdentifiers(in: compactWindow)
        XCTAssertTrue(compactIds.contains("question.demo-set.setCompact"))
        XCTAssertFalse(compactIds.contains("question.demo-set.setRegularSplit"))

        let compactStrings = QuestionViewTestHelper.allAccessibilityStrings(in: compactWindow).joined(separator: "\n")
        XCTAssertTrue(compactStrings.contains("Ngữ liệu chung cho câu 31–32"))

        let child2Field = try XCTUnwrap(
            QuestionViewTestHelper.findViews(ofType: ExamUITextField.self, in: compactWindow)
                .first { $0.accessibilityIdentifier == "question.demo-set-b.shortAnswerField" }
        )
        child2Field.text = "2148,6"
        child2Field.sendActions(for: .editingChanged)
        QuestionViewTestHelper.pumpRunLoop()

        XCTAssertEqual(emittedIds.last, "demo-set-b", "Child answer must emit child ID, never question set ID")
        XCTAssertFalse(emittedIds.contains("demo-set"))
        XCTAssertEqual(answers["demo-set-b"]?.shortAnswerText, "2148,6")

        let (regularWindow, _) = QuestionViewTestHelper.host(
            QuestionView(
                item: sample.item,
                answers: answers,
                mode: .answering,
                onAnswer: { itemId, value in
                    emittedIds.append(itemId)
                    answers[itemId] = value
                },
                locale: .vi,
                fixedWidth: 992
            )
            .padding(16),
            size: CGSize(width: 1024, height: 768),
            sizeClass: .regular
        )
        defer { regularWindow.isHidden = true }

        let regularIds = QuestionViewTestHelper.allAccessibilityIdentifiers(in: regularWindow)
        XCTAssertTrue(regularIds.contains("question.demo-set.setRegularSplit"))
        XCTAssertTrue(regularIds.contains("question.demo-set.passageScroll"))
        XCTAssertTrue(regularIds.contains("question.demo-set.childrenScroll"))

        let regularChild2Field = try XCTUnwrap(
            QuestionViewTestHelper.findViews(ofType: ExamUITextField.self, in: regularWindow)
                .first { $0.accessibilityIdentifier == "question.demo-set-b.shortAnswerField" }
        )
        XCTAssertEqual(regularChild2Field.text, "2148,6", "Child draft must survive compact -> regular transition")
    }
}
