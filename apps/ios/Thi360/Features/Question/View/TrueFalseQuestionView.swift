import SwiftUI

struct TrueFalseQuestionView<Attachments: View>: View {
    let item: QuestionItem
    let statements: [TrueFalseStatement]
    let scoringMethod: TrueFalseScoringMethod?
    let currentStatements: [String: Bool]
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
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var shouldStackAnswerButtons: Bool {
        if dynamicTypeSize.isAccessibilitySize {
            return true
        }
        if let fixedWidth, fixedWidth < 480 {
            return true
        }
        return horizontalSizeClass != .regular
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
            if let review = mode.reviewData {
                reviewContent(review: review)
            } else {
                answeringContent
            }
        }
    }

    private var answeringContent: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(QuestionStrings.text("question.trueFalseHelp", locale: locale))
                .font(.caption)
                .foregroundStyle(brandPalette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)

            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(statements.enumerated()), id: \.element.id) { index, statement in
                    answeringRow(index: index, statement: statement)
                }
            }
        }
    }

    @ViewBuilder
    private func answeringRow(index: Int, statement: TrueFalseStatement) -> some View {
        let letter = OptionOrder.statementLetter(at: index)
        let currentValue = currentStatements[statement.id]
        let isAnswered = currentValue != nil
        let contentBlock = HStack(alignment: .top, spacing: 8) {
            Text("\(letter))")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(brandPalette.primaryTextColor)
                .fixedSize()
                .padding(.top, 2)

            QuestionRichText(
                document: RichHTMLParser.parse(statement.content),
                assets: assets,
                mode: mode.richContentMode,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: statementContentWidth
            )
            .frame(maxWidth: .infinity, alignment: .leading)
        }

        let buttonGroup = HStack(spacing: 8) {
            tfToggleButton(
                title: QuestionStrings.text("question.trueOption", locale: locale),
                letter: letter,
                isSelected: currentValue == true,
                rowAnswered: isAnswered,
                isTrueButton: true,
                statementId: statement.id
            )
            tfToggleButton(
                title: QuestionStrings.text("question.falseOption", locale: locale),
                letter: letter,
                isSelected: currentValue == false,
                rowAnswered: isAnswered,
                isTrueButton: false,
                statementId: statement.id
            )
        }

        Group {
            if shouldStackAnswerButtons {
                VStack(alignment: .leading, spacing: 10) {
                    contentBlock
                    buttonGroup
                }
            } else {
                HStack(alignment: .top, spacing: 12) {
                    contentBlock
                    buttonGroup
                        .fixedSize(horizontal: true, vertical: false)
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(isAnswered ? brandPalette.bgSubtleColor : brandPalette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder(brandPalette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("question.\(item.id).tf.\(statement.id).row")
        .questionProbe(
            id: "question.\(item.id).tf.\(statement.id).row",
            value: isAnswered ? "answered" : "unanswered"
        )
    }

    private var statementContentWidth: CGFloat? {
        guard let fixedWidth else { return nil }
        if shouldStackAnswerButtons {
            return max(fixedWidth - 52, 80)
        }
        return max(fixedWidth - 230, 120)
    }

    private func tfToggleButton(
        title: String,
        letter: String,
        isSelected: Bool,
        rowAnswered: Bool,
        isTrueButton: Bool,
        statementId: String
    ) -> some View {
        Button {
            guard mode.isAnswering else { return }
            var updated = currentStatements
            if currentStatements[statementId] == isTrueButton {
                updated.removeValue(forKey: statementId)
            } else {
                updated[statementId] = isTrueButton
            }
            onAnswer(AnswerValues.trueFalse(item: item, statements: updated))
        } label: {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(isSelected ? brandPalette.bgSurfaceColor : brandPalette.textColor)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .frame(minWidth: 72, minHeight: 44)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(
                            isSelected
                                ? brandPalette.primaryFillColor
                                : (rowAnswered ? brandPalette.bgSurfaceColor : brandPalette.bgSubtleColor)
                        )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .strokeBorder(
                            isSelected ? brandPalette.primaryFillColor : brandPalette.borderStrongColor,
                            lineWidth: isSelected ? 2 : 1
                        )
                )
        }
        .buttonStyle(.plain)
        .disabled(!mode.isAnswering)
        .opacity(mode.isLocked ? 0.6 : 1)
        .accessibilityLabel("\(letter)) \(title)")
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityValue(isSelected ? "1" : "0")
        .accessibilityIdentifier("question.\(item.id).tf.\(statementId).\(isTrueButton ? "true" : "false")")
        .questionProbe(
            id: "question.\(item.id).tf.\(statementId).\(isTrueButton ? "true" : "false")",
            label: "\(letter)) \(title)",
            value: isSelected ? "1" : "0",
            isButton: true,
            isEnabled: mode.isAnswering,
            isSelected: isSelected
        )
    }

    @ViewBuilder
    private func reviewContent(review: ItemReview) -> some View {
        let showAnswerCol = review.showAnswers && statements.contains(where: { $0.answer != nil })
        let useTable = horizontalSizeClass == .regular && (fixedWidth ?? 1000) >= 600
        let showCountSummary = scoringMethod != .perStatement && review.earned != nil

        let reviewSummaryText: String = {
            var parts: [String] = [QuestionStrings.text("question.youChoseCol", locale: locale)]
            if showAnswerCol {
                parts.append("\(QuestionStrings.text("question.answerCol", locale: locale)):")
            }
            parts.append("\(QuestionStrings.text("question.pointsCol", locale: locale)):")
            parts.append(contentsOf: statements.map { statement in
                pointsLabel(part: review.detail[statement.id], fallbackMax: statement.points ?? 1)
            })
            return parts.joined(separator: " | ")
        }()

        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 8) {
                if useTable {
                    reviewTableHeader(showAnswerCol: showAnswerCol)
                }

                ForEach(Array(statements.enumerated()), id: \.element.id) { index, statement in
                    let letter = OptionOrder.statementLetter(at: index)
                    let picked = currentStatements[statement.id]
                    let part = review.detail[statement.id]
                    if useTable {
                        reviewTableRow(
                            letter: letter,
                            statement: statement,
                            picked: picked,
                            part: part,
                            showAnswerCol: showAnswerCol
                        )
                    } else {
                        reviewStackedCard(
                            letter: letter,
                            statement: statement,
                            picked: picked,
                            part: part,
                            showAnswerCol: showAnswerCol
                        )
                    }
                }
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(brandPalette.bgSurfaceColor))
            .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(brandPalette.borderColor, lineWidth: 1))
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("question.\(item.id).tfReviewList")
            .questionProbe(id: "question.\(item.id).tfReviewList", label: reviewSummaryText)

            if showCountSummary, let earned = review.earned {
                let correctCount = statements.filter { review.detail[$0.id]?.outcome == .correct }.count
                let summaryText = QuestionStrings.countTableSummary(
                    correct: correctCount,
                    total: statements.count,
                    earned: earned,
                    locale: locale
                )
                Text(summaryText)
                .font(.caption.weight(.semibold))
                .foregroundStyle(brandPalette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("question.\(item.id).tfCountTableSummary")
                .questionProbe(id: "question.\(item.id).tfCountTableSummary", label: summaryText)
            }
        }
    }

    @ViewBuilder
    private func reviewTableHeader(showAnswerCol: Bool) -> some View {
        HStack(alignment: .center, spacing: 8) {
            Text(QuestionStrings.text("question.itemCol", locale: locale))
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(QuestionStrings.text("question.youChoseCol", locale: locale))
                .frame(width: 112, alignment: .leading)
            if showAnswerCol {
                Text(QuestionStrings.text("question.answerCol", locale: locale))
                    .frame(width: 96, alignment: .leading)
            }
            Text(QuestionStrings.text("question.pointsCol", locale: locale))
                .frame(width: 96, alignment: .trailing)
        }
        .font(.caption.weight(.semibold))
        .foregroundStyle(brandPalette.textMutedColor)
        .padding(.bottom, 4)
        Divider()
    }

    @ViewBuilder
    private func reviewTableRow(
        letter: String,
        statement: TrueFalseStatement,
        picked: Bool?,
        part: PartResult?,
        showAnswerCol: Bool
    ) -> some View {
        HStack(alignment: .top, spacing: 8) {
            HStack(alignment: .top, spacing: 6) {
                Text("\(letter))")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(brandPalette.primaryTextColor)
                    .fixedSize()
                QuestionRichText(
                    document: RichHTMLParser.parse(statement.content),
                    assets: assets,
                    mode: .review,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: fixedWidth.map { max($0 - 360, 120) }
                )
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 6) {
                if let part {
                    PartOutcomeBadge(outcome: part.outcome, locale: locale)
                }
                Text(boolLabel(picked))
                    .font(.subheadline)
                    .foregroundStyle(picked == nil ? brandPalette.textMutedColor : brandPalette.textColor)
            }
            .frame(width: 112, alignment: .leading)

            if showAnswerCol {
                Text(statement.answer.map(boolLabel) ?? "—")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(brandPalette.textColor)
                    .frame(width: 96, alignment: .leading)
            }

            Text(pointsLabel(part: part, fallbackMax: statement.points ?? 1))
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(brandPalette.textColor)
                .frame(width: 96, alignment: .trailing)
        }
        .padding(.vertical, 6)
        Divider()
    }

    @ViewBuilder
    private func reviewStackedCard(
        letter: String,
        statement: TrueFalseStatement,
        picked: Bool?,
        part: PartResult?,
        showAnswerCol: Bool
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 6) {
                Text("\(letter))")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(brandPalette.primaryTextColor)
                    .fixedSize()
                QuestionRichText(
                    document: RichHTMLParser.parse(statement.content),
                    assets: assets,
                    mode: .review,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: fixedWidth.map { max($0 - 52, 80) }
                )
            }

            WrappingHStackLayout(horizontalSpacing: 12, verticalSpacing: 6) {
                HStack(spacing: 6) {
                    Text("\(QuestionStrings.text("question.youChoseCol", locale: locale)):")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(brandPalette.textMutedColor)
                    if let part {
                        PartOutcomeBadge(outcome: part.outcome, locale: locale)
                    }
                    Text(boolLabel(picked))
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(picked == nil ? brandPalette.textMutedColor : brandPalette.textColor)
                }

                if showAnswerCol {
                    HStack(spacing: 6) {
                        Text("\(QuestionStrings.text("question.answerCol", locale: locale)):")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(brandPalette.textMutedColor)
                        Text(statement.answer.map(boolLabel) ?? "—")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(brandPalette.textColor)
                    }
                }

                HStack(spacing: 6) {
                    Text("\(QuestionStrings.text("question.pointsCol", locale: locale)):")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(brandPalette.textMutedColor)
                    Text(pointsLabel(part: part, fallbackMax: statement.points ?? 1))
                        .font(.subheadline.monospacedDigit())
                        .foregroundStyle(brandPalette.textColor)
                }
            }
        }
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        Divider()
    }

    private func boolLabel(_ value: Bool?) -> String {
        guard let value else {
            return QuestionStrings.text("question.notAnswered", locale: locale)
        }
        return QuestionStrings.text(value ? "question.trueOption" : "question.falseOption", locale: locale)
    }

    private func pointsLabel(part: PartResult?, fallbackMax: Double) -> String {
        guard let earned = part?.earned else { return "—" }
        return QuestionStrings.pointsText(earned: earned, max: part?.max ?? fallbackMax, locale: locale)
    }
}
