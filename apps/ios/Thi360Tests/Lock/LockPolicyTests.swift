import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class LockPolicyTests: XCTestCase {
    func testFiveMinuteTimeoutBoundariesAndColdLaunch() {
        let policy = LockPolicy()
        let start = Date(timeIntervalSince1970: 1_764_000_000)

        // Cold launch locks when enabled, does not lock when disabled.
        XCTAssertTrue(policy.shouldLock(isBiometricEnabled: true, isColdLaunch: true, backgroundedAt: nil, now: start))
        XCTAssertFalse(policy.shouldLock(isBiometricEnabled: false, isColdLaunch: true, backgroundedAt: nil, now: start))

        // Boundary 1: 4m59s (299s) -> must NOT lock.
        XCTAssertFalse(
            policy.shouldLock(
                isBiometricEnabled: true,
                isColdLaunch: false,
                backgroundedAt: start,
                now: start.addingTimeInterval(299)
            ),
            "4m59s away must stay unlocked"
        )

        // Boundary 2: 5m00s (300s) -> locks at 5-minute threshold.
        XCTAssertTrue(
            policy.shouldLock(
                isBiometricEnabled: true,
                isColdLaunch: false,
                backgroundedAt: start,
                now: start.addingTimeInterval(300)
            ),
            "5m00s away must lock"
        )

        // Boundary 3: 5m01s (301s) -> locks.
        XCTAssertTrue(
            policy.shouldLock(
                isBiometricEnabled: true,
                isColdLaunch: false,
                backgroundedAt: start,
                now: start.addingTimeInterval(301)
            ),
            "5m01s away must lock"
        )

        // Clock rolled backwards while backgrounded fails closed.
        XCTAssertTrue(
            policy.shouldLock(
                isBiometricEnabled: true,
                isColdLaunch: false,
                backgroundedAt: start,
                now: start.addingTimeInterval(-30)
            )
        )

        // Disabled biometric never locks even after 10 minutes.
        XCTAssertFalse(
            policy.shouldLock(
                isBiometricEnabled: false,
                isColdLaunch: false,
                backgroundedAt: start,
                now: start.addingTimeInterval(600)
            )
        )
    }

    func testColdLaunchAndBackgroundLifecycleWithPrivacyShield() {
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { _ in
            MockClientTransport.emptyResponse(statusCode: 200)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        // Cold launch with biometric enabled immediately starts in .locked.
        XCTAssertEqual(stack.sessionStore.state, .locked)

        stack.sessionStore.unlockSession()
        XCTAssertEqual(stack.sessionStore.state, .active)

        // Leaving to inactive/background immediately activates privacy shield so app switcher hides content.
        XCTAssertFalse(stack.biometricManager.handleScenePhaseChange(.inactive, sessionState: .active))
        XCTAssertTrue(stack.biometricManager.isPrivacyShieldVisible, "Privacy shield must cover content on inactive")

        XCTAssertFalse(stack.biometricManager.handleScenePhaseChange(.background, sessionState: .active))
        XCTAssertTrue(stack.biometricManager.isPrivacyShieldVisible, "Privacy shield must remain active in background")

        // Returning after 4m59s hides privacy shield and keeps session active.
        stack.clock.advance(by: 299)
        let shouldLockShort = stack.biometricManager.handleScenePhaseChange(.active, sessionState: .active)
        XCTAssertFalse(shouldLockShort)
        XCTAssertFalse(stack.biometricManager.isPrivacyShieldVisible)
        XCTAssertEqual(stack.sessionStore.state, .active)

        // Leaving again and returning after 5m01s triggers lock.
        _ = stack.biometricManager.handleScenePhaseChange(.background, sessionState: .active)
        stack.clock.advance(by: 301)
        let shouldLockLong = stack.biometricManager.handleScenePhaseChange(.active, sessionState: .active)
        XCTAssertTrue(shouldLockLong)
        stack.sessionStore.lockSession()
        XCTAssertEqual(stack.sessionStore.state, .locked)
    }
}
