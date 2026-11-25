import Foundation
import XCTest
@testable import Thi360

@MainActor
final class BiometricEnableTests: XCTestCase {
    func testEnableSucceedsOnlyWhenOSConfirmsAndCancelOrFailKeepsOff() async throws {
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(refreshToken: "refresh-secret-1"),
            capability: .faceIDAvailable,
            enableResults: [.cancelled, .failed, .lockout, .succeeded]
        ) { _ in
            MockClientTransport.emptyResponse(statusCode: 200)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )

        // 1. OS cancelled -> stays off, nothing stored.
        await accountVM.setBiometricEnabled(true, locale: .vi)
        XCTAssertEqual(stack.biometricManager.state, .off)
        XCTAssertFalse(stack.secretStore.hasProtectedSecret())

        // 2. OS failed -> stays off, shows feedback, nothing stored.
        await accountVM.setBiometricEnabled(true, locale: .vi)
        XCTAssertEqual(stack.biometricManager.state, .off)
        XCTAssertNotNil(accountVM.biometricFeedbackMessage(locale: .vi))
        XCTAssertFalse(stack.secretStore.hasProtectedSecret())

        // 3. OS lockout -> stays off, shows lockout feedback.
        await accountVM.setBiometricEnabled(true, locale: .vi)
        XCTAssertEqual(stack.biometricManager.state, .off)
        XCTAssertEqual(accountVM.biometricFeedbackKey, "account.biometric.lockout")
        XCTAssertFalse(stack.secretStore.hasProtectedSecret())

        // 4. OS confirmed -> transitions to .on and writes protected Keychain secret.
        await accountVM.setBiometricEnabled(true, locale: .vi)
        XCTAssertEqual(stack.biometricManager.state, .on)
        XCTAssertTrue(accountVM.isBiometricSwitchOn)
        XCTAssertNil(accountVM.biometricFeedbackKey)
        XCTAssertTrue(stack.secretStore.hasProtectedSecret())
        XCTAssertEqual(stack.secretStore.peekStoredDataForTesting(), Data("refresh-secret-1".utf8))
        XCTAssertEqual(stack.authenticator.enableCallCount, 4)
    }

    func testDisableStopsLockingAndReEnableRequiresOSConfirmationAgain() async throws {
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(refreshToken: "refresh-secret-2"),
            capability: .touchIDAvailable,
            enableResults: [.succeeded, .cancelled, .succeeded]
        ) { _ in
            MockClientTransport.emptyResponse(statusCode: 200)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )

        await accountVM.setBiometricEnabled(true, locale: .vi)
        XCTAssertEqual(stack.biometricManager.state, .on)
        XCTAssertEqual(stack.authenticator.enableCallCount, 1)

        // Disable in Account -> stops locking and removes protected secret.
        await accountVM.setBiometricEnabled(false, locale: .vi)
        XCTAssertEqual(stack.biometricManager.state, .off)
        XCTAssertFalse(stack.secretStore.hasProtectedSecret())
        XCTAssertFalse(stack.biometricManager.shouldLockOnColdLaunch())
        _ = stack.biometricManager.handleScenePhaseChange(.background, sessionState: .active)
        stack.clock.advance(by: 600)
        let shouldLockWhenDisabled = stack.biometricManager.handleScenePhaseChange(.active, sessionState: .active)
        XCTAssertFalse(shouldLockWhenDisabled, "App must not lock once biometric toggle is turned off")

        // Turning back on requires fresh OS confirmation; cancelling keeps it off.
        await accountVM.setBiometricEnabled(true, locale: .vi)
        XCTAssertEqual(stack.authenticator.enableCallCount, 2)
        XCTAssertEqual(stack.biometricManager.state, .off)

        // Confirming re-enable turns it back on.
        await accountVM.setBiometricEnabled(true, locale: .vi)
        XCTAssertEqual(stack.authenticator.enableCallCount, 3)
        XCTAssertEqual(stack.biometricManager.state, .on)
        XCTAssertTrue(stack.secretStore.hasProtectedSecret())
    }
}
