import XCTest
@testable import Thi360

@MainActor
final class PaymentReturnGatingTests: XCTestCase {
    private let demoEnv = AppEnvironment(
        kind: .demo,
        apiBaseURL: URL(string: "https://thi360.zone17.click")!,
        webBaseURL: URL(string: "https://thi360.zone17.click")!
    )

    private let sampleLink = URL(
        string: "https://thi360.zone17.click/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_SecureHash=sig01"
    )!

    override func tearDown() {
        PaymentRoutesCoordinator.shared.resetMemory()
        super.tearDown()
    }

    func testLockedFirst() async throws {
        let harness = try PaymentTestFactory.makeHarness()
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let router = AppRouter()
        PaymentRoutes.register(
            router: router,
            sessionStore: harness.sessionStore,
            sessionCleanup: harness.sessionStore.sessionCleanup,
            environment: demoEnv
        )

        await router.inboundLinkRouter.notifySessionStateChanged(.locked)
        XCTAssertTrue(router.handleIncomingURL(sampleLink, environment: demoEnv))

        XCTAssertTrue(router.path(for: .home).isEmpty)
        XCTAssertTrue(router.inboundLinkRouter.hasPendingLink)

        let resetURL = URL(string: "https://thi360.zone17.click/reset-password?token=tok123")!
        XCTAssertTrue(router.handleIncomingURL(resetURL, environment: demoEnv))
        XCTAssertNotNil(router.activeResetLink)
        router.clearRecoveryOverlay()

        await router.inboundLinkRouter.notifySessionStateChanged(.active)
        XCTAssertEqual(router.path(for: .home), [.paymentResult(.byReturnLink(sampleLink))])
        XCTAssertFalse(router.inboundLinkRouter.hasPendingLink)
    }

    func testSignedOutThenLogin() async throws {
        let harness = try PaymentTestFactory.makeHarness()
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let router = AppRouter()
        PaymentRoutes.register(
            router: router,
            sessionStore: harness.sessionStore,
            sessionCleanup: harness.sessionStore.sessionCleanup,
            environment: demoEnv
        )

        await router.inboundLinkRouter.notifySessionStateChanged(.signedOut(reason: .none))
        XCTAssertTrue(router.handleIncomingURL(sampleLink, environment: demoEnv))
        XCTAssertTrue(router.path(for: .home).isEmpty)
        XCTAssertTrue(router.inboundLinkRouter.hasPendingLink)

        await router.inboundLinkRouter.notifySessionStateChanged(.active)
        XCTAssertEqual(router.path(for: .home), [.paymentResult(.byReturnLink(sampleLink))])
    }

    func testNotOwnedAfterLogin() async throws {
        let harness = try PaymentTestFactory.makeHarness(username: "student.other") { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/public/payments/vnpay/return",
                statusCode: 200,
                relativeFixturePath: "api/vnpay-return-200-succeeded.json"
            )
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/orders/DH261126000001",
                statusCode: 404,
                relativeFixturePath: "api/orders-detail-404-order-not-found.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let parsed = try XCTUnwrap(PaymentReturnLink.parse(sampleLink, environment: demoEnv))
        let vm = PaymentResultViewModel(
            initialEntry: .byReturnLink(parsed),
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            usernameProvider: { "student.other" }
        )
        await vm.startIfNeeded()

        XCTAssertEqual(vm.state, .notOwned(orderCode: "DH261126000001", username: "student.other"))
    }

