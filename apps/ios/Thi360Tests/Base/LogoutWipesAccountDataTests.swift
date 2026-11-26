import Foundation
import XCTest
@testable import Thi360

@MainActor
final class LogoutWipesAccountDataTests: XCTestCase {
    func testAllThreeLogoutPathsWipeAccountAAndPreserveAccountB() async throws {
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-logout-paths-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }

        let paths: [(name: String, run: (AccountTestFactory.Stack) async -> Void)] = [
            ("Settings/Account logout", { stack in
                let vm = AccountViewModel(sessionStore: stack.sessionStore, biometricManager: stack.biometricManager)
                await vm.confirmLogout()
            }),
            ("Lock screen use another account", { stack in
                stack.sessionStore.lockSession()
                let lockVM = LockViewModel(sessionStore: stack.sessionStore, biometricManager: stack.biometricManager)
                await lockVM.signInWithOtherAccount()
            }),
            ("Forced password change sign out", { stack in
                await stack.sessionStore.signOut(reason: .none)
            })
        ]

        for (index, item) in paths.enumerated() {
            let subRoot = rootDir.appendingPathComponent("case-\(index)", isDirectory: true)
            let provider = AccountScopedStoreProvider(rootDirectoryURL: subRoot)
            let cleanup = SessionCleanup(storeProvider: provider)

            _ = try await provider.write(Data("data-A".utf8), accountId: "account-A", namespace: "drafts", key: "att-1")
            _ = try await provider.write(Data("data-B".utf8), accountId: "account-B", namespace: "drafts", key: "att-1")

            let stack = AccountTestFactory.makeContractStack(
                initialTokens: AccountTestFactory.sampleTokens(
                    username: "userA",
                    accountId: "account-A",
                    refreshToken: "refresh-A-\(index)"
                ),
                initialEnrolled: true,
                storeProvider: provider,
                sessionCleanup: cleanup
            ) { transport in
                transport.registerEmpty(method: .post, path: "/api/v1/auth/mobile/logout", statusCode: 204)
            }
            defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

            await item.run(stack)

            XCTAssertNil(try stack.tokenStore.read(), "\(item.name): tokens must be cleared")
            XCTAssertFalse(provider.accountDirectoryExists(for: "account-A"), "\(item.name): account-A directory must be wiped")
            let dataB = await provider.read(accountId: "account-B", namespace: "drafts", key: "att-1")
            XCTAssertEqual(dataB, Data("data-B".utf8), "\(item.name): account-B data must remain intact")
            XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/auth/mobile/logout"), 1)
        }
    }

    func testDoubleTap() async throws {
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-logout-doubletap-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }

        let provider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let cleanup = SessionCleanup(storeProvider: provider)
        let cleanupGate = TestAsyncGate()

        final class GatedParticipant: SessionCleanupParticipant, @unchecked Sendable {
            let id = "test.gated"
            let gate: TestAsyncGate
            private let lock = NSLock()
            private var _calls = 0
            var calls: Int { lock.lock(); defer { lock.unlock() }; return _calls }
            init(gate: TestAsyncGate) { self.gate = gate }
            func onLogout(accountId: String) async throws {
                lock.lock()
                _calls += 1
                lock.unlock()
                await gate.enterAndWait()
            }
        }
        let participant = GatedParticipant(gate: cleanupGate)
        await cleanup.register(participant)

        _ = try await provider.write(Data("payload".utf8), accountId: "account-dt", namespace: "drafts", key: "k")

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(accountId: "account-dt", refreshToken: "refresh-dt"),
            storeProvider: provider,
            sessionCleanup: cleanup
        ) { transport in
            transport.registerEmpty(method: .post, path: "/api/v1/auth/mobile/logout", statusCode: 204)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let first = Task { @MainActor in
            await stack.sessionStore.signOut(reason: .none)
        }
        await cleanupGate.waitUntilEntered()

        let second = Task { @MainActor in
            await stack.sessionStore.signOut(reason: .none)
        }
        await second.value

        await cleanupGate.release()
        await first.value

        XCTAssertEqual(participant.calls, 1, "Double-tapping logout must run SessionCleanup strictly once")
        XCTAssertEqual(
            stack.transport.callCount(method: .post, path: "/api/v1/auth/mobile/logout"),
            1,
            "Double-tapping logout must invoke mobile/logout strictly once"
        )
        XCTAssertFalse(provider.accountDirectoryExists(for: "account-dt"))
    }
}
