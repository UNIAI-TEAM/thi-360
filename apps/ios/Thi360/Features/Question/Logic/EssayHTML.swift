import Foundation
import SwiftSoup
import UIKit

enum EssayListKind: String, Equatable, Hashable, Sendable {
    case bullet = "b"
    case ordered = "o"

    var markerFormat: NSTextList.MarkerFormat {
        switch self {
        case .bullet: .disc
        case .ordered: .decimal
        }
    }
}

extension NSAttributedString.Key {
    static let essayListStack = NSAttributedString.Key("vn.unicom.thi360.essay.listStack")
}

enum EssayHTML {
    private static let dropSubtreeTags: Set<String> = [
        "script", "style", "template", "iframe", "object", "embed", "img", "svg", "video", "audio", "source", "canvas"
    ]

    static func encodeListStack(_ stack: [EssayListKind]) -> String {
        stack.map(\.rawValue).joined(separator: ",")
    }

    static func decodeListStack(from attributes: [NSAttributedString.Key: Any]) -> [EssayListKind] {
        if let encoded = attributes[.essayListStack] as? String {
            guard !encoded.isEmpty else { return [] }
            return encoded.split(separator: ",").compactMap { EssayListKind(rawValue: String($0)) }
        }
        if let style = attributes[.paragraphStyle] as? NSParagraphStyle, !style.textLists.isEmpty {
            return style.textLists.map { list in
                switch list.markerFormat {
                case .decimal, .lowercaseAlpha, .uppercaseAlpha, .lowercaseLatin, .uppercaseLatin, .lowercaseRoman, .uppercaseRoman:
                    .ordered
                default:
                    .bullet
                }
            }
        }
        return []
    }

    static func makeParagraphStyle(for stack: [EssayListKind]) -> NSParagraphStyle {
        let style = NSMutableParagraphStyle()
        style.lineSpacing = 4
        style.paragraphSpacing = 6
        if !stack.isEmpty {
            style.textLists = stack.map { NSTextList(markerFormat: $0.markerFormat, options: 0) }
            let indent = CGFloat(stack.count) * 24
            style.headIndent = indent
            style.firstLineHeadIndent = max(0, indent - 16)
        }
        return style
    }

    static func font(baseFont: UIFont, bold: Bool, italic: Bool) -> UIFont {
        var traits: UIFontDescriptor.SymbolicTraits = []
        if bold { traits.insert(.traitBold) }
        if italic { traits.insert(.traitItalic) }
        guard !traits.isEmpty else {
            return UIFont.systemFont(ofSize: baseFont.pointSize, weight: .regular)
        }
        if let descriptor = baseFont.fontDescriptor.withSymbolicTraits(traits) {
            return UIFont(descriptor: descriptor, size: baseFont.pointSize)
        }
        return bold ? UIFont.boldSystemFont(ofSize: baseFont.pointSize) : UIFont.italicSystemFont(ofSize: baseFont.pointSize)
    }

    static func inlineAttributes(
        baseFont: UIFont,
        textColor: UIColor,
        bold: Bool,
        italic: Bool,
        listStack: [EssayListKind]
    ) -> [NSAttributedString.Key: Any] {
        var attrs: [NSAttributedString.Key: Any] = [
            .font: font(baseFont: baseFont, bold: bold, italic: italic),
            .foregroundColor: textColor,
            .paragraphStyle: makeParagraphStyle(for: listStack)
        ]
        if !listStack.isEmpty {
            attrs[.essayListStack] = encodeListStack(listStack)
        }
        return attrs
    }

    static func isBold(in attributes: [NSAttributedString.Key: Any]) -> Bool {
        guard let font = attributes[.font] as? UIFont else { return false }
        return font.fontDescriptor.symbolicTraits.contains(.traitBold)
    }

    static func isItalic(in attributes: [NSAttributedString.Key: Any]) -> Bool {
        guard let font = attributes[.font] as? UIFont else { return false }
        return font.fontDescriptor.symbolicTraits.contains(.traitItalic)
    }

    private struct ExportedParagraph {
        let listStack: [EssayListKind]
        let innerHTML: String
        let hasVisibleText: Bool
    }

    private struct StyledRun {
        let text: String
        let bold: Bool
        let italic: Bool
    }

