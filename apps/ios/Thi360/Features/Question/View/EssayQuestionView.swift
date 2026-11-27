import SwiftUI

enum EssayReviewMetrics {
    static let collapseCharThreshold = 600
    static let collapsedLineCount = 6

    static func plainCharacterCount(html: String) -> Int {
        WordCounter.textForWordCount(html: html)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .count
    }

    static func shouldCollapse(html: String) -> Bool {
        plainCharacterCount(html: html) > collapseCharThreshold
    }
}

struct EssayQuestionView<Attachments: View>: View {
    let item: QuestionItem
    let wordLimit: Int?
    let currentHtml: String
    let currentWordCount: Int?
    let mode: QuestionMode
    let flagged: Bool
    let isLoadingEditor: Bool
    let externalController: EssayEditorController?
    let onAnswer: (AnswerValue) -> Void
    let onFlag: (Bool) -> Void
    let assets: AttemptAssetSource?
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let fixedWidth: CGFloat?
    @ViewBuilder let attachments: () -> Attachments

    @Environment(\.brandPalette) private var brandPalette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    @State private var localController = EssayEditorController()
    @State private var isEssayExpanded = false

    private var controller: EssayEditorController {
        externalController ?? localController
    }

    private var computedWordCount: Int {
        if let essayCount = mode.reviewData?.essay?.wordCount {
            return essayCount
        }
        if let currentWordCount, !currentHtml.isEmpty {
            return currentWordCount
        }
        return WordCounter.count(html: currentHtml)
    }

    var body: some View {
        QuestionFrame(
            item: item,
            mode: mode,
            flagged: flagged,
            onFlag: onFlag,
            manualTag: mode.isReview,
            assets: assets,
            formulaEngine: formulaEngine,
            locale: locale,
            fixedWidth: fixedWidth,
            attachments: attachments
        ) {
            if let review = mode.reviewData {
                reviewContent(review: review)
            } else {
                answeringContent
            }
        }
    }

    private var answeringContent: some View {
        let count = computedWordCount
        let editorLabelText = QuestionStrings.text("question.essayEditorLabel", locale: locale)

        return VStack(alignment: .leading, spacing: 10) {
            Text(editorLabelText)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(brandPalette.textColor)
                .accessibilityIdentifier("question.\(item.id).essayEditorLabel")
                .questionProbe(id: "question.\(item.id).essayEditorLabel", label: editorLabelText)

            EssayEditorView(
                itemId: item.id,
                html: currentHtml,
                isLocked: mode.isLocked,
                isLoading: isLoadingEditor,
                locale: locale,
                controller: controller,
                onCommittedChange: { html, words in
                    onAnswer(AnswerValues.essay(html: html, wordCount: words))
                }
            )

            WrappingHStackLayout(horizontalSpacing: 10, verticalSpacing: 6) {
                let countText = QuestionStrings.essayWordCount(count: count, min: wordLimit, locale: locale)
                Text(countText)
                    .font(.caption.weight(.semibold).monospacedDigit())
                    .foregroundStyle(brandPalette.textSecondaryColor)
                    .accessibilityIdentifier("question.\(item.id).wordCount")
                    .questionProbe(id: "question.\(item.id).wordCount", label: countText)

                if let limit = wordLimit {
                    if count < limit {
                        let warnText = QuestionStrings.wordsNeeded(count: limit - count, locale: locale)
                        HStack(spacing: 4) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.caption2)
                            Text(warnText)
                                .font(.caption.weight(.semibold))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .foregroundStyle(statusPalette.warningColor)
                        .accessibilityIdentifier("question.\(item.id).wordLimitWarning")
                        .questionProbe(id: "question.\(item.id).wordLimitWarning", label: warnText)
                    } else {
                        let metText = QuestionStrings.text("question.wordLimitMet", locale: locale)
                        HStack(spacing: 4) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.caption2)
                            Text(metText)
                                .font(.caption.weight(.semibold))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .foregroundStyle(statusPalette.successColor)
                        .accessibilityIdentifier("question.\(item.id).wordLimitMet")
                        .questionProbe(id: "question.\(item.id).wordLimitMet", label: metText)
                    }
                }
            }

