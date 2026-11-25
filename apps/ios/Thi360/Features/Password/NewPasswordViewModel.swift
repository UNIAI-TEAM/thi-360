import Foundation
import Observation

@Observable
@MainActor
final class NewPasswordViewModel {
    let mode: NewPasswordMode

    var oldPassword: String = ""
    var newPassword: String = ""
    var confirmPassword: String = ""

    var isOldPasswordVisible: Bool = false
    var isNewPasswordVisible: Bool = false
    var isConfirmPasswordVisible: Bool = false

    private(set) var oldPasswordError: NewPasswordFieldError?
    private(set) var newPasswordError: NewPasswordFieldError?
    private(set) var confirmPasswordError: NewPasswordFieldError?
    private(set) var banner: NewPasswordBanner?
    private(set) var isSubmitting: Bool = false
    private(set) var isResetLinkInvalid: Bool = false
    private(set) var didComplete: Bool = false

    // Kept private and cleared immediately on screen dismissal or completion so a reset token can never be replayed (Spec AC-51).
    private var activeResetToken: String?

    private let sessionStore: SessionStore
    private let apiClient: any MobileRecoveryClientProtocol
    private let logger: RedactingAppLogger
    private let onResetCompleted: (() -> Void)?
    private let onVoluntaryCompleted: (() -> Void)?

    init(
        mode: NewPasswordMode,
        sessionStore: SessionStore,
        apiClient: (any MobileRecoveryClientProtocol)? = nil,
        logger: RedactingAppLogger = RedactingAppLogger(),
        onResetCompleted: (() -> Void)? = nil,
        onVoluntaryCompleted: (() -> Void)? = nil
    ) {
        self.mode = mode
        self.sessionStore = sessionStore
        self.apiClient = apiClient ?? sessionStore.apiClient
        self.logger = logger
        self.onResetCompleted = onResetCompleted
        self.onVoluntaryCompleted = onVoluntaryCompleted

        if case let .fromLink(resetLink) = mode {
            let trimmed = resetLink.token.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty {
                self.activeResetToken = nil
                self.isResetLinkInvalid = true
            } else {
                self.activeResetToken = trimmed
            }
        }
    }

    var passwordEvaluation: PasswordRuleEvaluation {
        PasswordRules.evaluate(newPassword)
    }

    var hasActiveResetToken: Bool {
        guard let token = activeResetToken else { return false }
        return !token.isEmpty
    }

    func toggleOldPasswordVisibility() {
        isOldPasswordVisible.toggle()
    }

    func toggleNewPasswordVisibility() {
        isNewPasswordVisible.toggle()
    }

    func toggleConfirmPasswordVisibility() {
        isConfirmPasswordVisible.toggle()
    }

    func clearFieldErrorOnEdit(
        oldPassword: Bool = false,
        newPassword: Bool = false,
        confirmPassword: Bool = false
    ) {
        if oldPassword, oldPasswordError != nil, !self.oldPassword.isEmpty {
            oldPasswordError = nil
        }
        if newPassword, newPasswordError != nil,
           PasswordRules.isStrong(self.newPassword),
           !PasswordRules.matchesUsername(self.newPassword, username: mode.username)
        {
            newPasswordError = nil
        }
        if confirmPassword, confirmPasswordError != nil,
           !self.confirmPassword.isEmpty,
           self.confirmPassword == self.newPassword
        {
            confirmPasswordError = nil
        }
    }

    func clearResetToken() {
        activeResetToken = nil
        clearSensitiveInputs()
    }

    func clearSensitiveInputs() {
        oldPassword = ""
        newPassword = ""
        confirmPassword = ""
        isOldPasswordVisible = false
        isNewPasswordVisible = false
        isConfirmPasswordVisible = false
    }

