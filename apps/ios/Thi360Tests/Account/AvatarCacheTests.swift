import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class AvatarCacheTests: XCTestCase {
    private let avatarPathA = "/api/v1/avatars/00000000-0000-7000-8000-000000000001"

    func testSameAssetIdHitsMemoryCacheLogoutWipesCacheAndUserBNeverSeesUserAAvatar() async throws {
        URLCache.shared.removeAllCachedResponses()
        let pngA = try ProfileFixtureHelper.makeSamplePNG(width: 24, height: 24, color: .systemRed)
        let userAProfile = try ContractFixtureLoader.loadApiFixture("me-profile-200-avatar-pending-email.json")
        let userBProfile = try ContractFixtureLoader.loadApiFixture("me-profile-200-no-email.json")
        let userBLogin = try ContractFixtureLoader.loadAuthFixture("login-200.json")
        let meData = try ContractFixtureLoader.loadApiFixture("me-200.json")

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "userA", accountId: "acc-A", accessToken: "token-A", refreshToken: "refresh-A")
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: userAProfile)
            transport.registerJSON(method: .get, path: "/api/v1/me", statusCode: 200, data: meData)
            transport.register(method: .get, path: avatarPathA) { _ in
                var fields = HTTPFields()
                fields[.contentType] = "image/png"
                return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngA))
            }
            transport.registerEmpty(method: .post, path: "/api/v1/auth/mobile/logout", statusCode: 204)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/login", statusCode: 200, data: userBLogin)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let imageLoader = ImageLoader(
            apiBaseURL: stack.sessionStore.apiClient.serverURL,
            tokenStore: stack.tokenStore,
            refresher: stack.sessionStore.apiClient.refresher,
            fetcher: ClientTransportImageDataFetcher(transport: stack.transport)
        )
        await stack.sessionCleanup.register(imageLoader)

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.ensureCleanupRegistered()
        await accountVM.loadProfile()

        let resolvedURL = try XCTUnwrap(imageLoader.resolveURL(avatarPathA))
        _ = try await imageLoader.loadImage(from: avatarPathA, authenticated: true)
        _ = try await imageLoader.loadImage(from: avatarPathA, authenticated: true)
        XCTAssertEqual(
            stack.transport.callCount(method: .get, path: avatarPathA),
            1,
            "Second fetch for same assetId within session must hit memory cache with zero network calls"
        )
        XCTAssertNotNil(imageLoader.cachedImage(for: resolvedURL))
        XCTAssertNil(
            URLCache.shared.cachedResponse(for: URLRequest(url: resolvedURL)),
            "URLCache.shared must never cache authenticated avatar responses on disk or memory"
        )

        await accountVM.confirmLogout()

        XCTAssertNil(imageLoader.cachedImage(for: resolvedURL), "Logout must clear ImageLoader memory cache")
        XCTAssertNil(accountVM.loadedProfile, "Logout must wipe AccountProfileStore")
        XCTAssertNil(URLCache.shared.cachedResponse(for: URLRequest(url: resolvedURL)))

        stack.transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: userBProfile)
        try await stack.sessionStore.login(identifier: "student.noemail", password: "Password123!")
        await accountVM.loadProfile()

        XCTAssertEqual(accountVM.loadedProfile?.username, "student.noemail")
        XCTAssertNil(accountVM.loadedProfile?.avatarUrl)
        XCTAssertNil(imageLoader.cachedImage(for: resolvedURL), "User B must never see User A's cached avatar")
    }

    func testConcurrentAvatarCircleViewMountsIssueStrictlyOneGetRequestAndPopulateAllViews() async throws {
        URLCache.shared.removeAllCachedResponses()
        let pngA = try ProfileFixtureHelper.makeSamplePNG(width: 24, height: 24, color: .systemBlue)
        let gate = TestAsyncGate()

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "userA", accountId: "acc-A", accessToken: "token-A", refreshToken: "refresh-A")
        ) { transport in
            transport.register(method: .get, path: self.avatarPathA) { _ in
                await gate.enterAndWait()
                var fields = HTTPFields()
                fields[.contentType] = "image/png"
                return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngA))
            }
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let imageLoader = ImageLoader(
            apiBaseURL: stack.sessionStore.apiClient.serverURL,
            tokenStore: stack.tokenStore,
            refresher: stack.sessionStore.apiClient.refresher,
            fetcher: ClientTransportImageDataFetcher(transport: stack.transport)
        )

        let combinedView = HStack(spacing: 12) {
            AvatarCircleView(
                avatarUrl: avatarPathA,
                initials: "MA",
                size: 36,
                fontSize: 13,
                locale: .vi,
                exposeAvatarAccessibility: true,
                loaderOverride: imageLoader
            )
            AvatarCircleView(
                avatarUrl: avatarPathA,
                initials: "MA",
                size: 48,
                fontSize: 17,
                locale: .vi,
                exposeAvatarAccessibility: true,
                loaderOverride: imageLoader
            )
            AvatarCircleView(
                avatarUrl: avatarPathA,
                initials: "MA",
                size: 68,
                fontSize: 22,
                locale: .vi,
                exposeAvatarAccessibility: true,
                loaderOverride: imageLoader
            )
        }
        .environment(\.brandPalette, .defaultLight)

        let (window, controller) = ProfileFixtureHelper.host(
            combinedView,
            size: CGSize(width: 390, height: 200),
            sizeClass: .regular,
            themeMode: .light
        )
        defer { window.isHidden = true }

        await gate.waitUntilEntered()
        try await Task.sleep(nanoseconds: 80_000_000)
        XCTAssertEqual(
            stack.transport.callCount(method: .get, path: avatarPathA),
            1,
            "3 simultaneous AvatarCircleView mounts must coalesce into strictly 1 GET /api/v1/avatars/{assetId}"
        )

        await gate.release()
        let loadedLabel = ProfileStrings.text("account.profile.avatar.current", locale: .vi)
        let allRendered = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            ProfileFixtureHelper.collectAccessibilityLabels(in: controller.view)
                .filter { $0 == loadedLabel }
                .count == 3
        }

        XCTAssertEqual(stack.transport.callCount(method: .get, path: avatarPathA), 1)
        XCTAssertTrue(
            allRendered,
            "All 3 mounted AvatarCircleView instances must render the loaded avatar after shared request completes"
        )
    }
}
