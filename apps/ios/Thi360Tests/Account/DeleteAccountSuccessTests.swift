import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class DeleteAccountSuccessTests: XCTestCase {
    func test204SignsOutWithAccountDeletedWipesAllDataClearsBackStackAndShowsNoticeOnLogin() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200-avatar-pending-email.json")
        let checkData = try ContractFixtureLoader.loadApiFixture("me-account-deletion-check-200.json")
        let pngData = try ProfileFixtureHelper.makeSamplePNG()
        let avatarPath = "/api/v1/avatars/00000000-0000-7000-8000-000000000001"

        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-delete-success-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }
        let storeProvider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let cleanup = SessionCleanup(storeProvider: storeProvider)
        _ = try await storeProvider.write(Data("draft-secret".utf8), accountId: "acc-del-204", namespace: "drafts", key: "d1")

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(accountId: "acc-del-204"),
            capability: .faceIDAvailable,
            initialEnrolled: true,
            storeProvider: storeProvider,
            sessionCleanup: cleanup
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: profileData)
            transport.register(method: .get, path: avatarPath) { _ in
                var fields = HTTPFields()
                fields[.contentType] = "image/png"
                return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngData))
            }
            transport.registerJSON(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, data: checkData)
            transport.registerEmpty(method: .post, path: "/api/v1/me/account/deletion", statusCode: 204)
            transport.registerEmpty(method: .post, path: "/api/v1/auth/mobile/logout", statusCode: 204)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }
        stack.sessionStore.unlockSession()

        let imageLoader = ImageLoader(
            apiBaseURL: stack.sessionStore.apiClient.serverURL,
            tokenStore: stack.tokenStore,
            refresher: stack.sessionStore.apiClient.refresher,
            fetcher: ClientTransportImageDataFetcher(transport: stack.transport)
        )
        await cleanup.register(imageLoader)

        let router = AppRouter(initialDestination: .account)
        stack.sessionStore.onSessionCleared = { [weak router] in
            router?.resetForSessionCleared()
        }
        let accountVM = router.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.ensureCleanupRegistered()
        await accountVM.loadProfile()

        let avatarURL = try XCTUnwrap(imageLoader.resolveURL(avatarPath))
        _ = try await imageLoader.loadImage(from: avatarPath, authenticated: true)
        XCTAssertNotNil(imageLoader.cachedImage(for: avatarURL))

        accountVM.selectDetail(.deleteAccount)
        router.openDeleteAccount()
        let deleteVM = accountVM.ensureDeleteAccountViewModel()
        await deleteVM.loadCheck()
        deleteVM.updatePassword("Password123!")
        deleteVM.toggleAcknowledge()
        deleteVM.requestConfirmation()
        await deleteVM.confirmDelete()

        XCTAssertEqual(deleteVM.state, .deleted)
        XCTAssertEqual(stack.sessionStore.state, .signedOut(reason: .accountDeleted))
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/auth/mobile/logout"), 0)
        XCTAssertNil(try stack.tokenStore.read(), "Tokens must be cleared")
        XCTAssertFalse(stack.secretStore.hasProtectedSecret(), "Biometric secret must be cleared")
        XCTAssertEqual(stack.biometricManager.state, .off)
        XCTAssertFalse(storeProvider.accountDirectoryExists(for: "acc-del-204"), "AccountScopedStore drafts/cache must be wiped")
        XCTAssertNil(imageLoader.cachedImage(for: avatarURL), "ImageLoader cache must be cleared")
        XCTAssertNil(accountVM.loadedProfile, "AccountProfileStore must be wiped")
        XCTAssertTrue(router.path(for: .account).isEmpty, "Back stack must be cleared so back gesture cannot reopen old screens")

        let loginVM = LoginViewModel(sessionStore: stack.sessionStore, initialReason: .accountDeleted)
        XCTAssertEqual(loginVM.serverBanner?.message(locale: .vi), "Tài khoản của bạn đã được xóa.")
    }

    func testLostResponseRetryReceiving401IsTreatedAsAccountDeletedSuccessWithoutError() async throws {
        let checkData = try ContractFixtureLoader.loadApiFixture("me-account-deletion-check-200.json")
        let unauthData = try ContractFixtureLoader.loadApiFixture("me-401-unauthenticated.json")
        let refresh401Data = try ContractFixtureLoader.loadAuthFixture("refresh-401-session-revoked.json")

        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-delete-401-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }
        let storeProvider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let cleanup = SessionCleanup(storeProvider: storeProvider)
        _ = try await storeProvider.write(Data("draft-lost-response".utf8), accountId: "acc-del-401", namespace: "drafts", key: "d1")

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(accountId: "acc-del-401"),
            capability: .faceIDAvailable,
            initialEnrolled: true,
            storeProvider: storeProvider,
            sessionCleanup: cleanup
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, data: checkData)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 401, data: refresh401Data)
            transport.enqueue(method: .post, path: "/api/v1/me/account/deletion", handlers: [
                { _ in throw URLError(.networkConnectionLost) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 401, data: unauthData) }
            ])
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }
        stack.sessionStore.unlockSession()

        let service = OpenAPIAccountDeletionService(apiClient: stack.sessionStore.apiClient)
        let vm = DeleteAccountViewModel(deletionService: service, sessionStore: stack.sessionStore)
        await vm.loadCheck()

        vm.updatePassword("Password123!")
        vm.toggleAcknowledge()
        vm.requestConfirmation()
        await vm.confirmDelete()
        XCTAssertEqual(vm.state, .failed(kind: .network, unusedAttempts: 0, pendingGrading: false))
        XCTAssertEqual(stack.sessionStore.state, .active)

        vm.requestConfirmation()
        await vm.confirmDelete()

        XCTAssertEqual(vm.state, .deleted)
        XCTAssertEqual(
            stack.sessionStore.state,
            .signedOut(reason: .accountDeleted),
            "401 on retry after lost deletion response must sign out with .accountDeleted (not .expired)"
        )
        XCTAssertNil(try stack.tokenStore.read())
        XCTAssertFalse(storeProvider.accountDirectoryExists(for: "acc-del-401"))
    }
}
