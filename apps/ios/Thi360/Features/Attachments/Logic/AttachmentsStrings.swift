import Foundation

enum AttachmentsStrings {
    static let table = "Attachments"

    static func text(_ key: String, locale: AppLocale, replacements: [String: String] = [:]) -> String {
        AppStrings.text(key, table: table, locale: locale, replacements: replacements)
    }

    static func attachmentsLabel(locale: AppLocale) -> String {
        text("attempt.player.attachmentsLabel", locale: locale)
    }

    static func openError(locale: AppLocale) -> String {
        text("attempt.attachments.openError", locale: locale)
    }

    static func regionLabel(locale: AppLocale) -> String {
        text("question.audioPlayer.regionLabel", locale: locale)
    }

    static func progressLabel(locale: AppLocale) -> String {
        text("question.audioPlayer.progressLabel", locale: locale)
    }

    static func playsRemaining(left: Int, max: Int, locale: AppLocale) -> String {
        text(
            "question.audioPlayer.playsRemaining",
            locale: locale,
            replacements: [
                "left": OptionOrder.formatInteger(left, locale: locale),
                "max": OptionOrder.formatInteger(max, locale: locale)
            ]
        )
    }

    static func playsExhaustedBadge(max: Int, locale: AppLocale) -> String {
        text(
            "question.audioPlayer.playsExhaustedBadge",
            locale: locale,
            replacements: [
                "max": OptionOrder.formatInteger(max, locale: locale)
            ]
        )
    }
}
