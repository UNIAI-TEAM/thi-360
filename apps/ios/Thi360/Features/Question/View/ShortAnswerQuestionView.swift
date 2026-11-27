import SwiftUI
import UIKit

struct ShortAnswerQuestionView<Attachments: View>: View {
    let item: QuestionItem
    let answerFormat: AnswerFormat?
    let inputHint: String?
    let acceptedAnswers: [String]
    let currentText: String
    let mode: QuestionMode
    let flagged: Bool
    let onAnswer: (AnswerValue) -> Void
    let onFlag: (Bool) -> Void
    let assets: AttemptAssetSource?
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let fixedWidth: CGFloat?
    @ViewBuilder let attachments: () -> Attachments

    @Environment(\.brandPalette) private var brandPalette
    @Environment(\.statusPalette) private var statusPalette
    @StateObject private var fieldController = ExamTextFieldController()

    private var keyboardType: UIKeyboardType {
        switch answerFormat {
        case .decimal:
            .decimalPad
        case .integer, .fraction:
            .numbersAndPunctuation
        default:
            .default
        }
    }

    private var showsNumericHelperRow: Bool {
        !mode.isReview && (answerFormat?.isNumericFormat == true)
    }

    private var showsSymbolBar: Bool {
        !mode.isReview && SymbolInputRules.shouldShowSymbolInput(answerFormat: answerFormat, inputHint: inputHint)
    }

    private var hintParts: [String] {
        var parts: [String] = []
        if let inputHint, !inputHint.isEmpty {
            parts.append(inputHint)
        }
        if let formatHint = answerFormat?.formatHint {
            parts.append(formatHint)
        }
        return parts
    }

    private var fullHintLine: String {
        let note = QuestionStrings.text("question.shortAnswerNote", locale: locale)
        guard !hintParts.isEmpty else { return note }
        let prefix = QuestionStrings.text("question.hintPrefix", locale: locale)
        return "\(prefix) \(hintParts.joined(separator: " · ")). \(note)"
    }

