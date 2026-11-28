import SwiftUI

struct ResultSectionCard<Content: View>: View {
    let title: String
    let subtitle: String?
    let identifier: String
    @ViewBuilder let content: () -> Content

    @Environment(\.brandPalette) private var palette

    init(
        title: String,
        subtitle: String? = nil,
        identifier: String,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.title = title
        self.subtitle = subtitle
        self.identifier = identifier
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 10) {
                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                        .fill(palette.primaryFillColor)
                        .frame(width: 4, height: 22)
                        .accessibilityHidden(true)

                    Text(title)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                        .resultProbe(id: "\(identifier).title", label: title)
                }

                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .resultProbe(id: "\(identifier).subtitle", label: subtitle)
                }
            }

            content()
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(identifier)
    }
}

struct ResultStatusChip: View {
    let title: String
    let tone: ResultStatusTone
    let symbolName: String?

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var foreground: Color {
        switch tone {
        case .success: statusPalette.successColor
        case .warning: statusPalette.warningColor
        case .info: statusPalette.infoColor
        case .danger: palette.dangerTextColor
        case .neutral: palette.textSecondaryColor
        }
    }

    private var background: Color {
        switch tone {
        case .success: statusPalette.successBgColor
        case .warning: statusPalette.warningBgColor
        case .info: statusPalette.infoBgColor
        case .danger: palette.dangerBgColor
        case .neutral: palette.bgSubtleColor
        }
    }

