import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class PaymentOutcomeDisplayTests: XCTestCase {
    func testMultiAttemptAndDuplicatePaymentRenderedFaithfully() async throws {
        let multiAttemptData = try PaymentFixtureHelper.mutatingFixture("orders-detail-200-duplicate-payment.json") { obj in
            if var payments = obj["payments"] as? [[String: Any]], payments.count >= 2 {
                payments[0]["state"] = "CREATED"
                payments[0]["duplicate"] = false
                obj["payments"] = payments
            }
        }
        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                transport.registerJSON(method: .get, path: "/api/v1/me/orders/DH261126000001", statusCode: 200, data: multiAttemptData)
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = OrderDetailViewModel(
                orderCode: "DH261126000001",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.load(isRefresh: false)
            XCTAssertEqual(vm.loadedOrder?.state, .FULFILLED)
            XCTAssertEqual(vm.loadedOrder?.payments.count, 2)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/me/orders/DH261126000001",
                    statusCode: 200,
                    relativeFixturePath: "api/orders-detail-200-duplicate-payment.json"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = OrderDetailViewModel(
                orderCode: "DH261126000001",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL
            )
            await vm.load(isRefresh: false)

            let view = OrderDetailView(
                viewModel: vm,
                locale: .vi,
                supportContact: .empty,
                onGoMyExams: {},
                onOpenOrder: { _ in },
                onBackToOrders: {},
                onCreateNewOrder: { _ in },
                onOpenCatalog: {},
                onSwitchAccount: {}
            )
            let (window, controller) = RecoveryViewTestHelper.host(view, size: CGSize(width: 390, height: 1800), sizeClass: .compact)
            defer { window.isHidden = true }

            XCTAssertGreaterThan(controller.view.bounds.width, 0)
            let sortedPayments = (vm.loadedOrder?.payments ?? []).sorted { $0.attemptSeq < $1.attemptSeq }
            XCTAssertEqual(sortedPayments.count, 2)
            XCTAssertEqual(sortedPayments[0].duplicate, false)
            XCTAssertEqual(sortedPayments[1].duplicate, true)
            XCTAssertEqual(PaymentStrings.text("orders.detail.payments.duplicate", locale: .vi), "Thu trùng")
        }
    }

    func testClosedOrderReclaimedToPaidThenFulfilledUpdatesResultListAndDetail() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            transport.enqueue(method: .get, path: "/api/v1/me/orders/DH261126000001", handlers: [
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-detail-200-expired.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-detail-200-paid.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-detail-200-fulfilled.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                }
            ])
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let poller = OrderPoller(interval: 2.0, window: 60.0, clock: harness.clock, sleep: { _ in
            try await Task.sleep(nanoseconds: 60_000_000_000)
        })
        let resultVM = PaymentResultViewModel(
            initialEntry: .byOrder(orderCode: "DH261126000001"),
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            poller: poller,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            clock: harness.clock
        )
        await resultVM.startIfNeeded()
        if case .closed = resultVM.state {} else { return XCTFail("Expected .closed") }

        await resultVM.retryLoad()
        if case .checking = resultVM.state {} else { return XCTFail("Expected .checking after late PAID") }

        await resultVM.checkAgainNow()
        if case .fulfilled = resultVM.state {} else { return XCTFail("Expected .fulfilled after reclaim") }
        await resultVM.onLeaveScreen(clearPendingOrder: false)

        harness.transport.enqueue(method: .get, path: "/api/v1/me/orders/DH261126000001", handlers: [
            { _ in
                let data = try ContractFixtureLoader.loadApiFixture("orders-detail-200-expired.json")
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
            },
            { _ in
                let data = try ContractFixtureLoader.loadApiFixture("orders-detail-200-fulfilled.json")
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
            }
        ])
        let detailVM = OrderDetailViewModel(
            orderCode: "DH261126000001",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL
        )
        await detailVM.load(isRefresh: false)
        XCTAssertEqual(detailVM.loadedOrder?.state, .EXPIRED)
        await detailVM.reload()
        XCTAssertEqual(detailVM.loadedOrder?.state, .FULFILLED)
    }
}
