import SwiftUI
import UIKit

struct ClozeQuestionView<Attachments: View>: View {
    let item: QuestionItem
    let passage: String
    let blanks: [ClozeBlank]
    let blankNumberStart: Int?
    let currentBlanks: [String: String]
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
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var parsedDocument: RichDocument {
        RichHTMLParser.parse(passage, options: RichParseOptions(slotMarkers: true))
    }

    private var filledCount: Int {
        blanks.filter { !(currentBlanks[$0.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }.count
    }

    private var effectiveStartNumber: Int {
        blankNumberStart ?? item.displayNo
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
            VStack(alignment: .leading, spacing: 12) {
                if !mode.isReview {
                    WrappingHStackLayout(horizontalSpacing: 10, verticalSpacing: 4) {
                        Text(QuestionStrings.text("question.clozeHelp", locale: locale))
                            .font(.caption)
                            .foregroundStyle(brandPalette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)

                        let counterText = QuestionStrings.clozeFilled(filled: filledCount, total: blanks.count, locale: locale)
                        Text(counterText)
                            .font(.caption.weight(.semibold).monospacedDigit())
                            .foregroundStyle(brandPalette.textSecondaryColor)
                            .accessibilityIdentifier("question.\(item.id).clozeCounter")
                            .questionProbe(id: "question.\(item.id).clozeCounter", label: counterText)
                    }
                }

                QuestionRichText(
                    document: parsedDocument,
                    assets: assets,
                    mode: mode.richContentMode,
                    slots: buildSlots(review: review),
                    blankNumberStart: effectiveStartNumber,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: fixedWidth
                )
                .accessibilityIdentifier("question.\(item.id).clozePassage")

                if !mode.isReview {
                    Text(QuestionStrings.text("question.clozeBlankNote", locale: locale))
                        .font(.caption)
                        .foregroundStyle(brandPalette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if let review {
                    clozeReviewTable(review: review)
                }
            }
        }
    }

    private func buildSlots(review: ItemReview?) -> [String: () -> AnyView] {
        var map: [String: () -> AnyView] = [:]
        for (index, blank) in blanks.enumerated() {
            let ordinal = index + 1
            let questionNo = effectiveStartNumber + index
            let value = currentBlanks[blank.id] ?? ""
            let part = review?.detail[blank.id]
            let ariaLabel = QuestionStrings.clozeBlankAria(questionNo: questionNo, ordinal: ordinal, locale: locale)
            let placeholder = QuestionStrings.clozePlaceholder(ordinal: ordinal, locale: locale)
            let fieldIdentifier = "question.\(item.id).cloze.\(blank.id)"

            if mode.isReview {
                map[blank.id] = {
                    AnyView(
                        ClozeReviewSlotView(
                            text: value,
                            placeholder: placeholder,
                            part: part,
                            ariaLabel: ariaLabel,
                            identifier: fieldIdentifier,
                            locale: locale,
                            brandPalette: brandPalette,
                            statusPalette: statusPalette
                        )
                    )
                }
            } else {
                map[blank.id] = {
                    AnyView(
                        ExamTextField(
                            text: value,
                            placeholder: placeholder,
                            keyboardType: .default,
                            isReadOnly: mode.isLocked,
                            textAlignment: .center,
                            isClozeInline: true,
                            scrollIntoViewOnKeyboard: true,
                            accessibilityLabelText: ariaLabel,
                            accessibilityIdentifierText: fieldIdentifier,
                            textColor: UIColor(hex: brandPalette.text),
                            placeholderColor: UIColor(hex: brandPalette.textMuted),
                            controller: nil,
                            onCommittedChange: { nextText in
                                var updated = currentBlanks
                                updated[blank.id] = nextText
                                onAnswer(AnswerValues.cloze(item: item, blanks: updated))
                            }
                        )
                        .frame(width: 104, height: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(mode.isLocked ? brandPalette.bgSubtleColor : brandPalette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .strokeBorder(brandPalette.borderStrongColor, lineWidth: 1.5)
                        )
                    )
                }
            }
        }
        return map
    }

    @ViewBuilder
    private func clozeReviewTable(review: ItemReview) -> some View {
        let showAnswers = review.showAnswers
        let hasPoints = blanks.contains { review.detail[$0.id]?.earned != nil }
        let useTable = horizontalSizeClass == .regular

        let reviewSummaryText: String = {
            var parts: [String] = []
            if showAnswers {
                parts.append(QuestionStrings.text("question.acceptedCol", locale: locale))
            }
            for (index, blank) in blanks.enumerated() {
                parts.append(QuestionStrings.questionLabel(no: effectiveStartNumber + index, locale: locale))
                let filledText = currentBlanks[blank.id] ?? ""
                if !filledText.isEmpty { parts.append(filledText) }
                if showAnswers {
                    parts.append(blank.answers.joined(separator: " · "))
                }
            }
            return parts.joined(separator: " | ")
        }()

        VStack(alignment: .leading, spacing: 8) {
            if useTable {
                clozeTableHeader(showAnswers: showAnswers, hasPoints: hasPoints)
            }

            ForEach(Array(blanks.enumerated()), id: \.element.id) { index, blank in
                let number = effectiveStartNumber + index
                let part = review.detail[blank.id]
                let filledText = currentBlanks[blank.id] ?? ""
                if useTable {
                    clozeTableRow(
                        number: number,
                        blank: blank,
                        part: part,
                        filledText: filledText,
                        showAnswers: showAnswers,
                        hasPoints: hasPoints
                    )
                } else {
                    clozeStackedRow(
                        number: number,
                        blank: blank,
                        part: part,
                        filledText: filledText,
                        showAnswers: showAnswers,
                        hasPoints: hasPoints
                    )
                }
            }
        }
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(brandPalette.bgSurfaceColor))
        .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(brandPalette.borderColor, lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("question.\(item.id).clozeReviewTable")
        .questionProbe(id: "question.\(item.id).clozeReviewTable", label: reviewSummaryText)
    }

    @ViewBuilder
    private func clozeTableHeader(showAnswers: Bool, hasPoints: Bool) -> some View {
        HStack(alignment: .center, spacing: 8) {
            Text(QuestionStrings.text("question.itemCol", locale: locale))
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(QuestionStrings.text("question.youChoseCol", locale: locale))
                .frame(maxWidth: .infinity, alignment: .leading)
            if showAnswers {
                Text(QuestionStrings.text("question.acceptedCol", locale: locale))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if hasPoints {
                Text(QuestionStrings.text("question.pointsCol", locale: locale))
                    .frame(width: 96, alignment: .trailing)
            }
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(brandPalette.textMutedColor)
        .padding(.bottom, 4)
        Divider()
    }

    @ViewBuilder
    private func clozeTableRow(
        number: Int,
        blank: ClozeBlank,
        part: PartResult?,
        filledText: String,
        showAnswers: Bool,
        hasPoints: Bool
    ) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text(QuestionStrings.questionLabel(no: number, locale: locale))
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .foregroundStyle(brandPalette.textColor)
                .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 6) {
                if let part {
                    PartOutcomeBadge(outcome: part.outcome, locale: locale)
                }
                Text(filledText.isEmpty ? QuestionStrings.text("question.blankFilledTag", locale: locale) : filledText)
                    .foregroundStyle(filledText.isEmpty ? brandPalette.textMutedColor : brandPalette.textColor)
            }
            .font(.subheadline)
            .frame(maxWidth: .infinity, alignment: .leading)

            if showAnswers {
                Text(blank.answers.joined(separator: " · "))
                    .font(.subheadline)
                    .foregroundStyle(brandPalette.textColor)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            if hasPoints {
                Text(pointsLabel(part: part, fallbackMax: blank.points ?? 1))
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(brandPalette.textColor)
                    .frame(width: 96, alignment: .trailing)
            }
        }
        .padding(.vertical, 4)
        Divider()
    }

    @ViewBuilder
    private func clozeStackedRow(
        number: Int,
        blank: ClozeBlank,
        part: PartResult?,
        filledText: String,
        showAnswers: Bool,
        hasPoints: Bool
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(QuestionStrings.questionLabel(no: number, locale: locale))
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .foregroundStyle(brandPalette.textColor)

            HStack(alignment: .center, spacing: 6) {
                Text("\(QuestionStrings.text("question.youChoseCol", locale: locale)):")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(brandPalette.textMutedColor)
                if let part {
                    PartOutcomeBadge(outcome: part.outcome, locale: locale)
                }
                Text(filledText.isEmpty ? QuestionStrings.text("question.blankFilledTag", locale: locale) : filledText)
                    .font(.subheadline)
                    .foregroundStyle(filledText.isEmpty ? brandPalette.textMutedColor : brandPalette.textColor)
            }

            if showAnswers {
                HStack(alignment: .top, spacing: 6) {
                    Text("\(QuestionStrings.text("question.acceptedCol", locale: locale)):")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(brandPalette.textMutedColor)
                    Text(blank.answers.joined(separator: " · "))
                        .font(.subheadline)
                        .foregroundStyle(brandPalette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            if hasPoints {
                HStack(alignment: .center, spacing: 6) {
                    Text("\(QuestionStrings.text("question.pointsCol", locale: locale)):")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(brandPalette.textMutedColor)
                    Text(pointsLabel(part: part, fallbackMax: blank.points ?? 1))
                        .font(.subheadline.monospacedDigit())
                        .foregroundStyle(brandPalette.textColor)
                }
            }
        }
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        Divider()
    }

    private func pointsLabel(part: PartResult?, fallbackMax: Double) -> String {
        guard let earned = part?.earned else { return "—" }
        return QuestionStrings.pointsText(earned: earned, max: part?.max ?? fallbackMax, locale: locale)
    }
}

private struct ClozeReviewSlotView: View {
    let text: String
    let placeholder: String
    let part: PartResult?
    let ariaLabel: String
    let identifier: String
    let locale: AppLocale
    let brandPalette: BrandPalette
    let statusPalette: StatusPalette

    private var borderColor: Color {
        switch part?.outcome {
        case .correct: statusPalette.successColor
        case .partial: statusPalette.warningColor
        case .wrong: brandPalette.dangerTextColor
        default: brandPalette.borderStrongColor
        }
    }

    private var fillColor: Color {
        switch part?.outcome {
        case .correct: statusPalette.successBgColor
        case .partial: statusPalette.warningBgColor
        case .wrong: brandPalette.dangerBgColor
        default: brandPalette.bgSubtleColor
        }
    }

    var body: some View {
        let slotA11yLabel = part.map {
            "\(ariaLabel), \(text.isEmpty ? placeholder : text), \(QuestionStrings.outcomeLabel($0.outcome, locale: locale))"
        } ?? "\(ariaLabel), \(text.isEmpty ? placeholder : text)"

        return ZStack(alignment: .topTrailing) {
            Text(text.isEmpty ? placeholder : text)
                .font(.body.weight(.semibold))
                .foregroundStyle(text.isEmpty ? brandPalette.textMutedColor : brandPalette.textColor)
                .lineLimit(1)
                .frame(minWidth: 96, minHeight: 36)
                .padding(.horizontal, 10)
                .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(fillColor))
                .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).strokeBorder(borderColor, lineWidth: 2))

            if let part {
                PartOutcomeBadge(outcome: part.outcome, locale: locale)
                    .padding(3)
                    .background(Circle().fill(brandPalette.bgSurfaceColor))
                    .offset(x: 6, y: -6)
            }
        }
        .padding(.top, 4)
        .padding(.trailing, 4)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(slotA11yLabel)
        .accessibilityIdentifier(identifier)
        .questionProbe(id: identifier, label: slotA11yLabel)
    }
}
