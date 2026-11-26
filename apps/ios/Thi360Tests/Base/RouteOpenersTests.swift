import Foundation
import XCTest
@testable import Thi360

@MainActor
final class RouteOpenersTests: XCTestCase {
    func testOpenProduct() {
        let router = AppRouter(initialDestination: .home)
        router.openProduct(code: "ielts-01")
        XCTAssertEqual(router.path(for: .home), [.product(code: "ielts-01")])
        XCTAssertEqual(router.path(for: .myExams), [])
        XCTAssertEqual(router.path(for: .history), [])
        XCTAssertEqual(router.path(for: .account), [])
        XCTAssertNil(router.presentedAttempt)
    }

    func testOpenCheckout() {
        let router = AppRouter(initialDestination: .home)
        router.openCheckout(code: "ielts-01", seenVersionNo: 2, seenPrice: 199_000)
        XCTAssertEqual(router.path(for: .home), [.checkout(code: "ielts-01", seenVersionNo: 2, seenPrice: 199_000)])
        XCTAssertNil(router.presentedAttempt)
    }

    func testOpenPaymentResult() {
        let router = AppRouter(initialDestination: .home)
        router.openPaymentResult(.byOrder(orderCode: "ORD-1"))
        let url = URL(string: "https://student.thi360.test/payment/vnpay-return?vnp_TxnRef=ORD-2")!
        router.openPaymentResult(.byReturnLink(url))
        XCTAssertEqual(
            router.path(for: .home),
            [.paymentResult(.byOrder(orderCode: "ORD-1")), .paymentResult(.byReturnLink(url))]
        )
        XCTAssertNil(router.presentedAttempt)
    }

    func testOpenOrdersAndOrder() {
        let router = AppRouter(initialDestination: .account)
        router.openOrders()
        router.openOrder(orderCode: "ORD-99")
        XCTAssertEqual(router.path(for: .account), [.orders, .order(orderCode: "ORD-99")])
        XCTAssertEqual(router.path(for: .home), [])
        XCTAssertNil(router.presentedAttempt)
    }

    func testOpenPrepare() {
        let router = AppRouter(initialDestination: .myExams)
        router.openPrepare(entitlementId: "ent-1")
        XCTAssertEqual(router.path(for: .myExams), [.prepare(entitlementId: "ent-1")])
        XCTAssertNil(router.presentedAttempt)
    }

    func testOpenResultAndReview() {
        let router = AppRouter(initialDestination: .history)
        router.openResult(attemptId: "att-1")
        router.openReview(attemptId: "att-1")
        XCTAssertEqual(router.path(for: .history), [.result(attemptId: "att-1"), .review(attemptId: "att-1")])
        XCTAssertNil(router.presentedAttempt)
    }

    func testOpenProfileAndProfileVerifyEmail() {
        let router = AppRouter(initialDestination: .account)
        router.openProfile()
        router.openProfileVerifyEmail()
        XCTAssertEqual(router.path(for: .account), [.profile, .profileVerifyEmail])
        XCTAssertNil(router.presentedAttempt)
    }

    func testOpenDeleteAccount() {
        let router = AppRouter(initialDestination: .account)
        router.openDeleteAccount()
        XCTAssertEqual(router.path(for: .account), [.deleteAccount])
        XCTAssertEqual(router.path(for: .home), [])
        XCTAssertNil(router.presentedAttempt)
    }

    func testOpenLegal() {
        let router = AppRouter(initialDestination: .account)
        router.openLegal(.terms)
        router.openLegal(.privacy)
        XCTAssertEqual(router.path(for: .account), [.legal(.terms), .legal(.privacy)])
        XCTAssertNil(router.presentedAttempt)
    }

    func testOpenAttemptPreservesOriginStackBeneath() {
        let router = AppRouter(initialDestination: .myExams)
        router.openOrder(orderCode: "ORD-1")
        router.openAttempt(attemptId: "att-10")

        XCTAssertEqual(router.presentedAttempt, AttemptRef(attemptId: "att-10", originDestination: .myExams))
        XCTAssertEqual(router.path(for: .myExams), [.order(orderCode: "ORD-1")])
        XCTAssertEqual(router.path(for: .home), [])
    }

    func testReplaceAttemptDoesNotKeepPreviousAttemptOnBack() {
        let router = AppRouter(initialDestination: .myExams)
        router.openAttempt(attemptId: "att-old")
        router.replaceAttempt(attemptId: "att-new")

        XCTAssertEqual(router.presentedAttempt, AttemptRef(attemptId: "att-new", originDestination: .myExams))
        XCTAssertEqual(router.path(for: .myExams), [])

        router.back()
        XCTAssertNil(router.presentedAttempt)
        XCTAssertEqual(router.selectedDestination, .myExams)
        XCTAssertEqual(router.path(for: .myExams), [])
    }

    func testCloseAttemptReturnsToOriginDestination() {
        let router = AppRouter(initialDestination: .myExams)
        router.openOrder(orderCode: "ORD-7")
        router.openAttempt(attemptId: "att-7")
        router.selectedDestination = .home

        router.closeAttempt()
        XCTAssertNil(router.presentedAttempt)
        XCTAssertEqual(router.selectedDestination, .myExams)
        XCTAssertEqual(router.path(for: .myExams), [.order(orderCode: "ORD-7")])
    }

    func testOpenAttemptAfterStartRemovesPrepareAndPresentsAttempt() {
        let router = AppRouter(initialDestination: .myExams)
        router.openOrder(orderCode: "ORD-1")
        router.openPrepare(entitlementId: "ent-1")
        router.openAttemptAfterStart(attemptId: "att-start")

        XCTAssertEqual(router.presentedAttempt, AttemptRef(attemptId: "att-start", originDestination: .myExams))
        XCTAssertEqual(router.path(for: .myExams), [.order(orderCode: "ORD-1")])
        XCTAssertFalse(router.path(for: .myExams).contains(.prepare(entitlementId: "ent-1")))
    }

    func testOpenResultFromAttemptStripsAttemptFlowAndPushesResultOnOrigin() {
        let router = AppRouter(initialDestination: .myExams)
        router.openOrder(orderCode: "ORD-1")
        router.openPrepare(entitlementId: "ent-1")
        router.openAttempt(attemptId: "att-5")

        router.openResultFromAttempt(attemptId: "att-5")

        XCTAssertNil(router.presentedAttempt)
        XCTAssertEqual(router.selectedDestination, .myExams)
        XCTAssertEqual(router.path(for: .myExams), [.order(orderCode: "ORD-1"), .result(attemptId: "att-5")])

        router.back()
        XCTAssertEqual(router.path(for: .myExams), [.order(orderCode: "ORD-1")])
    }

    func testSwitchTabReloadTrueResetsDestinationStack() {
        let router = AppRouter(initialDestination: .myExams)
        router.openAttempt(attemptId: "att-closed")
        router.closeAttempt()
        router.openPrepare(entitlementId: "ent-9")
        router.switchTab(.myExams, reload: true)

        XCTAssertNil(router.presentedAttempt)
        XCTAssertEqual(router.selectedDestination, .myExams)
        XCTAssertEqual(router.path(for: .myExams), [])
        XCTAssertEqual(router.reloadCount(for: .myExams), 1)
    }
}
