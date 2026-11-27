import Foundation

enum ResultStrings {
    static let table = "Result"

    static func text(
        _ key: String,
        locale: AppLocale,
        replacements: [String: String] = [:],
        bundle: Bundle = .main
    ) -> String {
        AppStrings.text(
            key,
            table: table,
            locale: locale,
            replacements: replacements,
            bundle: bundle
        )
    }

    static func plural(
        _ key: String,
        count: Int,
        locale: AppLocale,
        replacements: [String: String] = [:],
        bundle: Bundle = .main
    ) -> String {
        let raw = text(key, locale: locale, replacements: replacements, bundle: bundle)
        return resolvePlural(raw, count: count, locale: locale)
    }

    static func resolvePlural(_ template: String, count: Int, locale: AppLocale) -> String {
        let formattedCount = ResultFormat.formatScore(Double(count), locale: locale)
        guard let startRange = template.range(of: "{count, plural,") else {
            return template
                .replacingOccurrences(of: "{count}", with: formattedCount)
                .replacingOccurrences(of: "#", with: formattedCount)
        }

        var depth = 0
        var endIndex = template.endIndex
        var cursor = startRange.lowerBound
        while cursor < template.endIndex {
            let ch = template[cursor]
            if ch == "{" {
                depth += 1
            } else if ch == "}" {
                depth -= 1
                if depth == 0 {
                    endIndex = template.index(after: cursor)
                    break
                }
            }
            cursor = template.index(after: cursor)
        }

        let innerStart = startRange.upperBound
        let innerEnd = template.index(before: endIndex)
        let inner = String(template[innerStart..<innerEnd])

        let formKey = (locale == .en && count == 1) ? "one" : "other"
        let selected = extractPluralBranch(formKey, from: inner)
            ?? extractPluralBranch("other", from: inner)
            ?? formattedCount
        let rendered = selected.replacingOccurrences(of: "#", with: formattedCount)
        return String(template[..<startRange.lowerBound]) + rendered + String(template[endIndex...])
    }

    private static func extractPluralBranch(_ name: String, from body: String) -> String? {
        guard let nameRange = body.range(of: "\(name) {") ?? body.range(of: "\(name){") else {
            return nil
        }
        var depth = 1
        var cursor = nameRange.upperBound
        while cursor < body.endIndex {
            let ch = body[cursor]
            if ch == "{" {
                depth += 1
            } else if ch == "}" {
                depth -= 1
                if depth == 0 {
                    return String(body[nameRange.upperBound..<cursor])
                }
            }
            cursor = body.index(after: cursor)
        }
        return nil
    }
}
