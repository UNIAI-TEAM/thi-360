import XCTest
@testable import Thi360

@MainActor
final class PaymentLogRedactionTests: XCTestCase {
    func testDemoPaymentFlowNeverLogsVoucherVnpParamsOrTokens() async throws {
        let secretVoucher = "VIPSECRET2026"
        let secretVnpHash = "HASH99887766AABBCC"
        let secretTxnRef = "DH26112600000199"
        let secretVnpAmount = "20000000"
        let secretToken = "demo-secret-payment-access-token"

        let harness = try PaymentTestFactory.makeHarness(
            environment: .demo,
            browserOutcomes: [.dismissed]
        ) { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            try transport.registerFixture(
                method: .post,
                path: "/api/v1/me/orders",
                statusCode: 201,
                relativeFixturePath: "api/orders-create-201-redirect.json"
            )
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/public/payments/vnpay/return",
                statusCode: 200,
                relativeFixturePath: "api/vnpay-return-200-succeeded.json"
            )
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/orders/DH261126000001",
                statusCode: 200,
                relativeFixturePath: "api/orders-detail-200-fulfilled.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        try harness.tokenStore.write(
            AccountTestFactory.sampleTokens(accessToken: secretToken, refreshToken: "refresh-secret-pay-01")
        )

        let checkoutVM = CheckoutViewModel(
            code: "EXAM-DEMO-01",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await checkoutVM.loadAll()

        try harness.transport.registerFixture(
            method: .post,
            path: "/api/v1/me/price-quote",
            statusCode: 200,
            relativeFixturePath: "api/price-quote-200-voucher.json"
        )
        checkoutVM.updateVoucherInput(secretVoucher)
        await checkoutVM.applyVoucher()
        await checkoutVM.createOrder()

        let demoEnv = AppEnvironment(
            kind: .demo,
            apiBaseURL: URL(string: "https://thi360.zone17.click")!,
            webBaseURL: URL(string: "https://thi360.zone17.click")!
        )
        let returnURL = URL(
            string: "https://thi360.zone17.click/checkout/result/app?vnp_TxnRef=\(secretTxnRef)&vnp_Amount=\(secretVnpAmount)&vnp_ResponseCode=00&vnp_SecureHash=\(secretVnpHash)"
        )!
        let parsed = try XCTUnwrap(PaymentReturnLink.parse(returnURL, environment: demoEnv))
        XCTAssertFalse(String(describing: parsed).contains(secretVnpHash))
        XCTAssertFalse(String(describing: parsed).contains(secretTxnRef))
        XCTAssertFalse(String(reflecting: parsed).contains(secretVnpHash))

        let entry = PaymentResultEntry.byReturnLink(parsed)
        XCTAssertFalse(String(describing: entry).contains(secretVnpHash))

        let resultVM = PaymentResultViewModel(
            initialEntry: entry,
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await resultVM.startIfNeeded()

        let entries = harness.logSink.recordedEntries
        XCTAssertFalse(entries.isEmpty, "Expected network logs from payment flow")

        let secrets = [secretVoucher, secretVnpHash, secretTxnRef, secretVnpAmount, secretToken, "refresh-secret-pay-01"]
        for logEntry in entries {
            for secret in secrets {
                XCTAssertFalse(
                    logEntry.message.contains(secret),
                    "Demo log leaked '\(secret)' in entry: \(logEntry.message)"
                )
            }
        }
    }
}
