import Foundation

enum MyExamsStrings {
    static let table = "MyExams"

    private static let knownFormats: Set<String> = ["FULL", "PART", "SUBJECT", "BUNDLE"]
    private static let knownMyExamStates: Set<String> = ["IN_PROGRESS", "CAN_START", "EXHAUSTED"]

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
        return formatICUPlural(template, count: count, locale: locale)
    }

    static func formatLabel(for formatCode: String, locale: AppLocale, bundle: Bundle = .main) -> String {
        guard knownFormats.contains(formatCode) else {
            return formatCode
        }
        return AppStrings.common("storefront.format.\(formatCode)", locale: locale, bundle: bundle)
    }

    static func examStateLabel(for state: String, locale: AppLocale, bundle: Bundle = .main) -> String {
        guard knownMyExamStates.contains(state) else {
            return state
        }
        return text("home.status.\(state)", locale: locale, bundle: bundle)
    }

    struct CrossReferenceParts: Equatable, Sendable {
        let prefix: String
        let examName: String
        let middle: String
        let bundleName: String
        let suffix: String

        var plainText: String {
            "\(prefix)\(examName)\(middle)\(bundleName)\(suffix)"
        }
    }

    static func crossReferenceParts(
        examName: String,
        bundleName: String,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> CrossReferenceParts {
        let raw = text(
            "bundle.home.crossReference",
            locale: locale,
            replacements: ["examName": examName, "bundleName": bundleName],
            bundle: bundle
        )
        let examStartTag = "<examContent>"
        let examEndTag = "</examContent>"
        let bundleStartTag = "<bundleLink>"
        let bundleEndTag = "</bundleLink>"

        guard let examStart = raw.range(of: examStartTag),
              let examEnd = raw.range(of: examEndTag, range: examStart.upperBound..<raw.endIndex),
              let bundleStart = raw.range(of: bundleStartTag, range: examEnd.upperBound..<raw.endIndex),
              let bundleEnd = raw.range(of: bundleEndTag, range: bundleStart.upperBound..<raw.endIndex)
        else {
            let stripped = raw
                .replacingOccurrences(of: examStartTag, with: "")
                .replacingOccurrences(of: examEndTag, with: "")
                .replacingOccurrences(of: bundleStartTag, with: "")
                .replacingOccurrences(of: bundleEndTag, with: "")
            return CrossReferenceParts(prefix: stripped, examName: "", middle: "", bundleName: bundleName, suffix: "")
        }

        return CrossReferenceParts(
            prefix: String(raw[..<examStart.lowerBound]),
            examName: String(raw[examStart.upperBound..<examEnd.lowerBound]),
            middle: String(raw[examEnd.upperBound..<bundleStart.lowerBound]),
            bundleName: String(raw[bundleStart.upperBound..<bundleEnd.lowerBound]),
            suffix: String(raw[bundleEnd.upperBound...])
        )
    }

    static func formatICUPlural(_ template: String, count: Int, locale: AppLocale) -> String {
        guard let pluralRange = template.range(of: ", plural,"),
              let openBrace = template[..<pluralRange.lowerBound].lastIndex(of: "{"),
              let closeBrace = matchingClosingBrace(in: template, startingAt: openBrace)
        else {
            return template
        }

        let body = String(template[pluralRange.upperBound..<closeBrace])
        let branches = parsePluralBranches(body)
        let selectedKey = (locale == .en && count == 1 && branches["one"] != nil) ? "one" : "other"
        let chosen = branches[selectedKey] ?? branches["other"] ?? body
        let expanded = chosen.replacingOccurrences(of: "#", with: String(count))

        return String(template[..<openBrace]) + expanded + String(template[template.index(after: closeBrace)...])
    }

    private static func matchingClosingBrace(in text: String, startingAt openIndex: String.Index) -> String.Index? {
        var depth = 0
        var idx = openIndex
        while idx < text.endIndex {
            let ch = text[idx]
            if ch == "{" {
                depth += 1
            } else if ch == "}" {
                depth -= 1
                if depth == 0 {
                    return idx
                }
            }
            idx = text.index(after: idx)
        }
        return nil
    }

    private static func parsePluralBranches(_ body: String) -> [String: String] {
        var result: [String: String] = [:]
        var idx = body.startIndex
        while idx < body.endIndex {
            while idx < body.endIndex && body[idx].isWhitespace {
                idx = body.index(after: idx)
            }
            guard idx < body.endIndex else { break }

            let keyStart = idx
            while idx < body.endIndex && !body[idx].isWhitespace && body[idx] != "{" {
                idx = body.index(after: idx)
            }
            let key = String(body[keyStart..<idx])

            while idx < body.endIndex && body[idx].isWhitespace {
                idx = body.index(after: idx)
            }
            guard idx < body.endIndex, body[idx] == "{",
                  let endBrace = matchingClosingBrace(in: body, startingAt: idx)
            else {
                break
            }

            result[key] = String(body[body.index(after: idx)..<endBrace])
            idx = body.index(after: endBrace)
        }
        return result
    }
}
