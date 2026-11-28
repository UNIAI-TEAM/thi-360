import Foundation

enum ProfileStrings {
    static let tableName = "Profile"

    static func text(
        _ key: String,
        locale: AppLocale,
        replacements: [String: String] = [:],
        bundle: Bundle = .main
    ) -> String {
        AppStrings.text(
            key,
            table: tableName,
            locale: locale,
            replacements: replacements,
            bundle: bundle
        )
    }
}
