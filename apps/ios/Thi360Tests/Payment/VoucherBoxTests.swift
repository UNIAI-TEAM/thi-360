import XCTest
@testable import Thi360

@MainActor
final class VoucherBoxTests: XCTestCase {
    func testEmptyOrWhitespaceInputShowsValidationAndSkipsApi() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
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
        let initialCalls = harness.transport.callCount(method: .post, path: "/api/v1/me/price-quote")

        vm.updateVoucherInput("   ")
        await vm.applyVoucher()

        XCTAssertEqual(vm.voucher.feedback, .empty)
        XCTAssertEqual(harness.transport.callCount(method: .post, path: "/api/v1/me/price-quote"), initialCalls)
    }

    func testApplyVariantsNotBetterAndRemove() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
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

        try harness.transport.registerFixture(
            method: .post,
            path: "/api/v1/me/price-quote",
            statusCode: 200,
            relativeFixturePath: "api/price-quote-200-voucher.json"
        )
        vm.updateVoucherInput("  EXAM-DEMO-03  ")
        await vm.applyVoucher()

        let req = try XCTUnwrap(harness.transport.requests(method: .post, path: "/api/v1/me/price-quote").last)
        let body = try PaymentFixtureHelper.requestBodyJSON(req)
        XCTAssertEqual(body["voucherCode"] as? String, "EXAM-DEMO-03")
        XCTAssertEqual(vm.voucher.feedback, .applied(variant: .applied, code: "EXAM-DEMO-03", discountVnd: 40_000))

        let combinedData = try PaymentFixtureHelper.mutatingFixture("price-quote-200-voucher.json") { obj in
            obj["discountApplied"] = "COMBINED"
            obj["promotion"] = [
                "id": "00000000-0000-7000-8000-000000000099",
                "name": "Sale",
                "discountType": "AMOUNT",
                "discountValue": 10_000,
                "discountVnd": 10_000,
                "endsAt": "2026-12-31T23:59:59Z"
            ]
        }
        harness.transport.registerJSON(method: .post, path: "/api/v1/me/price-quote", statusCode: 200, data: combinedData)
        await vm.applyVoucher()
        XCTAssertEqual(vm.voucher.feedback, .applied(variant: .combined, code: "EXAM-DEMO-03", discountVnd: 40_000))

        let replacedData = try PaymentFixtureHelper.mutatingFixture("price-quote-200-voucher.json") { obj in
            obj["discountApplied"] = "VOUCHER_ONLY"
            obj["promotion"] = NSNull()
        }
        harness.transport.registerJSON(method: .post, path: "/api/v1/me/price-quote", statusCode: 200, data: replacedData)
        await vm.applyVoucher()
        XCTAssertEqual(vm.voucher.feedback, .applied(variant: .replacedPromo, code: "EXAM-DEMO-03", discountVnd: 40_000))

        let notBetterData = try PaymentFixtureHelper.mutatingFixture("price-quote-200.json") { obj in
            obj["voucherNotBetter"] = true
        }
        harness.transport.registerJSON(method: .post, path: "/api/v1/me/price-quote", statusCode: 200, data: notBetterData)
        vm.updateVoucherInput("SMALL5")
        await vm.applyVoucher()
        XCTAssertEqual(vm.voucher.feedback, .notBetter(code: "SMALL5"))

        try harness.transport.registerFixture(
            method: .post,
            path: "/api/v1/me/price-quote",
            statusCode: 200,
            relativeFixturePath: "api/price-quote-200.json"
        )
        await vm.removeVoucher()
        let removeReq = try XCTUnwrap(harness.transport.requests(method: .post, path: "/api/v1/me/price-quote").last)
        let removeBody = try PaymentFixtureHelper.requestBodyJSON(removeReq)
        XCTAssertNil(removeBody["voucherCode"])
        XCTAssertEqual(vm.voucher.codeInput, "")
        XCTAssertNil(vm.voucher.feedback)
        XCTAssertNil(vm.activeQuote?.voucher)
    }

    func testAllVoucherErrorCodesRateLimitUnknownAndTypingClearsError() async throws {
        let cases: [(fixture: String, status: Int, expected: VoucherErrorKey)] = [
            ("price-quote-422-voucher-invalid.json", 422, .voucherInvalid),
            ("price-quote-422-voucher-expired.json", 422, .voucherExpired),
            ("price-quote-422-voucher-exhausted.json", 422, .voucherExhausted),
            ("price-quote-422-voucher-user-limit.json", 422, .voucherUserLimit),
            ("price-quote-422-voucher-not-applicable.json", 422, .voucherNotApplicable),
            ("price-quote-422-voucher-not-eligible.json", 422, .voucherNotEligible),
            ("price-quote-429-rate-limited.json", 429, .rateLimited)
        ]

        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
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
        let baselineQuote = vm.activeQuote

        for testCase in cases {
            try harness.transport.registerFixture(
                method: .post,
                path: "/api/v1/me/price-quote",
                statusCode: testCase.status,
                relativeFixturePath: "api/\(testCase.fixture)"
            )
            vm.updateVoucherInput("BADCODE")
            await vm.applyVoucher()

            XCTAssertEqual(vm.voucher.feedback, .error(testCase.expected), "Failed mapping for \(testCase.fixture)")
            XCTAssertEqual(vm.activeQuote, baselineQuote, "Failed voucher must keep previous quote")
            XCTAssertEqual(vm.voucher.codeInput, "BADCODE", "Failed voucher must keep typed code")

            vm.updateVoucherInput("BADCODE2")
            XCTAssertNil(vm.voucher.feedback)
        }

        let unknownCodeData = try PaymentFixtureHelper.mutatingFixture("price-quote-422-voucher-invalid.json") { obj in
            obj["code"] = "VOUCHER_UNKNOWN_FUTURE_CODE"
        }
        harness.transport.registerJSON(method: .post, path: "/api/v1/me/price-quote", statusCode: 422, data: unknownCodeData)
        vm.updateVoucherInput("FUTURE")
        await vm.applyVoucher()
        XCTAssertEqual(vm.voucher.feedback, .error(.generic))

        XCTAssertEqual(
            VoucherErrorKey.voucherNotApplicable.messageKey(isBundle: false),
            "checkout.voucher.errors.VOUCHER_NOT_APPLICABLE"
        )
        XCTAssertEqual(
            VoucherErrorKey.voucherNotApplicable.messageKey(isBundle: true),
            "checkout.voucher.errors.VOUCHER_NOT_APPLICABLE_BUNDLE"
        )
    }

    func testDoubleTapSendsOneQuote() async throws {
        let gate = TestAsyncGate()
        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
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
        let beforeCount = harness.transport.callCount(method: .post, path: "/api/v1/me/price-quote")

        harness.transport.register(method: .post, path: "/api/v1/me/price-quote") { _ in
            await gate.enterAndWait()
            let data = try ContractFixtureLoader.loadApiFixture("price-quote-200-voucher.json")
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
        }

        vm.updateVoucherInput("EXAM-DEMO-03")
        let first = Task { await vm.applyVoucher() }
        await gate.waitUntilEntered()

        XCTAssertTrue(vm.isVoucherDisabled)
        await vm.applyVoucher()

        await gate.release()
        await first.value

        XCTAssertEqual(
            harness.transport.callCount(method: .post, path: "/api/v1/me/price-quote") - beforeCount,
            1
        )
    }
}
