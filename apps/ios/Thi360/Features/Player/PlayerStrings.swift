import Foundation

enum PlayerStrings {
    static let table = "Player"

    static let knownPartChoiceErrorCodes: Set<String> = [
        "ATTEMPT_SESSION_REVOKED",
        "CHOICE_LOCKED",
        "CHOICE_NOT_ALLOWED",
        "CHOICE_WINDOW_CLOSED",
        "PART_GENERATING",
        "PART_NOT_AWAITING",
        "RATE_LIMITED",
        "SUBJECTS_INVALID"
    ]

    static func text(
        _ key: String,
        locale: AppLocale,
        replacements: [String: String] = [:],
        bundle: Bundle = .main
    ) -> String {
        AppStrings.text(key, table: table, locale: locale, replacements: replacements, bundle: bundle)
    }

    static func plural(
        _ key: String,
        count: Int,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        let template = AppStrings.text(key, table: table, locale: locale, bundle: bundle)
        return PrepareStrings.formatICUPlural(template, count: count, locale: locale)
    }

    static func partStateLabel(
        _ state: Components.Schemas.AttemptPartStateView.statePayload,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        text("attempt.parts.states.\(state.rawValue)", locale: locale, bundle: bundle)
    }

    static func partChoiceErrorMessage(
        code: String?,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        if let code, knownPartChoiceErrorCodes.contains(code) {
            return text("attempt.partChoice.errors.\(code)", locale: locale, bundle: bundle)
        }
        return text("attempt.partChoice.errors.generic", locale: locale, bundle: bundle)
    }

    static func saveStatusText(
        _ status: SaveStatus,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> (label: String, reason: String?) {
        switch status {
        case .saved:
            return (text("attempt.saveStatus.saved", locale: locale, bundle: bundle), nil)
        case .saving:
            return (text("attempt.saveStatus.saving", locale: locale, bundle: bundle), nil)
        case .offline:
            return (
                text("attempt.saveStatus.unsaved", locale: locale, bundle: bundle),
                text("attempt.saveStatus.reasons.offline", locale: locale, bundle: bundle)
            )
        case let .unsaved(failure):
            let reasonText: String
            switch failure {
            case .networkError:
                reasonText = text("attempt.saveStatus.reasons.network_error", locale: locale, bundle: bundle)
            case .serverError:
                reasonText = text("attempt.saveStatus.reasons.server_error", locale: locale, bundle: bundle)
            case let .rateLimited(seconds):
                reasonText = text(
                    "attempt.saveStatus.reasons.rate_limited",
                    locale: locale,
                    replacements: ["seconds": String(max(1, seconds))],
                    bundle: bundle
                )
            case .rejected:
                reasonText = text("attempt.saveStatus.reasons.rejected", locale: locale, bundle: bundle)
            case .sessionConflict:
                reasonText = text("attempt.saveStatus.reasons.session_conflict", locale: locale, bundle: bundle)
            }
            return (text("attempt.saveStatus.unsaved", locale: locale, bundle: bundle), reasonText)
        }
    }

    static func formatPoints(_ value: Double, locale: AppLocale) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 2
        formatter.locale = locale.locale
        return formatter.string(from: NSNumber(value: value)) ?? String(value)
    }
}
