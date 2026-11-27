import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class PaymentResultViewTests: XCTestCase {
    private let demoEnv = AppEnvironment(
        kind: .demo,
        apiBaseURL: URL(string: "https://thi360.zone17.click")!,
        webBaseURL: URL(string: "https://thi360.zone17.click")!
    )

    func testVerifyInvalidSignature422And400DoNotReadOrder() async throws {
        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/public/payments/vnpay/return",
                    statusCode: 422,
                    relativeFixturePath: "api/vnpay-return-422-vnpay-signature-invalid.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let link = try XCTUnwrap(PaymentReturnLink.parse(
                URL(string: "https://thi360.zone17.click/checkout/result/app?vnp_TxnRef=DH1&vnp_SecureHash=bad")!,
                environment: demoEnv
            ))
            let vm = PaymentResultViewModel(
                initialEntry: .byReturnLink(link),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.startIfNeeded()

            XCTAssertEqual(vm.state, .invalidSignature)
            XCTAssertEqual(harness.transport.callCount(method: .get, path: "/api/v1/me/orders/DH261126000001"), 0)
        }

        // 400 validation failed (Plan A6 derived from certified vnpay-return-422-vnpay-signature-invalid.json)
        do {
            let badRequestData = try PaymentFixtureHelper.mutatingFixture("vnpay-return-422-vnpay-signature-invalid.json") { obj in
                obj["code"] = "VALIDATION_FAILED"
            }
            let harness = try PaymentTestFactory.makeHarness { transport in
                transport.registerJSON(
                    method: .get,
                    path: "/api/v1/public/payments/vnpay/return",
                    statusCode: 400,
                    data: badRequestData
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let link = try XCTUnwrap(PaymentReturnLink.parse(
                URL(string: "https://thi360.zone17.click/checkout/result/app?vnp_TxnRef=DH1")!,
                environment: demoEnv
            ))
            let vm = PaymentResultViewModel(
                initialEntry: .byReturnLink(link),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.startIfNeeded()
            XCTAssertEqual(vm.state, .invalidSignature)
        }
    }

    func testVerifyNotFoundAndVerifyServerError() async throws {
        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/public/payments/vnpay/return",
                    statusCode: 404,
                    relativeFixturePath: "api/vnpay-return-404-order-not-found.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let link = try XCTUnwrap(PaymentReturnLink.parse(
                URL(string: "https://thi360.zone17.click/checkout/result/app?vnp_TxnRef=MISSING")!,
                environment: demoEnv
            ))
            let vm = PaymentResultViewModel(
                initialEntry: .byReturnLink(link),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.startIfNeeded()
            XCTAssertEqual(vm.state, .verifyNotFound)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                transport.registerEmpty(
                    method: .get,
                    path: "/api/v1/public/payments/vnpay/return",
                    statusCode: 500
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let link = try XCTUnwrap(PaymentReturnLink.parse(
                URL(string: "https://thi360.zone17.click/checkout/result/app?vnp_TxnRef=DH1")!,
                environment: demoEnv
            ))
            let vm = PaymentResultViewModel(
                initialEntry: .byReturnLink(link),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.startIfNeeded()
            XCTAssertEqual(vm.state, .loadError(canRetryVerify: true))
        }
    }

    func testFulfilledFreeVoucherFailedAndClosedStates() async throws {
        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/me/orders/DH261126000001",
                    statusCode: 200,
                    relativeFixturePath: "api/orders-detail-200-free-voucher.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = PaymentResultViewModel(
                initialEntry: .byOrder(orderCode: "DH261126000001"),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.startIfNeeded()

            if case let .fulfilled(order) = vm.state {
                XCTAssertEqual(order.amountDueVnd, 0)
                XCTAssertGreaterThan(order.voucherDiscountVnd, 0)
            } else {
                XCTFail("Expected .fulfilled")
            }
        }

        XCTAssertEqual(VnpayReasonMapper.message(for: "24", locale: .vi), "Bạn đã hủy giao dịch trên VNPAY.")
        XCTAssertEqual(VnpayReasonMapper.message(for: "ZZ", locale: .vi), "Ngân hàng hoặc VNPAY từ chối giao dịch.")

        for fixture in [
            "orders-detail-200-expired.json",
            "orders-detail-200-cancelled.json",
            "orders-detail-200-replaced.json",
            "orders-detail-200-refunded.json"
        ] {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/me/orders/DH261126000001",
                    statusCode: 200,
                    relativeFixturePath: "api/\(fixture)"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = PaymentResultViewModel(
                initialEntry: .byOrder(orderCode: "DH261126000001"),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.startIfNeeded()
            if case .closed = vm.state {
            } else {
                XCTFail("Expected .closed for \(fixture), got \(vm.state)")
            }
        }
    }

    func testVietnamTimeRegardlessOfDeviceZone() {
        let originalDefault = NSTimeZone.default
        NSTimeZone.default = TimeZone(secondsFromGMT: 0)!
        defer { NSTimeZone.default = originalDefault }

        let utcDate = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:00Z")!
        XCTAssertEqual(PaymentFormatters.formatDateTime(utcDate), "20/11/2026 17:00")
        XCTAssertEqual(PaymentFormatters.formatTimeOnly(utcDate), "17:00")

        let lateUTC = FlexibleISO8601DateTranscoder.parse("2026-11-20T19:30:00Z")!
        XCTAssertEqual(PaymentFormatters.formatDateTime(lateUTC), "21/11/2026 02:30")
    }

    func testOldTxnRefFollowsOrderState() async throws {
        let orderWithNewerAttempt = try PaymentFixtureHelper.mutatingFixture("orders-detail-200-pending-payment.json") { obj in
            obj["payments"] = [
                [
                    "id": "00000000-0000-7000-8000-000000000011",
                    "attemptSeq": 1,
                    "txnRef": "DH26112600000101",
                    "amountVnd": 200_000,
                    "state": "FAILED",
                    "duplicate": false,
                    "responseCode": "24",
                    "createdAt": "2026-11-20T10:00:00Z"
                ],
                [
                    "id": "00000000-0000-7000-8000-000000000012",
                    "attemptSeq": 2,
                    "txnRef": "DH26112600000102",
                    "amountVnd": 200_000,
                    "state": "CREATED",
                    "duplicate": false,
                    "createdAt": "2026-11-20T10:02:00Z"
                ]
            ]
        }

        let harness = try PaymentTestFactory.makeHarness { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/public/payments/vnpay/return",
                statusCode: 200,
                relativeFixturePath: "api/vnpay-return-200-failed.json"
            )
            transport.registerJSON(
                method: .get,
                path: "/api/v1/me/orders/DH261126000001",
                statusCode: 200,
                data: orderWithNewerAttempt
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let oldLink = try XCTUnwrap(PaymentReturnLink.parse(
            URL(string: "https://thi360.zone17.click/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=24")!,
            environment: demoEnv
        ))
        let poller = OrderPoller(interval: 2.0, window: 60.0, clock: harness.clock, sleep: { _ in
            try await Task.sleep(nanoseconds: 60_000_000_000)
        })
        let vm = PaymentResultViewModel(
            initialEntry: .byReturnLink(oldLink),
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            poller: poller,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            clock: harness.clock
        )
        await vm.startIfNeeded()
        defer { Task { await vm.onLeaveScreen(clearPendingOrder: false) } }

        if case .checking = vm.state {
        } else {
            XCTFail("Old txnRef link must follow current order state (.checking), not .failed; got \(vm.state)")
        }
    }
}
