import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class PrivacyShieldPortalRoutesTests: XCTestCase {
    func testPrivacyShieldCoversPortalSubRoutesAndChromelessAttemptWhenInactive() throws {
        let refreshData = try ContractFixtureLoader.loadAuthFixture("refresh-200.json")
        let meData = try ContractFixtureLoader.loadApiFixture("me-200.json")
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 200, data: refreshData)
            transport.registerJSON(method: .get, path: "/api/v1/me", statusCode: 200, data: meData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        stack.sessionStore.unlockSession()
        let router = AppRouter(initialDestination: .myExams)
        router.openAttempt(attemptId: "att-shield-1")

        let rootView = AppRootView(
            router: router,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            idiomOverride: .phone
        )

        let size = CGSize(width: 390, height: 844)
        let (window, _) = RecoveryViewTestHelper.host(
            rootView,
            size: size,
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        XCTAssertFalse(stack.biometricManager.isPrivacyShieldVisible)
        let unshieldedPNG = try RecoveryViewTestHelper.captureSnapshot(
            window: window,
            size: size,
            name: "privacy-shield-attempt-active",
            in: self
        )

        _ = stack.biometricManager.handleScenePhaseChange(.inactive, sessionState: stack.sessionStore.state)
        XCTAssertTrue(stack.biometricManager.isPrivacyShieldVisible, "Biometric manager must enable privacy shield when inactive")
        window.setNeedsLayout()
        window.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.08))

        let shieldedPNG = try RecoveryViewTestHelper.captureSnapshot(
            window: window,
            size: size,
            name: "privacy-shield-attempt-inactive",
            in: self
        )
        XCTAssertNotEqual(
            unshieldedPNG,
            shieldedPNG,
            "PrivacyShieldView must visibly cover chromeless attempt route when inactive"
        )

        let shieldSource = try String(
            contentsOf: ContractFixtureLoader.repoRootURL
                .appendingPathComponent("apps/ios/Thi360/Shell/AppRouter.swift"),
            encoding: .utf8
        )
        XCTAssertTrue(shieldSource.contains("PrivacyShieldView"), "AppRootView must render PrivacyShieldView at root ZStack")
        XCTAssertTrue(shieldSource.contains(".zIndex(100)"), "PrivacyShieldView must sit above chromeless attempt route")

        _ = stack.biometricManager.handleScenePhaseChange(.active, sessionState: stack.sessionStore.state)
        XCTAssertFalse(stack.biometricManager.isPrivacyShieldVisible)
    }
}
