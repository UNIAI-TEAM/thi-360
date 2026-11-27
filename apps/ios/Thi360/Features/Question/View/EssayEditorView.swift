import Observation
import SwiftUI
import UIKit
import UniformTypeIdentifiers

@Observable
@MainActor
final class EssayEditorController {
    @ObservationIgnored weak var textView: EssayUITextView?
    @ObservationIgnored var onCommittedHTMLChange: ((String, Int) -> Void)?

    var isBoldActive: Bool = false
    var isItalicActive: Bool = false
    var isBulletListActive: Bool = false
    var isOrderedListActive: Bool = false
    var canUndo: Bool = false
    var canRedo: Bool = false
    var isEmpty: Bool = true
    var currentWordCount: Int = 0

    @ObservationIgnored private var currentBaseFont: UIFont = UIFont.preferredFont(forTextStyle: .body)
    @ObservationIgnored private var currentTextColor: UIColor = .label
    @ObservationIgnored private var isApplyingExternalHTML = false
    @ObservationIgnored private var isApplyingInternalEdit = false
    @ObservationIgnored private var lastExternalHTML: String?
    @ObservationIgnored private var lastEmittedHTML: String?
    @ObservationIgnored private weak var configuredTextView: EssayUITextView?

    func configure(
        baseFont: UIFont,
        textColor: UIColor,
        html: String,
        isLocked: Bool
    ) {
        let fontChanged = abs(currentBaseFont.pointSize - baseFont.pointSize) > 0.25
        let colorChanged = currentTextColor != textColor
        currentBaseFont = baseFont
        currentTextColor = textColor

        guard let textView else { return }
        let isNewTextView = (configuredTextView !== textView)
        configuredTextView = textView
        textView.baseFont = baseFont
        textView.baseTextColor = textColor
        textView.isEditable = !isLocked
        textView.isSelectable = true
        if isLocked && textView.isFirstResponder {
            textView.resignFirstResponder()
        }

        let externalChanged = (lastExternalHTML == nil) || (html != lastExternalHTML && html != lastEmittedHTML)
        lastExternalHTML = html

        if isNewTextView || (externalChanged && textView.markedTextRange == nil) {
            let sourceHTML = isNewTextView ? (lastEmittedHTML ?? html) : html
            isApplyingExternalHTML = true
            let imported = EssayHTML.attributedString(from: sourceHTML, baseFont: baseFont, textColor: textColor)
            let previousSelection = textView.selectedRange
            textView.attributedText = imported
            let clampedLoc = min(previousSelection.location, imported.length)
            textView.selectedRange = NSRange(location: clampedLoc, length: 0)
            applyTypingAttributes(bold: false, italic: false, listStack: currentListStack(in: textView))
            lastEmittedHTML = sourceHTML
            isApplyingExternalHTML = false
            currentWordCount = WordCounter.count(html: sourceHTML)
            isEmpty = textView.text.isEmpty
            refreshToolbarState()
            textView.setNeedsDisplay()
            return
        }

        if fontChanged || colorChanged {
            rescaleInPlace(baseFont: baseFont, textColor: textColor)
        }
        refreshToolbarState()
    }

    func committedHTML() -> String {
        guard let textView else { return lastEmittedHTML ?? lastExternalHTML ?? "" }
        let committed = committedAttributedString(from: textView)
        return EssayHTML.export(committed)
    }

    func toggleBold() {
        toggleInlineTrait(bold: true)
    }

    func toggleItalic() {
        toggleInlineTrait(bold: false)
    }

    func toggleBulletList() {
        toggleList(.bullet)
    }

    func toggleOrderedList() {
        toggleList(.ordered)
    }

    func undo() {
        guard let textView, textView.isEditable, textView.markedTextRange == nil else { return }
        guard textView.undoManager?.canUndo == true else { return }
        textView.undoManager?.undo()
        handleTextDidChange(in: textView)
    }

    func redo() {
        guard let textView, textView.isEditable, textView.markedTextRange == nil else { return }
        guard textView.undoManager?.canRedo == true else { return }
        textView.undoManager?.redo()
        handleTextDidChange(in: textView)
    }

