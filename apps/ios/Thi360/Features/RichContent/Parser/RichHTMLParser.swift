import Foundation
import SwiftSoup

enum RichHTMLParser {
    private static let allowedTags: Set<String> = [
        "p", "br", "strong", "em", "u", "s", "sub", "sup",
        "ul", "ol", "li",
        "table", "thead", "tbody", "tr", "th", "td",
        "span", "img",
        "figure", "figcaption",
        "blockquote", "footer", "cite"
    ]

    private static let allowedTagsNoImages: Set<String> = allowedTags.subtracting(["img"])
    private static let dropSubtreeTags: Set<String> = ["script", "style", "template", "iframe", "object", "embed"]
    private static let mathClasses: Set<String> = ["math-inline", "math-block"]
    private static let figureClasses: Set<String> = ["data-table", "image", "cross"]
    private static let paragraphClasses: Set<String> = ["figure-note", "figure-source"]
    private static let tdClasses: Set<String> = ["num"]
    private static let figureWidths: Set<String> = ["small", "medium", "full"]
    private static let figureZooms: Set<String> = ["true", "false"]
    private static let quoteKinds: Set<String> = ["poem", "prose", "source"]
    private static let quoteLineNumbers: Set<String> = ["0", "5"]
    private static let thScopes: Set<String> = ["col", "row"]

    private static let underlineLabelRegex = try! NSRegularExpression(pattern: "^[A-H]$")
    private static let blankNumberRegex = try! NSRegularExpression(pattern: "^(?:[1-9]|1[0-9]|20)$")
    private static let integerRegex = try! NSRegularExpression(pattern: "^\\d+$")
    private static let uuidRegex = try! NSRegularExpression(
        pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        options: .caseInsensitive
    )
    private static let slotMarkerRegex = try! NSRegularExpression(pattern: "\\[\\[([A-Za-z0-9_.-]+)\\]\\]")
    private static let maxRecursionDepth = 512

    static func parse(_ html: String, options: RichParseOptions = RichParseOptions()) -> RichDocument {
        guard !html.isEmpty else {
            return RichDocument(blocks: [], violations: [], blankNumberStart: options.blankNumberStart)
        }

        do {
            var violations: [SanitizeViolation] = []

            let fullDoc = try SwiftSoup.parse(html)
            for shell in [fullDoc.children().first(), fullDoc.head(), fullDoc.body()].compactMap({ $0 }) {
                if let attrs = shell.getAttributes() {
                    let shellTag = shell.tagName().lowercased()
                    for attr in attrs {
                        violations.append(
                            SanitizeViolation(
                                tag: shellTag,
                                attribute: attr.getKey().lowercased(),
                                value: attr.getValue()
                            )
                        )
                    }
                }
            }

            let fragmentDoc = try SwiftSoup.parseBodyFragment(html)
            guard let body = fragmentDoc.body() else {
                return RichDocument(blocks: [], violations: violations, blankNumberStart: options.blankNumberStart)
            }

            let permitted = options.allowImages ? allowedTags : allowedTagsNoImages
            sanitizeChildren(
                of: body,
                isRoot: true,
                allowedTags: permitted,
                allowImages: options.allowImages,
                violations: &violations,
                depth: 0
            )

            let blocks = extractBlocks(from: body, options: options, depth: 0)
            return RichDocument(
                blocks: blocks,
                violations: violations,
                blankNumberStart: options.blankNumberStart
            )
        } catch {
            return RichDocument(blocks: [], violations: [], blankNumberStart: options.blankNumberStart)
        }
    }

