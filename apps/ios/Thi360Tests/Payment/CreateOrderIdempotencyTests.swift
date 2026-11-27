import HTTPTypes
import XCTest
@testable import Thi360

@MainActor
final class CreateOrderIdempotencyTests: XCTestCase {
    func testDoubleTapSendsOnce() async throws {
        let gate = TestAsyncGate()
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            transport.register(method: .post, path: "/api/v1/me/orders") { _ in
                await gate.enterAndWait()
                let data = try ContractFixtureLoader.loadApiFixture("orders-create-201-fulfilled.json")
                return ContractFakeTransport.jsonResponse(statusCode: 201, data: data)
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

        let first = Task { await vm.createOrder() }
        await gate.waitUntilEntered()
        XCTAssertEqual(vm.submit, .submitting)
        XCTAssertFalse(vm.isPayButtonEnabled)

        await vm.createOrder()
        await gate.release()
        await first.value

        XCTAssertEqual(harness.transport.callCount(method: .post, path: "/api/v1/me/orders"), 1)
    }

    func testTransientErrorsKeepKey() async throws {
        let keys = SequenceKeyGenerator()
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            transport.enqueue(method: .post, path: "/api/v1/me/orders", handlers: [
                { _ in throw URLError(.timedOut) },
                { _ in throw URLError(.notConnectedToInternet) },
                { _ in ContractFakeTransport.emptyResponse(statusCode: 408) },
                { _ in ContractFakeTransport.emptyResponse(statusCode: 429) },
                { _ in ContractFakeTransport.emptyResponse(statusCode: 503) },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-create-201-fulfilled.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 201, data: data)
                }
            ])
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = CheckoutViewModel(
            code: "EXAM-DEMO-01",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            keyGenerator: { keys.next() }
        )
        await vm.loadAll()

        for _ in 0..<6 {
            await vm.createOrder()
        }

        let requests = harness.transport.requests(method: .post, path: "/api/v1/me/orders")
        XCTAssertEqual(requests.count, 6)
        let sentKeys = requests.compactMap { $0.header(HTTPField.Name("Idempotency-Key")!) }
        XCTAssertEqual(sentKeys.count, 6)
        XCTAssertEqual(Set(sentKeys).count, 1, "All retries after transient errors must reuse the exact same Idempotency-Key")
    }

    func testNewKeyAfterSuccess4xxOrVoucherChange() async throws {
        let keys = SequenceKeyGenerator()
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            transport.enqueue(method: .post, path: "/api/v1/me/orders", handlers: [
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("exam-access-query-400-validation-failed.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 400, data: data)
                },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-create-201-fulfilled.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 201, data: data)
                },
                { _ in ContractFakeTransport.emptyResponse(statusCode: 503) },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-create-201-fulfilled.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 201, data: data)
                }
            ])
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = CheckoutViewModel(
            code: "EXAM-DEMO-01",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            keyGenerator: { keys.next() }
        )
        await vm.loadAll()

        await vm.createOrder()
        await vm.createOrder()
        await vm.createOrder()

        try harness.transport.registerFixture(
            method: .post,
            path: "/api/v1/me/price-quote",
            statusCode: 200,
            relativeFixturePath: "api/price-quote-200-voucher.json"
        )
        vm.updateVoucherInput("EXAM-DEMO-03")
        await vm.applyVoucher()

        await vm.createOrder()

        let requests = harness.transport.requests(method: .post, path: "/api/v1/me/orders")
        let sentKeys = requests.compactMap { $0.header(HTTPField.Name("Idempotency-Key")!) }
        XCTAssertEqual(sentKeys.count, 4)
        XCTAssertEqual(Set(sentKeys).count, 4, "4xx, success, and voucher change must each produce a fresh Idempotency-Key")
    }

    func testReusedAndReplacedOrderCodes() async throws {
        do {
            var openedCode: String?
            let harness = try PaymentTestFactory.makeHarness(browserOutcomes: [.dismissed]) { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/orders",
                    statusCode: 201,
                    relativeFixturePath: "api/orders-create-201-reused.json"
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
                onOpenPaymentResult: { code, _ in openedCode = code }
            )
            await vm.loadAll()
            await vm.createOrder()

            XCTAssertEqual(harness.browser.openedURLs.count, 1)
            XCTAssertEqual(openedCode, "DH261126000001")
            let pending = await harness.pendingOrderStore.readValid(now: harness.clock.now)
            XCTAssertEqual(pending?.orderCode, "DH261126000001")
        }

        do {
            var openedCode: String?
            let replacedOrderData = try PaymentFixtureHelper.mutatingFixture("orders-create-201-redirect.json") { obj in
                if var order = obj["order"] as? [String: Any] {
                    order["code"] = "DH261126000099"
                    obj["order"] = order
                }
                obj["paymentUrl"] = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=DH26112600009901"
            }
            let harness = try PaymentTestFactory.makeHarness(browserOutcomes: [.dismissed]) { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
                transport.registerJSON(method: .post, path: "/api/v1/me/orders", statusCode: 201, data: replacedOrderData)
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL,
                clock: harness.clock,
                onOpenPaymentResult: { code, _ in openedCode = code }
            )
            await vm.loadAll()
            await vm.createOrder()

            XCTAssertEqual(openedCode, "DH261126000099")
            let pending = await harness.pendingOrderStore.readValid(now: harness.clock.now)
            XCTAssertEqual(pending?.orderCode, "DH261126000099")
        }
    }
}
