import Foundation
import XCTest
@testable import Thi360

final class RouteTableTests: XCTestCase {
    func testAllSixteenRoutesHaveDistinctLogicalNamesAndLocalizedKeys() {
        let returnURL = URL(string: "https://student.thi360.test/payment/vnpay-return?vnp_TxnRef=ORD-1")!
        let allRoutes: [AppRoute] = [
            .catalog,
            .product(code: "ielts-01"),
            .checkout(code: "ielts-01", seenVersionNo: 2, seenPrice: 199_000),
            .paymentResult(.byOrder(orderCode: "ORD-1")),
            .orders,
            .order(orderCode: "ORD-1"),
            .myExams,
            .history(resetFilters: true),
            .prepare(entitlementId: "ent-1"),
            .attempt(attemptId: "att-1"),
            .result(attemptId: "att-1"),
            .review(attemptId: "att-1"),
            .profile,
            .profileVerifyEmail,
            .deleteAccount,
            .legal(.terms)
        ]

        XCTAssertEqual(allRoutes.count, 16)
        XCTAssertEqual(Set(allRoutes.map(\.logicalName)).count, 16)

        for route in allRoutes {
            XCTAssertFalse(ShellStrings.text(route.nameKey, locale: .vi).isEmpty, "Missing vi name for \(route.logicalName)")
            XCTAssertFalse(ShellStrings.text(route.nameKey, locale: .en).isEmpty, "Missing en name for \(route.logicalName)")
        }

        let byReturn = AppRoute.paymentResult(.byReturnLink(returnURL))
        XCTAssertEqual(byReturn.logicalName, "payment-result")
        XCTAssertNotEqual(byReturn, .paymentResult(.byOrder(orderCode: "ORD-1")))

        let checkoutNoOptional = AppRoute.checkout(code: "ielts-01")
        XCTAssertEqual(checkoutNoOptional.parameterSummary.map(\.key), ["code"])
        let checkoutWithOptional = AppRoute.checkout(code: "ielts-01", seenVersionNo: 3, seenPrice: 250_000)
        XCTAssertEqual(checkoutWithOptional.parameterSummary.map(\.key), ["code", "seenVersionNo", "seenPrice"])
    }

    func testDecodeValidPathsAndRejectMalformedOrUnsupportedPaths() {
        XCTAssertEqual(AppRoute.decode(path: "catalog"), .catalog)
        XCTAssertEqual(AppRoute.decode(path: "/product/ielts-01"), .product(code: "ielts-01"))
        XCTAssertEqual(AppRoute.decode(path: "checkout/ielts-01"), .checkout(code: "ielts-01"))
        XCTAssertEqual(
            AppRoute.decode(path: "checkout/ielts-01?seenVersionNo=4&seenPrice=120000"),
            .checkout(code: "ielts-01", seenVersionNo: 4, seenPrice: 120_000)
        )
        XCTAssertEqual(
            AppRoute.decode(path: "payment-result/ORD-9"),
            .paymentResult(.byOrder(orderCode: "ORD-9"))
        )
        XCTAssertEqual(
            AppRoute.decode(path: "payment-result?orderCode=ORD-10"),
            .paymentResult(.byOrder(orderCode: "ORD-10"))
        )
        XCTAssertEqual(AppRoute.decode(path: "orders"), .orders)
        XCTAssertEqual(AppRoute.decode(path: "order/ORD-9"), .order(orderCode: "ORD-9"))
        XCTAssertEqual(AppRoute.decode(path: "my-exams"), .myExams)
        XCTAssertEqual(AppRoute.decode(path: "history"), .history(resetFilters: false))
        XCTAssertEqual(AppRoute.decode(path: "history?resetFilters=true"), .history(resetFilters: true))
        XCTAssertEqual(AppRoute.decode(path: "prepare/ent-7"), .prepare(entitlementId: "ent-7"))
        XCTAssertEqual(AppRoute.decode(path: "attempt/att-7"), .attempt(attemptId: "att-7"))
        XCTAssertEqual(AppRoute.decode(path: "result/att-7"), .result(attemptId: "att-7"))
        XCTAssertEqual(AppRoute.decode(path: "review/att-7"), .review(attemptId: "att-7"))
        XCTAssertEqual(AppRoute.decode(path: "profile"), .profile)
        XCTAssertEqual(AppRoute.decode(path: "profile/verify-email"), .profileVerifyEmail)
        XCTAssertEqual(AppRoute.decode(path: "account/delete"), .deleteAccount)
        XCTAssertEqual(AppRoute.decode(path: "legal/terms"), .legal(.terms))
        XCTAssertEqual(AppRoute.decode(path: "legal/privacy"), .legal(.privacy))

        XCTAssertNil(AppRoute.decode(path: "profile/email"), "profile/email must not exist in route table")
        XCTAssertNil(AppRoute.decode(path: ""))
        XCTAssertNil(AppRoute.decode(path: "product"))
        XCTAssertNil(AppRoute.decode(path: "product/"))
        XCTAssertNil(AppRoute.decode(path: "checkout/ielts-01?seenVersionNo=notanumber"))
        XCTAssertNil(AppRoute.decode(path: "checkout/ielts-01?seenPrice=abc"))
        XCTAssertNil(AppRoute.decode(path: "payment-result"))
        XCTAssertNil(AppRoute.decode(path: "history?resetFilters=maybe"))
        XCTAssertNil(AppRoute.decode(path: "prepare"))
        XCTAssertNil(AppRoute.decode(path: "attempt"))
        XCTAssertNil(AppRoute.decode(path: "result"))
        XCTAssertNil(AppRoute.decode(path: "review"))
        XCTAssertNil(AppRoute.decode(path: "legal/unknown"))
        XCTAssertNil(AppRoute.decode(path: "unknown/route"))
    }
}
