import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class CheckoutQuoteTests: XCTestCase {
    func testRequestFields() async throws {
        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                seenPrice: SeenPrice(versionNo: 2, price: 250_000),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()

            let quoteRequest = try XCTUnwrap(harness.transport.requests(method: .post, path: "/api/v1/me/price-quote").first)
            let body = try PaymentFixtureHelper.requestBodyJSON(quoteRequest)
            XCTAssertEqual(body["code"] as? String, "EXAM-DEMO-01")
            XCTAssertEqual(body["seenPrice"] as? Int, 250_000)
            XCTAssertEqual(body["seenVersionNo"] as? Int, 2)
            XCTAssertNil(body["voucherCode"])
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                seenPrice: SeenPrice(),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()

            let quoteRequest = try XCTUnwrap(harness.transport.requests(method: .post, path: "/api/v1/me/price-quote").first)
            let body = try PaymentFixtureHelper.requestBodyJSON(quoteRequest)
            XCTAssertEqual(body["code"] as? String, "EXAM-DEMO-01")
            XCTAssertNil(body["seenPrice"])
            XCTAssertNil(body["seenVersionNo"])
        }
    }

    func testSummaryLines() async throws {
        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, quoteFixture: "price-quote-200.json")
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()

            let view = CheckoutView(
                viewModel: vm,
                locale: .vi,
                supportContact: .empty,
                fallbackUsername: nil,
                onBackToProduct: {},
                onBackToCatalog: {},
                onSwitchAccount: {},
                onEnterExam: { _ in },
                onContinueAttempt: { _ in },
                onViewPendingOrder: { _ in }
            )
            let (window, controller) = RecoveryViewTestHelper.host(view, size: CGSize(width: 390, height: 1500), sizeClass: .compact)
            defer { window.isHidden = true }

            XCTAssertGreaterThan(controller.view.bounds.width, 0)
            XCTAssertEqual(vm.activeQuote?.listPrice, 200_000)
            XCTAssertEqual(vm.activeQuote?.finalPrice, 200_000)
            XCTAssertNil(vm.activeQuote?.promotion)
            XCTAssertNil(vm.activeQuote?.voucher)
        }

        do {
            let combinedQuoteData = try PaymentFixtureHelper.mutatingFixture("price-quote-200-voucher.json") { obj in
                obj["promotion"] = [
                    "id": "00000000-0000-7000-8000-000000000099",
                    "name": "Ưu đãi khai giảng",
                    "discountType": "AMOUNT",
                    "discountValue": 20_000,
                    "discountVnd": 20_000,
                    "endsAt": "2026-12-31T23:59:59Z"
                ]
                obj["discountApplied"] = "COMBINED"
                obj["finalPrice"] = 140_000
            }
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
                transport.registerJSON(method: .post, path: "/api/v1/me/price-quote", statusCode: 200, data: combinedQuoteData)
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()

            let view = CheckoutView(
                viewModel: vm,
                locale: .vi,
                supportContact: .empty,
                fallbackUsername: nil,
                onBackToProduct: {},
                onBackToCatalog: {},
                onSwitchAccount: {},
                onEnterExam: { _ in },
                onContinueAttempt: { _ in },
                onViewPendingOrder: { _ in }
            )
            let (window, controller) = RecoveryViewTestHelper.host(view, size: CGSize(width: 390, height: 1500), sizeClass: .compact)
            defer { window.isHidden = true }

            XCTAssertGreaterThan(controller.view.bounds.width, 0)
            XCTAssertEqual(vm.activeQuote?.promotion?.discountVnd, 20_000)
            XCTAssertEqual(vm.activeQuote?.voucher?.discountVnd, 40_000)
            XCTAssertEqual(vm.activeQuote?.finalPrice, 140_000)
            XCTAssertEqual(PaymentFormatters.formatVnd(140_000, locale: .vi), "140.000 đ")
        }
    }

    func testCtaLabels() async throws {
        XCTAssertEqual(PaymentStrings.text("checkout.summary.ctaVnpay", locale: .vi), "Thanh toán qua VNPAY")
        XCTAssertEqual(PaymentStrings.text("checkout.summary.ctaFree", locale: .vi), "Dùng voucher và nhận bài thi")
        XCTAssertEqual(PaymentStrings.text("checkout.summary.freeVoucher", locale: .vi), "0 đ (đã áp dụng voucher)")
        XCTAssertEqual(PaymentStrings.text("checkout.summary.ctaFreeNoVoucher", locale: .vi), "Nhận bài thi miễn phí")

        let freeWithVoucherData = try PaymentFixtureHelper.mutatingFixture("price-quote-200-voucher.json") { obj in
            obj["finalPrice"] = 0
        }
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            transport.registerJSON(method: .post, path: "/api/v1/me/price-quote", statusCode: 200, data: freeWithVoucherData)
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = CheckoutViewModel(
            code: "EXAM-DEMO-01",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await vm.loadAll()

        XCTAssertEqual(vm.activeQuote?.finalPrice, 0)
        XCTAssertNotNil(vm.activeQuote?.voucher)
        XCTAssertTrue(vm.isPayButtonEnabled)
    }

    func testQuoteErrorNoAutoRetry() async throws {
        for statusCode in [500, 404] {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
                if statusCode == 404 {
                    try transport.registerFixture(
                        method: .post,
                        path: "/api/v1/me/price-quote",
                        statusCode: 404,
                        relativeFixturePath: "api/price-quote-404-product-not-found.json"
                    )
                } else {
                    transport.registerEmpty(method: .post, path: "/api/v1/me/price-quote", statusCode: 500)
                }
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()

            XCTAssertEqual(harness.transport.callCount(method: .post, path: "/api/v1/me/price-quote"), 1)
            XCTAssertNil(vm.activeQuote)
            XCTAssertFalse(vm.isPayButtonEnabled)

            await vm.retryLoadQuote()
            XCTAssertEqual(harness.transport.callCount(method: .post, path: "/api/v1/me/price-quote"), 2)
        }
    }
}