    static func splitMathInPlainText(
        _ text: String,
        style: RichTextStyle = [],
        slotMarkers: Bool = false
    ) -> [RichInline] {
        guard !text.isEmpty else { return [] }
        var result: [RichInline] = []
        var cursor = text.startIndex

        while cursor < text.endIndex {
            let inlineRange = text.range(of: "\\(", range: cursor..<text.endIndex)
            let blockRange = text.range(of: "\\[", range: cursor..<text.endIndex)

            let openRange: Range<String.Index>
            let isBlock: Bool
            switch (inlineRange, blockRange) {
            case let (i?, b?):
                if i.lowerBound <= b.lowerBound {
                    openRange = i
                    isBlock = false
                } else {
                    openRange = b
                    isBlock = true
                }
            case let (i?, nil):
                openRange = i
                isBlock = false
            case let (nil, b?):
                openRange = b
                isBlock = true
            case (nil, nil):
                appendPlainSegment(String(text[cursor..<text.endIndex]), style: style, slotMarkers: slotMarkers, into: &result)
                return result
            }

            let closeToken = isBlock ? "\\]" : "\\)"
            guard let closeRange = text.range(of: closeToken, range: openRange.upperBound..<text.endIndex) else {
                appendPlainSegment(String(text[cursor..<text.endIndex]), style: style, slotMarkers: slotMarkers, into: &result)
                return result
            }

            if openRange.lowerBound > cursor {
                appendPlainSegment(String(text[cursor..<openRange.lowerBound]), style: style, slotMarkers: slotMarkers, into: &result)
            }

            let latex = String(text[openRange.upperBound..<closeRange.lowerBound])
            result.append(.formula(Formula(source: latex, display: isBlock ? .block : .inline)))
            cursor = closeRange.upperBound
        }

        return result
    }

    private static func appendPlainSegment(
        _ segment: String,
        style: RichTextStyle,
        slotMarkers: Bool,
        into result: inout [RichInline]
    ) {
        guard !segment.isEmpty else { return }
        guard slotMarkers, segment.contains("[[") else {
            result.append(.text(segment, style: style))
            return
        }

        let nsText = segment as NSString
        let matches = slotMarkerRegex.matches(in: segment, range: NSRange(location: 0, length: nsText.length))
        guard !matches.isEmpty else {
            result.append(.text(segment, style: style))
            return
        }

        var cursor = 0
        for match in matches {
            let fullRange = match.range(at: 0)
            let idRange = match.range(at: 1)
            if fullRange.location > cursor {
                let prefix = nsText.substring(with: NSRange(location: cursor, length: fullRange.location - cursor))
                if !prefix.isEmpty {
                    result.append(.text(prefix, style: style))
                }
            }
            let slotId = nsText.substring(with: idRange)
            result.append(.slot(id: slotId))
            cursor = fullRange.location + fullRange.length
        }

        if cursor < nsText.length {
            let suffix = nsText.substring(from: cursor)
            if !suffix.isEmpty {
                result.append(.text(suffix, style: style))
            }
        }
    }