    func pasteHTML(_ rawHTML: String) {
        guard let textView, textView.isEditable else { return }
        let sanitized = EssayHTML.attributedString(
            from: rawHTML,
            baseFont: currentBaseFont,
            textColor: currentTextColor
        )
        insertSanitizedAttributedString(sanitized, in: textView)
    }

    func pasteAttributedString(_ source: NSAttributedString) {
        guard let textView, textView.isEditable else { return }
        let sanitized = EssayHTML.sanitizePastedAttributedString(
            source,
            baseFont: currentBaseFont,
            textColor: currentTextColor
        )
        insertSanitizedAttributedString(sanitized, in: textView)
    }

    func pastePlainText(_ rawText: String) {
        guard let textView, textView.isEditable else { return }
        let cleaned = AnswerValues.stripNullBytes(rawText)
        let stack = currentListStack(in: textView)
        let attrs = EssayHTML.inlineAttributes(
            baseFont: currentBaseFont,
            textColor: currentTextColor,
            bold: isBoldActive,
            italic: isItalicActive,
            listStack: stack
        )
        insertSanitizedAttributedString(NSAttributedString(string: cleaned, attributes: attrs), in: textView)
    }

    func handleShouldChangeText(in textView: EssayUITextView, range: NSRange, replacementText text: String) -> Bool {
        guard textView.isEditable else { return false }
        if text == "\n", textView.markedTextRange == nil {
            let stack = currentListStack(in: textView)
            if !stack.isEmpty {
                let nsText = textView.text as NSString
                let paraRange = nsText.paragraphRange(for: range)
                let paraText = nsText.substring(with: paraRange).trimmingCharacters(in: .newlines)
                if paraText.isEmpty && range.length == 0 {
                    applyListStack([], to: paraRange, in: textView, recordUndo: true)
                    return false
                }
                applyTypingAttributes(bold: isBoldActive, italic: isItalicActive, listStack: stack)
            }
        }
        return true
    }

    func handleTextDidChange(in textView: EssayUITextView) {
        guard !isApplyingExternalHTML else { return }
        isEmpty = textView.text.isEmpty
        textView.setNeedsDisplay()
        refreshToolbarState()

        guard textView.markedTextRange == nil else {
            return
        }

        let html = EssayHTML.export(textView.attributedText)
        let count = WordCounter.count(html: html)
        currentWordCount = count
        if html != lastEmittedHTML {
            lastEmittedHTML = html
            onCommittedHTMLChange?(html, count)
        }
    }

    func handleSelectionDidChange(in textView: EssayUITextView) {
        guard !isApplyingExternalHTML, !isApplyingInternalEdit else { return }
        refreshToolbarState()
    }

    func refreshToolbarState() {
        guard let textView else { return }
        canUndo = textView.undoManager?.canUndo ?? false
        canRedo = textView.undoManager?.canRedo ?? false
        isEmpty = textView.text.isEmpty

        let attrs: [NSAttributedString.Key: Any]
        let length = textView.attributedText.length
        let range = textView.selectedRange
        if range.length > 0, range.location < length {
            attrs = textView.attributedText.attributes(at: range.location, effectiveRange: nil)
        } else if !textView.typingAttributes.isEmpty {
            attrs = textView.typingAttributes
        } else if range.location > 0, range.location - 1 < length {
            attrs = textView.attributedText.attributes(at: range.location - 1, effectiveRange: nil)
        } else {
            attrs = [:]
        }

        isBoldActive = EssayHTML.isBold(in: attrs)
        isItalicActive = EssayHTML.isItalic(in: attrs)
        let stack = EssayHTML.decodeListStack(from: attrs)
        isBulletListActive = stack.last == .bullet
        isOrderedListActive = stack.last == .ordered
    }

