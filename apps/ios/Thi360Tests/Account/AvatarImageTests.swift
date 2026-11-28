import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class AvatarImageTests: XCTestCase {
    private let avatarPath = "/api/v1/avatars/00000000-0000-7000-8000-000000000001"

    func testNoAvatarShowsInitialsAndCurrentEmptyAccessibilityLabel() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: profileData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let imageLoader = ImageLoader(
            apiBaseURL: stack.sessionStore.apiClient.serverURL,
            tokenStore: stack.tokenStore,
            refresher: stack.sessionStore.apiClient.refresher,
            fetcher: ClientTransportImageDataFetcher(transport: stack.transport)
        )
        let avatarView = AvatarCircleView(
            avatarUrl: nil,
            initials: "NA",
            size: 80,
            fontSize: 28,
            locale: .vi,
            exposeAvatarAccessibility: true,
            loaderOverride: imageLoader
        )
        .environment(\.brandPalette, .defaultLight)

        let (window, controller) = ProfileFixtureHelper.host(
            avatarView,
            size: CGSize(width: 120, height: 120),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let labels = ProfileFixtureHelper.collectAccessibilityLabels(in: controller.view)
        XCTAssertTrue(labels.contains(ProfileStrings.text("account.profile.avatar.currentEmpty", locale: .vi)))
        XCTAssertTrue(stack.transport.requests(method: .get, path: avatarPath).isEmpty)
    }

    func testAvatarUrlFetchesOnceWithBearerShowsInitialsWhileLoadingThenImageWithCurrentLabel() async throws {
        let pngData = try ProfileFixtureHelper.makeSamplePNG()
        let gate = TestAsyncGate()
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(accessToken: "avatar-bearer-token")
        ) { transport in
            transport.register(method: .get, path: avatarPath) { _ in
                await gate.enterAndWait()
                var fields = HTTPFields()
                fields[.contentType] = "image/png"
                return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngData))
            }
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let imageLoader = ImageLoader(
            apiBaseURL: stack.sessionStore.apiClient.serverURL,
            tokenStore: stack.tokenStore,
            refresher: stack.sessionStore.apiClient.refresher,
            fetcher: ClientTransportImageDataFetcher(transport: stack.transport)
        )

        let avatarView = AvatarCircleView(
            avatarUrl: avatarPath,
            initials: "MA",
            size: 80,
            fontSize: 28,
            locale: .vi,
            exposeAvatarAccessibility: true,
            loaderOverride: imageLoader
        )
        .environment(\.brandPalette, .defaultLight)

        let (window, controller) = ProfileFixtureHelper.host(
            avatarView,
            size: CGSize(width: 120, height: 120),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        await gate.waitUntilEntered()
        let loadingLabels = ProfileFixtureHelper.collectAccessibilityLabels(in: controller.view)
        XCTAssertTrue(
            loadingLabels.contains(ProfileStrings.text("account.profile.avatar.currentEmpty", locale: .vi)),
            "While loading avatar, view must display initials fallback"
        )

        await gate.release()
        let loaded = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view).contains("profile.avatar.image")
        }
        XCTAssertTrue(loaded, "Avatar image must render once 200 completes")

        let loadedLabels = ProfileFixtureHelper.collectAccessibilityLabels(in: controller.view)
        XCTAssertTrue(loadedLabels.contains(ProfileStrings.text("account.profile.avatar.current", locale: .vi)))

        let requests = stack.transport.requests(method: .get, path: avatarPath)
        XCTAssertEqual(requests.count, 1, "Avatar GET must execute strictly once")
        XCTAssertEqual(requests.first?.header(.authorization), "Bearer avatar-bearer-token")
    }

    func testAvatar404AndOfflineKeepInitialsWithoutErrorOrRetryLoopUntilNextOpen() async throws {
        let pngData = try ProfileFixtureHelper.makeSamplePNG()
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(accessToken: "avatar-404-token")
        ) { transport in
            transport.enqueue(method: .get, path: avatarPath, handlers: [
                { _ in ContractFakeTransport.emptyResponse(statusCode: 404) },
                { _ in throw URLError(.notConnectedToInternet) },
                { _ in
                    var fields = HTTPFields()
                    fields[.contentType] = "image/png"
                    return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngData))
                }
            ])
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let imageLoader = ImageLoader(
            apiBaseURL: stack.sessionStore.apiClient.serverURL,
            tokenStore: stack.tokenStore,
            refresher: stack.sessionStore.apiClient.refresher,
            fetcher: ClientTransportImageDataFetcher(transport: stack.transport)
        )

        let firstView = AvatarCircleView(
            avatarUrl: avatarPath,
            initials: "MA",
            size: 80,
            fontSize: 28,
            locale: .vi,
            exposeAvatarAccessibility: true,
            loaderOverride: imageLoader,
            reloadToken: 0
        )
        .environment(\.brandPalette, .defaultLight)

        let (window, controller) = ProfileFixtureHelper.host(
            firstView,
            size: CGSize(width: 120, height: 120),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let firstDone = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            stack.transport.callCount(method: .get, path: self.avatarPath) == 1
                && ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view).contains("profile.avatar.initials")
        }
        XCTAssertTrue(firstDone)
        try await Task.sleep(nanoseconds: 200_000_000)

        XCTAssertEqual(stack.transport.callCount(method: .get, path: avatarPath), 1, "404 must not trigger a retry loop")
        XCTAssertTrue(ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view).contains("profile.avatar.initials"))

        ProfileFixtureHelper.setRootView(
            AvatarCircleView(
                avatarUrl: avatarPath,
                initials: "MA",
                size: 80,
                fontSize: 28,
                locale: .vi,
                exposeAvatarAccessibility: true,
                loaderOverride: imageLoader,
                reloadToken: 1
            )
            .environment(\.brandPalette, .defaultLight),
            on: controller
        )

        let secondDone = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            stack.transport.callCount(method: .get, path: self.avatarPath) == 2
                && ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view).contains("profile.avatar.initials")
        }
        XCTAssertTrue(secondDone)
        try await Task.sleep(nanoseconds: 200_000_000)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: avatarPath), 2, "Offline failure must not retry in a loop")
        XCTAssertTrue(ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view).contains("profile.avatar.initials"))

        ProfileFixtureHelper.setRootView(
            AvatarCircleView(
                avatarUrl: avatarPath,
                initials: "MA",
                size: 80,
                fontSize: 28,
                locale: .vi,
                exposeAvatarAccessibility: true,
                loaderOverride: imageLoader,
                reloadToken: 2
            )
            .environment(\.brandPalette, .defaultLight),
            on: controller
        )

        let recovered = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            ProfileFixtureHelper.collectAccessibilityIdentifiers(in: controller.view).contains("profile.avatar.image")
        }
        XCTAssertTrue(recovered, "Next open must retry strictly once and display recovered image")
        XCTAssertEqual(stack.transport.callCount(method: .get, path: avatarPath), 3)
    }

    func testAvatar401WithRevokedSessionClearsSessionAndSignsOutToLogin() async throws {
        let unauthFixture = try ContractFixtureLoader.loadApiFixture("me-401-unauthenticated.json")
        let refresh401 = try ContractFixtureLoader.loadAuthFixture("refresh-401-session-revoked.json")

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(accessToken: "expired-token", refreshToken: "revoked-refresh")
        ) { transport in
            transport.registerJSON(method: .get, path: avatarPath, statusCode: 401, data: unauthFixture)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 401, data: refresh401)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let imageLoader = ImageLoader(
            apiBaseURL: stack.sessionStore.apiClient.serverURL,
            tokenStore: stack.tokenStore,
            refresher: stack.sessionStore.apiClient.refresher,
            fetcher: ClientTransportImageDataFetcher(transport: stack.transport)
        )

        do {
            _ = try await imageLoader.loadImage(from: avatarPath, authenticated: true)
            XCTFail("Expected 401 failure")
        } catch {
        }

        let signedOut = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            stack.sessionStore.state == .signedOut(reason: .expired)
        }
        XCTAssertTrue(signedOut, "401 on avatar with revoked session must sign out to Login")
        XCTAssertNil(try stack.tokenStore.read())
    }
}
