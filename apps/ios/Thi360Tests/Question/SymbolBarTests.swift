import UIKit
import XCTest
@testable import Thi360

@MainActor
final class SymbolBarTests: XCTestCase {
    func testInsertCharge2MinusProducesASCII2Minus() {
        let charge = SymbolInputRules.defaultIonSymbols.first(where: { $0.id == "charge-2minus" })
        XCTAssertEqual(charge?.label, "²⁻")
        XCTAssertEqual(charge?.insert, "2-")

        let (result, cursor) = SymbolInputRules.insertTextAtCursor(
            currentValue: "SO4",
            selectionRange: NSRange(location: 3, length: 0),
            insertion: charge?.insert ?? ""
        )
        XCTAssertEqual(result, "SO42-")
        XCTAssertEqual(cursor, 5)
    }

    func testInsertSub2MidStringInsertsAtCursor() {
        let sub2 = SymbolInputRules.defaultIonSymbols.first(where: { $0.id == "sub-2" })
        XCTAssertEqual(sub2?.label, "₂")
        XCTAssertEqual(sub2?.insert, "2")

        let (result, cursor) = SymbolInputRules.insertTextAtCursor(
            currentValue: "Fe + O",
            selectionRange: NSRange(location: 2, length: 0),
            insertion: sub2?.insert ?? ""
        )
        XCTAssertEqual(result, "Fe2 + O")
        XCTAssertEqual(cursor, 3)
    }

    func testAll12IonSymbolsExistWithTranslatedLabels() {
        XCTAssertEqual(SymbolInputRules.defaultIonSymbols.count, 12)
        for symbol in SymbolInputRules.defaultIonSymbols {
            let vi = QuestionStrings.text(symbol.accessibilityKey, locale: .vi)
            let en = QuestionStrings.text(symbol.accessibilityKey, locale: .en)
            XCTAssertFalse(vi.isEmpty)
            XCTAssertFalse(en.isEmpty)
            XCTAssertNotEqual(vi, symbol.accessibilityKey)
            XCTAssertNotEqual(en, symbol.accessibilityKey)
        }
    }
}

final class SymbolBarRuleTests: XCTestCase {
    func testExplicitFormatsOverrideHeuristics() {
        XCTAssertTrue(SymbolInputRules.shouldShowSymbolInput(answerFormat: .chemFormula, inputHint: nil))
        XCTAssertTrue(SymbolInputRules.shouldShowSymbolInput(answerFormat: .chemEquation, inputHint: nil))
        XCTAssertTrue(SymbolInputRules.shouldShowSymbolInput(answerFormat: .text, inputHint: nil))
        XCTAssertFalse(
            SymbolInputRules.shouldShowSymbolInput(
                answerFormat: .decimal,
                inputHint: "Tính theo công thức hóa học của ion"
            )
        )
        XCTAssertFalse(SymbolInputRules.shouldShowSymbolInput(answerFormat: .integer, inputHint: "Công thức ion"))
        XCTAssertFalse(SymbolInputRules.shouldShowSymbolInput(answerFormat: .fraction, inputHint: "Công thức"))
    }

    func testNilFormatHeuristicDiacriticFoldingAndWordBoundaries() {
        XCTAssertTrue(
            SymbolInputRules.shouldShowSymbolInput(
                answerFormat: nil,
                inputHint: "Viết công thức của ion"
            )
        )
        XCTAssertTrue(
            SymbolInputRules.shouldShowSymbolInput(
                answerFormat: nil,
                inputHint: "Ghi KÝ HIỆU nguyên tố"
            )
        )
        XCTAssertFalse(
            SymbolInputRules.shouldShowSymbolInput(
                answerFormat: nil,
                inputHint: "Dung dịch chứa cation"
            )
        )
        XCTAssertFalse(
            SymbolInputRules.shouldShowSymbolInput(
                answerFormat: nil,
                inputHint: "Dung dịch chứa anion"
            )
        )
        XCTAssertFalse(SymbolInputRules.shouldShowSymbolInput(answerFormat: nil, inputHint: nil))
    }
}
