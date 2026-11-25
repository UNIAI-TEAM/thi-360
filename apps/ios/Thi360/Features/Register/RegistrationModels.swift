import Foundation

struct RegistrationFormData: Equatable, Sendable {
    var fullName: String = ""
    var username: String = ""
    var email: String = ""
    var password: String = ""
    var acceptTerms: Bool = false

    func withoutPassword() -> RegistrationFormData {
        RegistrationFormData(
            fullName: fullName,
            username: username,
            email: email,
            password: "",
            acceptTerms: acceptTerms
        )
    }
}

enum RegistrationState: Equatable, Sendable {
    case form(data: RegistrationFormData, pendingRegistrationId: String? = nil)
    case otp(registrationId: String, email: String, resendAt: Date, formData: RegistrationFormData)
    case done
}

enum RegistrationValidation {
    static let termsVersion = "2026-11"
    static let maxFullNameLength = 200
    static let otpLength = 6

    private static let usernameRegex = try! NSRegularExpression(pattern: "^[A-Za-z0-9][A-Za-z0-9._@-]{3,29}$")
    private static let emailRegex = try! NSRegularExpression(pattern: "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private static let otpRegex = try! NSRegularExpression(pattern: "^[0-9]{6}$")

    static func isValidUsername(_ raw: String) -> Bool {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let range = NSRange(trimmed.startIndex..., in: trimmed)
        return usernameRegex.firstMatch(in: trimmed, range: range) != nil
    }

    static func isValidEmail(_ raw: String) -> Bool {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let range = NSRange(trimmed.startIndex..., in: trimmed)
        return emailRegex.firstMatch(in: trimmed, range: range) != nil
    }

    static func isValidOtpCode(_ raw: String) -> Bool {
        let range = NSRange(raw.startIndex..., in: raw)
        return otpRegex.firstMatch(in: raw, range: range) != nil
    }
}

enum RegisterFieldError: Equatable, Sendable {
    case fullNameRequired
    case fullNameTooLong
    case usernameRequired
    case usernamePattern
    case usernameTaken
    case emailRequired
    case emailInvalid
    case emailTaken
    case passwordRequired
    case passwordWeak
    case passwordSameAsUsername
    case termsRequired

    var localizationKey: String {
        switch self {
        case .fullNameRequired: "recovery.register.fullNameRequired"
        case .fullNameTooLong: "recovery.register.fullNameTooLong"
        case .usernameRequired: "recovery.register.usernameRequired"
        case .usernamePattern: "recovery.register.usernamePattern"
        case .usernameTaken: "recovery.register.usernameTaken"
        case .emailRequired: "recovery.register.emailRequired"
        case .emailInvalid: "recovery.register.emailInvalid"
        case .emailTaken: "recovery.register.emailTaken"
        case .passwordRequired: "recovery.register.passwordRequired"
        case .passwordWeak: "recovery.register.passwordWeak"
        case .passwordSameAsUsername: "recovery.register.passwordSameAsUsername"
        case .termsRequired: "recovery.register.termsRequired"
        }
    }

    func message(locale: AppLocale, bundle: Bundle = .main) -> String {
        RecoveryStrings.text(localizationKey, locale: locale, bundle: bundle)
    }
}

enum RegisterAlert: Equatable, Sendable {
    case rateLimited
    case offline
    case generic

    func message(locale: AppLocale, bundle: Bundle = .main) -> String {
        switch self {
        case .rateLimited:
            RecoveryStrings.text("recovery.register.errorRateLimited", locale: locale, bundle: bundle)
        case .offline:
            AuthStrings.text("auth.login.error.offline", locale: locale, bundle: bundle)
        case .generic:
            RecoveryStrings.text("recovery.register.errorGeneric", locale: locale, bundle: bundle)
        }
    }
}

enum OtpFieldError: Equatable, Sendable {
    case codeRequired
    case codeInvalid(remainingAttempts: Int?)

    func message(locale: AppLocale, bundle: Bundle = .main) -> String {
        switch self {
        case .codeRequired:
            RecoveryStrings.text("recovery.otp.codeRequired", locale: locale, bundle: bundle)
        case let .codeInvalid(remainingAttempts):
            if let remainingAttempts {
                RecoveryStrings.text(
                    "recovery.otp.codeInvalid",
                    locale: locale,
                    replacements: ["count": String(remainingAttempts)],
                    bundle: bundle
                )
            } else {
                RecoveryStrings.text("recovery.otp.codeInvalidGeneric", locale: locale, bundle: bundle)
            }
        }
    }
}

enum OtpAlert: Equatable, Sendable {
    case codeExpired
    case rateLimited
    case resendFailed
    case offline
    case generic

    func message(locale: AppLocale, bundle: Bundle = .main) -> String {
        switch self {
        case .codeExpired:
            RecoveryStrings.text("recovery.otp.alertCodeExpired", locale: locale, bundle: bundle)
        case .rateLimited:
            RecoveryStrings.text("recovery.otp.alertRateLimited", locale: locale, bundle: bundle)
        case .resendFailed:
            RecoveryStrings.text("recovery.otp.alertResendFailed", locale: locale, bundle: bundle)
        case .offline:
            AuthStrings.text("auth.login.error.offline", locale: locale, bundle: bundle)
        case .generic:
            RecoveryStrings.text("recovery.otp.alertGeneric", locale: locale, bundle: bundle)
        }
    }
}
