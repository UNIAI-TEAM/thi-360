import Foundation

enum CatalogStrings {
    static let table = "Catalog"

    private static let knownFormats: Set<String> = ["FULL", "PART", "SUBJECT", "BUNDLE"]
    private static let knownAnswerTypes: Set<String> = [
        "CLOZE",
        "ESSAY",
        "MATCHING",
        "MULTIPLE_CHOICE",
        "QUESTION_SET",
        "SHORT_ANSWER",
        "SINGLE_CHOICE",
        "TRUE_FALSE"
    ]
    private static let knownResultViews: Set<String> = [
        "FULL_SOLUTION",
        "SCORE",
        "SCORE_AND_ANSWERS"
    ]

    static func text(
        _ key: String,
        locale: AppLocale,
        replacements: [String: String] = [:],
        bundle: Bundle = .main
    ) -> String {
        let raw = AppStrings.text(key, table: table, locale: locale, replacements: replacements, bundle: bundle)
        return stripExamContentTags(raw)
    }

    static func plural(
        _ key: String,
        table: String = CatalogStrings.table,
        count: Int,
        locale: AppLocale,
        replacements: [String: String] = [:],
        bundle: Bundle = .main
    ) -> String {
        let template = AppStrings.text(key, table: table, locale: locale, bundle: bundle)
        let resolved = formatICUPlural(template, count: count, locale: locale)
        var output = resolved
        for (token, value) in replacements {
            output = output.replacingOccurrences(of: "{\(token)}", with: value)
        }
        return stripExamContentTags(output)
    }

    static func formatLabel(for formatCode: String, locale: AppLocale, bundle: Bundle = .main) -> String {
        guard knownFormats.contains(formatCode) else {
            return formatCode
        }
        return AppStrings.common("storefront.format.\(formatCode)", locale: locale, bundle: bundle)
    }

    static func answerTypeLabel(for typeCode: String, locale: AppLocale, bundle: Bundle = .main) -> String {
        guard knownAnswerTypes.contains(typeCode) else {
            return typeCode
        }
        return text("storefront.detail.answerType.\(typeCode)", locale: locale, bundle: bundle)
    }

    static func resultViewLabel(for viewCode: String, locale: AppLocale, bundle: Bundle = .main) -> String {
        guard knownResultViews.contains(viewCode) else {
            return viewCode
        }
        return text("storefront.detail.rights.resultViews.\(viewCode)", locale: locale, bundle: bundle)
    }

    static func stripExamContentTags(_ input: String) -> String {
        input
            .replacingOccurrences(of: "<examContent>", with: "")
            .replacingOccurrences(of: "</examContent>", with: "")
    }

    static func formatICUPlural(_ template: String, count: Int, locale: AppLocale) -> String {
        guard let pluralRange = template.range(of: ", plural,") else {
            return template
        }
        guard let openBrace = template[..<pluralRange.lowerBound].lastIndex(of: "{") else {
            return template
        }
        guard let closeBrace = matchingClosingBrace(in: template, startingAt: openBrace) else {
            return template
        }

        let bodyStart = pluralRange.upperBound
        let body = String(template[bodyStart..<closeBrace])
        let branches = parsePluralBranches(body)

        let selectedKey: String
        if locale == .en && count == 1 && branches["one"] != nil {
            selectedKey = "one"
        } else {
            selectedKey = "other"
        }

        let chosen = branches[selectedKey] ?? branches["other"] ?? body
        let countText = CatalogFormatting.formatInteger(Int64(count), locale: locale)
        let expanded = chosen.replacingOccurrences(of: "#", with: countText)

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

            let innerStart = body.index(after: idx)
            result[key] = String(body[innerStart..<endBrace])
            idx = body.index(after: endBrace)
        }

        return result
    }
}
