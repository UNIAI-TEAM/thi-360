import HTTPTypes
import XCTest
@testable import Thi360

@MainActor
final class PayAgainTests: XCTestCase {
    func testSingleFlightAcrossList() async throws {
        let twoPendingList = try PaymentFixtureHelper.mutatingFixture("orders-list-200.json") { obj in
            if var items = obj["items"] as? [[String: Any]], let first = items.first {
                var second = first
                second["id"] = "00000000-0000-7000-8000-000000000002"
                second["code"] = "DH261126000002"
                items.append(second)
                obj["items"] = items
                obj["totalItems"] = 2
            }
        }
        let gate = TestAsyncGate()
        let harness = try PaymentTestFactory.makeHarness { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/orders", statusCode: 200, data: twoPendingList)
            transport.register(method: .post, path: "/api/v1/me/orders/DH261126000001/pay") { _ in
                await gate.enterAndWait()
                let data = try ContractFixtureLoader.loadApiFixture("orders-pay-200.json")
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
            }
            try transport.registerFixture(
                method: .post,
                path: "/api/v1/me/orders/DH261126000002/pay",
                statusCode: 200,
                relativeFixturePath: "api/orders-pay-200.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = OrdersViewModel(
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            clock: harness.clock
        )
        await vm.loadFirstPage(isRefresh: false)

        guard case let .loaded(items, _) = vm.state, items.count == 2 else {
            return XCTFail("Expected 2 pending orders")
        }

        let firstTask = Task { await vm.continuePayment(for: items[0]) }
        await gate.waitUntilEntered()
        XCTAssertTrue(vm.anyActionRunning)

        await vm.continuePayment(for: items[0])
        await vm.continuePayment(for: items[1])

        await gate.release()
        await firstTask.value

        XCTAssertEqual(harness.transport.callCount(method: .post, path: "/api/v1/me/orders/DH261126000001/pay"), 1)
        XCTAssertEqual(harness.transport.callCount(method: .post, path: "/api/v1/me/orders/DH261126000002/pay"), 0)
    }

    func testKeyPolicy() async throws {
        let keys = SequenceKeyGenerator()
        let harness = try PaymentTestFactory.makeHarness { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/orders/DH261126000001",
                statusCode: 200,
                relativeFixturePath: "api/orders-detail-200-pending-payment.json"
            )
            transport.enqueue(method: .post, path: "/api/v1/me/orders/DH261126000001/pay", handlers: [
                { _ in throw URLError(.notConnectedToInternet) },
                { _ in ContractFakeTransport.emptyResponse(statusCode: 408) },
                { _ in ContractFakeTransport.emptyResponse(statusCode: 429) },
                { _ in ContractFakeTransport.emptyResponse(statusCode: 503) },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-pay-409-order-not-pending.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 409, data: data)
                },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-pay-200.json")
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
            clock: harness.clock,
            keyGenerator: { keys.next() }
        )
        await vm.load(isRefresh: false)

        for _ in 0..<6 {
            await vm.continuePayment()
        }

        let requests = harness.transport.requests(method: .post, path: "/api/v1/me/orders/DH261126000001/pay")
        let sentKeys = requests.compactMap { $0.header(HTTPField.Name("Idempotency-Key")!) }
        XCTAssertEqual(sentKeys.count, 6)
        XCTAssertEqual(Set(sentKeys.prefix(5)).count, 1, "Transient errors must retain the same Idempotency-Key")
        XCTAssertNotEqual(sentKeys[4], sentKeys[5], "Non-transient 409 must rotate to a new Idempotency-Key")
    }

    func testErrorActions() async throws {
        let errorCases: [(fixture: String, status: Int, expected: OrderActionErrorKey)] = [
            ("orders-pay-409-order-expired.json", 409, .orderExpired),
            ("orders-pay-422-payment-attempts-exceeded.json", 422, .paymentAttemptsExceeded),
            ("orders-pay-410-product-discontinued.json", 410, .productDiscontinued),
            ("orders-pay-404-product-not-found.json", 404, .productNotFound),
            ("orders-pay-409-order-not-pending.json", 409, .orderNotPending)
        ]

        for testCase in errorCases {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/me/orders/DH261126000001",
                    statusCode: 200,
                    relativeFixturePath: "api/orders-detail-200-pending-payment.json"
                )
                try transport.registerFixture(
                    method: .post,
                    path: "/api/v1/me/orders/DH261126000001/pay",
                    statusCode: testCase.status,
                    relativeFixturePath: "api/\(testCase.fixture)"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            let vm = OrderDetailViewModel(
                orderCode: "DH261126000001",
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL,
                clock: harness.clock
            )
            await vm.load(isRefresh: false)
            let callsBefore = harness.transport.callCount(method: .get, path: "/api/v1/me/orders/DH261126000001")

            await vm.continuePayment()

            XCTAssertEqual(vm.actionError?.code, testCase.expected, "Expected \(testCase.expected) for \(testCase.fixture)")
            XCTAssertEqual(
                harness.transport.callCount(method: .get, path: "/api/v1/me/orders/DH261126000001"),
                callsBefore + 1,
                "Failed pay must reload order"
            )
        }
    }
}