    private func toggleInlineTrait(bold isBoldToggle: Bool) {
        guard let textView, textView.isEditable, textView.markedTextRange == nil else { return }
        let targetBold = isBoldToggle ? !isBoldActive : isBoldActive
        let targetItalic = isBoldToggle ? isItalicActive : !isItalicActive
        let range = textView.selectedRange
        let stack = currentListStack(in: textView)

        if range.length > 0, range.location + range.length <= textView.attributedText.length {
            let before = NSAttributedString(attributedString: textView.attributedText)
            let beforeSelection = range

            isApplyingInternalEdit = true
            let mutable = NSMutableAttributedString(attributedString: textView.attributedText)
            mutable.enumerateAttributes(in: range, options: []) { attrs, subrange, _ in
                let subBold = isBoldToggle ? targetBold : EssayHTML.isBold(in: attrs)
                let subItalic = isBoldToggle ? EssayHTML.isItalic(in: attrs) : targetItalic
                let subFont = EssayHTML.font(baseFont: currentBaseFont, bold: subBold, italic: subItalic)
                mutable.addAttribute(.font, value: subFont, range: subrange)
            }
            applySnapshot(mutable, selection: range, previous: before, previousSelection: beforeSelection, in: textView)
            isApplyingInternalEdit = false
        }

        applyTypingAttributes(bold: targetBold, italic: targetItalic, listStack: stack)
        isBoldActive = targetBold
        isItalicActive = targetItalic
        if range.length > 0 {
            handleTextDidChange(in: textView)
        }
    }

    private func toggleList(_ kind: EssayListKind) {
        guard let textView, textView.isEditable, textView.markedTextRange == nil else { return }
        let currentStack = currentListStack(in: textView)
        let nextStack: [EssayListKind] = (currentStack.last == kind) ? [] : [kind]
        let nsText = textView.text as NSString
        let paraRange: NSRange
        if nsText.length == 0 {
            paraRange = NSRange(location: 0, length: 0)
        } else {
            let safeRange = NSRange(
                location: min(textView.selectedRange.location, nsText.length),
                length: min(textView.selectedRange.length, max(0, nsText.length - min(textView.selectedRange.location, nsText.length)))
            )
            paraRange = nsText.paragraphRange(for: safeRange)
        }

        applyListStack(nextStack, to: paraRange, in: textView, recordUndo: true)
    }

    private func applyListStack(
        _ stack: [EssayListKind],
        to paragraphRange: NSRange,
        in textView: EssayUITextView,
        recordUndo: Bool
    ) {
        let selection = textView.selectedRange
        if paragraphRange.length > 0, paragraphRange.location + paragraphRange.length <= textView.attributedText.length {
            let before = NSAttributedString(attributedString: textView.attributedText)
            isApplyingInternalEdit = true
            let mutable = NSMutableAttributedString(attributedString: textView.attributedText)
            let style = EssayHTML.makeParagraphStyle(for: stack)
            mutable.addAttribute(.paragraphStyle, value: style, range: paragraphRange)
            if stack.isEmpty {
                mutable.removeAttribute(.essayListStack, range: paragraphRange)
            } else {
                mutable.addAttribute(.essayListStack, value: EssayHTML.encodeListStack(stack), range: paragraphRange)
            }
            if recordUndo {
                applySnapshot(mutable, selection: selection, previous: before, previousSelection: selection, in: textView)
            } else {
                textView.attributedText = mutable
                textView.selectedRange = selection
            }
            isApplyingInternalEdit = false
        }

        applyTypingAttributes(bold: isBoldActive, italic: isItalicActive, listStack: stack)
        isBulletListActive = stack.last == .bullet
        isOrderedListActive = stack.last == .ordered
        textView.setNeedsDisplay()
        if paragraphRange.length > 0 {
            handleTextDidChange(in: textView)
        }
    }

