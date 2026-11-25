import Foundation
import Observation

@Observable
@MainActor
final class LockViewModel {
    private(set) var errorMessageKey: String?
    private(set) var isAuthenticating: Bool = false
    private(set) var requiresPasswordAfterInvalidation: Bool = false
    private(set) var isSigningOut: Bool = false
    var isShowingPasswordFallback: Bool = false

    let sessionStore: SessionStore
    let biometricManager: BiometricManager

    init(
        sessionStore: SessionStore,
        biometricManager: BiometricManager
    ) {
        self.sessionStore = sessionStore
        self.biometricManager = biometricManager
    }

    var biometricKind: BiometricKind {
        biometricManager.capability.kind
    }

    var lockedUsername: String {
        sessionStore.currentUsername ?? ""
    }

    var biometricLabel: String {
        let name = biometricKind.displayName
        return name.isEmpty ? "Face ID" : name
    }

    func unlockButtonTitle(locale: AppLocale) -> String {
        AccountStrings.text(
            "lock.unlockButton",
            locale: locale,
            replacements: ["biometric": biometricLabel]
        )
    }

    func subtitleText(locale: AppLocale) -> String {
        if requiresPasswordAfterInvalidation || !biometricManager.capability.isAvailable {
            return AccountStrings.text("lock.subtitlePasswordRequired", locale: locale)
        }
        return AccountStrings.text(
            "lock.subtitle",
            locale: locale,
            replacements: ["biometric": biometricLabel]
        )
    }

    func errorMessage(locale: AppLocale) -> String? {
        guard let key = errorMessageKey else { return nil }
        return AccountStrings.text(
            key,
            locale: locale,
            replacements: ["biometric": biometricLabel]
        )
    }

    func unlockWithBiometrics(locale: AppLocale) async {
        guard !isAuthenticating, !requiresPasswordAfterInvalidation else { return }
        isAuthenticating = true
        errorMessageKey = nil
        defer { isAuthenticating = false }

        let reason = AccountStrings.text(
            "account.biometric.reasonUnlock",
            locale: locale,
            replacements: ["biometric": biometricLabel]
        )
        let result = await biometricManager.attemptUnlock(reason: reason)
        switch result {
        case .succeeded:
            reset()
            sessionStore.unlockSession()
            await sessionStore.validateSessionAfterUnlock()
        case .cancelled:
            errorMessageKey = "lock.error.cancelled"
        case .failed:
            errorMessageKey = "lock.error.failed"
        case .lockout:
            errorMessageKey = "lock.error.lockout"
        case .invalidated, .unavailable:
            requiresPasswordAfterInvalidation = true
            errorMessageKey = "lock.error.invalidated"
        }
    }

    func checkInvalidationOnAppear() {
        if biometricManager.detectAndHandleInvalidationIfNeeded() {
            requiresPasswordAfterInvalidation = true
            errorMessageKey = "lock.error.invalidated"
        }
    }

    func openPasswordFallback() {
        isShowingPasswordFallback = true
    }

    func closePasswordFallback() {
        isShowingPasswordFallback = false
    }

    func signInWithOtherAccount() async {
        guard !isSigningOut else { return }
        isSigningOut = true
        isShowingPasswordFallback = false
        defer { isSigningOut = false }

        await sessionStore.signOut(reason: .none)
        reset()
    }

    func reset() {
        errorMessageKey = nil
        requiresPasswordAfterInvalidation = false
        isShowingPasswordFallback = false
    }
}
