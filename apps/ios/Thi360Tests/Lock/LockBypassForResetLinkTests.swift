import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class LockBypassForResetLinkTests: XCTestCase {
    func testResetLinkOpensWhileLockedAndCompletingResetDisablesBiometricsAndDeletesProtectedData() async throws {
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "locked.student", refreshToken: "locked-refresh"),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/password/reset") {
                return MockClientTransport.emptyResponse(statusCode: 204)
            }
            return MockClientTransport.emptyResponse(statusCode: 404)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        XCTAssertEqual(stack.sessionStore.state, .locked)
        XCTAssertEqual(stack.biometricManager.state, .on)
        XCTAssertTrue(stack.secretStore.hasProtectedSecret())

        let env = AppEnvironment.current
        let webHost = env.webBaseURL.host ?? "localhost"
        let resetURL = URL(string: "https://\(webHost)/reset-password?token=locked-bypass-reset-token")!

        let router = AppRouter()
        XCTAssertTrue(router.handleIncomingURL(resetURL, environment: env))
        let activeLink = try XCTUnwrap(router.activeResetLink)

        let rootView = AppRootView(
            router: router,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            idiomOverride: .phone
        )
        let (window, controller) = RecoveryViewTestHelper.host(
            rootView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        // While still .locked, reset password screen bypasses lock gate immediately.
        let passwordFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertEqual(passwordFields.count, 2, "Reset link while locked must bypass lock screen and show New Password fields")

        let resetVM = router.resetPasswordViewModel(for: activeLink, sessionStore: stack.sessionStore)
        resetVM.newPassword = "NewStrongPass1!"
        resetVM.confirmPassword = "NewStrongPass1!"
        await resetVM.submit()

        XCTAssertTrue(resetVM.didComplete)
        XCTAssertNil(router.recoveryOverlay)
        XCTAssertEqual(stack.sessionStore.state, .signedOut(reason: .passwordReset))
        XCTAssertNil(try stack.tokenStore.read())
        XCTAssertEqual(stack.biometricManager.state, .off, "Resetting password while locked must disable biometric unlock")
        XCTAssertFalse(stack.secretStore.hasProtectedSecret(), "Resetting password while locked must delete biometric protected Keychain data")
    }
}
