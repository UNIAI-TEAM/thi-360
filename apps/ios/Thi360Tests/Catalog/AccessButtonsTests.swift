import XCTest
@testable import Thi360

final class AccessButtonsTests: XCTestCase {
    func testCompleteR22MatrixForCardAndPricePanel() {
        let inProgress = ExamAccessRow(
            code: "EX-1",
            state: .inProgress,
            attemptsLeft: 1,
            entitlementId: "E1",
            activeAttemptId: "A1",
            hasPendingFulfillment: false,
            pendingOrderCode: nil,
            items: nil
        )
        let inProgCard = AccessButtons.forCard(row: inProgress, isBundle: false, seenVersionNo: 3, seenPrice: 150_000)
        XCTAssertEqual(inProgCard.leadNote, .continueInProgress)
        XCTAssertEqual(inProgCard.primary, .continue(attemptId: "A1"))
        XCTAssertEqual(inProgCard.secondary, .buyMore(code: "EX-1", seenVersionNo: 3, seenPrice: 150_000))
        XCTAssertNil(inProgCard.trailingNote)
        XCTAssertTrue(inProgCard.showsViewDetail)

        let inProgPanel = AccessButtons.forPricePanel(row: inProgress, seenVersionNo: 3, seenPrice: 150_000)
        XCTAssertEqual(inProgPanel.primary, .continue(attemptId: "A1"))
        XCTAssertEqual(inProgPanel.secondary, .buyMore(code: "EX-1", seenVersionNo: 3, seenPrice: 150_000))
        XCTAssertFalse(inProgPanel.showsViewDetail)

        let inProgressPending = ExamAccessRow(
            code: "EX-1",
            state: .inProgress,
            attemptsLeft: 1,
            entitlementId: "E1",
            activeAttemptId: "A1",
            hasPendingFulfillment: true,
            pendingOrderCode: "DH001",
            items: nil
        )
        let inProgPendingCard = AccessButtons.forCard(row: inProgressPending, isBundle: false, seenVersionNo: 3, seenPrice: 150_000)
        XCTAssertEqual(inProgPendingCard.leadNote, .continueInProgress)
        XCTAssertEqual(inProgPendingCard.primary, .continue(attemptId: "A1"))
        XCTAssertNil(inProgPendingCard.secondary)
        XCTAssertEqual(inProgPendingCard.trailingNote, .pendingTopUp(orderCode: "DH001"))

        let canStart = ExamAccessRow(
            code: "EX-2",
            state: .canStart,
            attemptsLeft: 2,
            entitlementId: "E",
            activeAttemptId: nil,
            hasPendingFulfillment: false,
            pendingOrderCode: nil,
            items: nil
        )
        let canStartCard = AccessButtons.forCard(row: canStart, isBundle: false, seenVersionNo: 3, seenPrice: 150_000)
        XCTAssertEqual(canStartCard.leadNote, .attemptsLeft(2))
        XCTAssertEqual(canStartCard.primary, .enter(entitlementId: "E"))
        XCTAssertEqual(canStartCard.secondary, .buyMore(code: "EX-2", seenVersionNo: 3, seenPrice: 150_000))
        XCTAssertTrue(canStartCard.showsViewDetail)

        let canStartPanel = AccessButtons.forPricePanel(row: canStart, seenVersionNo: 3, seenPrice: 150_000)
        XCTAssertEqual(canStartPanel.leadNote, .attemptsLeft(2))
        XCTAssertEqual(canStartPanel.primary, .enter(entitlementId: "E"))
        XCTAssertEqual(canStartPanel.secondary, .buyMore(code: "EX-2", seenVersionNo: 3, seenPrice: 150_000))
        XCTAssertFalse(canStartPanel.showsViewDetail)

        let exhausted = ExamAccessRow(
            code: "EX-3",
            state: .exhausted,
            attemptsLeft: 0,
            entitlementId: "E3",
            activeAttemptId: nil,
            hasPendingFulfillment: false,
            pendingOrderCode: nil,
            items: nil
        )
        let exhaustedCard = AccessButtons.forCard(row: exhausted, isBundle: false, seenVersionNo: 3, seenPrice: 150_000)
        XCTAssertNil(exhaustedCard.leadNote)
        XCTAssertEqual(exhaustedCard.primary, .buy(code: "EX-3", seenVersionNo: 3, seenPrice: 150_000))
        XCTAssertNil(exhaustedCard.secondary)
        XCTAssertTrue(exhaustedCard.showsViewDetail)

        let exhaustedPanel = AccessButtons.forPricePanel(row: exhausted, seenVersionNo: 3, seenPrice: 150_000)
        XCTAssertEqual(exhaustedPanel.leadNote, .exhausted)
        XCTAssertEqual(exhaustedPanel.primary, .buyAgain(code: "EX-3", seenVersionNo: 3, seenPrice: 150_000))
        XCTAssertFalse(exhaustedPanel.showsViewDetail)

        let checkingWithCode = ExamAccessRow(
            code: "EX-4",
            state: .checking,
            attemptsLeft: 0,
            entitlementId: nil,
            activeAttemptId: nil,
            hasPendingFulfillment: true,
            pendingOrderCode: "DH002",
            items: nil
        )
        let checkingCard = AccessButtons.forCard(row: checkingWithCode, isBundle: false, seenVersionNo: 1, seenPrice: 100_000)
        XCTAssertEqual(checkingCard.primary, .checking(orderCode: "DH002"))
        XCTAssertEqual(checkingCard.trailingNote, .checkingOrder(orderCode: "DH002"))

        let checkingNoCode = ExamAccessRow(
            code: "EX-4",
            state: .checking,
            attemptsLeft: 0,
            entitlementId: nil,
            activeAttemptId: nil,
            hasPendingFulfillment: true,
            pendingOrderCode: "",
            items: nil
        )
        let checkingNoCodeCard = AccessButtons.forCard(row: checkingNoCode, isBundle: false, seenVersionNo: 1, seenPrice: 100_000)
        XCTAssertEqual(checkingNoCodeCard.primary, .checking(orderCode: nil))
        XCTAssertNil(checkingNoCodeCard.trailingNote)

        let noneRow = ExamAccessRow.none(code: "BD-1")
        let bundleCard = AccessButtons.forCard(row: noneRow, isBundle: true, seenVersionNo: 2, seenPrice: 320_000)
        XCTAssertEqual(bundleCard.primary, .buy(code: "BD-1", seenVersionNo: 2, seenPrice: 320_000))
        XCTAssertTrue(bundleCard.isBundle)
        XCTAssertTrue(bundleCard.showsViewDetail)
    }
}
