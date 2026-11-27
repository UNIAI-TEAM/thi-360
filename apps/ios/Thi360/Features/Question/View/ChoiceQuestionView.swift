import SwiftUI

struct ChoiceQuestionView<Attachments: View>: View {
    let item: QuestionItem
    let options: [ChoiceOption]
    let optionOrder: [String]?
    let isMultiple: Bool
    let selectedIds: [String]
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

    private var orderedOptions: [ChoiceOption] {
        OptionOrder.order(options, by: optionOrder)
    }

    private var showReviewAnswers: Bool {
        guard let review = mode.reviewData else { return false }
        return review.showAnswers && options.contains { $0.correct != nil }
    }

    private var optionContentWidth: CGFloat? {
        fixedWidth.map { max($0 - 84, 80) }
    }

    var body: some View {
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
                if isMultiple && !mode.isReview {
                    Text(QuestionStrings.text("question.chooseMultiple", locale: locale))
                        .font(.caption)
                        .foregroundStyle(brandPalette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                }

                ForEach(Array(orderedOptions.enumerated()), id: \.element.id) { index, option in
                    optionRow(option: option, index: index)
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("question.\(item.id).options")
        }
    }

    @ViewBuilder
    private func optionRow(option: ChoiceOption, index: Int) -> some View {
        let letter = OptionOrder.optionLetter(at: index)
        let isChecked = selectedIds.contains(option.id)
        let isCorrect = option.correct == true
        let isReview = mode.isReview
        let showAnswers = showReviewAnswers

        let borderColor: Color = {
            if isReview {
                if isChecked && isCorrect { return statusPalette.successColor }
                if isChecked && !isCorrect && showAnswers { return brandPalette.dangerTextColor }
                if isChecked { return brandPalette.primaryTextColor }
                if isCorrect && showAnswers { return statusPalette.successColor }
                return brandPalette.borderColor
            }
            return isChecked ? brandPalette.primaryTextColor : brandPalette.borderColor
        }()

        let fillColor: Color = {
            if isReview {
                if isChecked && isCorrect { return statusPalette.successBgColor }
                if isChecked && !isCorrect && showAnswers { return brandPalette.dangerBgColor }
                if isChecked { return brandPalette.primaryContainerColor }
                if isCorrect && showAnswers { return statusPalette.successBgColor }
                return brandPalette.bgSurfaceColor
            }
            return isChecked ? brandPalette.primaryContainerColor : brandPalette.bgSurfaceColor
        }()

        let isDashed = isReview && isChecked && !isCorrect && showAnswers
        let borderWidth: CGFloat = (isChecked || (isReview && isCorrect && showAnswers)) ? 2 : 1

        let rowContent = VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 10) {
                optionMarker(isChecked: isChecked, isCorrect: isCorrect, isReview: isReview, showAnswers: showAnswers)
                    .padding(.top, 2)

                Text("\(letter).")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(brandPalette.textColor)
                    .fixedSize()

                QuestionRichText(
                    document: RichHTMLParser.parse(option.content),
                    assets: assets,
                    mode: mode.richContentMode,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: optionContentWidth
                )
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            if isReview {
                reviewTags(optionId: option.id, isChecked: isChecked, isCorrect: isCorrect, showAnswers: showAnswers)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(fillColor))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(
                    borderColor,
                    style: isDashed
                        ? StrokeStyle(lineWidth: borderWidth, dash: [6, 3])
                        : StrokeStyle(lineWidth: borderWidth)
                )
        )
        .contentShape(Rectangle())

        if mode.isAnswering {
            Button {
                handleTap(optionId: option.id)
            } label: {
                rowContent
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(isChecked ? [.isSelected] : [])
            .accessibilityIdentifier("question.\(item.id).option.\(option.id)")
            .questionProbe(
                id: "question.\(item.id).option.\(option.id)",
                label: "\(letter).",
                isButton: true,
                isEnabled: true,
                isSelected: isChecked,
                action: { handleTap(optionId: option.id) }
            )
        } else {
            rowContent
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("question.\(item.id).option.\(option.id)")
                .questionProbe(
                    id: "question.\(item.id).option.\(option.id)",
                    label: "\(letter).",
                    isButton: false,
                    isEnabled: false,
                    isSelected: isChecked
                )
        }
    }

    @ViewBuilder
    private func optionMarker(isChecked: Bool, isCorrect: Bool, isReview: Bool, showAnswers: Bool) -> some View {
        let activeColor: Color = {
            if isReview {
                if showAnswers {
                    return isCorrect ? statusPalette.successColor : brandPalette.dangerTextColor
                }
                return brandPalette.primaryFillColor
            }
            return brandPalette.primaryFillColor
        }()

        let strokeColor: Color = {
            if isReview && showAnswers && isCorrect {
                return statusPalette.successColor
            }
            return isChecked ? activeColor : brandPalette.borderStrongColor
        }()

        if isMultiple {
            ZStack {
                RoundedRectangle(cornerRadius: 5, style: .continuous)
                    .fill(isChecked ? activeColor : Color.clear)
                    .frame(width: 22, height: 22)
                RoundedRectangle(cornerRadius: 5, style: .continuous)
                    .strokeBorder(strokeColor, lineWidth: 2)
                    .frame(width: 22, height: 22)
                if isChecked {
                    Image(systemName: "checkmark")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(brandPalette.onPrimaryColor)
                }
            }
            .accessibilityHidden(true)
        } else {
            ZStack {
                Circle()
                    .strokeBorder(strokeColor, lineWidth: 2)
                    .frame(width: 22, height: 22)
                if isChecked {
                    Circle()
                        .fill(activeColor)
                        .frame(width: 11, height: 11)
                }
            }
            .accessibilityHidden(true)
        }
    }

    @ViewBuilder
    private func reviewTags(optionId: String, isChecked: Bool, isCorrect: Bool, showAnswers: Bool) -> some View {
        let hasAnyTag = isChecked || (showAnswers && isCorrect)
        if hasAnyTag {
            WrappingHStackLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                if isChecked {
                    let isNeutral = isCorrect || !showAnswers
                    let tagLabel = QuestionStrings.text(
                        isNeutral ? "question.yourPickTag" : "question.yourPickWrongTag",
                        locale: locale
                    )
                    ReviewTag(
                        label: tagLabel,
                        tone: isNeutral ? .neutral : .wrong
                    )
                    .questionProbe(id: "question.\(item.id).option.\(optionId).pickTag", label: tagLabel)
                }
                if showAnswers && isCorrect {
                    let correctLabel = QuestionStrings.text("question.correctAnswerTag", locale: locale)
                    ReviewTag(
                        label: correctLabel,
                        tone: .correct
                    )
                    .questionProbe(id: "question.\(item.id).option.\(optionId).correctTag", label: correctLabel)
                }
                if showAnswers && isCorrect && !isChecked && isMultiple {
                    let missedLabel = QuestionStrings.text("question.notPickedTag", locale: locale)
                    ReviewTag(
                        label: missedLabel,
                        tone: .partial
                    )
                    .questionProbe(id: "question.\(item.id).option.\(optionId).missedTag", label: missedLabel)
                }
            }
            .padding(.leading, 32)
        }
    }

    private func handleTap(optionId: String) {
        guard mode.isAnswering else { return }
        if isMultiple {
            var next = selectedIds
            if let existing = next.firstIndex(of: optionId) {
                next.remove(at: existing)
            } else {
                next.append(optionId)
            }
            onAnswer(AnswerValues.multipleChoice(item: item, optionIds: next))
        } else {
            let nextId: String? = (selectedIds.first == optionId) ? nil : optionId
            onAnswer(AnswerValues.singleChoice(item: item, optionId: nextId))
        }
    }
}