    private static func sanitizeChildren(
        of parent: Element,
        isRoot: Bool,
        allowedTags: Set<String>,
        allowImages: Bool,
        violations: inout [SanitizeViolation],
        depth: Int
    ) {
        if depth > maxRecursionDepth {
            for node in parent.getChildNodes() {
                try? node.remove()
            }
            return
        }

        let children = parent.getChildNodes()
        for child in children {
            if child is Comment {
                try? child.remove()
                continue
            }
            guard let el = child as? Element else {
                continue
            }

            let tag = el.tagName().lowercased()

            if dropSubtreeTags.contains(tag) {
                violations.append(SanitizeViolation(tag: tag))
                try? el.remove()
                continue
            }

            if !allowedTags.contains(tag) {
                violations.append(SanitizeViolation(tag: tag))
                sanitizeChildren(
                    of: el,
                    isRoot: isRoot,
                    allowedTags: allowedTags,
                    allowImages: allowImages,
                    violations: &violations,
                    depth: depth + 1
                )
                unwrapElement(el)
                continue
            }

            if tag == "figure" || tag == "blockquote" {
                let closed = closedAttributeViolations(for: el, tag: tag)
                if !closed.isEmpty {
                    violations.append(contentsOf: closed)
                    try? el.remove()
                    continue
                }
            }

            var reported = false
            var removed = false
            if let attrs = el.getAttributes() {
                let attrList = Array(attrs)
                for attr in attrList {
                    let rawName = attr.getKey()
                    let name = rawName.lowercased()
                    let value = attr.getValue()
                    if attributeAllowed(tag: tag, name: name, value: value) {
                        continue
                    }
                    violations.append(SanitizeViolation(tag: tag, attribute: name, value: value))
                    if isBlankSpan(el) {
                        try? el.remove()
                        removed = true
                        break
                    }
                    _ = try? el.removeAttr(rawName)
                    reported = true
                }
            }
            if removed { continue }

            if tag == "img" && allowImages {
                let rawId = trimmedAttr(el, "data-asset-id")
                if !matches(uuidRegex, rawId) {
                    violations.append(SanitizeViolation(tag: "img", attribute: "data-asset-id", value: rawId))
                    reported = true
                }
            }

            sanitizeChildren(
                of: el,
                isRoot: false,
                allowedTags: allowedTags,
                allowImages: allowImages,
                violations: &violations,
                depth: depth + 1
            )

            if reported { continue }

            if let attrViolation = checkElementStructure(el, parentIsRoot: isRoot, checkAttr: true) {
                violations.append(attrViolation)
                if isBlankSpan(el) {
                    try? el.remove()
                    continue
                }
                if let attrName = attrViolation.attribute {
                    _ = try? el.removeAttr(attrName)
                }
                continue
            }

            if let structureViolation = checkElementStructure(el, parentIsRoot: isRoot, checkAttr: false) {
                violations.append(structureViolation)
                try? el.remove()
            }
        }
    }

    private static func unwrapElement(_ element: Element) {
        let childNodes = element.getChildNodes()
        for child in childNodes {
            _ = try? element.before(child)
        }
        try? element.remove()
    }

    private static func attributeAllowed(tag: String, name: String, value: String) -> Bool {
        if name.hasPrefix("on") || name == "style" {
            return false
        }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        switch tag {
        case "img":
            if name == "src" {
                return trimmed.isEmpty || (trimmed.hasPrefix("/") && !trimmed.hasPrefix("//"))
            }
            return name == "alt" || name == "width" || name == "height" || name == "data-asset-id"
        case "span":
            if name == "class" {
                return mathClasses.contains(trimmed) || trimmed == "blank"
            }
            return name == "data-blank"
        case "u":
            return name == "data-label" && matches(underlineLabelRegex, trimmed)
        case "td":
            if name == "class" {
                return tdClasses.contains(trimmed)
            }
            return (name == "colspan" || name == "rowspan") && matches(integerRegex, trimmed)
        case "th":
            if name == "scope" {
                return thScopes.contains(trimmed)
            }
            return (name == "colspan" || name == "rowspan") && matches(integerRegex, trimmed)
        case "p":
            return name == "class" && paragraphClasses.contains(trimmed)
        case "figure":
            if name == "class" { return figureClasses.contains(trimmed) }
            if name == "data-width" { return figureWidths.contains(trimmed) }
            if name == "data-zoom" { return figureZooms.contains(trimmed) }
            return false
        case "blockquote":
            if name == "class" { return trimmed == "quote" }
            if name == "data-kind" { return quoteKinds.contains(trimmed) }
            if name == "data-line-numbers" { return quoteLineNumbers.contains(trimmed) }
            return false
        default:
            return false
        }
    }

