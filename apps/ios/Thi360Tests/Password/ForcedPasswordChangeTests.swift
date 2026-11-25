import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ForcedPasswordChangeTests: XCTestCase {
    func testMustChangePasswordFromLoginBlocksShellAndCompletesDirectlyIntoApp() async throws {
        let mustChangeLoginFixture = try ContractFixtureLoader.loadAuthFixture("login-200-must-change-password.json")
        let (sessionStore, tokenStore, transport, preferences) = SessionTestFactory.makeStack { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/login") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: mustChangeLoginFixture)
            }
            if request.path.hasSuffix("/api/v1/auth/change-password") {
                XCTAssertEqual(request.header(.authorization), "Bearer fixture-access-token")
                return MockClientTransport.emptyResponse(statusCode: 204)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }

        try await sessionStore.login(identifier: "forced.student", password: "OldPassword1!")
        XCTAssertEqual(sessionStore.state, .mustChangePassword)

        let rootView = AppRootView(
            router: AppRouter(),
            preferences: preferences,
            sessionStore: sessionStore,
            idiomOverride: .phone
        )
        let (window, controller) = RecoveryViewTestHelper.host(
            rootView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        XCTAssertTrue(
            RecoveryViewTestHelper.findViews(ofType: UITabBar.self, in: controller.view).isEmpty,
            "mustChangePassword gate must block access to shell tabs"
        )
        XCTAssertEqual(
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count,
            3,
            "Forced change password screen must render current, new, and confirm password fields"
        )

        let forcedVM = NewPasswordViewModel(
            mode: .forced(username: sessionStore.currentUsername),
            sessionStore: sessionStore
        )
        forcedVM.oldPassword = "OldPassword1!"
        forcedVM.newPassword = "NewStrongPass2!"
        forcedVM.confirmPassword = "NewStrongPass2!"

        await forcedVM.submit()

        XCTAssertTrue(forcedVM.didComplete)
        XCTAssertEqual(sessionStore.state, .active, "Completing forced change must enter app without re-login")
        let updatedTokens = try XCTUnwrap(try tokenStore.read())
        XCTAssertFalse(updatedTokens.mustChangePassword, "Stored mustChangePassword flag must be cleared")
        XCTAssertEqual(updatedTokens.accessToken, "fixture-access-token")

        let relaunchedSession = SessionStore(
            tokenStore: tokenStore,
            apiClient: sessionStore.apiClient,
            skipsLaunchValidation: true
        )
        XCTAssertEqual(
            relaunchedSession.state,
            .active,
            "Subsequent launch after forced password change must open directly into active shell"
        )
        XCTAssertEqual(
            transport.recordedRequests.filter { $0.path.hasSuffix("/api/v1/auth/change-password") }.count,
            1
        )
    }

    func testMustChangePasswordFromRefreshBlocksShellAndAllowsSignOutOnlyExit() async throws {
        let mustChangeFixture = try ContractFixtureLoader.loadAuthFixture("login-200-must-change-password.json")
        let initialTokens = SessionTokens(
            accessToken: "active-access",
            refreshToken: "active-refresh",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "student.demo"
        )

        let (sessionStore, tokenStore, _, _) = SessionTestFactory.makeStack(
            initialTokens: initialTokens
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/refresh") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: mustChangeFixture)
            }
            if request.path.hasSuffix("/api/v1/auth/mobile/logout") {
                return MockClientTransport.emptyResponse(statusCode: 204)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }
        XCTAssertEqual(sessionStore.state, .active)

        await sessionStore.validateSessionOnLaunch()
        XCTAssertEqual(sessionStore.state, .mustChangePassword, "Refresh returning mustChangePassword=true must activate forced gate")

        await sessionStore.signOut()
        XCTAssertEqual(sessionStore.state, .signedOut(reason: .none))
        XCTAssertNil(try tokenStore.read())
    }
}
