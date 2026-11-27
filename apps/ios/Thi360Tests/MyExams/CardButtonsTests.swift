import Foundation
import XCTest
@testable import Thi360

final class CardButtonsTests: XCTestCase {
    func testInProgressPurchasedOrderAndNoStart() throws {
        let cardJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "IN_PROGRESS") { item in
            item["entitlementId"] = "ent-1"
            item["code"] = "EX-1"
            item["saleState"] = "ON_SALE"
            item["activeAttemptId"] = "att-active"
            item["resultAttemptId"] = "att-result"
            item["hasPendingFulfillment"] = false
            item["actions"] = ["CONTINUE", "START", "VIEW_RESULT", "HISTORY", "BUY_MORE"]
            item["entitlementSource"] = "PURCHASE"
        }
        let card = try XCTUnwrap(try MyExamsTestFixtures.decodeMyExamCards(from: try MyExamsTestFixtures.makeMyExamsPayload(items: [cardJSON])).first)
        let buttons = CardButtons.of(card)

        XCTAssertEqual(
            buttons.orderedActions,
            [
                .continue(attemptId: "att-active"),
                .viewResult(attemptId: "att-result"),
                .history,
                .buyMore(code: "EX-1")
            ]
        )
        XCTAssertNil(buttons.startEntitlementId, "START must not show when CONTINUE is active")
    }

    func testContinueMissingActiveAttemptFallsBackToStart() throws {
        let cardJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "IN_PROGRESS") { item in
            item["entitlementId"] = "ent-fallback"
            item["code"] = "EX-FB"
            item["activeAttemptId"] = NSNull()
            item["actions"] = ["CONTINUE", "START"]
            item["entitlementSource"] = "PURCHASE"
        }
        let card = try XCTUnwrap(try MyExamsTestFixtures.decodeMyExamCards(from: try MyExamsTestFixtures.makeMyExamsPayload(items: [cardJSON])).first)
        let buttons = CardButtons.of(card)

        XCTAssertNil(buttons.continueAttemptId)
        XCTAssertEqual(buttons.startEntitlementId, "ent-fallback")
    }

    func testClassDiscontinuedAndPendingFulfillmentHideBuyButtons() throws {
        let classCardJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "EXHAUSTED") { item in
            item["code"] = "CLS-1"
            item["saleState"] = "ON_SALE"
            item["hasPendingFulfillment"] = false
            item["entitlementSource"] = "CLASS"
            item["actions"] = ["REBUY", "BUY_MORE", "HISTORY"]
        }
        let discontinuedJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "EXHAUSTED") { item in
            item["code"] = "DISC-1"
            item["saleState"] = "DISCONTINUED"
            item["hasPendingFulfillment"] = false
            item["entitlementSource"] = "PURCHASE"
            item["actions"] = ["REBUY", "BUY_MORE", "HISTORY"]
        }
        let pendingJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-pend"
            item["code"] = "PEND-1"
            item["saleState"] = "ON_SALE"
            item["hasPendingFulfillment"] = true
            item["pendingOrderCode"] = "DH002"
            item["entitlementSource"] = "PURCHASE"
            item["actions"] = ["START", "REBUY", "BUY_MORE"]
        }
        let cards = try MyExamsTestFixtures.decodeMyExamCards(
            from: try MyExamsTestFixtures.makeMyExamsPayload(items: [classCardJSON, discontinuedJSON, pendingJSON])
        )
        for card in cards {
            let buttons = CardButtons.of(card)
            XCTAssertNil(buttons.rebuyCode, "Card \(card.code) must hide REBUY")
            XCTAssertNil(buttons.buyMoreCode, "Card \(card.code) must hide BUY_MORE")
        }
    }
}
