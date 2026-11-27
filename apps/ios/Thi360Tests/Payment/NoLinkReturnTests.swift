import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class NoLinkReturnTests: XCTestCase {
    override func tearDown() {
        PaymentRoutesCoordinator.shared.resetMemory()
        super.tearDown()
    }

    func testDismissedBrowserAndForegroundRestoreOpenPendingOrderAndShowNoResultYet() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/orders/DH261126000001",
                statusCode: 200,
                relativeFixturePath: "api/orders-detail-200-pending-payment.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        await harness.pendingOrderStore.save(orderCode: "DH261126000001", openedAt: harness.clock.now)

        let router = AppRouter()
        PaymentRoutesCoordinator.shared.register(
            router: router,
            sessionStore: harness.sessionStore,
            sessionCleanup: harness.sessionStore.sessionCleanup,
            clock: harness.clock
        )
        await router.inboundLinkRouter.notifySessionStateChanged(.active)
        await router.inboundLinkRouter.notifyScenePhaseChanged(.background, sessionState: .active)
        await router.inboundLinkRouter.notifyScenePhaseChanged(.active, sessionState: .active)

        XCTAssertEqual(router.path(for: .home), [.paymentResult(.byOrder(orderCode: "DH261126000001"))])

        let poller = OrderPoller(
            interval: 2.0,
            window: 60.0,
            clock: harness.clock,
            sleep: { seconds in
                harness.clock.advance(by: seconds)
            }
        )
        let vm = PaymentResultViewModel(
            initialEntry: .byOrder(orderCode: "DH261126000001", verifiedFromLink: false, openedFromChecking: false),
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            poller: poller,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            clock: harness.clock
        )
        await vm.startIfNeeded()
        try? await Task.sleep(nanoseconds: 150_000_000)

        if case let .noResultYet(order, timedOut, _, _) = vm.state {
            XCTAssertEqual(order.code, "DH261126000001")
            XCTAssertTrue(timedOut, "Expected poller to time out after 60s while staying .noResultYet")
        } else {
            XCTFail("Expected .noResultYet after 60s timeout (A-79), got \(vm.state)")
        }

        let view = PaymentResultView(
            viewModel: vm,
            locale: .vi,
            supportContact: .empty,
            onGoMyExams: {},
            onViewOrderDetail: { _ in },
            onViewOrders: {},
            onBuyAgain: { _ in },
            onOpenCatalog: {},
            onSwitchAccount: {}
        )
        let (window, controller) = RecoveryViewTestHelper.host(view, size: CGSize(width: 390, height: 844), sizeClass: .compact)
        defer { window.isHidden = true }
        XCTAssertGreaterThan(controller.view.bounds.width, 0)
        XCTAssertEqual(PaymentStrings.text("checkout.result.noResultHeading", locale: .vi), "Chưa nhận được kết quả thanh toán")
        XCTAssertEqual(PaymentStrings.text("orders.actions.continuePayment", locale: .vi), "Tiếp tục thanh toán")
    }

    func testNoLinkReturnMapsFulfilledFailedAndPaidImmediately() async throws {
        let cases: [(fixture: String, check: (PaymentResultState) -> Bool)] = [
            ("orders-detail-200-fulfilled.json", { if case .fulfilled = $0 { return true }; return false }),
            ("orders-detail-200-payment-failed.json", { if case .failed = $0 { return true }; return false }),
            ("orders-detail-200-paid.json", { if case .checking = $0 { return true }; return false })
        ]

        for testCase in cases {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/me/orders/DH261126000001",
                    statusCode: 200,
                    relativeFixturePath: "api/\(testCase.fixture)"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let poller = OrderPoller(interval: 2.0, window: 60.0, clock: harness.clock, sleep: { _ in
                try await Task.sleep(nanoseconds: 60_000_000_000)
            })
            let vm = PaymentResultViewModel(
                initialEntry: .byOrder(orderCode: "DH261126000001"),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                poller: poller,
                apiBaseURL: harness.sessionStore.apiClient.serverURL,
                clock: harness.clock
            )
            await vm.startIfNeeded()
            XCTAssertTrue(testCase.check(vm.state), "Unexpected state \(vm.state) for \(testCase.fixture)")
            await vm.onLeaveScreen(clearPendingOrder: false)
        }
    }
}