    private static func closedAttributeViolations(for el: Element, tag: String) -> [SanitizeViolation] {
        var violations: [SanitizeViolation] = []
        var seen = Set<String>()

        func report(_ name: String, _ value: String?) {
            guard seen.insert(name).inserted else { return }
            violations.append(SanitizeViolation(tag: tag, attribute: name, value: value))
        }

        if let attrs = el.getAttributes() {
            for attr in attrs {
                let name = attr.getKey().lowercased()
                let value = attr.getValue()
                if !attributeAllowed(tag: tag, name: name, value: value) {
                    report(name, value)
                }
            }
        }

        if tag == "figure" {
            let figureClass = trimmedAttr(el, "class")
            if !figureClasses.contains(figureClass) {
                report("class", optionalAttr(el, "class"))
            } else if figureClass == "image" {
                if trimmedAttr(el, "data-width").isEmpty { report("data-width", nil) }
                if trimmedAttr(el, "data-zoom").isEmpty { report("data-zoom", nil) }
            } else {
                if el.hasAttr("data-width") { report("data-width", optionalAttr(el, "data-width")) }
                if el.hasAttr("data-zoom") { report("data-zoom", optionalAttr(el, "data-zoom")) }
            }
            return violations
        }

        if trimmedAttr(el, "class") != "quote" {
            report("class", optionalAttr(el, "class"))
        }
        let kind = trimmedAttr(el, "data-kind")
        if kind.isEmpty {
            report("data-kind", nil)
        }
        let lineNumbers = trimmedAttr(el, "data-line-numbers")
        if lineNumbers.isEmpty || (!kind.isEmpty && lineNumbers != "0" && kind != "poem") {
            report("data-line-numbers", optionalAttr(el, "data-line-numbers"))
        }
        return violations
    }

    private static func checkElementStructure(
        _ el: Element,
        parentIsRoot: Bool,
        checkAttr: Bool
    ) -> SanitizeViolation? {
        let tag = el.tagName().lowercased()
        let parentTag = el.parent()?.tagName().lowercased()

        func attrViolation(_ name: String) -> SanitizeViolation? {
            checkAttr ? SanitizeViolation(tag: tag, attribute: name, value: optionalAttr(el, name) ?? "") : nil
        }

        func structuralViolation() -> SanitizeViolation? {
            checkAttr ? nil : SanitizeViolation(tag: tag)
        }

        switch tag {
        case "figure":
            guard parentIsRoot else { return structuralViolation() }
            let figureClass = trimmedAttr(el, "class")
            let children = el.children().array()
            switch figureClass {
            case "image":
                guard let first = children.first, first.tagName().lowercased() == "img" else {
                    return structuralViolation()
                }
                let rest = children.dropFirst()
                let legal = rest.enumerated().allSatisfy { offset, child in
                    (offset == 0 && child.tagName().lowercased() == "figcaption" && classTokens(child).isEmpty)
                        || matchesTagAndClass(child, tag: "p", className: "figure-source")
                }
                return legal ? nil : structuralViolation()

            case "data-table":
                let tableCount = children.filter { $0.tagName().lowercased() == "table" }.count
                let legal = tableCount == 1 && children.enumerated().allSatisfy { index, child in
                    child.tagName().lowercased() == "table"
                        || (index == 0 && child.tagName().lowercased() == "figcaption" && classTokens(child).isEmpty)
                        || matchesTagAndClass(child, tag: "p", className: "figure-note")
                        || matchesTagAndClass(child, tag: "p", className: "figure-source")
                }
                return legal ? nil : structuralViolation()

            case "cross":
                let legal = children.count == 1 && children[0].tagName().lowercased() == "table"
                return legal ? nil : structuralViolation()

            default:
                return structuralViolation()
            }

        case "blockquote":
            guard parentIsRoot else { return structuralViolation() }
            let children = el.children().array()
            let paragraphs = children.filter { $0.tagName().lowercased() == "p" }
            let footers = children.filter { $0.tagName().lowercased() == "footer" }
            let sources = children.filter { matchesTagAndClass($0, tag: "p", className: "figure-source") }
            let plainCount = paragraphs.count - sources.count
            let legal = plainCount >= 1
                && children.count == paragraphs.count + footers.count
                && children.enumerated().allSatisfy { index, child in
                    (index < plainCount && child.tagName().lowercased() == "p" && classTokens(child).isEmpty)
                        || (index == plainCount && child.tagName().lowercased() == "footer")
                        || matchesTagAndClass(child, tag: "p", className: "figure-source")
                }
                && sources.allSatisfy { source in
                    children.firstIndex(where: { $0 === source }) == children.count - 1
                }
            return legal ? nil : structuralViolation()

        case "figcaption":
            return parentTag == "figure" ? nil : structuralViolation()

        case "footer":
            if let parent = el.parent(), matchesTagAndClass(parent, tag: "blockquote", className: "quote") {
                return nil
            }
            return structuralViolation()

        case "cite":
            return parentTag == "footer" ? nil : structuralViolation()

        case "p":
            let pClass = trimmedAttr(el, "class")
            if !pClass.isEmpty && parentTag != "figure" && parentTag != "blockquote" {
                return attrViolation("class")
            }
            return nil

        case "span":
            let isBlank = trimmedAttr(el, "class") == "blank"
            if !isBlank {
                return el.hasAttr("data-blank") ? attrViolation("data-blank") : nil
            }
            guard el.hasAttr("data-blank"), matches(blankNumberRegex, trimmedAttr(el, "data-blank")) else {
                return attrViolation("data-blank")
            }
            return el.getChildNodes().isEmpty ? nil : structuralViolation()

        default:
            return nil
        }
    }