    private func insertSanitizedAttributedString(_ sanitized: NSAttributedString, in textView: EssayUITextView) {
        let before = NSAttributedString(attributedString: textView.attributedText)
        let beforeSelection = textView.selectedRange
        let safeLoc = min(beforeSelection.location, before.length)
        let safeLen = min(beforeSelection.length, max(0, before.length - safeLoc))
        let replaceRange = NSRange(location: safeLoc, length: safeLen)

        let mutable = NSMutableAttributedString(attributedString: before)
        mutable.replaceCharacters(in: replaceRange, with: sanitized)
        let nextSelection = NSRange(location: safeLoc + sanitized.length, length: 0)

        isApplyingInternalEdit = true
        applySnapshot(mutable, selection: nextSelection, previous: before, previousSelection: beforeSelection, in: textView)
        isApplyingInternalEdit = false
        handleTextDidChange(in: textView)
    }

    private func applySnapshot(
        _ next: NSAttributedString,
        selection nextSelection: NSRange,
        previous: NSAttributedString,
        previousSelection: NSRange,
        in textView: EssayUITextView
    ) {
        textView.undoManager?.registerUndo(withTarget: textView) { [weak self] target in
            guard let self else { return }
            self.isApplyingInternalEdit = true
            self.applySnapshot(
                previous,
                selection: previousSelection,
                previous: next,
                previousSelection: nextSelection,
                in: target
            )
            self.isApplyingInternalEdit = false
            self.handleTextDidChange(in: target)
        }
        textView.attributedText = next
        textView.selectedRange = nextSelection
        textView.setNeedsDisplay()
    }

    private func rescaleInPlace(baseFont: UIFont, textColor: UIColor) {
        guard let textView else { return }
        let selection = textView.selectedRange
        isApplyingInternalEdit = true
        if textView.attributedText.length > 0 {
            let mutable = NSMutableAttributedString(attributedString: textView.attributedText)
            let fullRange = NSRange(location: 0, length: mutable.length)
            mutable.enumerateAttributes(in: fullRange, options: []) { attrs, subrange, _ in
                let bold = EssayHTML.isBold(in: attrs)
                let italic = EssayHTML.isItalic(in: attrs)
                mutable.addAttribute(.font, value: EssayHTML.font(baseFont: baseFont, bold: bold, italic: italic), range: subrange)
                mutable.addAttribute(.foregroundColor, value: textColor, range: subrange)
            }
            textView.attributedText = mutable
            textView.selectedRange = selection
        }
        applyTypingAttributes(bold: isBoldActive, italic: isItalicActive, listStack: currentListStack(in: textView))
        isApplyingInternalEdit = false
        textView.setNeedsDisplay()
    }

    private func applyTypingAttributes(bold: Bool, italic: Bool, listStack: [EssayListKind]) {
        guard let textView else { return }
        textView.typingAttributes = EssayHTML.inlineAttributes(
            baseFont: currentBaseFont,
            textColor: currentTextColor,
            bold: bold,
            italic: italic,
            listStack: listStack
        )
    }

    private func currentListStack(in textView: EssayUITextView) -> [EssayListKind] {
        let typingStack = EssayHTML.decodeListStack(from: textView.typingAttributes)
        if !typingStack.isEmpty {
            return typingStack
        }
        let nsText = textView.text as NSString
        guard nsText.length > 0 else { return [] }
        let loc = min(textView.selectedRange.location, nsText.length)
        let paraRange = nsText.paragraphRange(for: NSRange(location: loc, length: 0))
        if paraRange.length > 0, paraRange.location < textView.attributedText.length {
            let attrs = textView.attributedText.attributes(at: paraRange.location, effectiveRange: nil)
            return EssayHTML.decodeListStack(from: attrs)
        }
        return []
    }

    private func committedAttributedString(from textView: EssayUITextView) -> NSAttributedString {
        guard let markedRange = textView.markedTextRange else {
            return textView.attributedText
        }
        let start = textView.offset(from: textView.beginningOfDocument, to: markedRange.start)
        let end = textView.offset(from: textView.beginningOfDocument, to: markedRange.end)
        let length = textView.attributedText.length
        guard start >= 0, end >= start, end <= length else {
            return textView.attributedText
        }
        let mutable = NSMutableAttributedString(attributedString: textView.attributedText)
        mutable.deleteCharacters(in: NSRange(location: start, length: end - start))
        return mutable
    }
}

