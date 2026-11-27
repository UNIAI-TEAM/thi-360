import Foundation

enum PaymentStrings {
    static let table = "Payment"

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
        table: String = PaymentStrings.table,
        count: Int,
        locale: AppLocale,
        replacements: [String: String] = [:],
        bundle: Bundle = .main
    ) -> String {
        let raw = AppStrings.text(key, table: table, locale: locale, replacements: [:], bundle: bundle)
        var resolved = resolveICUPlural(raw, count: count)
        var allReplacements = replacements
        allReplacements["count"] = String(count)
        for (token, value) in allReplacements {
            resolved = resolved.replacingOccurrences(of: "{\(token)}", with: value)
        }
        return resolved
    }

    static func resolveICUPlural(_ template: String, count: Int) -> String {
        let marker = "{count, plural,"
        guard let startRange = template.range(of: marker) else {
            return template.replacingOccurrences(of: "#", with: String(count))
        }

        var depth = 0
        var endIndex: String.Index?
        var cursor = startRange.lowerBound
        while cursor < template.endIndex {
            let ch = template[cursor]
            if ch == "{" {
                depth += 1
            } else if ch == "}" {
                depth -= 1
                if depth == 0 {
                    endIndex = cursor
                    break
                }
            }
            cursor = template.index(after: cursor)
        }

        guard let endIndex else {
            return template
        }

        let bodyStart = startRange.upperBound
        let body = String(template[bodyStart..<endIndex])
        let branches = parsePluralBranches(body)
        let preferredKey = (count == 1) ? "one" : "other"
        let chosen = branches[preferredKey] ?? branches["other"] ?? branches["one"] ?? ""
        let expanded = chosen.replacingOccurrences(of: "#", with: String(count))

        var output = String(template[..<startRange.lowerBound])
        output.append(expanded)
        output.append(String(template[template.index(after: endIndex)...]))
        return output
    }

    private static func parsePluralBranches(_ body: String) -> [String: String] {
        var result: [String: String] = [:]
        var index = body.startIndex

        while index < body.endIndex {
            while index < body.endIndex, body[index].isWhitespace {
                index = body.index(after: index)
            }
            let keyStart = index
            while index < body.endIndex, !body[index].isWhitespace, body[index] != "{" {
                index = body.index(after: index)
            }
            let key = String(body[keyStart..<index])
            while index < body.endIndex, body[index].isWhitespace {
                index = body.index(after: index)
            }
            guard index < body.endIndex, body[index] == "{" else {
                break
            }
            let contentStart = body.index(after: index)
            var depth = 1
            var cursor = contentStart
            while cursor < body.endIndex, depth > 0 {
                if body[cursor] == "{" {
                    depth += 1
                } else if body[cursor] == "}" {
                    depth -= 1
                }
                if depth > 0 {
                    cursor = body.index(after: cursor)
                }
            }
            if depth == 0 {
                result[key] = String(body[contentStart..<cursor])
                index = body.index(after: cursor)
            } else {
                break
            }
        }

        return result
    }
}