    private static func extractBlocks(
        from container: Element,
        options: RichParseOptions,
        depth: Int
    ) -> [RichBlock] {
        guard depth <= maxRecursionDepth else { return [] }
        var blocks: [RichBlock] = []
        var pendingInlines: [RichInline] = []

        func flushPending() {
            let trimmed = trimEdgeWhitespaceOnlyIfEmpty(pendingInlines)
            if !trimmed.isEmpty {
                blocks.append(contentsOf: splitBlocksAroundBlockFormulas(trimmed))
            }
            pendingInlines.removeAll(keepingCapacity: true)
        }

        for node in container.getChildNodes() {
            if let textNode = node as? TextNode {
                let raw = textNode.getWholeText()
                if pendingInlines.isEmpty && raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    continue
                }
                pendingInlines.append(contentsOf: splitMathInPlainText(raw, style: [], slotMarkers: options.slotMarkers))
                continue
            }

            guard let el = node as? Element else { continue }
            let tag = el.tagName().lowercased()

            switch tag {
            case "p":
                flushPending()
                let inlines = extractInlines(from: el, style: [], options: options, depth: depth + 1)
                if !inlines.isEmpty {
                    blocks.append(contentsOf: splitBlocksAroundBlockFormulas(inlines))
                }

            case "span" where trimmedAttr(el, "class") == "math-block":
                flushPending()
                blocks.append(.formulaBlock(extractMathSpan(el, display: .block)))

            case "ul", "ol":
                flushPending()
                let items = el.children().array().compactMap { child -> RichListItem? in
                    guard child.tagName().lowercased() == "li" else { return nil }
                    let itemBlocks = extractBlocks(from: child, options: options, depth: depth + 1)
                    return RichListItem(blocks: itemBlocks)
                }
                if !items.isEmpty {
                    blocks.append(.list(ordered: tag == "ol", items: items))
                }

            case "table":
                flushPending()
                let table = extractTable(from: el, options: options, depth: depth + 1)
                if !table.rows.isEmpty {
                    blocks.append(.table(table))
                }

            case "figure":
                flushPending()
                if let block = extractFigure(from: el, options: options, depth: depth + 1) {
                    blocks.append(block)
                }

            case "blockquote":
                flushPending()
                if let quote = extractQuote(from: el, options: options, depth: depth + 1) {
                    blocks.append(.quote(quote))
                }

            default:
                pendingInlines.append(contentsOf: extractInlinesForElement(el, style: [], options: options, depth: depth + 1))
            }
        }

