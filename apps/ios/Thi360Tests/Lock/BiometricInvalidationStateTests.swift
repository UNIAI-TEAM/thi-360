import Foundation
import XCTest
@testable import Thi360

@MainActor
final class BiometricInvalidationStateTests: XCTestCase {
    func testEnrollmentDomainChangeInvalidatesUnlockTurnsStateOffAndDemandsPassword() async {
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "student.demo"),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { _ in
            MockClientTransport.emptyResponse(statusCode: 200)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        XCTAssertEqual(stack.sessionStore.state, .locked)
        XCTAssertEqual(stack.biometricManager.state, .on)
        XCTAssertTrue(stack.secretStore.hasProtectedSecret())

        // Simulate user changing device biometric enrollment (new face/fingerprint registered).
        stack.authenticator.domainState = Data("domain-v2-changed".utf8)
        stack.secretStore.isInvalidated = true

        let lockVM = LockViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )

        await lockVM.unlockWithBiometrics(locale: .vi)

        XCTAssertEqual(stack.sessionStore.state, .locked, "Invalidated biometrics must not unlock app")
        XCTAssertTrue(lockVM.requiresPasswordAfterInvalidation, "Next unlock after enrollment change must require password")
        XCTAssertEqual(lockVM.errorMessageKey, "lock.error.invalidated")
        XCTAssertEqual(stack.biometricManager.state, .off, "BiometricState must transition to .off after invalidation")
        XCTAssertFalse(stack.secretStore.hasProtectedSecret(), "Invalidated Keychain item must be cleared")
        XCTAssertFalse(accountVM.isBiometricSwitchOn, "Account biometric switch must display OFF until user re-enables")
    }

    func testKeychainItemMissingFromBiometryCurrentSetInvalidationTurnsStateOff() async {
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "student.demo"),
            capability: .touchIDAvailable,
            initialEnrolled: true
        ) { _ in
            MockClientTransport.emptyResponse(statusCode: 200)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        // Keychain `.biometryCurrentSet` drops the item when biometric set changes.
        stack.secretStore.isInvalidated = true

        let lockVM = LockViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await lockVM.unlockWithBiometrics(locale: .en)

        XCTAssertEqual(stack.sessionStore.state, .locked)
        XCTAssertTrue(lockVM.requiresPasswordAfterInvalidation)
        XCTAssertEqual(stack.biometricManager.state, .off)
        XCTAssertEqual(
            lockVM.errorMessage(locale: .en),
            "Touch ID data on this device has changed. Please enter your password to continue."
        )
    }

    func testKeychainBiometricSecretStoreUsesBiometryCurrentSetAndFailsWhenUnenrolled() async throws {
        let service = "vn.unicom.thi360.tests.biometric.kc.\(UUID().uuidString)"
        let store = KeychainBiometricSecretStore(service: service)
        defer { try? store.deleteProtectedSecret() }

        try store.writeProtectedSecret(Data("biometric-refresh-secret".utf8))
        XCTAssertTrue(store.hasProtectedSecret(), "Stored .biometryCurrentSet Keychain item must exist")

        // On an unenrolled simulator, attempting to read a .biometryCurrentSet Keychain item must fail without returning the secret.
        let capability = LocalAuthenticationBiometricAuthenticator().currentCapability()
        if capability.unavailableReason == .notEnrolled {
            let result = await store.readProtectedSecret(reason: "Verify invalidation")
            XCTAssertEqual(result, .invalidated, "Reading .biometryCurrentSet item when unenrolled must report .invalidated")
        }

        try store.deleteProtectedSecret()
        XCTAssertFalse(store.hasProtectedSecret(), "Deleted .biometryCurrentSet Keychain item must no longer exist")
    }
}
