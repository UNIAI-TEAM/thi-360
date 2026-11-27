import Foundation

enum PrepareStrings {
    static let table = "Prepare"

    static let knownAnswerFormats: Set<String> = [
        "SINGLE_CHOICE",
        "MULTIPLE_CHOICE",
        "SHORT_ANSWER",
        "CLOZE",
        "TRUE_FALSE",
        "MATCHING",
        "ESSAY",
        "QUESTION_SET"
    ]

    static let knownAbortReasons: Set<String> = [
        "GENERATION_FAILED",
        "GENERATION_TIMEOUT",
        "GENERATION_BUSY",
        "STALE_STARTING",
        "AUDIO_ASSET_MISSING",
        "AUDIO_ASSET_INVALID"
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
        return formatICUPlural(template, count: count, locale: locale)
    }

    static func scopeLabel(
        scope: String,
        subject: Components.Schemas.SubjectView?,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        let base: String
        switch scope {
        case "FULL", "PART", "SUBJECT":
            base = text("prepare.scope.\(scope)", locale: locale, bundle: bundle)
        default:
            base = scope
        }
        if let subject {
            let subjectLabel = PrepareFormatting.localizedName(
                nameVi: subject.nameVi,
                nameEn: subject.nameEn,
                code: subject.code,
                locale: locale
            )
            if !subjectLabel.isEmpty {
                return "\(base) · \(subjectLabel)"
            }
        }
        return base
    }

    static func answerFormatLabel(
        for code: String,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        guard knownAnswerFormats.contains(code) else {
            return code
        }
        return text("prepare.answerFormats.\(code)", locale: locale, bundle: bundle)
    }

    static func abortReasonMessage(
        for reason: String?,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        if let reason, knownAbortReasons.contains(reason) {
            return text("attempt.abortReasons.\(reason)", locale: locale, bundle: bundle)
        }
        return text("attempt.abortReasons.generic", locale: locale, bundle: bundle)
    }

    static func reassureNoteParts(
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> (prefix: String, linkText: String, suffix: String) {
        let raw = text("attempt.waiting.reassureNote", locale: locale, bundle: bundle)
        let openTag = "<myExams>"
        let closeTag = "</myExams>"
        guard let openRange = raw.range(of: openTag),
              let closeRange = raw.range(of: closeTag, range: openRange.upperBound..<raw.endIndex)
        else {
            return (raw, "", "")
        }
        let prefix = String(raw[..<openRange.lowerBound])
        let linkText = String(raw[openRange.upperBound..<closeRange.lowerBound])
        let suffix = String(raw[closeRange.upperBound...])
        return (prefix, linkText, suffix)
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

            let innerStart = body.index(after: idx)
            result[key] = String(body[innerStart..<endBrace])
            idx = body.index(after: endBrace)
        }

        return result
    }
}