    var body: some View {
        HStack(spacing: 5) {
            if let symbolName {
                Image(systemName: symbolName)
                    .font(.caption.weight(.bold))
                    .accessibilityHidden(true)
            }
            Text(title)
                .font(.caption.weight(.semibold))
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Capsule().fill(background))
        .overlay(Capsule().strokeBorder(foreground.opacity(0.35), lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}

struct ResultStatBucketsView: View {
    let stats: AttemptResultStats
    let showPendingBucket: Bool
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.shellWidth) private var shellWidth

    private var columns: [GridItem] {
        let count = (dynamicTypeSize.isAccessibilitySize || shellWidth == .narrow) ? 2 : 4
        return Array(repeating: GridItem(.flexible(), spacing: 10), count: count)
    }

    private var showsPendingTile: Bool {
        stats.pendingReviewCount > 0 || showPendingBucket
    }

    var body: some View {
        let denom = max(1, stats.totalCount)
        let denominatorText = ResultStrings.text(
            "result.stats.denominator",
            locale: locale,
            replacements: ["total": ResultFormat.formatScore(Double(stats.totalCount), locale: locale)]
        )

        VStack(alignment: .leading, spacing: 12) {
            LazyVGrid(columns: columns, spacing: 10) {
                statTile(
                    id: "result.stats.correct",
                    label: ResultStrings.text("result.stats.correct", locale: locale),
                    count: stats.correctCount,
                    color: statusPalette.successColor,
                    symbol: "checkmark",
                    highlighted: false
                )
                statTile(
                    id: "result.stats.partial",
                    label: ResultStrings.text("result.stats.partial", locale: locale),
                    count: stats.partialCount,
                    color: statusPalette.warningColor,
                    symbol: "circle.lefthalf.filled",
                    highlighted: false
                )
                statTile(
                    id: "result.stats.wrong",
                    label: ResultStrings.text("result.stats.wrong", locale: locale),
                    count: stats.wrongCount,
                    color: palette.dangerTextColor,
                    symbol: "xmark",
                    highlighted: false
                )
                statTile(
                    id: "result.stats.blank",
                    label: ResultStrings.text("result.stats.blank", locale: locale),
                    count: stats.blankCount,
                    color: palette.textSecondaryColor,
                    symbol: "minus",
                    highlighted: false
                )
            }

            if showsPendingTile {
                let pendingLabel = ResultStrings.text("result.stats.pending", locale: locale)
                let pendingHighlighted = stats.pendingReviewCount > 0
                statTile(
                    id: "result.stats.pending",
                    label: pendingLabel,
                    count: stats.pendingReviewCount,
                    color: pendingHighlighted ? statusPalette.warningColor : statusPalette.infoColor,
                    symbol: "clock",
                    highlighted: pendingHighlighted
                )
            }

            ResultProportionBar(
                correct: stats.correctCount,
                partial: stats.partialCount,
                wrong: stats.wrongCount,
                pending: stats.pendingReviewCount,
                total: denom,
                height: 8
            )
            .accessibilityIdentifier("result.stats.bar")

            Text(denominatorText)
                .font(.caption)
                .foregroundStyle(palette.textMutedColor)
                .monospacedDigit()
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("result.stats.denominator")
                .resultProbe(id: "result.stats.denominator", label: denominatorText)
        }
    }

    private func statTile(
        id: String,
        label: String,
        count: Int,
        color: Color,
        symbol: String,
        highlighted: Bool
    ) -> some View {
        let countText = ResultFormat.formatScore(Double(count), locale: locale)
        return VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Image(systemName: symbol)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(color)
                    .accessibilityHidden(true)
                Text(label)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(highlighted ? statusPalette.warningColor : palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Text(countText)
                .font(.title3.weight(.bold).monospacedDigit())
                .foregroundStyle(palette.textColor)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(highlighted ? statusPalette.warningBgColor : palette.bgSubtleColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(highlighted ? statusPalette.warningColor : palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label): \(countText)")
        .accessibilityIdentifier(id)
        .resultProbe(
            id: id,
            label: "\(label): \(countText)",
            value: highlighted ? "highlighted" : "normal"
        )
    }
}

struct ResultProportionBar: View {
    let correct: Int
    let partial: Int
    let wrong: Int
    let pending: Int
    let total: Int
    let height: CGFloat

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var body: some View {
        let denom = max(1, CGFloat(total))
        GeometryReader { proxy in
            let width = max(proxy.size.width, 0)
            HStack(spacing: 0) {
                if correct > 0 {
                    statusPalette.successColor
                        .frame(width: width * CGFloat(correct) / denom)
                }
                if partial > 0 {
                    statusPalette.warningColor
                        .frame(width: width * CGFloat(partial) / denom)
                }
                if wrong > 0 {
                    statusPalette.dangerSolidColor
                        .frame(width: width * CGFloat(wrong) / denom)
                }
                if pending > 0 {
                    statusPalette.infoColor
                        .frame(width: width * CGFloat(pending) / denom)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(palette.bgSubtleColor)
            .clipShape(Capsule())
        }
        .frame(height: height)
        .accessibilityHidden(true)
    }
}

struct ResultPartsBreakdownSection: View {
    let parts: [AttemptResultPartItem]
    let endReason: AttemptResultEndReason?
    let hasPendingAttempt: Bool
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var body: some View {
        ResultSectionCard(
            title: ResultStrings.text("result.parts.title", locale: locale),
            subtitle: ResultStrings.text(
                hasPendingAttempt ? "result.parts.pendingDescription" : "result.parts.description",
                locale: locale
            ),
            identifier: "result.parts.section"
        ) {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(Array(parts.enumerated()), id: \.element.id) { index, part in
                    partCard(part: part, index: index)
                }
            }
        }
    }

    @ViewBuilder
    private func partCard(part: AttemptResultPartItem, index: Int) -> some View {
        let cardId = "result.parts.card.\(index)"

        if part.skipped {
            let reasonKey: String = {
                switch endReason {
                case .choiceTimeout: "result.parts.skippedChoiceTimeout"
                case .faultClosed: "result.parts.skippedFaultClosed"
                default: "result.parts.skippedFallback"
                }
            }()
            let reasonText = ResultStrings.text(reasonKey, locale: locale)
            let badgeText = ResultStrings.text("result.parts.skippedBadge", locale: locale)
            let scorePair = part.maxScore.map {
                "\(ResultFormat.formatScore(0, locale: locale))/\(ResultFormat.formatScore($0, locale: locale))"
            }

            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top, spacing: 10) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(part.displayTitle)
                            .font(.headline)
                            .foregroundStyle(palette.textColor)
                            .fixedSize(horizontal: false, vertical: true)
                        ResultStatusChip(title: badgeText, tone: .warning, symbolName: "nosign")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    if let scorePair {
                        VStack(alignment: .trailing, spacing: 2) {
                            Text(ResultStrings.text("result.parts.partScoreLabel", locale: locale))
                                .font(.caption)
                                .foregroundStyle(palette.textSecondaryColor)
                            Text(scorePair)
                                .font(.headline.weight(.bold).monospacedDigit())
                                .foregroundStyle(palette.textColor)
                        }
                    }
                }

                Text(reasonText)
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(statusPalette.warningBgColor))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(statusPalette.warningColor, lineWidth: 1))
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier(cardId)
            .resultProbe(
                id: cardId,
                label: [part.displayTitle, badgeText, scorePair, reasonText].compactMap { $0 }.joined(separator: " | ")
            )
        } else if part.pendingReview {
            let manualText = ResultStrings.text("result.parts.manualChip", locale: locale)
            let pendingText = ResultStrings.text("result.stats.pending", locale: locale)
            let maxNote = part.maxScore.map {
                ResultStrings.text(
                    "result.parts.maxScoreNote",
                    locale: locale,
                    replacements: ["max": ResultFormat.formatScore($0, locale: locale)]
                )
            }

            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top, spacing: 10) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(part.displayTitle)
                            .font(.headline)
                            .foregroundStyle(palette.textColor)
                            .fixedSize(horizontal: false, vertical: true)

                        WrappingHStackLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                            ResultStatusChip(title: manualText, tone: .neutral, symbolName: "pencil.line")
                            ResultStatusChip(title: pendingText, tone: .warning, symbolName: "clock")
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                if let maxNote {
                    Text(maxNote)
                        .font(.subheadline)
                        .foregroundStyle(palette.textSecondaryColor)
                        .monospacedDigit()
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(statusPalette.warningBgColor))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(statusPalette.warningColor, lineWidth: 1))
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier(cardId)
            .resultProbe(
                id: cardId,
                label: [part.displayTitle, manualText, pendingText, maxNote].compactMap { $0 }.joined(separator: " | ")
            )
        } else {
            let scoreLabel = ResultStrings.text("result.parts.partScoreLabel", locale: locale)
            let scorePair: String? = {
                guard let score = part.score, let maxScore = part.maxScore else { return nil }
                return "\(ResultFormat.formatScore(score, locale: locale))/\(ResultFormat.formatScore(maxScore, locale: locale))"
            }()
            let countItems = buildCountMeta(stats: part.stats, includePending: false, locale: locale)

            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top, spacing: 10) {
                    Text(part.displayTitle)
                        .font(.headline)
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    if let scorePair {
                        VStack(alignment: .trailing, spacing: 2) {
                            Text(scoreLabel)
                                .font(.caption)
                                .foregroundStyle(palette.textMutedColor)
                            Text(scorePair)
                                .font(.headline.weight(.bold).monospacedDigit())
                                .foregroundStyle(palette.textColor)
                        }
                    }
                }

