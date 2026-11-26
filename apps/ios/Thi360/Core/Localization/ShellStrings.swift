import Foundation

enum ShellStrings {
    static let tableName = "Shell"

    static func text(_ key: String, locale: AppLocale, bundle: Bundle = .main) -> String {
        if let lprojPath = bundle.path(forResource: locale.rawValue, ofType: "lproj"),
           let localizedBundle = Bundle(path: lprojPath)
        {
            let value = localizedBundle.localizedString(forKey: key, value: nil, table: tableName)
            if value != key {
                return value
            }
        }

        // Fall back to Foundation's locale-aware String Catalog lookup when .lproj bundles are merged.
        return String(localized: String.LocalizationValue(key), table: tableName, bundle: bundle, locale: locale.locale)
    }
}
