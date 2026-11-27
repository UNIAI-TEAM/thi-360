import SwiftUI

enum MatchingLogic {
    static func orderedRight(
        right: [MatchingSideItem],
        optionOrder: [String]?
    ) -> [MatchingSideItem] {
        OptionOrder.order(right, by: optionOrder)
    }

    static func disabledRightIds(
        forLeftId leftId: String,
        pairs: [String: String],
        reuseRight: Bool
    ) -> Set<String> {
        guard !reuseRight else { return [] }
        var used = Set<String>()
        for (otherLeftId, rightId) in pairs where otherLeftId != leftId && !rightId.isEmpty {
            used.insert(rightId)
        }
        return used
    }

    static func assign(
        leftId: String,
        rightId: String?,
        currentPairs: [String: String],
        item: QuestionItem
    ) -> AnswerValue {
        var updated = currentPairs
        if let rightId, !rightId.isEmpty {
            updated[leftId] = rightId
        } else {
            updated.removeValue(forKey: leftId)
        }
        return AnswerValues.matching(item: item, pairs: updated)
    }
}

struct MatchingQuestionView<Attachments: View>: View {
    let item: QuestionItem
    let optionOrder: [String]?
    let left: [MatchingSideItem]
    let right: [MatchingSideItem]
    let reuseRight: Bool
    let pairs: [MatchingPairKey]?
    let currentPairs: [String: String]
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

    @State private var activeLeftPicker: ActiveMatchingPicker?

    private var orderedRight: [MatchingSideItem] {
        MatchingLogic.orderedRight(right: right, optionOrder: optionOrder)
    }

    private var rightLookup: [String: (letter: String, item: MatchingSideItem)] {
        var map: [String: (letter: String, item: MatchingSideItem)] = [:]
        for (idx, rightItem) in orderedRight.enumerated() {
            map[rightItem.id] = (OptionOrder.matchLetter(at: idx), rightItem)
        }
        return map
    }

    private var matchedCount: Int {
        left.filter { leftItem in
            guard let chosen = currentPairs[leftItem.id] else { return false }
            return rightLookup[chosen] != nil
        }.count
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
        .sheet(item: $activeLeftPicker) { picker in
            MatchingChoiceSheet(
                itemId: item.id,
                leftIndex: picker.index,
                leftItem: picker.leftItem,
                orderedRight: orderedRight,
                selectedRightId: currentPairs[picker.leftItem.id],
                disabledRightIds: MatchingLogic.disabledRightIds(
                    forLeftId: picker.leftItem.id,
                    pairs: currentPairs,
                    reuseRight: reuseRight
                ),
                assets: assets,
                formulaEngine: formulaEngine,
                locale: locale,
                onSelect: { rightId in
                    onAnswer(
                        MatchingLogic.assign(
                            leftId: picker.leftItem.id,
                            rightId: rightId,
                            currentPairs: currentPairs,
                            item: item
                        )
                    )
                    activeLeftPicker = nil
                },
                onClose: {
                    activeLeftPicker = nil
                }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
    }

    private var answeringContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            WrappingHStackLayout(horizontalSpacing: 10, verticalSpacing: 4) {
                Text(QuestionStrings.text(reuseRight ? "question.matchReusable" : "question.matchExclusive", locale: locale))
                    .font(.caption)
                    .foregroundStyle(brandPalette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)

                let matchDoneText = QuestionStrings.matchDone(done: matchedCount, total: left.count, locale: locale)
                Text(matchDoneText)
                    .font(.caption.weight(.semibold).monospacedDigit())
                    .foregroundStyle(brandPalette.textSecondaryColor)
                    .accessibilityIdentifier("question.\(item.id).matchCounter")
                    .questionProbe(id: "question.\(item.id).matchCounter", label: matchDoneText)
            }

            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(left.enumerated()), id: \.element.id) { index, leftItem in
                    answeringRow(index: index, leftItem: leftItem)
                }
            }