                if !countItems.isEmpty {
                    ResultInlineMetaRow(items: countItems)
                }

                if let stats = part.stats {
                    ResultProportionBar(
                        correct: stats.correctCount,
                        partial: stats.partialCount,
                        wrong: stats.wrongCount,
                        pending: 0,
                        total: max(1, stats.totalCount),
                        height: 6
                    )
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.bgSubtleColor))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier(cardId)
            .resultProbe(
                id: cardId,
                label: (
                    [part.displayTitle]
                        + (scorePair.map { ["\(scoreLabel): \($0)"] } ?? [])
                        + countItems.map { "\($0.label): \($0.value)" }
                ).joined(separator: " | ")
            )
        }
    }
}

struct ResultSegmentsBreakdownSection: View {
    let segments: [AttemptResultSegmentItem]
    let hasPendingAttempt: Bool
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette

    var body: some View {
        ResultSectionCard(
            title: ResultStrings.text("result.segments.title", locale: locale),
            subtitle: ResultStrings.text(
                hasPendingAttempt ? "result.segments.pendingDescription" : "result.segments.description",
                locale: locale
            ),
            identifier: "result.segments.section"
        ) {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(segments.enumerated()), id: \.element.id) { index, segment in
                    segmentCard(segment: segment, index: index)
                }
            }
        }
    }

    private func segmentCard(segment: AttemptResultSegmentItem, index: Int) -> some View {
        let cardId = "result.segments.card.\(index)"
        let rangeText: String? = {
            guard let from = segment.fromPosition, let to = segment.toPosition else { return nil }
            return ResultStrings.text(
                "result.segments.range",
                locale: locale,
                replacements: ["from": String(from), "to": String(to)]
            )
        }()
        let scoreLabel = ResultStrings.text("result.segments.segmentScoreLabel", locale: locale)
        let scoreText = ResultFormat.formatSegmentScore(segment, locale: locale)
        let manualText = ResultStrings.text("result.segments.manualChip", locale: locale)
        let pendingText = ResultStrings.text("result.stats.pending", locale: locale)
        let countItems = buildCountMeta(stats: segment.stats, includePending: true, locale: locale)

        var probeParts = [segment.displayTitle]
        if let rangeText { probeParts.append(rangeText) }
        if segment.pendingReview {
            probeParts.append(manualText)
            probeParts.append(pendingText)
        } else if let scoreText {
            probeParts.append("\(scoreLabel): \(scoreText)")
        }
        probeParts.append(contentsOf: countItems.map { "\($0.label): \($0.value)" })

        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(segment.displayTitle)
                        .font(.headline)
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                    if let rangeText {
                        Text(rangeText)
                            .font(.caption)
                            .foregroundStyle(palette.textMutedColor)
                            .monospacedDigit()
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if segment.pendingReview {
                    VStack(alignment: .trailing, spacing: 4) {
                        ResultStatusChip(title: manualText, tone: .neutral, symbolName: "pencil.line")
                        ResultStatusChip(title: pendingText, tone: .warning, symbolName: "clock")
                    }
                } else if let scoreText {
                    Text("\(scoreLabel): \(scoreText)")
                        .font(.subheadline.weight(.bold).monospacedDigit())
                        .foregroundStyle(palette.textColor)
                        .multilineTextAlignment(.trailing)
                }
            }

            if !countItems.isEmpty {
                ResultInlineMetaRow(items: countItems)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.bgSubtleColor))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(cardId)
        .resultProbe(id: cardId, label: probeParts.joined(separator: " | "))
    }
}