    @discardableResult
    func validate() -> Bool {
        if mode.requiresCurrentPassword {
            oldPasswordError = oldPassword.isEmpty ? .oldPasswordRequired : nil
        } else {
            oldPasswordError = nil
        }

        if newPassword.isEmpty {
            newPasswordError = .newPasswordRequired
        } else if !PasswordRules.isStrong(newPassword) {
            newPasswordError = .newPasswordWeak
        } else if PasswordRules.matchesUsername(newPassword, username: mode.username) {
            newPasswordError = .newPasswordSameAsUsername
        } else {
            newPasswordError = nil
        }

        if confirmPassword.isEmpty {
            confirmPasswordError = .confirmPasswordRequired
        } else if confirmPassword != newPassword {
            confirmPasswordError = .confirmPasswordMismatch
        } else {
            confirmPasswordError = nil
        }

        return oldPasswordError == nil && newPasswordError == nil && confirmPasswordError == nil
    }

    func submit() async {
        guard !isSubmitting else { return }

        if case .fromLink = mode {
            guard let token = activeResetToken, !token.isEmpty else {
                isResetLinkInvalid = true
                return
            }
            await submitResetFromLink(token: token)
            return
        }

        await submitAuthenticatedPasswordChange()
    }

    private func submitResetFromLink(token: String) async {
        banner = nil
        guard validate() else { return }

        isSubmitting = true
        defer { isSubmitting = false }

        let submittedNewPassword = newPassword

        do {
            try await apiClient.resetPassword(token: token, newPassword: submittedNewPassword)
            clearResetToken()
            await sessionStore.clearLocalSession(reason: .passwordReset)
            didComplete = true
            logger.log(.info, category: "recovery", message: "Password reset by link succeeded")
            onResetCompleted?()
        } catch {
            let apiError = MobileApiError.classify(error)
            switch apiError {
            case let .api(statusCode, payload):
                if statusCode == 422 || payload.code == "RESET_TOKEN_INVALID" {
                    clearResetToken()
                    isResetLinkInvalid = true
                    return
                }
                if applyPasswordWeakError(payload: payload) {
                    return
                }
                banner = .genericResetError
            case .network:
                banner = .offline
            case .unexpectedResponse:
                banner = .genericResetError
            }
        }
    }

    private func submitAuthenticatedPasswordChange() async {
        banner = nil
        guard validate() else { return }

        isSubmitting = true
        defer { isSubmitting = false }

        let submittedOldPassword = oldPassword
        let submittedNewPassword = newPassword

        do {
            try await apiClient.changePassword(oldPassword: submittedOldPassword, newPassword: submittedNewPassword)
            clearSensitiveInputs()
            didComplete = true

            switch mode {
            case .forced:
                try await sessionStore.completeForcedPasswordChange()
                logger.log(.info, category: "password", message: "Forced password change succeeded")
            case .voluntary:
                banner = .changedSuccess
                logger.log(.info, category: "password", message: "Voluntary password change succeeded")
                onVoluntaryCompleted?()
            case .fromLink:
                break
            }
        } catch {
            let apiError = MobileApiError.classify(error)
            switch apiError {
            case let .api(_, payload):
                if payload.code == "WRONG_OLD_PASSWORD" {
                    oldPasswordError = .wrongOldPassword
                    return
                }
                if applyPasswordWeakError(payload: payload) {
                    return
                }
                banner = .genericChangeError
            case .network:
                banner = .offline
            case .unexpectedResponse:
                banner = .genericChangeError
            }
        }
    }

    @discardableResult
    private func applyPasswordWeakError(payload: ApiError) -> Bool {
        if payload.fieldErrors.contains(where: { $0.code == "WRONG_OLD_PASSWORD" }) {
            oldPasswordError = .wrongOldPassword
            return true
        }
        if payload.fieldErrors.contains(where: { $0.code == "PASSWORD_SAME_AS_USERNAME" }) {
            newPasswordError = .newPasswordSameAsUsername
            return true
        }
        if payload.code == "PASSWORD_WEAK"
            || payload.fieldErrors.contains(where: { $0.field == "newPassword" || $0.field == "password" })
        {
            newPasswordError = .newPasswordWeak
            return true
        }
        return false
    }
}