final class EssayUITextView: UITextView {
    var baseFont: UIFont = UIFont.preferredFont(forTextStyle: .body)
    var baseTextColor: UIColor = .label
    var onDidUnmarkText: (() -> Void)?
    var onPasteHTML: ((String) -> Void)?
    var onPasteAttributed: ((NSAttributedString) -> Void)?
    var onPastePlain: ((String) -> Void)?

    init() {
        super.init(frame: .zero, textContainer: nil)
        configureDefaults()
    }

    convenience init(usingTextKit2: Bool) {
        if usingTextKit2 {
            let layoutManager = NSTextLayoutManager()
            let contentStorage = NSTextContentStorage()
            let textContainer = NSTextContainer(size: CGSize(width: 0, height: CGFloat.greatestFiniteMagnitude))
            textContainer.widthTracksTextView = true
            layoutManager.textContainer = textContainer
            contentStorage.addTextLayoutManager(layoutManager)
            self.init(frame: .zero, textContainer: textContainer)
        } else {
            self.init()
        }
    }

    override init(frame: CGRect, textContainer: NSTextContainer?) {
        super.init(frame: frame, textContainer: textContainer)
        configureDefaults()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func configureDefaults() {
        backgroundColor = .clear
        isScrollEnabled = true
        alwaysBounceVertical = false
        adjustsFontForContentSizeCategory = true
        autocorrectionType = .no
        smartQuotesType = .no
        smartDashesType = .no
        smartInsertDeleteType = .no
        textContainerInset = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
        textContainer.lineFragmentPadding = 0
        typingAttributes = EssayHTML.inlineAttributes(
            baseFont: baseFont,
            textColor: baseTextColor,
            bold: false,
            italic: false,
            listStack: []
        )
    }

    override func unmarkText() {
        super.unmarkText()
        onDidUnmarkText?()
    }

    override func paste(_ sender: Any?) {
        guard isEditable else { return }
        let pasteboard = UIPasteboard.general

        if let htmlData = pasteboard.data(forPasteboardType: UTType.html.identifier),
           let rawHTML = String(data: htmlData, encoding: .utf8)
        {
            onPasteHTML?(rawHTML)
            return
        }
        if let rawHTML = pasteboard.value(forPasteboardType: UTType.html.identifier) as? String {
            onPasteHTML?(rawHTML)
            return
        }
        if let rtfData = pasteboard.data(forPasteboardType: UTType.rtf.identifier),
           let attr = try? NSAttributedString(
               data: rtfData,
               options: [.documentType: NSAttributedString.DocumentType.rtf],
               documentAttributes: nil
           )
        {
            onPasteAttributed?(attr)
            return
        }
        if let plain = pasteboard.string {
            onPastePlain?(plain)
        }
    }

    override func draw(_ rect: CGRect) {
        super.draw(rect)
        drawListMarkers()
    }

    private func drawListMarkers() {
        guard let attrText = attributedText, attrText.length > 0 else { return }
        let nsText = attrText.string as NSString
        var cursor = 0
        var orderedCounter = 0

        while cursor < nsText.length {
            let paraRange = nsText.paragraphRange(for: NSRange(location: cursor, length: 0))
            let attrs = attrText.attributes(at: paraRange.location, effectiveRange: nil)
            let stack = EssayHTML.decodeListStack(from: attrs)
            if let kind = stack.last {
                let marker: String
                switch kind {
                case .bullet:
                    orderedCounter = 0
                    marker = "•"
                case .ordered:
                    orderedCounter += 1
                    marker = "\(orderedCounter)."
                }

                if let startPos = position(from: beginningOfDocument, offset: paraRange.location) {
                    let caret = caretRect(for: startPos)
                    if caret.height > 0, caret.origin.x.isFinite, caret.origin.y.isFinite {
                        let markerAttrs: [NSAttributedString.Key: Any] = [
                            .font: baseFont,
                            .foregroundColor: baseTextColor.withAlphaComponent(0.75)
                        ]
                        let markerSize = (marker as NSString).size(withAttributes: markerAttrs)
                        let markerX = max(textContainerInset.left, caret.minX - markerSize.width - 6)
                        let markerY = caret.minY + (caret.height - markerSize.height) / 2
                        (marker as NSString).draw(at: CGPoint(x: markerX, y: markerY), withAttributes: markerAttrs)
                    }
                }
            } else {
                orderedCounter = 0
            }
            cursor = NSMaxRange(paraRange)
        }
    }
}

struct EssayEditorView: View {
    let itemId: String
    let html: String
    let isLocked: Bool
    let isLoading: Bool
    let locale: AppLocale
    let controller: EssayEditorController
    let onCommittedChange: (String, Int) -> Void

