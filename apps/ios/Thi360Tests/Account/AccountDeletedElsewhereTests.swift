import Foundation
import HTTPTypes
import OpenAPIRuntime
import XCTest
@testable import Thi360

@MainActor
final class AccountDeletedElsewhereTests: XCTestCase {
    func testNextApi401AndRejectedRefreshWipesLocalAccountDataCacheProfileAndReturnsToLoginAsExpired() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200-avatar-pending-email.json")
        let unauthData = try ContractFixtureLoader.loadApiFixture("me-401-unauthenticated.json")
        let refresh401Data = try ContractFixtureLoader.loadAuthFixture("refresh-401-session-revoked.json")
        let pngData = try ProfileFixtureHelper.makeSamplePNG()
        let avatarPath = "/api/v1/avatars/00000000-0000-7000-8000-000000000001"

        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-deleted-elsewhere-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }
        let storeProvider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let cleanup = SessionCleanup(storeProvider: storeProvider)
        _ = try await storeProvider.write(Data("draft-elsewhere".utf8), accountId: "acc-elsewhere", namespace: "drafts", key: "k1")

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(accountId: "acc-elsewhere"),
            capability: .faceIDAvailable,
            initialEnrolled: true,
            storeProvider: storeProvider,
            sessionCleanup: cleanup
        ) { transport in
            transport.enqueue(method: .get, path: "/api/v1/me/profile", handlers: [
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: profileData) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 401, data: unauthData) }
            ])
            transport.register(method: .get, path: avatarPath) { _ in
                var fields = HTTPFields()
                fields[.contentType] = "image/png"
                return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngData))
            }
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 401, data: refresh401Data)
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
        accountVM.attachImageLoader(imageLoader)
        await accountVM.ensureCleanupRegistered()
        await accountVM.loadProfile()

        let avatarURL = try XCTUnwrap(imageLoader.resolveURL(avatarPath))
        _ = try await imageLoader.loadImage(from: avatarPath, authenticated: true)
        XCTAssertNotNil(imageLoader.cachedImage(for: avatarURL))
        XCTAssertTrue(storeProvider.accountDirectoryExists(for: "acc-elsewhere"))

        await accountVM.loadProfile()

        let signedOut = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            stack.sessionStore.state == .signedOut(reason: .expired)
        }
        XCTAssertTrue(signedOut, "401 + rejected refresh must return to Login as expired session")
        XCTAssertNil(try stack.tokenStore.read())
        XCTAssertFalse(stack.secretStore.hasProtectedSecret())
        XCTAssertEqual(stack.biometricManager.state, .off)
        XCTAssertNil(accountVM.loadedProfile, "AccountProfileStore must be wiped")
        XCTAssertNil(imageLoader.cachedImage(for: avatarURL), "ImageLoader cache must be wiped when account deleted elsewhere")
        XCTAssertTrue(router.path(for: .account).isEmpty, "Account navigation stack must be cleared on expired sign-out")
        let loginVM = LoginViewModel(sessionStore: stack.sessionStore, initialReason: .expired)
        XCTAssertEqual(
            loginVM.serverBanner?.message(locale: .vi),
            "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        )
    }
}
