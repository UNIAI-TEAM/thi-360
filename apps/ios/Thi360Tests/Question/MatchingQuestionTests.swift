import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class MatchingQuestionTests: XCTestCase {
    private func makeMatchingItem(reuseRight: Bool) -> QuestionItem {
        .matching(
            id: "match-ac11",
            displayNo: 25,
            points: 1,
            stem: "<p>Ghép công thức hóa học tương ứng.</p>",
            optionOrder: ["r2", "r1", "r4", "r3"],
            left: [
                MatchingSideItem(id: "l1", content: "<p>Axit sunfuric</p>"),
                MatchingSideItem(id: "l2", content: "<p>Axit clohiđric</p>"),
                MatchingSideItem(id: "l3", content: "<p>Nước</p>")
            ],
            right: [
                MatchingSideItem(id: "r1", content: "<p><span class=\"math-inline\">\\(\\ce{H2SO4}\\)</span></p>"),
                MatchingSideItem(id: "r2", content: "<p><span class=\"math-inline\">\\(\\ce{HCl}\\)</span></p>"),
                MatchingSideItem(id: "r3", content: "<p><span class=\"math-inline\">\\(\\ce{H2O}\\)</span></p>"),
                MatchingSideItem(id: "r4", content: "<p><span class=\"math-inline\">\\(\\ce{NaCl}\\)</span></p>")
            ],
            reuseRight: reuseRight,
            pairs: [
                MatchingPairKey(leftId: "l1", rightId: "r1", points: 0.33),
                MatchingPairKey(leftId: "l2", rightId: "r2", points: 0.33),
                MatchingPairKey(leftId: "l3", rightId: "r3", points: 0.34)
            ]
        )
    }

    func testExclusiveModeDisablesUsedRightChoiceAndClearReenablesIt() throws {
        let item = makeMatchingItem(reuseRight: false)
        let afterMatch1 = MatchingLogic.assign(leftId: "l1", rightId: "r1", currentPairs: [:], item: item)
        XCTAssertEqual(afterMatch1.matchingPairs, ["l1": "r1"])

        let disabledForRow2 = MatchingLogic.disabledRightIds(
            forLeftId: "l2",
            pairs: afterMatch1.matchingPairs,
            reuseRight: false
        )
        XCTAssertEqual(disabledForRow2, ["r1"], "Right item matched by row 1 must be disabled for row 2 when reuseRight=false")

        let disabledForRow1 = MatchingLogic.disabledRightIds(
            forLeftId: "l1",
            pairs: afterMatch1.matchingPairs,
            reuseRight: false
        )
        XCTAssertTrue(disabledForRow1.isEmpty, "Row 1 must still be able to see its own matched choice enabled")

        let duplicateAttempt = AnswerValues.matching(item: item, pairs: ["l1": "r1", "l2": "r1"])
        XCTAssertEqual(duplicateAttempt.matchingPairs, ["l1": "r1"])

        let afterClear = MatchingLogic.assign(leftId: "l1", rightId: nil, currentPairs: afterMatch1.matchingPairs, item: item)
        XCTAssertTrue(afterClear.matchingPairs.isEmpty)
        XCTAssertTrue(
            MatchingLogic.disabledRightIds(forLeftId: "l2", pairs: afterClear.matchingPairs, reuseRight: false).isEmpty
        )
    }

    func testReusableModeAllowsMultipleLeftItemsToShareRightChoice() {
        let item = makeMatchingItem(reuseRight: true)
        let pairs = ["l1": "r1", "l2": "r1"]
        let value = AnswerValues.matching(item: item, pairs: pairs)
        XCTAssertEqual(value.matchingPairs, pairs)
        XCTAssertTrue(MatchingLogic.disabledRightIds(forLeftId: "l2", pairs: pairs, reuseRight: true).isEmpty)
    }

    func testMatchingRowAndPickerSheetRenderFormulaNativelyAndShowClearLabel() throws {
        let item = makeMatchingItem(reuseRight: false)
        let currentAnswer = AnswerValues.matching(item: item, pairs: ["l1": "r1"])

        let (rowWindow, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: item,
                    answer: currentAnswer,
                    mode: .answering,
                    onAnswer: { _ in },
                    locale: .vi,
                    fixedWidth: 358
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 700)
        )
        defer { rowWindow.isHidden = true }

        let strings = QuestionViewTestHelper.allAccessibilityStrings(in: rowWindow).joined(separator: "\n")
        XCTAssertTrue(strings.contains("Đã ghép 1/3 mục"))

        let clearButton = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.match-ac11.match.l1.clear", in: rowWindow)
        )
        XCTAssertEqual(clearButton.accessibilityLabel, "Bỏ ghép mục 1")
        XCTAssertGreaterThanOrEqual(clearButton.accessibilityFrame.width, 44)
        XCTAssertGreaterThanOrEqual(clearButton.accessibilityFrame.height, 44)

        guard case let .matching(_, _, _, _, optionOrder, left, right, _, _) = item else {
            return XCTFail("Expected matching item")
        }
        let orderedRight = MatchingLogic.orderedRight(right: right, optionOrder: optionOrder)
        let (sheetWindow, _) = QuestionViewTestHelper.host(
            MatchingChoiceSheet(
                itemId: item.id,
                leftIndex: 1,
                leftItem: left[1],
                orderedRight: orderedRight,
                selectedRightId: nil,
                disabledRightIds: ["r1"],
                assets: nil,
                formulaEngine: SwaTexFormulaEngine.shared,
                locale: .vi,
                onSelect: { _ in },
                onClose: {}
            ),
            size: CGSize(width: 390, height: 600)
        )
        defer { sheetWindow.isHidden = true }

        let disabledChoice = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.match-ac11.matchSheet.r1", in: sheetWindow)
        )
        XCTAssertTrue(disabledChoice.accessibilityTraits.contains(.notEnabled), "Used right option must be disabled in sheet")

        let parsedFormula = RichHTMLParser.parse(right[0].content)
        let containsH2SO4 = parsedFormula.blocks.contains { block in
            if case let .paragraph(runs) = block {
                return runs.contains {
                    if case let .formula(formula) = $0 { return formula.source.contains("\\ce{H2SO4}") }
                    return false
                }
            }
            return false
        }
        XCTAssertTrue(containsH2SO4)
    }
}
