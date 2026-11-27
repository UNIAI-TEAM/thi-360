import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class AccountDeletedSignOutTests: XCTestCase {
    func testAccountDeletedSignOutRunsCleanupSkipsMobileLogoutShowsNoticeAndClearsOnNextLaunch() async throws {
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-deleted-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }

        let provider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let cleanup = SessionCleanup(storeProvider: provider)
        let deletedAccountId = "acc-deleted-1"
        _ = try await provider.write(Data("secret".utf8), accountId: deletedAccountId, namespace: "drafts", key: "k1")

        final class SpyCleanup: SessionCleanupParticipant, @unchecked Sendable {
            let id = "spy.deleted"
            private let lock = NSLock()
            private var _calledIds: [String] = []
            var calledIds: [String] { lock.lock(); defer { lock.unlock() }; return _calledIds }
            func onLogout(accountId: String) async throws {
                lock.lock()
                _calledIds.append(accountId)
                lock.unlock()
            }
        }
        let spy = SpyCleanup()
        await cleanup.register(spy)

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(accountId: deletedAccountId, refreshToken: "refresh-deleted"),
            capability: .faceIDAvailable,
            initialEnrolled: true,
            storeProvider: provider,
            sessionCleanup: cleanup
        ) { transport in
            transport.registerEmpty(method: .post, path: "/api/v1/auth/mobile/logout", statusCode: 204)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        await stack.sessionStore.signOut(reason: .accountDeleted)

        XCTAssertEqual(
            stack.transport.callCount(method: .post, path: "/api/v1/auth/mobile/logout"),
            0,
            "accountDeleted signOut must NOT call mobile/logout because server already revoked session"
        )
        XCTAssertEqual(spy.calledIds, [deletedAccountId], "SessionCleanup participants must run")
        XCTAssertFalse(provider.accountDirectoryExists(for: deletedAccountId), "AccountScopedStore must be wiped")
        XCTAssertNil(try stack.tokenStore.read(), "Tokens must be cleared")
        XCTAssertFalse(stack.secretStore.hasProtectedSecret(), "Biometric secret must be cleared")
        XCTAssertEqual(stack.biometricManager.state, .off)
        XCTAssertEqual(stack.sessionStore.state, .signedOut(reason: .accountDeleted))

        let loginVM = LoginViewModel(sessionStore: stack.sessionStore, initialReason: .accountDeleted)
        XCTAssertEqual(loginVM.serverBanner, .accountDeleted)
        XCTAssertEqual(loginVM.serverBanner?.message(locale: .vi), "Tài khoản của bạn đã được xóa.")
        XCTAssertEqual(loginVM.serverBanner?.message(locale: .en), "Your account has been deleted.")

        let relaunchedSession = SessionStore(
            tokenStore: stack.tokenStore,
            apiClient: stack.sessionStore.apiClient,
            biometricManager: stack.biometricManager,
            storeProvider: provider,
            sessionCleanup: cleanup
        )
        XCTAssertEqual(relaunchedSession.state, .signedOut(reason: .none))
        let relaunchedLoginVM = LoginViewModel(sessionStore: relaunchedSession, initialReason: .none)
        XCTAssertNil(relaunchedLoginVM.serverBanner)
    }
}