    static func export(_ attributedString: NSAttributedString) -> String {
        guard attributedString.length > 0 else { return "" }
        let fullNSString = attributedString.string as NSString
        let totalLength = fullNSString.length
        var paragraphs: [ExportedParagraph] = []

        var cursor = 0
        while cursor <= totalLength {
            let searchRange = NSRange(location: cursor, length: totalLength - cursor)
            let nlRange = fullNSString.range(of: "\n", options: [], range: searchRange)
            let paraEnd = (nlRange.location == NSNotFound) ? totalLength : nlRange.location
            let paraRange = NSRange(location: cursor, length: paraEnd - cursor)

            let stack: [EssayListKind]
            if paraRange.length > 0 {
                stack = decodeListStack(from: attributedString.attributes(at: paraRange.location, effectiveRange: nil))
            } else if nlRange.location != NSNotFound {
                stack = decodeListStack(from: attributedString.attributes(at: nlRange.location, effectiveRange: nil))
            } else if cursor > 0 && cursor == totalLength {
                stack = decodeListStack(from: attributedString.attributes(at: cursor - 1, effectiveRange: nil))
            } else {
                stack = []
            }

            let (innerHTML, hasVisibleText) = exportParagraphRuns(attributedString, range: paraRange)
            paragraphs.append(ExportedParagraph(listStack: stack, innerHTML: innerHTML, hasVisibleText: hasVisibleText))

            if nlRange.location == NSNotFound {
                break
            }
            cursor = nlRange.location + nlRange.length
        }

        while let last = paragraphs.last, last.innerHTML.isEmpty, last.listStack.isEmpty {
            paragraphs.removeLast()
        }

        guard paragraphs.contains(where: \.hasVisibleText) else {
            return ""
        }

        var output = ""
        var openStack: [EssayListKind] = []

        for para in paragraphs {
            let target = para.listStack
            var lcp = 0
            while lcp < openStack.count && lcp < target.count && openStack[lcp] == target[lcp] {
                lcp += 1
            }

            if target.isEmpty {
                while let closed = openStack.popLast() {
                    output.append("</li>")
                    output.append(closed == .bullet ? "</ul>" : "</ol>")
                }
                output.append("<p>")
                output.append(para.innerHTML)
                output.append("</p>")
            } else {
                while openStack.count > lcp {
                    let closed = openStack.removeLast()
                    output.append("</li>")
                    output.append(closed == .bullet ? "</ul>" : "</ol>")
                }
                if lcp == target.count {
                    output.append("</li><li>")
                } else {
                    for depth in lcp..<target.count {
                        let kind = target[depth]
                        output.append(kind == .bullet ? "<ul><li>" : "<ol><li>")
                        openStack.append(kind)
                    }
                }
                output.append("<p>")
                output.append(para.innerHTML)
                output.append("</p>")
            }
        }

        while let closed = openStack.popLast() {
            output.append("</li>")
            output.append(closed == .bullet ? "</ul>" : "</ol>")
        }

        return output
    }

    static func sanitizePastedHTML(_ rawHTML: String) -> String {
        let baseFont = UIFont.systemFont(ofSize: 17)
        let attr = attributedString(from: rawHTML, baseFont: baseFont, textColor: .label)
        return export(attr)
    }

    static func sanitizePastedAttributedString(
        _ source: NSAttributedString,
        baseFont: UIFont,
        textColor: UIColor
    ) -> NSAttributedString {
        guard source.length > 0 else { return NSAttributedString() }
        let full = source.string as NSString
        let result = NSMutableAttributedString()
        source.enumerateAttributes(in: NSRange(location: 0, length: source.length), options: []) { attrs, range, _ in
            if attrs[.attachment] != nil {
                return
            }
            let rawText = full.substring(with: range)
                .replacingOccurrences(of: "\u{0000}", with: "")
                .replacingOccurrences(of: "\u{FFFC}", with: "")
            guard !rawText.isEmpty else { return }
            let bold = isBold(in: attrs)
            let italic = isItalic(in: attrs)
            let stack = decodeListStack(from: attrs)
            let cleanAttrs = inlineAttributes(
                baseFont: baseFont,
                textColor: textColor,
                bold: bold,
                italic: italic,
                listStack: stack
            )
            result.append(NSAttributedString(string: rawText, attributes: cleanAttrs))
        }
        return result
    }

