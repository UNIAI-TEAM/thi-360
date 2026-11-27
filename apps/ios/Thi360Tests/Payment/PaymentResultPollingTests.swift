import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class PaymentResultPollingTests: XCTestCase {
    func testBackgroundStopsCallsAndForegroundPollsImmediatelyWithinWindow() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            transport.enqueue(method: .get, path: "/api/v1/me/orders/DH261126000001", handlers: [
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

        let poller = OrderPoller(
            interval: 2.0,
            window: 60.0,
            clock: harness.clock,
            sleep: { _ in
                try await Task.sleep(nanoseconds: 60_000_000_000)
            }
        )
        let vm = PaymentResultViewModel(
            initialEntry: .byOrder(orderCode: "DH261126000001", verifiedFromLink: true, openedFromChecking: false),
            gateway: harness.gateway,
            paymentBrowser: harness.browser,
            pendingOrderStore: harness.pendingOrderStore,
            poller: poller,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            clock: harness.clock
        )

        await vm.startIfNeeded()
        XCTAssertEqual(harness.transport.callCount(method: .get, path: "/api/v1/me/orders/DH261126000001"), 1)
        if case .checking(_, let timedOut, _) = vm.state {
            XCTAssertFalse(timedOut)
        } else {
            XCTFail("Expected .checking state")
        }

        vm.onScenePhaseChanged(.background)
        harness.clock.advance(by: 20.0)
        try? await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertEqual(harness.transport.callCount(method: .get, path: "/api/v1/me/orders/DH261126000001"), 1)

        vm.onScenePhaseChanged(.active)
        try? await Task.sleep(nanoseconds: 80_000_000)

        XCTAssertEqual(harness.transport.callCount(method: .get, path: "/api/v1/me/orders/DH261126000001"), 2)
        if case .fulfilled = vm.state {
        } else {
            XCTFail("Expected .fulfilled after foreground poll, got \(vm.state)")
        }
    }

    func testPollErrorDuringWindowKeepsOrderAndShowsNoticeThenCheckAgainRecovers() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            transport.enqueue(method: .get, path: "/api/v1/me/orders/DH261126000001", handlers: [
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-detail-200-paid.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                },
                { _ in throw URLError(.notConnectedToInternet) },
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("orders-detail-200-fulfilled.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
                }
            ])
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let poller = OrderPoller(
            interval: 2.0,
            window: 60.0,
            clock: harness.clock,
            sleep: { seconds in
                harness.clock.advance(by: seconds)
                try await Task.sleep(nanoseconds: 30_000_000)
            }
        )
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
        try? await Task.sleep(nanoseconds: 180_000_000)

        if case .fulfilled = vm.state {
        } else {
            XCTFail("Expected recovery to .fulfilled after transient poll error, got \(vm.state)")
        }
    }

    func testCheckingTimeoutAndManualCheckAgainRestartsWindow() async throws {
        let harness = try PaymentTestFactory.makeHarness { transport in
            try transport.registerFixture(
                method: .get,
                path: "/api/v1/me/orders/DH261126000001",
                statusCode: 200,
                relativeFixturePath: "api/orders-detail-200-paid.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let poller = OrderPoller(
            interval: 2.0,
            window: 60.0,
            clock: harness.clock,
            sleep: { seconds in
                harness.clock.advance(by: seconds)
            }
        )
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
        try? await Task.sleep(nanoseconds: 150_000_000)

        if case let .checking(_, timedOut, _) = vm.state {
            XCTAssertTrue(timedOut)
        } else {
            XCTFail("Expected .checking(timedOut: true), got \(vm.state)")
        }

        try harness.transport.registerFixture(
            method: .get,
            path: "/api/v1/me/orders/DH261126000001",
            statusCode: 200,
            relativeFixturePath: "api/orders-detail-200-fulfilled.json"
        )
        await vm.checkAgainNow()
        if case .fulfilled = vm.state {
        } else {
            XCTFail("Expected .fulfilled after checkAgainNow, got \(vm.state)")
        }
    }
}
