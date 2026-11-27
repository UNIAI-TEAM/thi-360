import SwiftUI
import UIKit

@MainActor
final class ExamTextFieldController: ObservableObject {
    weak var textField: ExamUITextField?
    var onCommittedChange: ((String) -> Void)?

    func insertAtCursor(_ insertion: String) {
        guard let field = textField, !field.isReadOnly else { return }
        if !field.isFirstResponder {
            field.becomeFirstResponder()
        }
        if field.markedTextRange != nil {
            field.unmarkText()
        }
        let current = field.text ?? ""
        let selection = field.currentSelectionNSRange
        let (nextValue, nextCursor) = SymbolInputRules.insertTextAtCursor(
            currentValue: current,
            selectionRange: selection,
            insertion: insertion
        )
        let cleaned = AnswerValues.stripNullBytes(nextValue)
        field.text = cleaned
        field.setCaretPosition(utf16Offset: min(nextCursor, (cleaned as NSString).length))
        onCommittedChange?(cleaned)
    }
}

final class ExamUITextField: UITextField {
    var isReadOnly: Bool = false
    var scrollIntoViewOnKeyboard: Bool = false
    var onDidUnmarkText: (() -> Void)?
    var textInsets: UIEdgeInsets = UIEdgeInsets(top: 8, left: 12, bottom: 8, right: 12)
    private var keyboardObservers: [NSObjectProtocol] = []
    private var lastKnownKeyboardFrame: CGRect = .zero

    override func unmarkText() {
        super.unmarkText()
        onDidUnmarkText?()
    }

    override func textRect(forBounds bounds: CGRect) -> CGRect {
        bounds.inset(by: textInsets)
    }

    override func editingRect(forBounds bounds: CGRect) -> CGRect {
        bounds.inset(by: textInsets)
    }

    override func placeholderRect(forBounds bounds: CGRect) -> CGRect {
        bounds.inset(by: textInsets)
    }

    var currentSelectionNSRange: NSRange? {
        guard let range = selectedTextRange else { return nil }
        let start = offset(from: beginningOfDocument, to: range.start)
        let end = offset(from: beginningOfDocument, to: range.end)
        guard start >= 0, end >= start else { return nil }
        return NSRange(location: start, length: end - start)
    }

    func setCaretPosition(utf16Offset: Int) {
        let clamped = max(0, min(utf16Offset, ((text ?? "") as NSString).length))
        if let pos = position(from: beginningOfDocument, offset: clamped) {
            selectedTextRange = textRange(from: pos, to: pos)
        }
    }

    func installKeyboardAvoidanceIfNeeded() {
        guard scrollIntoViewOnKeyboard, keyboardObservers.isEmpty else { return }
        let center = NotificationCenter.default
        let showNames: [NSNotification.Name] = [
            UIResponder.keyboardWillShowNotification,
            UIResponder.keyboardDidShowNotification,
            UIResponder.keyboardWillChangeFrameNotification
        ]
        var tokens = showNames.map { name in
            center.addObserver(forName: name, object: nil, queue: .main) { [weak self] notification in
                MainActor.assumeIsolated {
                    self?.handleKeyboardNotification(notification)
                }
            }
        }
        tokens.append(
            center.addObserver(
                forName: UIResponder.keyboardWillHideNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                MainActor.assumeIsolated {
                    self?.handleKeyboardHide()
                }
            }
        )
        keyboardObservers = tokens
    }

    func removeKeyboardAvoidance() {
        for token in keyboardObservers {
            NotificationCenter.default.removeObserver(token)
        }
        keyboardObservers.removeAll()
    }

    deinit {
        for token in keyboardObservers {
            NotificationCenter.default.removeObserver(token)
        }
    }