struct ResultSubjectsBreakdownSection: View {
    let subjects: [AttemptResultSubjectItem]
    let subjectOptions: [String: CatalogOptionItem]
    let hasPendingAttempt: Bool
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette

    var body: some View {
        ResultSectionCard(
            title: ResultStrings.text("result.subjects.title", locale: locale),
            subtitle: ResultStrings.text(
                hasPendingAttempt ? "result.subjects.pendingDescription" : "result.subjects.description",
                locale: locale
            ),
            identifier: "result.subjects.section"
        ) {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(subjects.enumerated()), id: \.element.id) { index, subject in
                    subjectCard(subject: subject, index: index)
                }
            }
        }
    }

    private func subjectCard(subject: AttemptResultSubjectItem, index: Int) -> some View {
        let cardId = "result.subjects.card.\(index)"
        let name = ResultFormat.resolveSubjectName(
            subject: subject,
            subjectOptions: subjectOptions,
            locale: locale
        )
        let scoreLabel = ResultStrings.text("result.subjects.subjectScoreLabel", locale: locale)
        let scoreText: String? = {
            guard !subject.pendingReview, let score = subject.score, let maxScore = subject.maxScore else {
                return nil
            }
            return "\(ResultFormat.formatScore(score, locale: locale))/\(ResultFormat.formatScore(maxScore, locale: locale))"
        }()
        let manualText = ResultStrings.text("result.subjects.manualChip", locale: locale)
        let pendingText = ResultStrings.text("result.stats.pending", locale: locale)
        let countItems = buildCountMeta(stats: subject.stats, includePending: true, locale: locale)

        var probeParts = [name]
        if subject.pendingReview {
            probeParts.append(manualText)
            probeParts.append(pendingText)
        } else if let scoreText {
            probeParts.append("\(scoreLabel): \(scoreText)")
        }
        probeParts.append(contentsOf: countItems.map { "\($0.label): \($0.value)" })

        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 10) {
                Text(name)
                    .font(.headline)
                    .foregroundStyle(palette.textColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)

                if subject.pendingReview {
                    VStack(alignment: .trailing, spacing: 4) {
                        ResultStatusChip(title: manualText, tone: .neutral, symbolName: "pencil.line")
                        ResultStatusChip(title: pendingText, tone: .warning, symbolName: "clock")
                    }
                } else if let scoreText {
                    Text("\(scoreLabel): \(scoreText)")
                        .font(.subheadline.weight(.bold).monospacedDigit())
                        .foregroundStyle(palette.textColor)
                        .multilineTextAlignment(.trailing)
                }
            }

            if !countItems.isEmpty {
                ResultInlineMetaRow(items: countItems)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.bgSubtleColor))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(cardId)
        .resultProbe(id: cardId, label: probeParts.joined(separator: " | "))
    }
}