    static func attributedString(
        from html: String,
        baseFont: UIFont,
        textColor: UIColor
    ) -> NSAttributedString {
        let cleaned = html.replacingOccurrences(of: "\u{0000}", with: "")
        guard !cleaned.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return NSAttributedString()
        }

        do {
            let doc = try SwiftSoup.parseBodyFragment(cleaned)
            guard let body = doc.body() else { return NSAttributedString() }
            var builder = HTMLImportBuilder(baseFont: baseFont, textColor: textColor)
            builder.visitContainer(body, listStack: [], bold: false, italic: false)
            return builder.finish()
        } catch {
            return NSAttributedString(
                string: cleaned,
                attributes: inlineAttributes(baseFont: baseFont, textColor: textColor, bold: false, italic: false, listStack: [])
            )
        }
    }

    private static func exportParagraphRuns(
        _ attributed: NSAttributedString,
        range: NSRange
    ) -> (html: String, hasVisibleText: Bool) {
        guard range.length > 0 else { return ("", false) }
        let nsString = attributed.string as NSString
        var rawRuns: [StyledRun] = []

        attributed.enumerateAttributes(in: range, options: []) { attrs, subrange, _ in
            if attrs[.attachment] != nil { return }
            let text = nsString.substring(with: subrange)
                .replacingOccurrences(of: "\u{0000}", with: "")
                .replacingOccurrences(of: "\u{FFFC}", with: "")
            guard !text.isEmpty else { return }
            let bold = isBold(in: attrs)
            let italic = isItalic(in: attrs)
            if let last = rawRuns.last, last.bold == bold, last.italic == italic {
                rawRuns[rawRuns.count - 1] = StyledRun(text: last.text + text, bold: bold, italic: italic)
            } else {
                rawRuns.append(StyledRun(text: text, bold: bold, italic: italic))
            }
        }

        var html = ""
        var hasVisibleText = false

        for run in rawRuns {
            let pieces = run.text.components(separatedBy: "\u{2028}")
            for (index, piece) in pieces.enumerated() {
                if index > 0 {
                    html.append("<br>")
                }
                guard !piece.isEmpty else { continue }
                if !piece.replacingOccurrences(of: "\u{00A0}", with: " ").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    hasVisibleText = true
                }
                let escaped = escapeHTML(piece)
                switch (run.bold, run.italic) {
                case (true, true):
                    html.append("<strong><em>\(escaped)</em></strong>")
                case (true, false):
                    html.append("<strong>\(escaped)</strong>")
                case (false, true):
                    html.append("<em>\(escaped)</em>")
                case (false, false):
                    html.append(escaped)
                }
            }
        }

        return (html, hasVisibleText)
    }

    private static func escapeHTML(_ text: String) -> String {
        var result = ""
        result.reserveCapacity(text.count)
        for ch in text {
            switch ch {
            case "&":
                result.append("&amp;")
            case "<":
                result.append("&lt;")
            case ">":
                result.append("&gt;")
            case "\u{00A0}":
                result.append("&nbsp;")
            default:
                result.append(ch)
            }
        }
        return result
    }

    private struct HTMLImportBuilder {
        let baseFont: UIFont
        let textColor: UIColor

        private struct BuiltParagraph {
            let listStack: [EssayListKind]
            let content: NSMutableAttributedString
        }

        private var paragraphs: [BuiltParagraph] = []
        private var currentParagraph: NSMutableAttributedString?
        private var currentListStack: [EssayListKind] = []

        init(baseFont: UIFont, textColor: UIColor) {
            self.baseFont = baseFont
            self.textColor = textColor
        }

        mutating func visitContainer(
            _ container: Element,
            listStack: [EssayListKind],
            bold: Bool,
            italic: Bool
        ) {
            for node in container.getChildNodes() {
                if let textNode = node as? TextNode {
                    let text = textNode.getWholeText().replacingOccurrences(of: "\u{0000}", with: "")
                    if currentParagraph == nil && text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !text.contains("\u{00A0}") {
                        continue
                    }
                    appendText(text, listStack: listStack, bold: bold, italic: italic)
                } else if let el = node as? Element {
                    visitElement(el, listStack: listStack, bold: bold, italic: italic)
                }
            }
        }

        private mutating func visitElement(
            _ el: Element,
            listStack: [EssayListKind],
            bold: Bool,
            italic: Bool
        ) {
            let tag = el.tagName().lowercased()
            if EssayHTML.dropSubtreeTags.contains(tag) {
                return
            }

            switch tag {
            case "ul":
                flushCurrentParagraph()
                visitList(el, kind: .bullet, parentStack: listStack, bold: bold, italic: italic)

            case "ol":
                flushCurrentParagraph()
                visitList(el, kind: .ordered, parentStack: listStack, bold: bold, italic: italic)

            case "li":
                flushCurrentParagraph()
                let effectiveStack = listStack.isEmpty ? [EssayListKind.bullet] : listStack
                visitListItem(el, listStack: effectiveStack, bold: bold, italic: italic)

            case "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "tr":
                flushCurrentParagraph()
                startParagraph(listStack: listStack)
                visitContainer(el, listStack: listStack, bold: bold, italic: italic)
                flushCurrentParagraph()

            case "br":
                appendText("\u{2028}", listStack: listStack, bold: bold, italic: italic)

            case "strong", "b":
                visitContainer(el, listStack: listStack, bold: true, italic: italic)

            case "em", "i":
                visitContainer(el, listStack: listStack, bold: bold, italic: true)

            default:
                visitContainer(el, listStack: listStack, bold: bold, italic: italic)
            }
        }

        private mutating func visitList(
            _ listElement: Element,
            kind: EssayListKind,
            parentStack: [EssayListKind],
            bold: Bool,
            italic: Bool
        ) {
            let childStack = parentStack + [kind]
            for child in listElement.children().array() {
                let tag = child.tagName().lowercased()
                if tag == "li" {
                    visitListItem(child, listStack: childStack, bold: bold, italic: italic)
                } else if tag == "ul" {
                    visitList(child, kind: .bullet, parentStack: childStack, bold: bold, italic: italic)
                } else if tag == "ol" {
                    visitList(child, kind: .ordered, parentStack: childStack, bold: bold, italic: italic)
                }
            }
        }

        private mutating func visitListItem(
            _ li: Element,
            listStack: [EssayListKind],
            bold: Bool,
            italic: Bool
        ) {
            let countBefore = paragraphs.count
            for node in li.getChildNodes() {
                if let textNode = node as? TextNode {
                    let text = textNode.getWholeText().replacingOccurrences(of: "\u{0000}", with: "")
                    if currentParagraph == nil && text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !text.contains("\u{00A0}") {
                        continue
                    }
                    appendText(text, listStack: listStack, bold: bold, italic: italic)
                } else if let el = node as? Element {
                    visitElement(el, listStack: listStack, bold: bold, italic: italic)
                }
            }
            flushCurrentParagraph()
            if paragraphs.count == countBefore {
                paragraphs.append(BuiltParagraph(listStack: listStack, content: NSMutableAttributedString()))
            }
        }

        private mutating func startParagraph(listStack: [EssayListKind]) {
            if currentParagraph == nil {
                currentParagraph = NSMutableAttributedString()
                currentListStack = listStack
            }
        }

        private mutating func appendText(
            _ text: String,
            listStack: [EssayListKind],
            bold: Bool,
            italic: Bool
        ) {
            startParagraph(listStack: listStack)
            let attrs = EssayHTML.inlineAttributes(
                baseFont: baseFont,
                textColor: textColor,
                bold: bold,
                italic: italic,
                listStack: currentListStack
            )
            currentParagraph?.append(NSAttributedString(string: text, attributes: attrs))
        }

        private mutating func flushCurrentParagraph() {
            guard let para = currentParagraph else { return }
            paragraphs.append(BuiltParagraph(listStack: currentListStack, content: para))
            currentParagraph = nil
            currentListStack = []
        }

        mutating func finish() -> NSAttributedString {
            flushCurrentParagraph()
            while let last = paragraphs.last, last.content.length == 0, last.listStack.isEmpty {
                paragraphs.removeLast()
            }
            guard !paragraphs.isEmpty else { return NSAttributedString() }
            let output = NSMutableAttributedString()
            for (index, para) in paragraphs.enumerated() {
                if index > 0 {
                    let prevStack = paragraphs[index - 1].listStack
                    let nlAttrs = EssayHTML.inlineAttributes(
                        baseFont: baseFont,
                        textColor: textColor,
                        bold: false,
                        italic: false,
                        listStack: prevStack
                    )
                    output.append(NSAttributedString(string: "\n", attributes: nlAttrs))
                }
                output.append(para.content)
            }
            return output
        }
    }
}