    func scrollAboveKeyboard(keyboardFrameInScreen: CGRect) {
        guard isFirstResponder, let window else { return }
        guard let scrollView = findEnclosingScrollView() else { return }
        let keyboardInWindow = window.convert(keyboardFrameInScreen, from: nil)
        let fieldInWindow = convert(bounds, to: window)
        let margin: CGFloat = 24
        let visibleBottom = keyboardInWindow.minY - margin

        let keyboardOverlap = max(0, scrollView.convert(scrollView.bounds, to: window).maxY - keyboardInWindow.minY)
        if scrollView.contentInset.bottom < keyboardOverlap + margin {
            var inset = scrollView.contentInset
            inset.bottom = keyboardOverlap + margin
            scrollView.contentInset = inset
            scrollView.verticalScrollIndicatorInsets.bottom = keyboardOverlap + margin
        }

        if fieldInWindow.maxY > visibleBottom {
            let delta = fieldInWindow.maxY - visibleBottom
            let maxOffsetY = max(
                0,
                scrollView.contentSize.height + scrollView.contentInset.bottom - scrollView.bounds.height
            )
            let targetY = min(maxOffsetY, scrollView.contentOffset.y + delta)
            scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: targetY), animated: true)
        }
    }

    private func handleKeyboardNotification(_ notification: Notification) {
        guard let frameValue = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue else {
            return
        }
        lastKnownKeyboardFrame = frameValue.cgRectValue
        guard isFirstResponder else { return }
        scrollAboveKeyboard(keyboardFrameInScreen: lastKnownKeyboardFrame)
    }

    private func handleKeyboardHide() {
        lastKnownKeyboardFrame = .zero
        guard let scrollView = findEnclosingScrollView() else { return }
        if scrollView.contentInset.bottom > 0 {
            var inset = scrollView.contentInset
            inset.bottom = 0
            scrollView.contentInset = inset
            scrollView.verticalScrollIndicatorInsets.bottom = 0
        }
    }

    func scrollAboveLastKeyboardIfKnown() {
        guard scrollIntoViewOnKeyboard, lastKnownKeyboardFrame.height > 0 else { return }
        scrollAboveKeyboard(keyboardFrameInScreen: lastKnownKeyboardFrame)
    }

    private func findEnclosingScrollView() -> UIScrollView? {
        var current = superview
        while let view = current {
            if let scrollView = view as? UIScrollView, !(scrollView is UITextView) {
                return scrollView
            }
            current = view.superview
        }
        return nil
    }
}

struct ExamTextField: UIViewRepresentable {
    let text: String
    let placeholder: String
    let keyboardType: UIKeyboardType
    let isReadOnly: Bool
    let textAlignment: NSTextAlignment
    let isClozeInline: Bool
    let scrollIntoViewOnKeyboard: Bool
    let accessibilityLabelText: String
    let accessibilityIdentifierText: String
    let textColor: UIColor
    let placeholderColor: UIColor
    let controller: ExamTextFieldController?
    let onCommittedChange: (String) -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: ExamTextField
        var lastCommittedText: String

        init(parent: ExamTextField) {
            self.parent = parent
            self.lastCommittedText = parent.text
        }

        func textFieldShouldBeginEditing(_ textField: UITextField) -> Bool {
            !parent.isReadOnly
        }

        func textField(
            _ textField: UITextField,
            shouldChangeCharactersIn range: NSRange,
            replacementString string: String
        ) -> Bool {
            guard !parent.isReadOnly else { return false }
            if string.contains("\u{0000}") {
                let sanitized = AnswerValues.stripNullBytes(string)
                let current = (textField.text ?? "") as NSString
                let updated = current.replacingCharacters(in: range, with: sanitized)
                textField.text = updated
                if let examField = textField as? ExamUITextField {
                    examField.setCaretPosition(utf16Offset: range.location + (sanitized as NSString).length)
                }
                emitCommittedIfReady(textField)
                return false
            }
            return true
        }

        @objc func editingChanged(_ textField: UITextField) {
            emitCommittedIfReady(textField)
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            (textField as? ExamUITextField)?.scrollAboveLastKeyboardIfKnown()
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            emitCommittedIfReady(textField)
        }

