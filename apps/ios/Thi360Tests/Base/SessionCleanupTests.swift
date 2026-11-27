import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

final class SessionCleanupTests: XCTestCase {
    private struct FailingParticipantError: Error {}

    private final class RecordingParticipant: SessionCleanupParticipant, @unchecked Sendable {
        let id: String
        let shouldThrowOnLogout: Bool
        private let lock = NSLock()
        private var _logoutAccountIds: [String] = []
        private var _appStartCount = 0

        init(id: String, shouldThrowOnLogout: Bool = false) {
            self.id = id
            self.shouldThrowOnLogout = shouldThrowOnLogout
        }

        var logoutAccountIds: [String] { lock.lock(); defer { lock.unlock() }; return _logoutAccountIds }
        var appStartCount: Int { lock.lock(); defer { lock.unlock() }; return _appStartCount }

        func onLogout(accountId: String) async throws {
            lock.lock()
            _logoutAccountIds.append(accountId)
            lock.unlock()
            if shouldThrowOnLogout {
                throw FailingParticipantError()
            }
        }

        func onAppStart() async throws {
            lock.lock()
            _appStartCount += 1
            lock.unlock()
        }
    }

    func testLogoutRunsParticipantsForTargetAccountOnlyAndThrowingParticipantDoesNotBlockOthers() async throws {
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-cleanup-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }

        let provider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let cleanup = SessionCleanup(storeProvider: provider)

        let failing = RecordingParticipant(id: "p1.failing", shouldThrowOnLogout: true)
        let healthy = RecordingParticipant(id: "p2.healthy", shouldThrowOnLogout: false)
        await cleanup.register(failing)
        await cleanup.register(healthy)

        _ = try await provider.write(Data("A".utf8), accountId: "acc-A", namespace: "ns", key: "k")
        _ = try await provider.write(Data("B".utf8), accountId: "acc-B", namespace: "ns", key: "k")

        await cleanup.runLogout(accountId: "acc-A")

        XCTAssertEqual(failing.logoutAccountIds, ["acc-A"])
        XCTAssertEqual(healthy.logoutAccountIds, ["acc-A"], "Throwing participant must not block subsequent participants")
        XCTAssertFalse(provider.accountDirectoryExists(for: "acc-A"), "Throwing participant must not block AccountScopedStore wipe")
        let bData = await provider.read(accountId: "acc-B", namespace: "ns", key: "k")
        XCTAssertEqual(bData, Data("B".utf8))
    }

    func testInterruptedLogoutCompletesOnStart() async throws {
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-interrupted-cleanup-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }

        let firstProvider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        _ = try await firstProvider.write(Data("leftover".utf8), accountId: "acc-crashed", namespace: "drafts", key: "att-1")
        let firstCleanup = SessionCleanup(storeProvider: firstProvider)
        await firstCleanup.recordPendingLogoutForTesting("acc-crashed")
        let pendingBefore = await firstCleanup.pendingAccountIdsForTesting()
        XCTAssertEqual(pendingBefore, ["acc-crashed"])

        let restartedProvider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let restartedCleanup = SessionCleanup(storeProvider: restartedProvider)
        let startupParticipant = RecordingParticipant(id: "p.startup")
        await restartedCleanup.register(startupParticipant)

        await restartedCleanup.runAppStart()

        XCTAssertFalse(restartedProvider.accountDirectoryExists(for: "acc-crashed"), "Pending logout must be completed on app start")
        let pendingAfter = await restartedCleanup.pendingAccountIdsForTesting()
        XCTAssertTrue(pendingAfter.isEmpty)
        XCTAssertEqual(startupParticipant.logoutAccountIds, ["acc-crashed"])
        XCTAssertEqual(startupParticipant.appStartCount, 1)
    }

    @MainActor
    func testStartupCleanupGateFinishesBeforeContentMountsOrReadsStore() async throws {
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-startup-gate-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }

        let provider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        _ = try await provider.write(Data("stale-secret".utf8), accountId: "acc-pending", namespace: "drafts", key: "k")
        let cleanup = SessionCleanup(storeProvider: provider)
        await cleanup.recordPendingLogoutForTesting("acc-pending")

        let gate = TestAsyncGate()
        final class SlowStartupParticipant: SessionCleanupParticipant, @unchecked Sendable {
            let id = "test.slowStartup"
            let gate: TestAsyncGate
            init(gate: TestAsyncGate) { self.gate = gate }
            func onLogout(accountId: String) async throws {
                await gate.enterAndWait()
            }
        }

        final class MountProbe {
            var mounted = false
            var storeExistedAtMount = true
        }
        struct ChildProbeView: View {
            let provider: AccountScopedStoreProvider
            let probe: MountProbe
            var body: some View {
                Color.clear
                    .onAppear {
                        probe.mounted = true
                        probe.storeExistedAtMount = provider.accountDirectoryExists(for: "acc-pending")
                    }
            }
        }

        let probe = MountProbe()
        let view = StartupCleanupGateView(
            sessionCleanup: cleanup,
            participants: [SlowStartupParticipant(gate: gate)]
        ) {
            ChildProbeView(provider: provider, probe: probe)
        }

        let (window, _) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        await gate.waitUntilEntered()
        XCTAssertFalse(probe.mounted, "App content must not mount before SessionCleanup.runAppStart finishes")

        await gate.release()
        let deadline = Date().addingTimeInterval(3)
        while !probe.mounted && Date() < deadline {
            try await Task.sleep(nanoseconds: 50_000_000)
            RunLoop.main.run(until: Date().addingTimeInterval(0.02))
        }

        XCTAssertTrue(probe.mounted, "App content must mount once startup cleanup completes")
        XCTAssertFalse(
            probe.storeExistedAtMount,
            "Pending account store must already be wiped before any mounted screen can read it"
        )
    }
}
