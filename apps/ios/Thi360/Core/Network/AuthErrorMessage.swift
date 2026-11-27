import Foundation

enum AuthStrings {
    static let tableName = "Auth"

    static func text(_ key: String, locale: AppLocale, bundle: Bundle = .main) -> String {
        AppStrings.text(key, table: tableName, locale: locale, bundle: bundle)
    }
}

enum LoginFieldError: Equatable, Sendable {
    case identifierRequired
    case passwordRequired

    var localizationKey: String {
        switch self {
        case .identifierRequired: "auth.login.identifierRequired"
        case .passwordRequired: "auth.login.passwordRequired"
        }
    }

    func message(locale: AppLocale, bundle: Bundle = .main) -> String {
        AuthStrings.text(localizationKey, locale: locale, bundle: bundle)
    }
}

enum LoginBannerKind: Equatable, Sendable {
    case error(LoginErrorCode)
    case sessionExpired
    case passwordResetDone
    case accountDeleted

    var isDestructive: Bool {
        switch self {
        case .error, .sessionExpired: true
        case .passwordResetDone, .accountDeleted: false
        }
    }

    func message(locale: AppLocale, bundle: Bundle = .main) -> String {
        switch self {
        case let .error(code):
            AuthErrorMessage.message(for: code, locale: locale, bundle: bundle)
        case .sessionExpired:
            AuthStrings.text("auth.login.sessionExpired", locale: locale, bundle: bundle)
        case .passwordResetDone:
            AuthStrings.text("auth.login.passwordResetDone", locale: locale, bundle: bundle)
        case .accountDeleted:
            AppStrings.common("account.deletion.deletedNotice", locale: locale, bundle: bundle)
        }
    }
}

enum LoginErrorCode: String, Equatable, Sendable, CaseIterable {
    case invalidCredentials = "INVALID_CREDENTIALS"
    case accountInactive = "ACCOUNT_INACTIVE"
    case loginThrottled = "LOGIN_THROTTLED"
    case rateLimited = "RATE_LIMITED"
    case offline = "OFFLINE"
    case generic = "GENERIC"

    var localizationKey: String {
        switch self {
        case .invalidCredentials: "auth.login.error.invalidCredentials"
        case .accountInactive: "auth.login.error.accountInactive"
        case .loginThrottled: "auth.login.error.loginThrottled"
        case .rateLimited: "auth.login.error.rateLimited"
        case .offline: "auth.login.error.offline"
        case .generic: "auth.login.error.generic"
        }
    }
}

enum AuthErrorMessage {
    static func code(for apiCode: String?) -> LoginErrorCode {
        guard let normalized = apiCode?.trimmingCharacters(in: .whitespacesAndNewlines),
              !normalized.isEmpty
        else {
            return .generic
        }

        switch normalized {
        case LoginErrorCode.invalidCredentials.rawValue:
            return .invalidCredentials
        case LoginErrorCode.accountInactive.rawValue:
            return .accountInactive
        case LoginErrorCode.loginThrottled.rawValue:
            return .loginThrottled
        case LoginErrorCode.rateLimited.rawValue:
            return .rateLimited
        default:
            return .generic
        }
    }

    static func code(for error: MobileApiError) -> LoginErrorCode {
        switch error {
        case let .api(_, payload):
            return code(for: payload.code)
        case .network:
            return .offline
        case .unexpectedResponse:
            return .generic
        }
    }

    static func message(for code: LoginErrorCode, locale: AppLocale, bundle: Bundle = .main) -> String {
        AuthStrings.text(code.localizationKey, locale: locale, bundle: bundle)
    }

    static func message(forApiCode apiCode: String?, locale: AppLocale, bundle: Bundle = .main) -> String {
        message(for: code(for: apiCode), locale: locale, bundle: bundle)
    }

    static func message(for error: MobileApiError, locale: AppLocale, bundle: Bundle = .main) -> String {
        message(for: code(for: error), locale: locale, bundle: bundle)
    }
}