    @Environment(\.brandPalette) private var brandPalette
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if isLoading {
                HStack(spacing: 10) {
                    ProgressView()
                    Text(QuestionStrings.text("attempt.editor.loading", locale: locale))
                        .font(.subheadline)
                        .foregroundStyle(brandPalette.textSecondaryColor)
                }
                .padding(16)
                .frame(maxWidth: .infinity, minHeight: 160, alignment: .center)
                .accessibilityIdentifier("question.\(itemId).essayLoading")
                .questionProbe(
                    id: "question.\(itemId).essayLoading",
                    label: QuestionStrings.text("attempt.editor.loading", locale: locale)
                )
            } else {
                toolbar
                Divider()

                ZStack(alignment: .topLeading) {
                    EssayTextViewRepresentable(
                        itemId: itemId,
                        html: html,
                        isLocked: isLocked,
                        locale: locale,
                        textColor: UIColor(hex: brandPalette.text),
                        dynamicTypeSize: dynamicTypeSize,
                        controller: controller,
                        onCommittedChange: onCommittedChange
                    )
                    .frame(minHeight: 180)

                    if controller.isEmpty {
                        Text(QuestionStrings.text("question.essayPlaceholder", locale: locale))
                            .font(.body)
                            .foregroundStyle(brandPalette.textMutedColor)
                            .padding(.top, 12)
                            .padding(.leading, 12)
                            .allowsHitTesting(false)
                    }
                }
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(isLocked ? brandPalette.bgSubtleColor : brandPalette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder(brandPalette.borderStrongColor, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("question.\(itemId).essayEditor")
    }

    private var toolbar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                toolbarButton(
                    symbol: "bold",
                    labelKey: "attempt.editor.bold",
                    isActive: controller.isBoldActive,
                    isDisabled: isLocked,
                    idSuffix: "bold",
                    action: { controller.toggleBold() }
                )
                toolbarButton(
                    symbol: "italic",
                    labelKey: "attempt.editor.italic",
                    isActive: controller.isItalicActive,
                    isDisabled: isLocked,
                    idSuffix: "italic",
                    action: { controller.toggleItalic() }
                )

                Divider()
                    .frame(height: 24)
                    .padding(.horizontal, 2)

                toolbarButton(
                    symbol: "list.bullet",
                    labelKey: "attempt.editor.bulletList",
                    isActive: controller.isBulletListActive,
                    isDisabled: isLocked,
                    idSuffix: "bulletList",
                    action: { controller.toggleBulletList() }
                )
                toolbarButton(
                    symbol: "list.number",
                    labelKey: "attempt.editor.orderedList",
                    isActive: controller.isOrderedListActive,
                    isDisabled: isLocked,
                    idSuffix: "orderedList",
                    action: { controller.toggleOrderedList() }
                )

                Divider()
                    .frame(height: 24)
                    .padding(.horizontal, 2)

                toolbarButton(
                    symbol: "arrow.uturn.backward",
                    labelKey: "attempt.editor.undo",
                    isActive: false,
                    isDisabled: isLocked || !controller.canUndo,
                    idSuffix: "undo",
                    action: { controller.undo() }
                )
                toolbarButton(
                    symbol: "arrow.uturn.forward",
                    labelKey: "attempt.editor.redo",
                    isActive: false,
                    isDisabled: isLocked || !controller.canRedo,
                    idSuffix: "redo",
                    action: { controller.redo() }
                )
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
        }
        .background(brandPalette.bgSubtleColor)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(QuestionStrings.text("attempt.editor.toolbarLabel", locale: locale))
        .accessibilityIdentifier("question.\(itemId).essayToolbar")
    }