        flushPending()
        return blocks
    }

    private static func splitBlocksAroundBlockFormulas(_ inlines: [RichInline]) -> [RichBlock] {
        var result: [RichBlock] = []
        var buffer: [RichInline] = []

        func flushBuffer() {
            let cleaned = trimEdgeWhitespaceOnlyIfEmpty(buffer)
            if !cleaned.isEmpty {
                result.append(.paragraph(cleaned))
            }
            buffer.removeAll(keepingCapacity: true)
        }

        for inline in inlines {
            if case let .formula(formula) = inline, formula.display == .block {
                flushBuffer()
                result.append(.formulaBlock(formula))
            } else {
                buffer.append(inline)
            }
        }

        flushBuffer()
        return result
    }

    private static func trimEdgeWhitespaceOnlyIfEmpty(_ inlines: [RichInline]) -> [RichInline] {
        let hasMeaningfulContent = inlines.contains { item in
            switch item {
            case let .text(text, _):
                !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            default:
                true
            }
        }
        return hasMeaningfulContent ? inlines : []
    }

    private static func extractInlines(
        from container: Element,
        style: RichTextStyle,
        options: RichParseOptions,
        depth: Int
    ) -> [RichInline] {
        guard depth <= maxRecursionDepth else { return [] }
        var result: [RichInline] = []
        for node in container.getChildNodes() {
            if let textNode = node as? TextNode {
                let raw = textNode.getWholeText()
                result.append(contentsOf: splitMathInPlainText(raw, style: style, slotMarkers: options.slotMarkers))
            } else if let el = node as? Element {
                result.append(contentsOf: extractInlinesForElement(el, style: style, options: options, depth: depth + 1))
            }
        }
        return result
    }

    private static func extractInlinesForElement(
        _ el: Element,
        style: RichTextStyle,
        options: RichParseOptions,
        depth: Int
    ) -> [RichInline] {
        guard depth <= maxRecursionDepth else { return [] }
        let tag = el.tagName().lowercased()

        switch tag {
        case "br":
            return [.lineBreak]

        case "strong":
            return extractInlines(from: el, style: style.union(.bold), options: options, depth: depth + 1)

        case "em", "cite":
            return extractInlines(from: el, style: style.union(.italic), options: options, depth: depth + 1)

        case "s":
            return extractInlines(from: el, style: style.union(.strikethrough), options: options, depth: depth + 1)

        case "sub":
            return extractInlines(from: el, style: style.union(.subscript), options: options, depth: depth + 1)

        case "sup":
            return extractInlines(from: el, style: style.union(.superscript), options: options, depth: depth + 1)

        case "u":
            let label = trimmedAttr(el, "data-label")
            if !label.isEmpty {
                let children = extractInlines(from: el, style: style, options: options, depth: depth + 1)
                return [.labeledUnderline(label: label, children: children)]
            }
            return extractInlines(from: el, style: style.union(.underline), options: options, depth: depth + 1)

        case "span":
            let spanClass = trimmedAttr(el, "class")
            if spanClass == "math-inline" {
                return [.formula(extractMathSpan(el, display: .inline))]
            }
            if spanClass == "math-block" {
                return [.formula(extractMathSpan(el, display: .block))]
            }
            if spanClass == "blank", let ordinal = Int(trimmedAttr(el, "data-blank")) {
                return [.blank(ordinal: ordinal)]
            }
            return extractInlines(from: el, style: style, options: options, depth: depth + 1)

        case "img":
            return [.image(extractImageData(from: el))]

        default:
            return extractInlines(from: el, style: style, options: options, depth: depth + 1)
        }
    }

    private static func extractMathSpan(_ el: Element, display: FormulaDisplay) -> Formula {
        let raw = (try? el.text()) ?? ""
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let open = display == .block ? "\\[" : "\\("
        let close = display == .block ? "\\]" : "\\)"
        let latex: String
        if trimmed.hasPrefix(open), trimmed.hasSuffix(close), trimmed.count >= 4 {
            latex = String(trimmed.dropFirst(2).dropLast(2))
        } else {
            latex = raw
        }
        return Formula(source: latex, display: display)
    }

    private static func extractImageData(from el: Element) -> RichImageData {
        let src = trimmedAttr(el, "src")
        let dataAssetId = trimmedAttr(el, "data-asset-id")
        let alt = (try? el.attr("alt")) ?? ""
        let width = Int(trimmedAttr(el, "width"))
        let height = Int(trimmedAttr(el, "height"))
        let ref = AttemptAssetRef.resolve(src: src, dataAssetId: dataAssetId)
        return RichImageData(ref: ref, alt: alt, width: width, height: height)
    }

    private static func extractFigure(
        from el: Element,
        options: RichParseOptions,
        depth: Int
    ) -> RichBlock? {
        let figureClass = trimmedAttr(el, "class")
        let children = el.children().array()

        switch figureClass {
        case "image":
            guard let imgEl = children.first(where: { $0.tagName().lowercased() == "img" }) else {
                return nil
            }
            let image = extractImageData(from: imgEl)
            let width = FigureImageWidth(rawValue: trimmedAttr(el, "data-width")) ?? .full
            let zoomable = trimmedAttr(el, "data-zoom") == "true"
            let captionEl = children.first(where: { $0.tagName().lowercased() == "figcaption" })
            let caption = captionEl.map { extractInlines(from: $0, style: [], options: options, depth: depth + 1) }
            let sources = children
                .filter { matchesTagAndClass($0, tag: "p", className: "figure-source") }
                .map { extractInlines(from: $0, style: [], options: options, depth: depth + 1) }
            return .imageFigure(image: image, width: width, zoomable: zoomable, caption: caption, sources: sources)

        case "data-table":
            guard let tableEl = children.first(where: { $0.tagName().lowercased() == "table" }) else {
                return nil
            }
            let captionEl = children.first(where: { $0.tagName().lowercased() == "figcaption" })
            let caption = captionEl.map { extractInlines(from: $0, style: [], options: options, depth: depth + 1) }
            let table = extractTable(from: tableEl, options: options, depth: depth + 1)
            let notes = children
                .filter { matchesTagAndClass($0, tag: "p", className: "figure-note") }
                .map { extractInlines(from: $0, style: [], options: options, depth: depth + 1) }
            let sources = children
                .filter { matchesTagAndClass($0, tag: "p", className: "figure-source") }
                .map { extractInlines(from: $0, style: [], options: options, depth: depth + 1) }
            return .dataTableFigure(caption: caption, table: table, notes: notes, sources: sources)

        case "cross":
            guard let tableEl = children.first(where: { $0.tagName().lowercased() == "table" }) else {
                return nil
            }
            return .crossFigure(table: extractTable(from: tableEl, options: options, depth: depth + 1))

        default:
            return nil
        }
    }

    private static func extractTable(
        from tableEl: Element,
        options: RichParseOptions,
        depth: Int
    ) -> RichTable {
        var rows: [RichTableRow] = []

        func appendRows(from section: Element, isHeaderSection: Bool) {
            for tr in section.children().array() where tr.tagName().lowercased() == "tr" {
                let cells = tr.children().array().compactMap { cellEl -> RichTableCell? in
                    let cellTag = cellEl.tagName().lowercased()
                    guard cellTag == "th" || cellTag == "td" else { return nil }
                    let isHeader = cellTag == "th"
                    let scope = TableHeaderScope(rawValue: trimmedAttr(cellEl, "scope"))
                    let isNumeric = classTokens(cellEl).contains("num")
                    let colspan = max(1, Int(trimmedAttr(cellEl, "colspan")) ?? 1)
                    let rowspan = max(1, Int(trimmedAttr(cellEl, "rowspan")) ?? 1)
                    let cellBlocks = extractBlocks(from: cellEl, options: options, depth: depth + 1)
                    return RichTableCell(
                        isHeader: isHeader,
                        scope: scope,
                        isNumeric: isNumeric,
                        colspan: colspan,
                        rowspan: rowspan,
                        blocks: cellBlocks
                    )
                }
                if !cells.isEmpty {
                    rows.append(RichTableRow(isHeaderRow: isHeaderSection, cells: cells))
                }
            }
        }

        for child in tableEl.children().array() {
            switch child.tagName().lowercased() {
            case "thead":
                appendRows(from: child, isHeaderSection: true)
            case "tbody":
                appendRows(from: child, isHeaderSection: false)
            case "tr":
                let cells = child.children().array().compactMap { cellEl -> RichTableCell? in
                    let cellTag = cellEl.tagName().lowercased()
                    guard cellTag == "th" || cellTag == "td" else { return nil }
                    let isHeader = cellTag == "th"
                    let scope = TableHeaderScope(rawValue: trimmedAttr(cellEl, "scope"))
                    let isNumeric = classTokens(cellEl).contains("num")
                    let colspan = max(1, Int(trimmedAttr(cellEl, "colspan")) ?? 1)
                    let rowspan = max(1, Int(trimmedAttr(cellEl, "rowspan")) ?? 1)
                    let cellBlocks = extractBlocks(from: cellEl, options: options, depth: depth + 1)
                    return RichTableCell(
                        isHeader: isHeader,
                        scope: scope,
                        isNumeric: isNumeric,
                        colspan: colspan,
                        rowspan: rowspan,
                        blocks: cellBlocks
                    )
                }
                if !cells.isEmpty {
                    rows.append(RichTableRow(isHeaderRow: false, cells: cells))
                }
            default:
                break
            }
        }

        return RichTable(rows: rows)
    }

    private static func extractQuote(
        from el: Element,
        options: RichParseOptions,
        depth: Int
    ) -> RichQuote? {
        let kind = QuoteKind(rawValue: trimmedAttr(el, "data-kind")) ?? .prose
        let lineNumberStep = Int(trimmedAttr(el, "data-line-numbers")) ?? 0
        var paragraphs: [[RichInline]] = []
        var footer: [RichInline]?
        var source: [RichInline]?

        for child in el.children().array() {
            let tag = child.tagName().lowercased()
            if tag == "p" {
                if matchesTagAndClass(child, tag: "p", className: "figure-source") {
                    source = extractInlines(from: child, style: [], options: options, depth: depth + 1)
                } else {
                    paragraphs.append(extractInlines(from: child, style: [], options: options, depth: depth + 1))
                }
            } else if tag == "footer" {
                footer = extractInlines(from: child, style: [], options: options, depth: depth + 1)
            }
        }

        guard !paragraphs.isEmpty else { return nil }
        return RichQuote(
            kind: kind,
            lineNumberStep: lineNumberStep,
            paragraphs: paragraphs,
            footer: footer,
            source: source
        )
    }

    private static func isBlankSpan(_ el: Element) -> Bool {
        el.tagName().lowercased() == "span" && classTokens(el).contains("blank")
    }

    private static func matchesTagAndClass(_ el: Element, tag: String, className: String) -> Bool {
        el.tagName().lowercased() == tag && classTokens(el).contains(className)
    }

    private static func classTokens(_ el: Element) -> [String] {
        trimmedAttr(el, "class")
            .split(whereSeparator: \.isWhitespace)
            .map(String.init)
    }

    private static func trimmedAttr(_ el: Element, _ key: String) -> String {
        ((try? el.attr(key)) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func optionalAttr(_ el: Element, _ key: String) -> String? {
        el.hasAttr(key) ? (try? el.attr(key)) : nil
    }

    private static func matches(_ regex: NSRegularExpression, _ value: String) -> Bool {
        let range = NSRange(location: 0, length: (value as NSString).length)
        return regex.firstMatch(in: value, range: range) != nil
    }
}