        func emitCommittedIfReady(_ textField: UITextField) {
            guard !parent.isReadOnly else { return }
            if let marked = textField.markedTextRange, !marked.isEmpty {
                return
            }
            let raw = textField.text ?? ""
            let cleaned = AnswerValues.stripNullBytes(raw)
            if cleaned != raw {
                let selection = (textField as? ExamUITextField)?.currentSelectionNSRange
                textField.text = cleaned
                if let selection, let examField = textField as? ExamUITextField {
                    examField.setCaretPosition(utf16Offset: min(selection.location, (cleaned as NSString).length))
                }
            }
            if cleaned != lastCommittedText {
                lastCommittedText = cleaned
                parent.onCommittedChange(cleaned)
            }
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> ExamUITextField {
        let field = ExamUITextField()
        field.delegate = context.coordinator
        field.addTarget(context.coordinator, action: #selector(Coordinator.editingChanged(_:)), for: .editingChanged)
        field.autocorrectionType = .no
        field.spellCheckingType = .no
        field.smartQuotesType = .no
        field.smartDashesType = .no
        field.smartInsertDeleteType = .no
        field.inlinePredictionType = .no
        field.autocapitalizationType = .none
        field.borderStyle = .none
        field.backgroundColor = .clear
        field.adjustsFontForContentSizeCategory = false
        field.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        configure(field, coordinator: context.coordinator)
        return field
    }

    func updateUIView(_ uiView: ExamUITextField, context: Context) {
        context.coordinator.parent = self
        configure(uiView, coordinator: context.coordinator)
    }

    static func dismantleUIView(_ uiView: ExamUITextField, coordinator: Coordinator) {
        uiView.removeKeyboardAvoidance()
    }

    private func configure(_ field: ExamUITextField, coordinator: Coordinator) {
        field.onDidUnmarkText = { [weak coordinator, weak field] in
            guard let coordinator, let field else { return }
            coordinator.emitCommittedIfReady(field)
        }
        field.isReadOnly = isReadOnly
        field.scrollIntoViewOnKeyboard = scrollIntoViewOnKeyboard
        if scrollIntoViewOnKeyboard {
            field.installKeyboardAvoidanceIfNeeded()
        } else {
            field.removeKeyboardAvoidance()
        }
        field.textInsets = isClozeInline
            ? UIEdgeInsets(top: 6, left: 8, bottom: 6, right: 8)
            : UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 12)

        if field.keyboardType != keyboardType {
            field.keyboardType = keyboardType
        }
        field.textAlignment = textAlignment
        field.textColor = textColor
        field.accessibilityLabel = accessibilityLabelText
        field.accessibilityIdentifier = accessibilityIdentifierText

        let traits = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(dynamicTypeSize))
        let scaledFont = RichDynamicTypeFont.scaledFont(
            baseSize: 17,
            style: .bold,
            textStyle: .body,
            tabularDigits: true,
            traitCollection: traits
        )
        field.font = scaledFont
        field.attributedPlaceholder = NSAttributedString(
            string: placeholder,
            attributes: [
                .foregroundColor: placeholderColor,
                .font: RichDynamicTypeFont.scaledFont(baseSize: 16, style: [], textStyle: .body, traitCollection: traits)
            ]
        )

        controller?.textField = field
        controller?.onCommittedChange = { [weak coordinator] committed in
            coordinator?.lastCommittedText = committed
            onCommittedChange(committed)
        }

        if isReadOnly && field.isFirstResponder {
            field.resignFirstResponder()
        }

        if field.markedTextRange == nil && field.text != text {
            let previousSelection = field.currentSelectionNSRange
            field.text = text
            coordinator.lastCommittedText = text
            if field.isFirstResponder, let previousSelection {
                field.setCaretPosition(utf16Offset: min(previousSelection.location, (text as NSString).length))
            }
        }
    }
}
