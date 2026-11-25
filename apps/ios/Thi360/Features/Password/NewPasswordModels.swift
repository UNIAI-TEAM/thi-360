import Foundation

enum NewPasswordMode: Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    case fromLink(ResetLink)
    case forced(username: String?)
    case voluntary(username: String?)

    var requiresCurrentPassword: Bool {
        switch self {
        case .fromLink: false
        case .forced, .voluntary: true
        }
    }

    var username: String? {
        switch self {
        case .fromLink: nil
        case let .forced(username), let .voluntary(username): username
        }
    }

    var description: String {
        switch self {
        case .fromLink: "NewPasswordMode.fromLink(<redacted>)"
        case .forced: "NewPasswordMode.forced"
        case .voluntary: "NewPasswordMode.voluntary"
        }
    }

    var debugDescription: String { description }
}

enum NewPasswordFieldError: Equatable, Sendable {
    case oldPasswordRequired
    case wrongOldPassword
    case newPasswordRequired
    case newPasswordWeak
    case newPasswordSameAsUsername
    case confirmPasswordRequired
    case confirmPasswordMismatch

    var localizationKey: String {
        switch self {
        case .oldPasswordRequired: "recovery.password.oldRequired"
        case .wrongOldPassword: "recovery.password.wrongOld"
        case .newPasswordRequired: "recovery.password.newRequired"
        case .newPasswordWeak: "recovery.password.newWeak"
        case .newPasswordSameAsUsername: "recovery.password.sameAsUsername"
        case .confirmPasswordRequired: "recovery.password.confirmRequired"
        case .confirmPasswordMismatch: "recovery.password.confirmMismatch"
        }
    }

    func message(locale: AppLocale, bundle: Bundle = .main) -> String {
        RecoveryStrings.text(localizationKey, locale: locale, bundle: bundle)
    }
}

enum NewPasswordBanner: Equatable, Sendable {
    case changedSuccess
    case offline
    case genericResetError
    case genericChangeError

    var isDestructive: Bool {
        switch self {
        case .changedSuccess: false
        case .offline, .genericResetError, .genericChangeError: true
        }
    }

    func message(locale: AppLocale, bundle: Bundle = .main) -> String {
        switch self {
        case .changedSuccess:
            RecoveryStrings.text("recovery.change.success", locale: locale, bundle: bundle)
        case .offline:
            AuthStrings.text("auth.login.error.offline", locale: locale, bundle: bundle)
        case .genericResetError:
            RecoveryStrings.text("recovery.reset.genericError", locale: locale, bundle: bundle)
        case .genericChangeError:
            RecoveryStrings.text("recovery.change.genericError", locale: locale, bundle: bundle)
        }
    }
}
