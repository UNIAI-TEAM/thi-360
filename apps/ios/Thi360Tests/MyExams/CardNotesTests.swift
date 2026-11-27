import Foundation
import XCTest
@testable import Thi360

final class CardNotesTests: XCTestCase {
    func testClassExhaustedWithAndWithoutHistory() throws {
        let withHistoryJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "EXHAUSTED") { item in
            item["code"] = "CLS-HIST"
            item["entitlementSource"] = "CLASS"
            item["actions"] = ["HISTORY"]
            item["resultAttemptId"] = NSNull()
        }
        let withoutHistoryJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "EXHAUSTED") { item in
            item["code"] = "CLS-NONE"
            item["entitlementSource"] = "CLASS"
            item["actions"] = []
            item["resultAttemptId"] = NSNull()
        }
        let cards = try MyExamsTestFixtures.decodeMyExamCards(
            from: try MyExamsTestFixtures.makeMyExamsPayload(items: [withHistoryJSON, withoutHistoryJSON])
        )

        let notesWithHistory = CardNotes.of(cards[0])
        XCTAssertEqual(notesWithHistory.middleNotes, [.classExhaustedWithHistory])
        XCTAssertEqual(
            MyExamsStrings.text(notesWithHistory.middleNotes[0].localizationKey, locale: .vi),
            "Đã hết lượt được giao cho bài thi này. Kết quả và lịch sử vẫn xem được."
        )
        XCTAssertNil(CardButtons.of(cards[0]).rebuyCode)
        XCTAssertTrue(CardButtons.of(cards[0]).showsHistory)

        let notesWithoutHistory = CardNotes.of(cards[1])
        XCTAssertEqual(notesWithoutHistory.middleNotes, [.classExhausted])
    }

    func testDiscontinuedCanStartAndExhaustedNotes() throws {
        let unavailableCanStartJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-unavail"
            item["code"] = "UNAVAIL-1"
            item["saleState"] = "UNAVAILABLE"
            item["entitlementSource"] = "PURCHASE"
            item["actions"] = ["START"]
        }
        let discontinuedExhaustedJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "EXHAUSTED") { item in
            item["code"] = "DISC-EXH"
            item["saleState"] = "DISCONTINUED"
            item["entitlementSource"] = "PURCHASE"
            item["actions"] = ["HISTORY"]
        }
        let cards = try MyExamsTestFixtures.decodeMyExamCards(
            from: try MyExamsTestFixtures.makeMyExamsPayload(items: [unavailableCanStartJSON, discontinuedExhaustedJSON])
        )

        let n1 = CardNotes.of(cards[0])
        XCTAssertEqual(n1.middleNotes, [.discontinuedCanStart])
        XCTAssertEqual(
            MyExamsStrings.text(n1.middleNotes[0].localizationKey, locale: .vi),
            "Bài thi đã ngừng bán. Bạn vẫn dùng được các lượt còn lại."
        )
        XCTAssertEqual(CardButtons.of(cards[0]).startEntitlementId, "ent-unavail")

        let n2 = CardNotes.of(cards[1])
        XCTAssertEqual(n2.middleNotes, [.discontinuedExhaustedWithHistory])
    }

    func testInProgressLastAttemptAndPendingTopUpNote() throws {
        let json = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "IN_PROGRESS") { item in
            item["code"] = "LAST-PEND"
            item["attemptsLeft"] = 0
            item["activeAttemptId"] = "att-last"
            item["saleState"] = "ON_SALE"
            item["hasPendingFulfillment"] = true
            item["pendingOrderCode"] = "DH002"
            item["entitlementSource"] = "PURCHASE"
            item["actions"] = ["CONTINUE", "BUY_MORE"]
        }
        let card = try XCTUnwrap(try MyExamsTestFixtures.decodeMyExamCards(from: try MyExamsTestFixtures.makeMyExamsPayload(items: [json])).first)
        let notes = CardNotes.of(card)
        let buttons = CardButtons.of(card)

        XCTAssertEqual(notes.middleNotes, [.lastAttempt])
        XCTAssertEqual(
            MyExamsStrings.text(notes.middleNotes[0].localizationKey, locale: .vi),
            "Bạn đang làm lượt cuối cùng của bài này."
        )
        XCTAssertEqual(notes.pendingTopUpOrderCode, "DH002")
        XCTAssertEqual(buttons.continueAttemptId, "att-last")
        XCTAssertNil(buttons.buyMoreCode)
    }
}