            Text(QuestionStrings.text("question.matchDoneNote", locale: locale))
                .font(.caption)
                .foregroundStyle(brandPalette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    @ViewBuilder
    private func answeringRow(index: Int, leftItem: MatchingSideItem) -> some View {
        let leftNo = index + 1
        let matchedRight = currentPairs[leftItem.id].flatMap { rightLookup[$0] }

        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Text(OptionOrder.formatInteger(leftNo, locale: locale))
                    .font(.subheadline.weight(.bold).monospacedDigit())
                    .foregroundStyle(brandPalette.primaryTextColor)
                    .frame(width: 28, height: 28)
                    .background(Circle().fill(brandPalette.primaryContainerColor))

                QuestionRichText(
                    document: RichHTMLParser.parse(leftItem.content),
                    assets: assets,
                    mode: mode.richContentMode,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: fixedWidth.map { max($0 - 72, 80) }
                )
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            HStack(alignment: .center, spacing: 8) {
                Text(QuestionStrings.text("question.matchWith", locale: locale))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(brandPalette.textSecondaryColor)
                    .fixedSize()

                Button {
                    guard mode.isAnswering else { return }
                    activeLeftPicker = ActiveMatchingPicker(index: index, leftItem: leftItem)
                } label: {
                    HStack(alignment: .center, spacing: 8) {
                        if let matchedRight {
                            Text("\(matchedRight.letter).")
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(brandPalette.primaryTextColor)
                                .fixedSize()

                            QuestionRichText(
                                document: RichHTMLParser.parse(matchedRight.item.content),
                                assets: assets,
                                mode: mode.richContentMode,
                                formulaEngine: formulaEngine,
                                locale: locale,
                                fixedWidth: fixedWidth.map { max($0 - 180, 80) }
                            )
                            .allowsHitTesting(false)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        } else {
                            Text(QuestionStrings.text("question.matchPlaceholder", locale: locale))
                                .font(.subheadline)
                                .foregroundStyle(brandPalette.textMutedColor)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }

                        Image(systemName: "chevron.down")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(brandPalette.textSecondaryColor)
                            .accessibilityHidden(true)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(matchedRight != nil ? brandPalette.primaryContainerColor.opacity(0.35) : brandPalette.bgSubtleColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .strokeBorder(
                                matchedRight != nil ? brandPalette.primaryFillColor : brandPalette.borderStrongColor,
                                lineWidth: matchedRight != nil ? 1.5 : 1
                            )
                    )
                }
                .buttonStyle(.plain)
                .disabled(!mode.isAnswering)
                .opacity(mode.isLocked ? 0.65 : 1)
                .accessibilityLabel(
                    "\(QuestionStrings.matchPickLeft(leftNo: leftNo, locale: locale)), \(QuestionStrings.text("question.matchWith", locale: locale))"
                )
                .accessibilityIdentifier("question.\(item.id).match.\(leftItem.id).picker")

                if matchedRight != nil {
                    Button {
                        guard mode.isAnswering else { return }
                        onAnswer(
                            MatchingLogic.assign(
                                leftId: leftItem.id,
                                rightId: nil,
                                currentPairs: currentPairs,
                                item: item
                            )
                        )
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.body)
                            .foregroundStyle(brandPalette.textSecondaryColor)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(!mode.isAnswering)
                    .opacity(mode.isLocked ? 0.45 : 1)
                    .accessibilityLabel(QuestionStrings.matchClear(leftNo: leftNo, locale: locale))
                    .accessibilityIdentifier("question.\(item.id).match.\(leftItem.id).clear")
                    .questionProbe(
                        id: "question.\(item.id).match.\(leftItem.id).clear",
                        label: QuestionStrings.matchClear(leftNo: leftNo, locale: locale),
                        isButton: true,
                        isEnabled: mode.isAnswering
                    )
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(brandPalette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder(brandPalette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("question.\(item.id).match.\(leftItem.id).row")
    }

    @ViewBuilder
    private func reviewContent(review: ItemReview) -> some View {
        let keyByLeft: [String: MatchingPairKey] = Dictionary(
            uniqueKeysWithValues: (pairs ?? []).map { ($0.leftId, $0) }
        )
        let showAnswerCol = review.showAnswers && !keyByLeft.isEmpty
        let useTable = horizontalSizeClass == .regular && (fixedWidth ?? 1000) >= 600

        let reviewSummaryText: String = {
            var parts: [String] = [
                QuestionStrings.text("question.itemCol", locale: locale),
                QuestionStrings.text("question.youChoseCol", locale: locale)
            ]
            if showAnswerCol {
                parts.append(QuestionStrings.text("question.answerCol", locale: locale))
            }
            parts.append(QuestionStrings.text("question.pointsCol", locale: locale))
            for leftItem in left {
                if currentPairs[leftItem.id].flatMap({ rightLookup[$0] }) == nil {
                    parts.append(QuestionStrings.text("question.notMatched", locale: locale))
                }
                parts.append(pointsLabel(part: review.detail[leftItem.id], fallbackMax: keyByLeft[leftItem.id]?.points ?? 1))
            }
            return parts.joined(separator: " | ")
        }()

        VStack(alignment: .leading, spacing: 8) {
            if useTable {
                reviewTableHeader(showAnswerCol: showAnswerCol)
            }

            ForEach(Array(left.enumerated()), id: \.element.id) { index, leftItem in
                let leftNo = index + 1
                let part = review.detail[leftItem.id]
                let chosen = currentPairs[leftItem.id].flatMap { rightLookup[$0] }
                let expectedKey = keyByLeft[leftItem.id]
                let expected = expectedKey.flatMap { rightLookup[$0.rightId] }

                if useTable {
                    reviewTableRow(
                        leftNo: leftNo,
                        leftItem: leftItem,
                        chosen: chosen,
                        expected: expected,
                        expectedPoints: expectedKey?.points,
                        part: part,
                        showAnswerCol: showAnswerCol
                    )
                } else {
                    reviewStackedCard(
                        leftNo: leftNo,
                        leftItem: leftItem,
                        chosen: chosen,
                        expected: expected,
                        expectedPoints: expectedKey?.points,
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
        .accessibilityIdentifier("question.\(item.id).matchReviewList")
        .questionProbe(id: "question.\(item.id).matchReviewList", label: reviewSummaryText)
    }

    @ViewBuilder
    private func reviewTableHeader(showAnswerCol: Bool) -> some View {
        HStack(alignment: .center, spacing: 8) {
            Text(QuestionStrings.text("question.itemCol", locale: locale))
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(QuestionStrings.text("question.youChoseCol", locale: locale))
                .frame(maxWidth: .infinity, alignment: .leading)
            if showAnswerCol {
                Text(QuestionStrings.text("question.answerCol", locale: locale))
                    .frame(maxWidth: .infinity, alignment: .leading)
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
        leftNo: Int,
        leftItem: MatchingSideItem,
        chosen: (letter: String, item: MatchingSideItem)?,
        expected: (letter: String, item: MatchingSideItem)?,
        expectedPoints: Double?,
        part: PartResult?,
        showAnswerCol: Bool
    ) -> some View {
        let colWidth = fixedWidth.map { max(($0 - 140) / (showAnswerCol ? 3 : 2) - 24, 80) }

        HStack(alignment: .top, spacing: 8) {
            HStack(alignment: .top, spacing: 6) {
                Text("\(OptionOrder.formatInteger(leftNo, locale: locale)).")
                    .font(.subheadline.weight(.bold).monospacedDigit())
                    .foregroundStyle(brandPalette.primaryTextColor)
                    .fixedSize()
                QuestionRichText(
                    document: RichHTMLParser.parse(leftItem.content),
                    assets: assets,
                    mode: .review,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: colWidth
                )
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            HStack(alignment: .top, spacing: 6) {
                if let part {
                    PartOutcomeBadge(outcome: part.outcome, locale: locale)
                        .padding(.top, 2)
                }
                if let chosen {
                    rightChoiceLabel(chosen, fixedWidth: colWidth)
                } else {
                    Text(QuestionStrings.text("question.notMatched", locale: locale))
                        .font(.subheadline)
                        .foregroundStyle(brandPalette.textMutedColor)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if showAnswerCol {
                Group {
                    if let expected {
                        rightChoiceLabel(expected, fixedWidth: colWidth)
                    } else {
                        Text("—")
                            .font(.subheadline)
                            .foregroundStyle(brandPalette.textMutedColor)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            Text(pointsLabel(part: part, fallbackMax: expectedPoints ?? 1))
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(brandPalette.textColor)
                .frame(width: 96, alignment: .trailing)
        }
        .padding(.vertical, 6)
        Divider()
    }

    @ViewBuilder
    private func reviewStackedCard(
        leftNo: Int,
        leftItem: MatchingSideItem,
        chosen: (letter: String, item: MatchingSideItem)?,
        expected: (letter: String, item: MatchingSideItem)?,
        expectedPoints: Double?,
        part: PartResult?,
        showAnswerCol: Bool
    ) -> some View {
        let contentWidth = fixedWidth.map { max($0 - 72, 80) }

        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 6) {
                Text("\(OptionOrder.formatInteger(leftNo, locale: locale)).")
                    .font(.subheadline.weight(.bold).monospacedDigit())
                    .foregroundStyle(brandPalette.primaryTextColor)
                    .fixedSize()
                QuestionRichText(
                    document: RichHTMLParser.parse(leftItem.content),
                    assets: assets,
                    mode: .review,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: contentWidth
                )
            }

            HStack(alignment: .top, spacing: 6) {
                Text("\(QuestionStrings.text("question.youChoseCol", locale: locale)):")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(brandPalette.textMutedColor)
                    .padding(.top, 2)
                if let part {
                    PartOutcomeBadge(outcome: part.outcome, locale: locale)
                        .padding(.top, 2)
                }
                if let chosen {
                    rightChoiceLabel(chosen, fixedWidth: contentWidth)
                } else {
                    Text(QuestionStrings.text("question.notMatched", locale: locale))
                        .font(.subheadline)
                        .foregroundStyle(brandPalette.textMutedColor)
                }
            }

            if showAnswerCol {
                HStack(alignment: .top, spacing: 6) {
                    Text("\(QuestionStrings.text("question.answerCol", locale: locale)):")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(brandPalette.textMutedColor)
                        .padding(.top, 2)
                    if let expected {
                        rightChoiceLabel(expected, fixedWidth: contentWidth)
                    } else {
                        Text("—")
                            .font(.subheadline)
                            .foregroundStyle(brandPalette.textMutedColor)
                    }
                }
            }

            HStack(spacing: 6) {
                Text("\(QuestionStrings.text("question.pointsCol", locale: locale)):")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(brandPalette.textMutedColor)
                Text(pointsLabel(part: part, fallbackMax: expectedPoints ?? 1))
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(brandPalette.textColor)
            }
        }
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        Divider()
    }

    private func rightChoiceLabel(
        _ choice: (letter: String, item: MatchingSideItem),
        fixedWidth: CGFloat?
    ) -> some View {
        HStack(alignment: .top, spacing: 6) {
            Text("\(choice.letter).")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(brandPalette.primaryTextColor)
                .fixedSize()
            QuestionRichText(
                document: RichHTMLParser.parse(choice.item.content),
                assets: assets,
                mode: .review,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: fixedWidth
            )
        }
    }

    private func pointsLabel(part: PartResult?, fallbackMax: Double) -> String {
        guard let earned = part?.earned else { return "—" }
        return QuestionStrings.pointsText(earned: earned, max: part?.max ?? fallbackMax, locale: locale)
    }
}

private struct ActiveMatchingPicker: Identifiable {
    let index: Int
    let leftItem: MatchingSideItem
    var id: String { leftItem.id }
}

struct MatchingChoiceSheet: View {
    let itemId: String
    let leftIndex: Int
    let leftItem: MatchingSideItem
    let orderedRight: [MatchingSideItem]
    let selectedRightId: String?
    let disabledRightIds: Set<String>
    let assets: AttemptAssetSource?
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let onSelect: (String?) -> Void
    let onClose: () -> Void

    @Environment(\.brandPalette) private var brandPalette

    var body: some View {
        let leftNo = leftIndex + 1

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .top, spacing: 8) {
                        Text("\(OptionOrder.formatInteger(leftNo, locale: locale)).")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(brandPalette.primaryTextColor)
                        QuestionRichText(
                            document: RichHTMLParser.parse(leftItem.content),
                            assets: assets,
                            mode: .answering,
                            formulaEngine: formulaEngine,
                            locale: locale
                        )
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(brandPalette.bgSubtleColor))

                    if selectedRightId != nil {
                        Button {
                            onSelect(nil)
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "xmark.circle")
                                Text(QuestionStrings.matchClear(leftNo: leftNo, locale: locale))
                                    .font(.subheadline.weight(.semibold))
                                Spacer()
                            }
                            .foregroundStyle(brandPalette.dangerTextColor)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 10)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(brandPalette.dangerBgColor)
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("question.\(itemId).matchSheet.clear")
                    }

                    ForEach(Array(orderedRight.enumerated()), id: \.element.id) { idx, rightItem in
                        let letter = OptionOrder.matchLetter(at: idx)
                        let isSelected = selectedRightId == rightItem.id
                        let isDisabled = disabledRightIds.contains(rightItem.id)

                        Button {
                            guard !isDisabled else { return }
                            onSelect(rightItem.id)
                        } label: {
                            HStack(alignment: .center, spacing: 10) {
                                Text("\(letter).")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundStyle(
                                        isSelected ? brandPalette.bgSurfaceColor : brandPalette.primaryTextColor
                                    )
                                    .frame(width: 28, height: 28)
                                    .background(
                                        Circle()
                                            .fill(isSelected ? brandPalette.primaryFillColor : brandPalette.primaryContainerColor)
                                    )

                                QuestionRichText(
                                    document: RichHTMLParser.parse(rightItem.content),
                                    assets: assets,
                                    mode: .answering,
                                    formulaEngine: formulaEngine,
                                    locale: locale
                                )
                                .allowsHitTesting(false)
                                .frame(maxWidth: .infinity, alignment: .leading)

                                if isSelected {
                                    Image(systemName: "checkmark")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundStyle(brandPalette.primaryTextColor)
                                }
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(isSelected ? brandPalette.primaryContainerColor.opacity(0.45) : brandPalette.bgSurfaceColor)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .strokeBorder(
                                        isSelected ? brandPalette.primaryFillColor : brandPalette.borderColor,
                                        lineWidth: isSelected ? 2 : 1
                                    )
                            )
                        }
                        .buttonStyle(.plain)
                        .disabled(isDisabled)
                        .opacity(isDisabled ? 0.42 : 1)
                        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
                        .accessibilityIdentifier("question.\(itemId).matchSheet.\(rightItem.id)")
                        .questionProbe(
                            id: "question.\(itemId).matchSheet.\(rightItem.id)",
                            label: "\(letter).",
                            isButton: true,
                            isEnabled: !isDisabled,
                            isSelected: isSelected
                        )
                    }
                }
                .padding(16)
            }
            .navigationTitle(QuestionStrings.matchPickLeft(leftNo: leftNo, locale: locale))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(AppStrings.common("common.close", locale: locale)) {
                        onClose()
                    }
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityIdentifier("question.\(itemId).matchSheet.close")
                }
            }
        }
    }
}
