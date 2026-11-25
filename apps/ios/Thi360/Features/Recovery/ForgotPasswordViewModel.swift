import Foundation
import Observation

enum ForgotPasswordState: Equatable, Sendable {
    case form
    case sent(email: String, resendAt: Date)
}

enum ForgotFieldError: Equatable, Sendable {
    case emailRequired
    case emailInvalid

    var localizationKey: String {
        switch self {
        case .emailRequired: "recovery.forgot.emailRequired"
        case .emailInvalid: "recovery.forgot.emailInvalid"
        }
    }

    func message(locale: AppLocale, bundle: Bundle = .main) -> String {
        RecoveryStrings.text(localizationKey, locale: locale, bundle: bundle)
    }
}

enum ForgotAlert: Equatable, Sendable {
    case rateLimited
    case offline
    case generic

    func message(locale: AppLocale, bundle: Bundle = .main) -> String {
        switch self {
        case .rateLimited:
            RecoveryStrings.text("recovery.forgot.errorRateLimited", locale: locale, bundle: bundle)
        case .offline:
            AuthStrings.text("auth.login.error.offline", locale: locale, bundle: bundle)
        case .generic:
            RecoveryStrings.text("recovery.forgot.errorGeneric", locale: locale, bundle: bundle)
        }
    }
}

@Observable
@MainActor
final class ForgotPasswordViewModel {
    static let resendCooldownSeconds = 60

    var email: String = ""

    private(set) var state: ForgotPasswordState = .form
    private(set) var emailError: ForgotFieldError?
    private(set) var alert: ForgotAlert?
    private(set) var isSubmitting: Bool = false
    private(set) var currentTime: Date

    private let apiClient: any MobileRecoveryClientProtocol
    private let clock: any AppClock
    private let logger: RedactingAppLogger

    init(
        apiClient: any MobileRecoveryClientProtocol,
        clock: any AppClock = SystemClock(),
        logger: RedactingAppLogger = RedactingAppLogger()
    ) {
        self.apiClient = apiClient
        self.clock = clock
        self.logger = logger
        self.currentTime = clock.now
    }

    var resendSecondsRemaining: Int {
        guard case let .sent(_, resendAt) = state else { return 0 }
        let remaining = resendAt.timeIntervalSince(currentTime)
        return remaining > 0 ? Int(ceil(remaining)) : 0
    }

    var canResend: Bool {
        guard case .sent = state else { return false }
        return resendSecondsRemaining == 0 && !isSubmitting
    }

    func tick() {
        currentTime = clock.now
    }

    func clearEmailErrorIfNeeded() {
        if emailError != nil && RegistrationValidation.isValidEmail(email) {
            emailError = nil
        }
    }

    @discardableResult
    func validate() -> Bool {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            emailError = .emailRequired
        } else if !RegistrationValidation.isValidEmail(trimmed) {
            emailError = .emailInvalid
        } else {
            emailError = nil
        }
        return emailError == nil
    }

    func submit() async {
        guard !isSubmitting else { return }
        alert = nil
        guard validate() else { return }

        await sendRequest(for: email.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    func resend() async {
        guard case let .sent(sentEmail, _) = state, canResend else { return }
        alert = nil
        await sendRequest(for: sentEmail)
    }

    private func sendRequest(for trimmedEmail: String) async {
        isSubmitting = true
        defer { isSubmitting = false }

        do {
            try await apiClient.forgotPassword(email: trimmedEmail)
            let now = clock.now
            currentTime = now
            let resendAt = now.addingTimeInterval(TimeInterval(Self.resendCooldownSeconds))
            state = .sent(email: trimmedEmail, resendAt: resendAt)
            logger.log(.info, category: "recovery", message: "Password recovery instructions requested")
        } catch {
            let apiError = MobileApiError.classify(error)
            switch apiError {
            case let .api(statusCode, _):
                alert = statusCode == 429 ? .rateLimited : .generic
            case .network:
                alert = .offline
            case .unexpectedResponse:
                alert = .generic
            }
        }
    }
}