    var body: some View {
        let review = mode.reviewData

        QuestionFrame(
            item: item,
            mode: mode,
            flagged: flagged,
            onFlag: onFlag,
            assets: assets,
            formulaEngine: formulaEngine,
            locale: locale,
            fixedWidth: fixedWidth,
            attachments: attachments
        ) {
            VStack(alignment: .leading, spacing: 10) {
                Text(QuestionStrings.text("question.answerLabel", locale: locale))
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(brandPalette.textColor)
                    .fixedSize(horizontal: false, vertical: true)

                HStack(alignment: .center, spacing: 10) {
                    ExamTextField(
                        text: currentText,
                        placeholder: QuestionStrings.text("question.answerPlaceholder", locale: locale),
                        keyboardType: keyboardType,
                        isReadOnly: !mode.isAnswering,
                        textAlignment: .left,
                        isClozeInline: false,
                        scrollIntoViewOnKeyboard: false,
                        accessibilityLabelText: QuestionStrings.text("question.answerLabel", locale: locale),
                        accessibilityIdentifierText: "question.\(item.id).shortAnswerField",
                        textColor: UIColor(hex: brandPalette.text),
                        placeholderColor: UIColor(hex: brandPalette.textMuted),
                        controller: fieldController,
                        onCommittedChange: { nextText in
                            onAnswer(AnswerValues.shortAnswer(text: nextText))
                        }
                    )
                    .frame(minHeight: 48)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(mode.isAnswering ? brandPalette.bgSurfaceColor : brandPalette.bgSubtleColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .strokeBorder(brandPalette.borderStrongColor, lineWidth: 1)
                    )

                    if let outcome = review?.outcome {
                        ZStack {
                            Circle()
                                .fill(brandPalette.bgSurfaceColor)
                                .frame(width: 34, height: 34)
                            Circle()
                                .strokeBorder(brandPalette.borderColor, lineWidth: 1)
                                .frame(width: 34, height: 34)
                            PartOutcomeBadge(outcome: outcome, locale: locale)
                        }
                    }
                }

                if showsNumericHelperRow {
                    NumericHelperKeyRow(
                        itemId: item.id,
                        isDisabled: mode.isLocked,
                        locale: locale,
                        onInsert: { insertion in
                            fieldController.insertAtCursor(insertion)
                        }
                    )
                }

                if showsSymbolBar {
                    SymbolInputBarView(
                        itemId: item.id,
                        isDisabled: mode.isLocked,
                        locale: locale,
                        onInsert: { insertion in
                            fieldController.insertAtCursor(insertion)
                        }
                    )
                }

                if !mode.isReview {
                    Text(fullHintLine)
                        .font(.caption)
                        .foregroundStyle(brandPalette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("question.\(item.id).shortAnswerHint")
                        .questionProbe(id: "question.\(item.id).shortAnswerHint", label: fullHintLine)
                }

                if let review {
                    if review.outcome == .blank || currentText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Text(QuestionStrings.text("question.notAnswered", locale: locale))
                            .font(.caption)
                            .foregroundStyle(brandPalette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    if review.showAnswers && !acceptedAnswers.isEmpty {
                        reviewAcceptedLine(review: review)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func reviewAcceptedLine(review: ItemReview) -> some View {
        let prefix = QuestionStrings.text("question.correctAnswerPrefix", locale: locale)
        let joinedAnswers = acceptedAnswers.joined(separator: " · ")
        let pointsSuffix: String = {
            guard let earned = review.earned else { return "" }
            let pointsText = QuestionStrings.pointsText(earned: earned, max: review.max ?? item.points, locale: locale)
            return " (\(pointsText))"
        }()

        let lineText = "\(prefix) \(joinedAnswers)\(pointsSuffix)"
        Text(lineText)
            .font(.caption)
            .foregroundStyle(brandPalette.textSecondaryColor)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityIdentifier("question.\(item.id).shortAnswerExpected")
            .questionProbe(id: "question.\(item.id).shortAnswerExpected", label: lineText)
    }
}

struct NumericHelperKeyRow: View {
    let itemId: String
    let isDisabled: Bool
    let locale: AppLocale
    let onInsert: (String) -> Void

    @Environment(\.brandPalette) private var brandPalette

    var body: some View {
        HStack(spacing: 8) {
            ForEach(SymbolInputRules.numericHelperKeys) { key in
                Button {
                    guard !isDisabled else { return }
                    onInsert(key.insert)
                } label: {
                    Text(key.label)
                        .font(.title3.weight(.semibold).monospaced())
                        .foregroundStyle(brandPalette.textColor)
                        .frame(minWidth: 44, maxWidth: .infinity, minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(brandPalette.bgSubtleColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(brandPalette.borderStrongColor, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .disabled(isDisabled)
                .accessibilityLabel(QuestionStrings.text(key.accessibilityKey, locale: locale))
                .accessibilityIdentifier("question.\(itemId).numericKey.\(key.id)")
                .questionProbe(
                    id: "question.\(itemId).numericKey.\(key.id)",
                    label: QuestionStrings.text(key.accessibilityKey, locale: locale),
                    isButton: true,
                    isEnabled: !isDisabled
                )
            }
        }
        .opacity(isDisabled ? 0.45 : 1.0)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("question.\(itemId).numericKeyRow")
    }
}

struct SymbolInputBarView: View {
    let itemId: String
    let isDisabled: Bool
    let locale: AppLocale
    let onInsert: (String) -> Void

    @Environment(\.brandPalette) private var brandPalette

    var body: some View {
        WrappingHStackLayout(horizontalSpacing: 6, verticalSpacing: 6) {
            ForEach(SymbolInputRules.defaultIonSymbols) { key in
                Button {
                    guard !isDisabled else { return }
                    onInsert(key.insert)
                } label: {
                    Text(key.label)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(brandPalette.textColor)
                        .frame(minWidth: 44, minHeight: 44)
                        .padding(.horizontal, 6)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(brandPalette.bgSubtleColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(brandPalette.borderStrongColor, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .disabled(isDisabled)
                .accessibilityLabel(QuestionStrings.text(key.accessibilityKey, locale: locale))
                .accessibilityIdentifier("question.\(itemId).symbolKey.\(key.id)")
                .questionProbe(
                    id: "question.\(itemId).symbolKey.\(key.id)",
                    label: QuestionStrings.text(key.accessibilityKey, locale: locale),
                    isButton: true,
                    isEnabled: !isDisabled
                )
            }
        }
        .opacity(isDisabled ? 0.45 : 1.0)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(QuestionStrings.text("question.symbolInput.groupLabel", locale: locale))
        .accessibilityIdentifier("question.\(itemId).symbolInputBar")
    }
}
