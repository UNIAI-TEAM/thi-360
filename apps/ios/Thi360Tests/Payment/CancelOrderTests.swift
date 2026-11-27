import HTTPTypes
import XCTest
@testable import Thi360

@MainActor
final class CancelOrderTests: XCTestCase {
    func testKeepDismissesWithoutCallingAPI() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/orders/DH261126000001",
                statusCode: 200,
                relativeFixturePath: "api/orders-detail-200-pending-payment.json"
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

        vm.requestCancel()
        XCTAssertTrue(vm.isShowingCancelDialog)
        vm.dismissCancelDialog()
        XCTAssertFalse(vm.isShowingCancelDialog)
        XCTAssertEqual(harness.transport.callCount(method: .post, path: "/api/v1/me/orders/DH261126000001/cancel"), 0)
    }

    func testConfirmCancelsClosesDialogShowsToastAndUpdatesState() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/orders/DH261126000001",
                statusCode: 200,
                relativeFixturePath: "api/orders-detail-200-pending-payment.json"
            )
            try transport.registerFixture(
                method: .post,
                path: "/api/v1/me/orders/DH261126000001/cancel",
                statusCode: 200,
                relativeFixturePath: "api/orders-cancel-200.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        var syncedDetail: Components.Schemas.OrderDetail?
        let vm = OrderDetailViewModel(
            orderCode: "DH261126000001",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            onOrderMutated: { syncedDetail = $0 }
        )
        await vm.load(isRefresh: false)

        vm.requestCancel()
        await vm.confirmCancel()

        XCTAssertFalse(vm.isShowingCancelDialog)
        XCTAssertEqual(vm.toastOrderCode, "DH261126000001")
        XCTAssertEqual(vm.loadedOrder?.state, .CANCELLED)
        XCTAssertEqual(syncedDetail?.state, .CANCELLED)
    }

    func testCancelFailureKeepsDialogOpenShowsErrorReloadsAndFollowsKeyPolicy() async throws {
        let keys = SequenceKeyGenerator()
        let harness = try PaymentTestFactory.makeHarness { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/orders/DH261126000001",
                statusCode: 200,
                relativeFixturePath: "api/orders-detail-200-pending-payment.json"
            )
            transport.enqueue(method: .post, path: "/api/v1/me/orders/DH261126000001/cancel", handlers: [
                { _ in throw URLError(.notConnectedToInternet) },
                { _ in ContractFakeTransport.emptyResponse(statusCode: 503) },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-cancel-409-order-not-pending.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 409, data: data)
                },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-cancel-200.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                }
            ])
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = OrderDetailViewModel(
            orderCode: "DH261126000001",
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            keyGenerator: { keys.next() }
        )
        await vm.load(isRefresh: false)

        vm.requestCancel()
        await vm.confirmCancel()
        XCTAssertTrue(vm.isShowingCancelDialog)
        XCTAssertEqual(vm.cancelErrorKey, .generic)

        await vm.confirmCancel()
        XCTAssertTrue(vm.isShowingCancelDialog)

        await vm.confirmCancel()
        XCTAssertTrue(vm.isShowingCancelDialog)
        XCTAssertEqual(vm.cancelErrorKey, .orderNotPending)

        await vm.confirmCancel()
        XCTAssertFalse(vm.isShowingCancelDialog)

        let requests = harness.transport.requests(method: .post, path: "/api/v1/me/orders/DH261126000001/cancel")
        let sentKeys = requests.compactMap { $0.header(HTTPField.Name("Idempotency-Key")!) }
        XCTAssertEqual(sentKeys.count, 4)
        XCTAssertEqual(Set(sentKeys.prefix(3)).count, 1)
        XCTAssertNotEqual(sentKeys[2], sentKeys[3])
    }

    func testCancelInFlightLocksActionsPreventsDismissAndDeduplicatesDoubleTap() async throws {
        let gate = TestAsyncGate()
        let harness = try PaymentTestFactory.makeHarness { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/orders/DH261126000001",
                statusCode: 200,
                relativeFixturePath: "api/orders-detail-200-pending-payment.json"
            )
            transport.register(method: .post, path: "/api/v1/me/orders/DH261126000001/cancel") { _ in
                await gate.enterAndWait()
                let data = try ContractFixtureLoader.loadApiFixture("orders-cancel-200.json")
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
            }
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

        vm.requestCancel()
        let first = Task { await vm.confirmCancel() }
        await gate.waitUntilEntered()

        XCTAssertTrue(vm.isCancelling)
        XCTAssertTrue(vm.anyActionRunning)

        vm.dismissCancelDialog()
        XCTAssertTrue(vm.isShowingCancelDialog)
        await vm.confirmCancel()
        await vm.continuePayment()

        await gate.release()
        await first.value

        XCTAssertEqual(harness.transport.callCount(method: .post, path: "/api/v1/me/orders/DH261126000001/cancel"), 1)
        XCTAssertEqual(harness.transport.callCount(method: .post, path: "/api/v1/me/orders/DH261126000001/pay"), 0)
    }
}
