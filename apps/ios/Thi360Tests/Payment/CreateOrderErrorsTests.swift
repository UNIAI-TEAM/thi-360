import XCTest
@testable import Thi360

@MainActor
final class CreateOrderErrorsTests: XCTestCase {
    func testVoucherRejectedOnCreateClearsInputAndReloadsBaseQuote() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, quoteFixture: "price-quote-200-voucher.json")
            try transport.registerFixture(
                method: .post,
                path: "/api/v1/me/orders",
                statusCode: 422,
                relativeFixturePath: "api/orders-create-422-voucher-invalid.json"
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
        vm.updateVoucherInput("EXAM-DEMO-03")

        try harness.transport.registerFixture(
            method: .post,
            path: "/api/v1/me/price-quote",
            statusCode: 200,
            relativeFixturePath: "api/price-quote-200.json"
        )
        await vm.createOrder()

        XCTAssertEqual(vm.submit, .failed(.voucher(.voucherInvalid)))
        XCTAssertEqual(vm.voucher.codeInput, "")
        XCTAssertNil(vm.activeQuote?.voucher)
        let lastQuoteReq = try XCTUnwrap(harness.transport.requests(method: .post, path: "/api/v1/me/price-quote").last)
        let body = try PaymentFixtureHelper.requestBodyJSON(lastQuoteReq)
        XCTAssertNil(body["voucherCode"])
    }

    func testPaymentAttemptsExceeded() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            try transport.registerFixture(
                method: .post,
                path: "/api/v1/me/orders",
                statusCode: 422,
                relativeFixturePath: "api/orders-create-422-payment-attempts-exceeded.json"
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

        XCTAssertEqual(vm.submit, .failed(.paymentAttemptsExceeded))
        XCTAssertTrue(vm.isPayButtonEnabled)
    }

    func testRateLimited429() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            try transport.registerFixture(
                method: .post,
                path: "/api/v1/me/orders",
                statusCode: 429,
                relativeFixturePath: "api/price-quote-429-rate-limited.json"
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

        XCTAssertEqual(vm.submit, .failed(.rateLimited))
    }

    func testAccountChangedDifferentUserAndSameUser() async throws {
        do {
            let otherUserData = try PaymentFixtureHelper.mutatingFixture("me-200.json") { obj in
                obj["id"] = "019320a0-0000-7000-8000-000000000099"
                obj["username"] = "student.other"
                obj["fullName"] = "Trần Thị B"
            }
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/orders",
                    statusCode: 409,
                    relativeFixturePath: "api/orders-create-409-account-changed.json"
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

            harness.transport.registerJSON(method: .get, path: "/api/v1/me", statusCode: 200, data: otherUserData)
            await vm.createOrder()

            XCTAssertEqual(vm.accountConflict?.previousUsername, "student.demo")
            XCTAssertEqual(vm.accountConflict?.currentUser.username, "student.other")
            XCTAssertFalse(vm.isPayButtonEnabled)

            let beforeQuoteCount = harness.transport.callCount(method: .post, path: "/api/v1/me/price-quote")
            let beforeAccessCount = harness.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query")
            await vm.acknowledgeAccountChange()

            XCTAssertNil(vm.accountConflict)
            XCTAssertEqual(vm.baselineUser?.username, "student.other")
            XCTAssertEqual(harness.transport.callCount(method: .post, path: "/api/v1/me/price-quote"), beforeQuoteCount + 1)
            XCTAssertEqual(harness.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query"), beforeAccessCount + 1)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/orders",
                    statusCode: 409,
                    relativeFixturePath: "api/orders-create-409-account-changed.json"
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

            XCTAssertNil(vm.accountConflict)
            XCTAssertEqual(vm.submit, .failed(.generic))
        }
    }

    func testIdempotencyReused5xxNetworkAndUnknownShowGenericAndKeepQuote() async throws {
        let scenarios: [(String, ContractFakeTransport.RouteHandler)] = [
            ("IDEMPOTENCY_KEY_REUSED", { _ in
                let data = try ContractFixtureLoader.loadApiFixture("orders-create-409-idempotency-key-reused.json")
                return ContractFakeTransport.jsonResponse(statusCode: 409, data: data)
            }),
            ("500", { _ in ContractFakeTransport.emptyResponse(statusCode: 500) }),
            ("network", { _ in throw URLError(.notConnectedToInternet) })
        ]

        for (label, handler) in scenarios {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
                transport.register(method: .post, path: "/api/v1/me/orders", handler: handler)
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
            let savedQuote = vm.activeQuote
            await vm.createOrder()

            XCTAssertEqual(vm.submit, .failed(.generic), "Scenario \(label) should show generic error")
            XCTAssertEqual(vm.activeQuote, savedQuote, "Scenario \(label) must keep existing quote")
            XCTAssertTrue(vm.isPayButtonEnabled, "Scenario \(label) must keep pay button clickable")
        }
    }

    func testUnauthorizedWithFailedRefreshSignsOutWithoutOpeningResult() async throws {
        var openedResult = false
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            try transport.registerUnauthorized(method: .post, path: "/api/v1/me/orders")
            try transport.registerUnauthorized(method: .post, path: "/api/v1/auth/mobile/refresh")
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = CheckoutViewModel(
            code: "EXAM-DEMO-01",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            onOpenPaymentResult: { _, _ in openedResult = true }
        )
        await vm.loadAll()
        await vm.createOrder()

        try? await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertFalse(openedResult)
        XCTAssertTrue(harness.browser.openedURLs.isEmpty)
        XCTAssertEqual(harness.sessionStore.state, .signedOut(reason: .expired))
    }
}