struct ResultVactComponentsSection: View {
    let components: [AttemptResultVactComponentItem]
    let subjectOptions: [String: CatalogOptionItem]
    let locale: AppLocale

    var body: some View {
        ResultSectionCard(
            title: ResultStrings.text("result.vact.componentsTitle", locale: locale),
            subtitle: ResultStrings.text("result.vact.componentsCaption", locale: locale),
            identifier: "result.vact.section"
        ) {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(Array(components.enumerated()), id: \.element.id) { index, comp in
                    VactComponentCard(
                        component: comp,
                        index: index,
                        subjectOptions: subjectOptions,
                        locale: locale
                    )
                }
            }
        }
    }
}

private struct VactComponentCard: View {
    let component: AttemptResultVactComponentItem
    let index: Int
    let subjectOptions: [String: CatalogOptionItem]
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette
    @State private var isExpanded: Bool = false

    var body: some View {
        let label = ResultFormat.resolveVactComponentLabel(
            code: component.componentCode,
            subjectOptions: subjectOptions,
            locale: locale
        )
        let rateText = ResultFormat.formatScore(component.correctRate, locale: locale)
        let ratioText = ResultStrings.text(
            "result.vact.correctRatio",
            locale: locale,
            replacements: [
                "correct": String(component.correctCount),
                "total": String(component.totalCount),
                "rate": rateText
            ]
        )
        let cardId = "result.vact.component.\(component.componentCode.isEmpty ? String(index) : component.componentCode)"
        let toggleId = "\(cardId).toggleSubsections"
        let toggleLabel = ResultStrings.text(
            isExpanded ? "result.vact.collapseSubsections" : "result.vact.expandSubsections",
            locale: locale
        )

        VStack(alignment: .leading, spacing: 10) {
            Text(label)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)

            Text(ratioText)
                .font(.headline.weight(.bold).monospacedDigit())
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)

            GeometryReader { proxy in
                let clamped = min(max(component.correctRate / 100.0, 0), 1)
                ZStack(alignment: .leading) {
                    Capsule().fill(palette.borderColor)
                    Capsule()
                        .fill(palette.primaryFillColor)
                        .frame(width: max(0, proxy.size.width * clamped))
                }
            }
            .frame(height: 6)
            .accessibilityHidden(true)

