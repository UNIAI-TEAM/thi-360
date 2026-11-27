import XCTest
@testable import Thi360

@MainActor
final class PriceChangeTests: XCTestCase {
    func testBannerConditions() async throws {
        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, quoteFixture: "price-quote-200-changed.json")
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }
            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                seenPrice: SeenPrice(versionNo: 1, price: 200_000),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()
            XCTAssertTrue(vm.priceChangedBanner)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, quoteFixture: "price-quote-200.json")
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }
            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                seenPrice: SeenPrice(versionNo: 2, price: 200_000),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()
            XCTAssertTrue(vm.priceChangedBanner)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, quoteFixture: "price-quote-200.json")
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }
            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                seenPrice: SeenPrice(versionNo: 1, price: 150_000),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()
            XCTAssertTrue(vm.priceChangedBanner)
            XCTAssertEqual(vm.displaySeenPrice, 150_000)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, quoteFixture: "price-quote-200.json")
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }
            let vm = CheckoutViewModel(
                code: "EXAM-DEMO-01",
                seenPrice: SeenPrice(versionNo: 1, price: 200_000),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.loadAll()
            XCTAssertFalse(vm.priceChangedBanner)
        }
    }

    func testPriceChangedOnCreate() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, quoteFixture: "price-quote-200.json")
            try transport.registerFixture(
                method: .post,
                path: "/api/v1/me/orders",
                statusCode: 409,
                relativeFixturePath: "api/orders-create-409-price-changed.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = CheckoutViewModel(
            code: "EXAM-DEMO-01",
            seenPrice: SeenPrice(versionNo: 1, price: 200_000),
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await vm.loadAll()
        XCTAssertFalse(vm.priceChangedBanner)

        await vm.createOrder()

        XCTAssertTrue(vm.priceChangedBanner)
        XCTAssertEqual(vm.displaySeenPrice, 200_000)
        XCTAssertTrue(harness.browser.openedURLs.isEmpty, "PRICE_CHANGED must never open payment browser")
        XCTAssertEqual(vm.submit, .idle)
    }

    func testSeenValuesKeptAfterPriceChanged() async throws {
        let updatedQuoteOn409 = try PaymentFixtureHelper.mutatingFixture("orders-create-409-price-changed.json") { obj in
            if var q = obj["quote"] as? [String: Any] {
                q["finalPrice"] = 250_000
                q["listPrice"] = 250_000
                q["versionNo"] = 2
                obj["quote"] = q
            }
        }
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport, quoteFixture: "price-quote-200.json")
            transport.enqueue(method: .post, path: "/api/v1/me/orders", handlers: [
                { _ in ContractFakeTransport.jsonResponse(statusCode: 409, data: updatedQuoteOn409) },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-create-201-redirect.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 201, data: data)
                }
            ])
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = CheckoutViewModel(
            code: "EXAM-DEMO-01",
            seenPrice: SeenPrice(versionNo: 1, price: 200_000),
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await vm.loadAll()
        await vm.createOrder()

        XCTAssertTrue(vm.priceChangedBanner)
        XCTAssertEqual(vm.activeQuote?.finalPrice, 250_000)
        XCTAssertEqual(vm.activeQuote?.versionNo, 2)

        try harness.transport.registerFixture(
            method: .post,
            path: "/api/v1/me/price-quote",
            statusCode: 200,
            relativeFixturePath: "api/price-quote-200-voucher.json"
        )
        vm.updateVoucherInput("EXAM-DEMO-03")
        await vm.applyVoucher()

        let applyReq = try XCTUnwrap(harness.transport.requests(method: .post, path: "/api/v1/me/price-quote").last)
        let applyBody = try PaymentFixtureHelper.requestBodyJSON(applyReq)
        XCTAssertEqual(applyBody["seenVersionNo"] as? Int, 1)
        XCTAssertNil(applyBody["seenPrice"], "Applying voucher must send seenVersionNo only, not seenPrice")
        XCTAssertTrue(vm.priceChangedBanner)

        let refreshedAfterRemove = try PaymentFixtureHelper.mutatingFixture("price-quote-200.json") { obj in
            obj["finalPrice"] = 250_000
            obj["listPrice"] = 250_000
            obj["versionNo"] = 2
        }
        harness.transport.registerJSON(method: .post, path: "/api/v1/me/price-quote", statusCode: 200, data: refreshedAfterRemove)
        await vm.removeVoucher()

        let removeReq = try XCTUnwrap(harness.transport.requests(method: .post, path: "/api/v1/me/price-quote").last)
        let removeBody = try PaymentFixtureHelper.requestBodyJSON(removeReq)
        XCTAssertEqual(removeBody["seenPrice"] as? Int, 200_000)
        XCTAssertEqual(removeBody["seenVersionNo"] as? Int, 1)
        XCTAssertTrue(vm.priceChangedBanner)

        await vm.createOrder()
        let secondOrderReq = try XCTUnwrap(harness.transport.requests(method: .post, path: "/api/v1/me/orders").last)
        let secondBody = try PaymentFixtureHelper.requestBodyJSON(secondOrderReq)
        XCTAssertEqual(secondBody["expectedAmountDue"] as? Int, 250_000)
        XCTAssertEqual(secondBody["expectedVersionNo"] as? Int, 2)
    }
}
