import Foundation
import Observation

@Observable
@MainActor
final class LoginViewModel {
    var identifier: String
    var password: String = ""
    var isPasswordVisible: Bool = false
    private(set) var identifierError: LoginFieldError?
    private(set) var passwordError: LoginFieldError?
    private(set) var serverBanner: LoginBannerKind?
    private(set) var isSubmitting: Bool = false

    private let sessionStore: SessionStore
    let isLockPasswordFallback: Bool

    init(
        sessionStore: SessionStore,
        prefilledIdentifier: String = "",
        initialReason: SignedOutReason = .none,
        isLockPasswordFallback: Bool = false
    ) {
        self.sessionStore = sessionStore
        self.identifier = prefilledIdentifier
        self.serverBanner = Self.banner(for: initialReason)
        self.isLockPasswordFallback = isLockPasswordFallback
    }

    func updateSignedOutReason(_ reason: SignedOutReason) {
        serverBanner = Self.banner(for: reason)
    }

    func clearIdentifierErrorIfNeeded() {
        if identifierError != nil && !identifier.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            identifierError = nil
        }
    }

    func clearPasswordErrorIfNeeded() {
        if passwordError != nil && !password.isEmpty {
            passwordError = nil
        }
    }

    func togglePasswordVisibility() {
        isPasswordVisible.toggle()
    }

    @discardableResult
    func validate() -> Bool {
        let trimmedIdentifier = identifier.trimmingCharacters(in: .whitespacesAndNewlines)
        identifierError = trimmedIdentifier.isEmpty ? .identifierRequired : nil
        passwordError = password.isEmpty ? .passwordRequired : nil
        return identifierError == nil && passwordError == nil
    }

    func submit() async {
        guard !isSubmitting else { return }

        serverBanner = nil
        guard validate() else { return }

        isSubmitting = true
        defer { isSubmitting = false }

        let submittedIdentifier = identifier.trimmingCharacters(in: .whitespacesAndNewlines)
        let submittedPassword = password

        do {
            if isLockPasswordFallback {
                try await sessionStore.reauthenticateFromLock(
                    identifier: submittedIdentifier,
                    password: submittedPassword
                )
            } else {
                try await sessionStore.login(identifier: submittedIdentifier, password: submittedPassword)
            }
            password = ""
            isPasswordVisible = false
        } catch {
            let apiError = MobileApiError.classify(error)
            serverBanner = .error(AuthErrorMessage.code(for: apiError))
            password = ""
        }
    }

    private static func banner(for reason: SignedOutReason) -> LoginBannerKind? {
        switch reason {
        case .expired:
            .sessionExpired
        case .passwordReset:
            .passwordResetDone
        case .accountDeleted:
            .accountDeleted
        case .none, .studentOnly:
            nil
        }
    }
}