            Text(QuestionStrings.text("question.essayNote", locale: locale))
                .font(.caption)
                .foregroundStyle(brandPalette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    @ViewBuilder
    private func reviewContent(review: ItemReview) -> some View {
        if review.outcome == .blank {
            let notAnsweredText = QuestionStrings.text("question.notAnswered", locale: locale)
            Text(notAnsweredText)
                .font(.subheadline)
                .foregroundStyle(brandPalette.textSecondaryColor)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(brandPalette.bgSubtleColor))
                .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(brandPalette.borderColor, lineWidth: 1))
                .accessibilityIdentifier("question.\(item.id).essayBlank")
                .questionProbe(id: "question.\(item.id).essayBlank", label: notAnsweredText)
        } else {
            nonBlankReviewContent(review: review)
        }
    }

    @ViewBuilder
    private func nonBlankReviewContent(review: ItemReview) -> some View {
        let essayDetail = review.essay
        let gradingState = essayDetail?.state ?? (review.outcome == .pendingReview ? .pending : .graded)
        let isBlank = EssayReviewMetrics.plainCharacterCount(html: currentHtml) == 0
        let needsCollapse = EssayReviewMetrics.shouldCollapse(html: currentHtml)
        let bodyFont = UIFont.preferredFont(
            forTextStyle: .body,
            compatibleWith: UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(dynamicTypeSize))
        )
        let collapsedMaxHeight = ceil(bodyFont.lineHeight * CGFloat(EssayReviewMetrics.collapsedLineCount) + 12)
        let yourEssayText = QuestionStrings.text("question.yourEssayLabel", locale: locale)

        VStack(alignment: .leading, spacing: 12) {
            if gradingState == .regrading {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.triangle.2.circlepath")
                        .font(.subheadline.weight(.semibold))
                    Text(QuestionStrings.text("question.regrading", locale: locale))
                        .font(.subheadline.weight(.semibold))
                }
                .foregroundStyle(statusPalette.warningColor)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(statusPalette.warningBgColor))
                .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(statusPalette.warningColor, lineWidth: 1))
                .accessibilityIdentifier("question.\(item.id).regradingBanner")
                .questionProbe(
                    id: "question.\(item.id).regradingBanner",
                    label: QuestionStrings.text("question.regrading", locale: locale)
                )
            }

            if gradingState == .pending {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "clock")
                        .font(.subheadline.weight(.semibold))
                        .padding(.top, 2)
                    Text(QuestionStrings.text("question.pendingReviewNote", locale: locale))
                        .font(.subheadline)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .foregroundStyle(statusPalette.infoColor)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(statusPalette.infoBgColor))
                .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(statusPalette.infoColor, lineWidth: 1))
                .accessibilityIdentifier("question.\(item.id).pendingReviewNote")
                .questionProbe(
                    id: "question.\(item.id).pendingReviewNote",
                    label: QuestionStrings.text("question.pendingReviewNote", locale: locale)
                )
            }

            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .center, spacing: 8) {
                    Text(yourEssayText)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(brandPalette.textMutedColor)
                        .accessibilityIdentifier("question.\(item.id).yourEssayLabel")
                        .questionProbe(id: "question.\(item.id).yourEssayLabel", label: yourEssayText)
                    Spacer()
                    if !isBlank {
                        let reviewCountText = QuestionStrings.essayWordCount(
                            count: computedWordCount,
                            min: wordLimit,
                            locale: locale
                        )
                        Text(reviewCountText)
                            .font(.caption.weight(.semibold).monospacedDigit())
                            .foregroundStyle(brandPalette.textSecondaryColor)
                            .accessibilityIdentifier("question.\(item.id).reviewWordCount")
                            .questionProbe(id: "question.\(item.id).reviewWordCount", label: reviewCountText)
                    }
                }

                if isBlank {
                    let notAnsweredText = QuestionStrings.text("question.notAnswered", locale: locale)
                    Text(notAnsweredText)
                        .font(.subheadline)
                        .foregroundStyle(brandPalette.textMutedColor)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(brandPalette.bgSubtleColor))
                        .accessibilityIdentifier("question.\(item.id).essayBlank")
                        .questionProbe(id: "question.\(item.id).essayBlank", label: notAnsweredText)
                } else {
                    VStack(alignment: .leading, spacing: 8) {
                        QuestionRichText(
                            document: RichHTMLParser.parse(currentHtml),
                            assets: assets,
                            mode: .review,
                            formulaEngine: formulaEngine,
                            locale: locale,
                            fixedWidth: fixedWidth.map { max($0 - 28, 80) }
                        )
                        .frame(
                            maxWidth: .infinity,
                            maxHeight: (needsCollapse && !isEssayExpanded) ? collapsedMaxHeight : nil,
                            alignment: .topLeading
                        )
                        .clipped()
                        .accessibilityIdentifier("question.\(item.id).essayReviewBody")

                        if needsCollapse {
                            let toggleLabel = QuestionStrings.text(
                                isEssayExpanded ? "question.hideFullEssay" : "question.showFullEssay",
                                locale: locale
                            )
                            Button {
                                isEssayExpanded.toggle()
                            } label: {
                                Text(toggleLabel)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(brandPalette.primaryTextColor)
                                .frame(minHeight: 44, alignment: .leading)
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("question.\(item.id).essayToggle")
                            .questionProbe(
                                id: "question.\(item.id).essayToggle",
                                label: toggleLabel,
                                isButton: true,
                                isEnabled: true,
                                action: { isEssayExpanded.toggle() }
                            )
                        }

                        if let limit = wordLimit, computedWordCount < limit {
                            let warnText = QuestionStrings.wordsNeeded(count: limit - computedWordCount, locale: locale)
                            Text(warnText)
                                .font(.caption.weight(.medium))
                                .foregroundStyle(statusPalette.warningColor)
                                .fixedSize(horizontal: false, vertical: true)
                                .accessibilityIdentifier("question.\(item.id).reviewWordsNeeded")
                                .questionProbe(id: "question.\(item.id).reviewWordsNeeded", label: warnText)
                        }
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(brandPalette.bgSurfaceColor))
                    .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).strokeBorder(brandPalette.borderColor, lineWidth: 1))
                }
            }

            if gradingState != .pending, let criteria = essayDetail?.criteria, !criteria.isEmpty {
                criteriaTable(criteria: criteria)
            }

            if gradingState != .pending, let comment = essayDetail?.comment, !comment.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text(QuestionStrings.text("question.gradingCommentLabel", locale: locale))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(brandPalette.textMutedColor)
                    Text(comment)
                        .font(.subheadline)
                        .foregroundStyle(brandPalette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(brandPalette.bgSubtleColor))
                .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(brandPalette.borderColor, lineWidth: 1))
                .accessibilityIdentifier("question.\(item.id).graderComment")
                .questionProbe(
                    id: "question.\(item.id).graderComment",
                    label: "\(QuestionStrings.text("question.gradingCommentLabel", locale: locale)): \(comment)"
                )
            }
        }
    }

    private func criteriaTable(criteria: [EssayCriterionResult]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(QuestionStrings.text("question.criteriaLabel", locale: locale))
                .font(.caption.weight(.semibold))
                .foregroundStyle(brandPalette.textMutedColor)

            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .center, spacing: 8) {
                    Text(QuestionStrings.text("question.criterionCol", locale: locale))
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(QuestionStrings.text("question.pointsCol", locale: locale))
                        .frame(width: 110, alignment: .trailing)
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(brandPalette.textMutedColor)
                Divider()

                ForEach(Array(criteria.enumerated()), id: \.offset) { _, criterion in
                    HStack(alignment: .top, spacing: 8) {
                        Text(criterion.description)
                            .font(.subheadline)
                            .foregroundStyle(brandPalette.textColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        Text(
                            criterion.earned.map { QuestionStrings.pointsText(earned: $0, max: criterion.max, locale: locale) }
                                ?? "— / \(OptionOrder.formatScore(criterion.max, locale: locale))"
                        )
                        .font(.subheadline.monospacedDigit())
                        .foregroundStyle(brandPalette.textColor)
                        .frame(width: 110, alignment: .trailing)
                    }
                    .padding(.vertical, 4)
                    Divider()
                }
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(brandPalette.bgSurfaceColor))
            .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(brandPalette.borderColor, lineWidth: 1))
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("question.\(item.id).criteriaTable")
        .questionProbe(
            id: "question.\(item.id).criteriaTable",
            label: ([QuestionStrings.text("question.criteriaLabel", locale: locale)] + criteria.map(\.description)).joined(separator: " | ")
        )
    }
}
