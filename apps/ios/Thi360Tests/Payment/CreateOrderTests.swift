import HTTPTypes
import XCTest
@testable import Thi360

@MainActor
final class CreateOrderTests: XCTestCase {
    func testRequestHeaderAndBody() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, quoteFixture: "price-quote-200-voucher.json")
            try transport.registerFixture(
                method: .post,
                path: "/api/v1/me/orders",
                statusCode: 201,
                relativeFixturePath: "api/orders-create-201-redirect.json"
            )
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
        await vm.createOrder()

        let req = try XCTUnwrap(harness.transport.requests(method: .post, path: "/api/v1/me/orders").first)
        let keyHeader = try XCTUnwrap(req.header(HTTPField.Name("Idempotency-Key")!))
        XCTAssertNotNil(UUID(uuidString: keyHeader), "Idempotency-Key must be a valid UUID")

        let body = try PaymentFixtureHelper.requestBodyJSON(req)
        XCTAssertEqual(body["code"] as? String, "EXAM-DEMO-01")
        XCTAssertEqual(body["expectedAccountId"] as? String, "019320a0-0000-7000-8000-000000000001")
        XCTAssertEqual(body["expectedAmountDue"] as? Int, 160_000)
        XCTAssertEqual(body["expectedVersionNo"] as? Int, 1)
        XCTAssertEqual(body["voucherCode"] as? String, "EXAM-DEMO-03")
    }

    func testNextRedirectFulfilledChecking() async throws {
        do {
            var openedResult: (String, Bool)?
            let harness = try PaymentTestFactory.makeHarness(browserOutcomes: [.dismissed]) { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/orders",
                    statusCode: 201,
                    relativeFixturePath: "api/orders-create-201-redirect.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL,
                clock: harness.clock,
                onOpenPaymentResult: { code, fromChecking in openedResult = (code, fromChecking) }
            )
            await vm.loadAll()
            await vm.createOrder()

            XCTAssertEqual(harness.browser.openedURLs.count, 1)
            XCTAssertEqual(
                harness.browser.openedURLs.first?.absoluteString,
                "http://localhost:3000/mock-vnpay/pay?vnp_TxnRef=DH26112600000101&vnp_SecureHash=fixture-vnpay-hash"
            )
            let pending = await harness.pendingOrderStore.readValid(now: harness.clock.now)
            XCTAssertEqual(pending?.orderCode, "DH261126000001")
            XCTAssertEqual(openedResult?.0, "DH261126000001")
            XCTAssertEqual(openedResult?.1, false)
        }

        do {
            var openedResult: (String, Bool)?
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/orders",
                    statusCode: 201,
                    relativeFixturePath: "api/orders-create-201-fulfilled.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL,
                onOpenPaymentResult: { code, fromChecking in openedResult = (code, fromChecking) }
            )
            await vm.loadAll()
            await vm.createOrder()

            XCTAssertTrue(harness.browser.openedURLs.isEmpty)
            XCTAssertEqual(openedResult?.0, "DH261126000001")
            XCTAssertEqual(openedResult?.1, false)
        }

        do {
            var openedResult: (String, Bool)?
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/orders",
                    statusCode: 201,
                    relativeFixturePath: "api/orders-create-201-checking.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL,
                onOpenPaymentResult: { code, fromChecking in openedResult = (code, fromChecking) }
            )
            await vm.loadAll()
            await vm.createOrder()

            XCTAssertTrue(harness.browser.openedURLs.isEmpty)
            XCTAssertEqual(openedResult?.0, "DH261126000001")
            XCTAssertEqual(openedResult?.1, true)
        }
    }
}
