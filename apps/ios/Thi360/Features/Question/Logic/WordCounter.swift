import Foundation
import SwiftSoup

enum WordCounter {
    private static let blockBoundaryRegex = try! NSRegularExpression(
        pattern: #"</(?:p|div|li|h[1-6]|blockquote|tr|td|th|figcaption|ul|ol|table)>|<br\s*/?>"#,
        options: [.caseInsensitive]
    )

    static func textForWordCount(html: String) -> String {
        guard !html.isEmpty else { return "" }
        let nsHTML = html as NSString
        let spaced = blockBoundaryRegex.stringByReplacingMatches(
            in: html,
            range: NSRange(location: 0, length: nsHTML.length),
            withTemplate: "$0 "
        )

        let rawText: String
        do {
            let doc = try SwiftSoup.parseBodyFragment(spaced)
            if let body = doc.body() {
                let mathNodes = try body.select(".math-inline, .math-block")
                for el in mathNodes.array() {
                    try el.replaceWith(TextNode(" ", nil))
                }
                rawText = extractRawText(from: body)
            } else {
                rawText = ""
            }
        } catch {
            rawText = spaced
        }

        let nfc = rawText.precomposedStringWithCanonicalMapping
        return nfc.replacingOccurrences(of: "\u{00A0}", with: " ")
    }

    static func count(html: String) -> Int {
        let text = textForWordCount(html: html)
        guard !text.isEmpty else { return 0 }

        var count = 0
        var inToken = false
        var tokenHasLetterOrDigit = false

        for scalar in text.unicodeScalars {
            if isUnicodeWhitespace(scalar) {
                if inToken && tokenHasLetterOrDigit {
                    count += 1
                }
                inToken = false
                tokenHasLetterOrDigit = false
            } else {
                inToken = true
                if !tokenHasLetterOrDigit && isLetterOrNumber(scalar) {
                    tokenHasLetterOrDigit = true
                }
            }
        }
        if inToken && tokenHasLetterOrDigit {
            count += 1
        }
        return count
    }

    private static func extractRawText(from node: Node) -> String {
        if let textNode = node as? TextNode {
            return textNode.getWholeText()
        }
        var buffer = ""
        for child in node.getChildNodes() {
            buffer.append(extractRawText(from: child))
        }
        return buffer
    }

    private static func isUnicodeWhitespace(_ scalar: Unicode.Scalar) -> Bool {
        if scalar.properties.isWhitespace {
            return true
        }
        switch scalar.properties.generalCategory {
        case .spaceSeparator, .lineSeparator, .paragraphSeparator:
            return true
        default:
            return false
        }
    }

    private static func isLetterOrNumber(_ scalar: Unicode.Scalar) -> Bool {
        switch scalar.properties.generalCategory {
        case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter,
             .decimalNumber, .letterNumber, .otherNumber:
            return true
        default:
            return false
        }
    }
}
