import XCTest
@testable import Thi360

@MainActor
final class PaymentReturnRoutingTests: XCTestCase {
    private let demoEnv = AppEnvironment(
        kind: .demo,
        apiBaseURL: URL(string: "https://thi360.zone17.click")!,
        webBaseURL: URL(string: "https://thi360.zone17.click")!
    )

    override func tearDown() {
        PaymentRoutesCoordinator.shared.resetMemory()
        super.tearDown()
    }

    func testIncomingURLUserActivityAndBrowserCallbackForwardExactVnpParams() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
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

        let router = AppRouter()
        PaymentRoutes.register(
            router: router,
            sessionStore: harness.sessionStore,
            sessionCleanup: harness.sessionStore.sessionCleanup,
            environment: demoEnv
        )
        await router.inboundLinkRouter.notifySessionStateChanged(.active)

        let linkURL = URL(
            string: "https://thi360.zone17.click/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_Amount=20000000&vnp_ResponseCode=00&vnp_SecureHash=sig123&nonVnp=dropMe"
        )!

        XCTAssertTrue(router.handleIncomingURL(linkURL, environment: demoEnv))
        XCTAssertEqual(router.path(for: .home), [.paymentResult(.byReturnLink(linkURL))])

        let parsed = try XCTUnwrap(PaymentReturnLink.parse(linkURL, environment: demoEnv))
        let resultVM = PaymentResultViewModel(
            initialEntry: .byReturnLink(parsed),
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await resultVM.startIfNeeded()

        let verifyReq = try XCTUnwrap(harness.transport.requests(method: .get, path: "/api/v1/public/payments/vnpay/return").first)
        let queryItems = URLComponents(string: "https://api.thi360.test\(verifyReq.path)")?.queryItems ?? []
        let queryMap = Dictionary(uniqueKeysWithValues: queryItems.compactMap { item in
            item.value.map { (item.name, $0) }
        })
        XCTAssertEqual(queryMap["vnp_TxnRef"], "DH26112600000101")
        XCTAssertEqual(queryMap["vnp_Amount"], "20000000")
        XCTAssertEqual(queryMap["vnp_ResponseCode"], "00")
        XCTAssertEqual(queryMap["vnp_SecureHash"], "sig123")
        XCTAssertNil(queryMap["nonVnp"])

        if case let .fulfilled(order) = resultVM.state {
            XCTAssertEqual(order.code, "DH261126000001")
        } else {
            XCTFail("Expected .fulfilled state, got \(resultVM.state)")
        }

        let router2 = AppRouter()
        PaymentRoutesCoordinator.shared.resetMemory()
        PaymentRoutes.register(
            router: router2,
            sessionStore: harness.sessionStore,
            sessionCleanup: harness.sessionStore.sessionCleanup,
            environment: demoEnv
        )
        await router2.inboundLinkRouter.notifySessionStateChanged(.active)

        let activity = NSUserActivity(activityType: NSUserActivityTypeBrowsingWeb)
        activity.webpageURL = linkURL
        XCTAssertTrue(router2.handleUserActivity(activity, environment: demoEnv))
        XCTAssertEqual(router2.path(for: .home), [.paymentResult(.byReturnLink(linkURL))])

        let countBefore = harness.transport.recordedRequests.count
        XCTAssertFalse(router2.handleIncomingURL(URL(string: "https://thi360.zone17.click/checkout/result?vnp_TxnRef=DH1")!, environment: demoEnv))
        XCTAssertEqual(harness.transport.recordedRequests.count, countBefore)
    }

    func testFallbackOpenAppTargetHandledAsReturnLink() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
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

        let router = AppRouter()
        PaymentRoutes.register(
            router: router,
            sessionStore: harness.sessionStore,
            sessionCleanup: harness.sessionStore.sessionCleanup,
            environment: demoEnv
        )
        await router.inboundLinkRouter.notifySessionStateChanged(.active)

        let fallbackOpenAppTarget = URL(
            string: "https://thi360.zone17.click/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_SecureHash=web1fallback"
        )!
        let activity = NSUserActivity(activityType: NSUserActivityTypeBrowsingWeb)
        activity.webpageURL = fallbackOpenAppTarget

        XCTAssertTrue(router.handleUserActivity(activity, environment: demoEnv))
        XCTAssertEqual(router.path(for: .home), [.paymentResult(.byReturnLink(fallbackOpenAppTarget))])

        let parsed = try XCTUnwrap(PaymentReturnLink.parse(fallbackOpenAppTarget, environment: demoEnv))
        let vm = PaymentResultViewModel(
            initialEntry: .byReturnLink(parsed),
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await vm.startIfNeeded()
        if case .fulfilled = vm.state {
        } else {
            XCTFail("Expected .fulfilled after fallback open-app return link")
        }
    }
}
