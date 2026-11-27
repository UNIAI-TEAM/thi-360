import XCTest
@testable import Thi360

@MainActor
final class PendingPaymentOrderTests: XCTestCase {
    func testExpiresAfter15Minutes() async throws {
        let harness = try PaymentTestFactory.makeHarness()
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let openedAt = harness.clock.now
        await harness.pendingOrderStore.save(orderCode: "DH261126000001", openedAt: openedAt)

        let at14m59s = openedAt.addingTimeInterval(14 * 60 + 59)
        let validRecord = await harness.pendingOrderStore.readValid(now: at14m59s)
        XCTAssertEqual(validRecord?.orderCode, "DH261126000001")

        let at15m01s = openedAt.addingTimeInterval(15 * 60 + 1)
        let expiredRecord = await harness.pendingOrderStore.readValid(now: at15m01s)
        XCTAssertNil(expiredRecord)

        let afterCleanup = await harness.pendingOrderStore.readValid(now: openedAt)
        XCTAssertNil(afterCleanup)
    }

    func testClearedOnTerminalLeaveLogout() async throws {
        for fixture in ["orders-detail-200-fulfilled.json", "orders-detail-200-payment-failed.json", "orders-detail-200-expired.json"] {
            let harness = try PaymentTestFactory.makeHarness { transport in
                try transport.registerFixture(
                    method: .get,
                    path: "/api/v1/me/orders/DH261126000001",
                    statusCode: 200,
                    relativeFixturePath: "api/\(fixture)"
                )
            }
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            await harness.pendingOrderStore.save(orderCode: "DH261126000001", openedAt: harness.clock.now)
            let vm = PaymentResultViewModel(
                initialEntry: .byOrder(orderCode: "DH261126000001"),
                gateway: harness.gateway,
                paymentBrowser: harness.browser,
                pendingOrderStore: harness.pendingOrderStore,
                apiBaseURL: harness.sessionStore.apiClient.serverURL,
                clock: harness.clock
            )
            await vm.startIfNeeded()
            let remaining = await harness.pendingOrderStore.readValid(now: harness.clock.now)
            XCTAssertNil(remaining, "Pending order must clear on terminal fixture \(fixture)")
        }

        do {
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
            let beforeLeave = await harness.pendingOrderStore.readValid(now: harness.clock.now)
            XCTAssertNotNil(beforeLeave)

            await vm.onLeaveScreen(clearPendingOrder: true)
            let afterLeave = await harness.pendingOrderStore.readValid(now: harness.clock.now)
            XCTAssertNil(afterLeave)
        }

        do {
            let harness = try PaymentTestFactory.makeHarness()
            defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

            await harness.sessionStore.sessionCleanup.register(
                PendingPaymentCleanupParticipant(storeProvider: harness.storeProvider)
            )
            await harness.pendingOrderStore.save(orderCode: "DH261126000001", openedAt: harness.clock.now)
            let beforeLogout = await harness.pendingOrderStore.readValid(now: harness.clock.now)
            XCTAssertNotNil(beforeLogout)

            await harness.sessionStore.signOut()

            let rawStore = harness.storeProvider.store(for: "019320a0-0000-7000-8000-000000000001")
            let rawData = await rawStore?.read(namespace: PendingPaymentOrderStore.namespace, key: PendingPaymentOrderStore.key)
            XCTAssertNil(rawData)
        }
    }

    func testOtherAccountDoesNotSee() async throws {
        let storeDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-payment-isolation-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: storeDir) }

        let storeProvider = AccountScopedStoreProvider(rootDirectoryURL: storeDir)
        let clock = ManualClock()

        let storeA = PendingPaymentOrderStore(storeProvider: storeProvider, accountIdProvider: { "account-A" })
        let storeB = PendingPaymentOrderStore(storeProvider: storeProvider, accountIdProvider: { "account-B" })

        await storeA.save(orderCode: "DH-ACCOUNT-A", openedAt: clock.now)

        let recordA = await storeA.readValid(now: clock.now)
        let recordB = await storeB.readValid(now: clock.now)
        XCTAssertEqual(recordA?.orderCode, "DH-ACCOUNT-A")
        XCTAssertNil(recordB, "Account B must never see Account A's pending order")
    }
}
