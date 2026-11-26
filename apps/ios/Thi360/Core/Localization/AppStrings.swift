import Foundation

enum AppStrings {
    static let commonTable = "Common"
    static let legalTable = "Legal"

    static func text(
        _ key: String,
        table: String,
        locale: AppLocale,
        replacements: [String: String] = [:],
        bundle: Bundle = .main
    ) -> String {
        var resolved: String
        if let lprojPath = bundle.path(forResource: locale.rawValue, ofType: "lproj"),
           let localizedBundle = Bundle(path: lprojPath)
        {
            let value = localizedBundle.localizedString(forKey: key, value: nil, table: table)
            if value != key {
                resolved = value
            } else {
                resolved = String(
                    localized: String.LocalizationValue(key),
                    table: table,
                    bundle: bundle,
                    locale: locale.locale
                )
            }
        } else {
            resolved = String(
                localized: String.LocalizationValue(key),
                table: table,
                bundle: bundle,
                locale: locale.locale
            )
        }

        for (token, value) in replacements {
            resolved = resolved.replacingOccurrences(of: "{\(token)}", with: value)
        }
        return resolved
    }

    static func common(
        _ key: String,
        locale: AppLocale,
        replacements: [String: String] = [:],
        bundle: Bundle = .main
    ) -> String {
        text(key, table: commonTable, locale: locale, replacements: replacements, bundle: bundle)
    }

    static func legal(
        _ key: String,
        locale: AppLocale,
        replacements: [String: String] = [:],
        bundle: Bundle = .main
    ) -> String {
        text(key, table: legalTable, locale: locale, replacements: replacements, bundle: bundle)
    }
}
