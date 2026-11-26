import Foundation

enum RecoveryStrings {
    static let tableName = "Recovery"

    static func text(_ key: String, locale: AppLocale, bundle: Bundle = .main) -> String {
        if let lprojPath = bundle.path(forResource: locale.rawValue, ofType: "lproj"),
           let localizedBundle = Bundle(path: lprojPath)
        {
            let value = localizedBundle.localizedString(forKey: key, value: nil, table: tableName)
            if value != key {
                return value
            }
        }

        return String(localized: String.LocalizationValue(key), table: tableName, bundle: bundle, locale: locale.locale)
    }

    static func text(
        _ key: String,
        locale: AppLocale,
        replacements: [String: String],
        bundle: Bundle = .main
    ) -> String {
        var resolved = text(key, locale: locale, bundle: bundle)
        for (token, value) in replacements {
            resolved = resolved.replacingOccurrences(of: "{\(token)}", with: value)
        }
        return resolved
    }
}