    func testSameOrderUpdatesOneScreen() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/public/payments/vnpay/return",
                statusCode: 200,
                relativeFixturePath: "api/vnpay-return-200-succeeded.json"
            )
            transport.enqueue(method: .get, path: "/api/v1/me/orders/DH261126000001", handlers: [
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-detail-200-pending-payment.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-detail-200-fulfilled.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                }
            ])
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

        router.openPaymentResult(.byOrder(orderCode: "DH261126000001"))
        let vm = PaymentRoutesCoordinator.shared.resultViewModel(
            target: .byOrder(orderCode: "DH261126000001"),
            router: router,
            sessionStore: harness.sessionStore,
            environment: demoEnv
        )
        await vm.startIfNeeded()
        XCTAssertEqual(router.path(for: .home).count, 1)

        XCTAssertTrue(router.handleIncomingURL(sampleLink, environment: demoEnv))
        try? await Task.sleep(nanoseconds: 80_000_000)

        XCTAssertEqual(router.path(for: .home).count, 1, "Must keep single PaymentResult screen on stack")
        if case .fulfilled = vm.state {
        } else {
            XCTFail("Expected existing PaymentResultViewModel to update to .fulfilled, got \(vm.state)")
        }
    }

    func testSameLinkTwiceOneScreen() async throws {
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

        XCTAssertTrue(router.handleIncomingURL(sampleLink, environment: demoEnv))
        let vm = PaymentRoutesCoordinator.shared.resultViewModel(
            target: .byReturnLink(sampleLink),
            router: router,
            sessionStore: harness.sessionStore,
            environment: demoEnv
        )
        await vm.startIfNeeded()

        XCTAssertTrue(router.handleIncomingURL(sampleLink, environment: demoEnv))
        try? await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertEqual(router.path(for: .home).count, 1)
        XCTAssertEqual(harness.transport.callCount(method: .get, path: "/api/v1/public/payments/vnpay/return"), 1)
    }

    func testPreMountRacingReturnLinksAndRestoreKeepSingleRoute() async throws {
        let harness = try PaymentTestFactory.makeHarness()
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let router = AppRouter()
        PaymentRoutesCoordinator.shared.register(
            router: router,
            sessionStore: harness.sessionStore,
            sessionCleanup: harness.sessionStore.sessionCleanup,
            environment: demoEnv,
            clock: harness.clock
        )
        await router.inboundLinkRouter.notifySessionStateChanged(.active)

        XCTAssertTrue(router.handleIncomingURL(sampleLink, environment: demoEnv))
        XCTAssertTrue(router.handleIncomingURL(sampleLink, environment: demoEnv))
        XCTAssertEqual(router.path(for: .home), [.paymentResult(.byReturnLink(sampleLink))])

        let router2 = AppRouter()
        PaymentRoutesCoordinator.shared.resetMemory()
        await harness.pendingOrderStore.save(orderCode: "DH261126000001", openedAt: harness.clock.now)
        PaymentRoutesCoordinator.shared.register(
            router: router2,
            sessionStore: harness.sessionStore,
            sessionCleanup: harness.sessionStore.sessionCleanup,
            environment: demoEnv,
            clock: harness.clock
        )
        await router2.inboundLinkRouter.notifySessionStateChanged(.active)
        await router2.inboundLinkRouter.notifyScenePhaseChanged(.background, sessionState: .active)
        await router2.inboundLinkRouter.notifyScenePhaseChanged(.active, sessionState: .active)
        XCTAssertEqual(router2.path(for: .home), [.paymentResult(.byOrder(orderCode: "DH261126000001"))])

        XCTAssertTrue(router2.handleIncomingURL(sampleLink, environment: demoEnv))
        XCTAssertEqual(
            router2.path(for: .home),
            [.paymentResult(.byReturnLink(sampleLink))],
            "Pre-mount return link racing restorePendingOrder must replace top .paymentResult route in place"
        )
    }

    func testRepeatedResultViewModelLookupForSameTargetDoesNotRestartCalls() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/orders/DH261126000001",
                statusCode: 200,
                relativeFixturePath: "api/orders-detail-200-fulfilled.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let router = AppRouter()
        let target = PaymentResultTarget.byOrder(orderCode: "DH261126000001")
        let vm1 = PaymentRoutesCoordinator.shared.resultViewModel(
            target: target,
            router: router,
            sessionStore: harness.sessionStore,
            environment: demoEnv
        )
        await vm1.startIfNeeded()
        XCTAssertEqual(harness.transport.callCount(method: .get, path: "/api/v1/me/orders/DH261126000001"), 1)

        let vm2 = PaymentRoutesCoordinator.shared.resultViewModel(
            target: target,
            router: router,
            sessionStore: harness.sessionStore,
            environment: demoEnv
        )
        let vm3 = PaymentRoutesCoordinator.shared.resultViewModel(
            target: target,
            router: router,
            sessionStore: harness.sessionStore,
            environment: demoEnv
        )
        try? await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertTrue(vm1 === vm2 && vm2 === vm3)
        XCTAssertEqual(
            harness.transport.callCount(method: .get, path: "/api/v1/me/orders/DH261126000001"),
            1,
            "Re-evaluating same target must not trigger acceptEntry or restart network/polling calls"
        )
    }
}