            if !component.subsections.isEmpty {
                Divider()

                Button {
                    isExpanded.toggle()
                } label: {
                    HStack(spacing: 8) {
                        Text(toggleLabel)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.primaryTextColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(palette.primaryTextColor)
                            .accessibilityHidden(true)
                    }
                    .padding(.horizontal, 12)
                    .frame(minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.bgPageColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier(toggleId)
                .accessibilityValue(isExpanded ? "expanded" : "collapsed")
                .resultProbe(
                    id: toggleId,
                    label: toggleLabel,
                    value: isExpanded ? "expanded" : "collapsed",
                    isButton: true,
                    isEnabled: true,
                    isSelected: isExpanded,
                    action: { isExpanded.toggle() }
                )

                if isExpanded {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(Array(component.subsections.enumerated()), id: \.element.id) { subIndex, sub in
                            let subLabel = ResultFormat.resolveVactSubsectionLabel(
                                subsection: sub,
                                index: subIndex,
                                totalCount: component.subsections.count,
                                locale: locale
                            )
                            let questionCount = sub.questionCount ?? sub.totalCount
                            let countLabel = ResultStrings.plural(
                                "result.vact.subsectionQuestions",
                                count: questionCount,
                                locale: locale
                            )
                            let subRatio = ResultStrings.text(
                                "result.vact.correctRatio",
                                locale: locale,
                                replacements: [
                                    "correct": String(sub.correctCount),
                                    "total": String(sub.totalCount),
                                    "rate": ResultFormat.formatScore(sub.correctRate, locale: locale)
                                ]
                            )
                            let subId = "\(cardId).subsection.\(subIndex)"

                            VStack(alignment: .leading, spacing: 4) {
                                HStack(alignment: .top, spacing: 8) {
                                    Text(subLabel)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(palette.textColor)
                                        .fixedSize(horizontal: false, vertical: true)
                                        .frame(maxWidth: .infinity, alignment: .leading)

                                    Text(countLabel)
                                        .font(.caption)
                                        .foregroundStyle(palette.textMutedColor)
                                        .monospacedDigit()
                                }

                                Text(subRatio)
                                    .font(.subheadline.weight(.medium).monospacedDigit())
                                    .foregroundStyle(palette.textSecondaryColor)
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(palette.bgPageColor))
                            .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
                            .accessibilityElement(children: .combine)
                            .accessibilityIdentifier(subId)
                            .resultProbe(id: subId, label: "\(subLabel) | \(countLabel) | \(subRatio)")
                        }
                    }
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.bgSubtleColor))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(palette.borderColor, lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(cardId)
        .resultProbe(id: cardId, label: "\(label) | \(ratioText)")
    }
}

private struct InlineMetaItem: Hashable {
    let label: String
    let value: String
}

private func buildCountMeta(
    stats: AttemptResultStats?,
    includePending: Bool,
    locale: AppLocale
) -> [InlineMetaItem] {
    guard let stats else { return [] }
    var items: [InlineMetaItem] = [
        InlineMetaItem(
            label: ResultStrings.text("result.stats.correct", locale: locale),
            value: String(stats.correctCount)
        )
    ]
    if stats.partialCount > 0 {
        items.append(
            InlineMetaItem(
                label: ResultStrings.text("result.stats.partial", locale: locale),
                value: String(stats.partialCount)
            )
        )
    }
    items.append(
        InlineMetaItem(
            label: ResultStrings.text("result.stats.wrong", locale: locale),
            value: String(stats.wrongCount)
        )
    )
    items.append(
        InlineMetaItem(
            label: ResultStrings.text("result.stats.blank", locale: locale),
            value: String(stats.blankCount)
        )
    )
    if includePending && stats.pendingReviewCount > 0 {
        items.append(
            InlineMetaItem(
                label: ResultStrings.text("result.stats.pending", locale: locale),
                value: String(stats.pendingReviewCount)
            )
        )
    }
    return items
}

private struct ResultInlineMetaRow: View {
    let items: [InlineMetaItem]

    @Environment(\.brandPalette) private var palette

    var body: some View {
        WrappingHStackLayout(horizontalSpacing: 12, verticalSpacing: 6) {
            ForEach(items, id: \.self) { item in
                HStack(spacing: 4) {
                    Text("\(item.label):")
                        .font(.caption)
                        .foregroundStyle(palette.textSecondaryColor)
                    Text(item.value)
                        .font(.caption.weight(.semibold).monospacedDigit())
                        .foregroundStyle(palette.textColor)
                }
            }
        }
    }
}