    private func toolbarButton(
        symbol: String,
        labelKey: String,
        isActive: Bool,
        isDisabled: Bool,
        idSuffix: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.body.weight(.semibold))
                .foregroundStyle(isActive ? brandPalette.bgSurfaceColor : brandPalette.textColor)
                .frame(minWidth: 44, minHeight: 44)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(isActive ? brandPalette.primaryFillColor : Color.clear)
                )
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .opacity(isDisabled ? 0.4 : 1)
        .accessibilityLabel(QuestionStrings.text(labelKey, locale: locale))
        .accessibilityAddTraits(isActive ? [.isSelected] : [])
        .accessibilityIdentifier("question.\(itemId).essayToolbar.\(idSuffix)")
        .questionProbe(
            id: "question.\(itemId).essayToolbar.\(idSuffix)",
            label: QuestionStrings.text(labelKey, locale: locale),
            isButton: true,
            isEnabled: !isDisabled,
            isSelected: isActive,
            action: action
        )
    }
}

private struct EssayTextViewRepresentable: UIViewRepresentable {
    let itemId: String
    let html: String
    let isLocked: Bool
    let locale: AppLocale
    let textColor: UIColor
    let dynamicTypeSize: DynamicTypeSize
    let controller: EssayEditorController
    let onCommittedChange: (String, Int) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(controller: controller)
    }

    func makeUIView(context: Context) -> EssayUITextView {
        let textView = EssayUITextView(usingTextKit2: true)
        textView.delegate = context.coordinator
        textView.accessibilityLabel = QuestionStrings.text("question.essayEditorLabel", locale: locale)
        textView.accessibilityIdentifier = "question.\(itemId).essayInput"

        controller.textView = textView
        controller.onCommittedHTMLChange = onCommittedChange

        textView.onDidUnmarkText = { [weak controller, weak textView] in
            guard let controller, let textView else { return }
            controller.handleTextDidChange(in: textView)
        }
        textView.onPasteHTML = { [weak controller] rawHTML in
            controller?.pasteHTML(rawHTML)
        }
        textView.onPasteAttributed = { [weak controller] attr in
            controller?.pasteAttributedString(attr)
        }
        textView.onPastePlain = { [weak controller] plain in
            controller?.pastePlainText(plain)
        }

        let baseFont = UIFont.preferredFont(forTextStyle: .body)
        controller.configure(baseFont: baseFont, textColor: textColor, html: html, isLocked: isLocked)
        return textView
    }

    func updateUIView(_ textView: EssayUITextView, context: Context) {
        controller.textView = textView
        controller.onCommittedHTMLChange = onCommittedChange
        textView.accessibilityLabel = QuestionStrings.text("question.essayEditorLabel", locale: locale)
        textView.accessibilityIdentifier = "question.\(itemId).essayInput"

        let traitCollection = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(dynamicTypeSize))
        let baseFont = UIFont.preferredFont(forTextStyle: .body, compatibleWith: traitCollection)
        controller.configure(baseFont: baseFont, textColor: textColor, html: html, isLocked: isLocked)
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        let controller: EssayEditorController

        init(controller: EssayEditorController) {
            self.controller = controller
        }

        func textView(
            _ textView: UITextView,
            shouldChangeTextIn range: NSRange,
            replacementText text: String
        ) -> Bool {
            guard let essayTextView = textView as? EssayUITextView else { return true }
            return controller.handleShouldChangeText(in: essayTextView, range: range, replacementText: text)
        }

        func textViewDidChange(_ textView: UITextView) {
            guard let essayTextView = textView as? EssayUITextView else { return }
            controller.handleTextDidChange(in: essayTextView)
        }

        func textViewDidChangeSelection(_ textView: UITextView) {
            guard let essayTextView = textView as? EssayUITextView else { return }
            controller.handleSelectionDidChange(in: essayTextView)
        }
    }
}
